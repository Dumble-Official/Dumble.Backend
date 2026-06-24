import hmac
import logging
import os
import re
import tempfile
import threading
from contextlib import asynccontextmanager
from types import SimpleNamespace

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

from fastapi import BackgroundTasks, FastAPI, File, Form, HTTPException, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from starlette.background import BackgroundTask
import asyncio
import httpx
import json as _json

from ai_engine import respond, fc_loop_stream, init_memory, _build_system_prompt
from key_manager import key_manager
from memory_store import get_user_memory
from vision_engine import analyze_media, analyze_media_stream
from content_filter import filter_message, get_block_message
from voice_engine import transcribe, ALLOWED_AUDIO_EXT

logging.basicConfig(
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
    level=logging.INFO,
)
logger = logging.getLogger(__name__)

_raw_keys = os.getenv("GEMINI_API_KEYS", "") or os.getenv("GEMINI_API_KEY", "")
GEMINI_API_KEYS_LIST = [k.strip() for k in _raw_keys.split(",") if k.strip()]

# Shared HMAC the gateway signs the X-Internal-Secret header with.
# Empty value is treated as a misconfiguration, not as "auth disabled" —
# _check_auth returns 503 until it's set. Previously this silently
# disabled auth and left the service open to anyone on the network.
INTERNAL_SECRET = os.getenv("INTERNAL_API_SECRET", "")

# #4 — base URL of the schedule service (e.g. http://schedule:8186/api). When set,
# an AI-generated plan (schedule_changes) is best-effort pushed into the client's
# schedule via the internal chatbot endpoint, authed with the shared INTERNAL_SECRET.
SCHEDULE_SERVICE_URL = os.getenv("SCHEDULE_SERVICE_URL", "").rstrip("/")

# CORS: FitCoach lives behind the gateway on a private compose network,
# so by default no browser origin is allowed. Set ALLOWED_ORIGINS in the
# env (comma-separated) to enable direct browser access — only used by
# the AI team's local fitcoach_ui.html during development.
_cors_raw       = os.getenv("ALLOWED_ORIGINS", "")
ALLOWED_ORIGINS = [o.strip() for o in _cors_raw.split(",") if o.strip()]

MAX_CHAT_HISTORY = 10
MAX_FILE_MB      = 100
MAX_AUDIO_MB     = 25
ALLOWED_EXT      = {"mp4", "mov", "avi", "mkv", "jpg", "jpeg", "png", "webp", "bmp"}

MIME_TO_EXT = {
    "video/quicktime": "mov", "video/mp4": "mp4",
    "video/x-msvideo": "avi", "video/x-matroska": "mkv",
    "image/jpeg": "jpg", "image/png": "png",
    "image/webp": "webp", "image/bmp": "bmp",
}

                                                                                

def _check_auth(request: Request):
    """Reject everything unless the gateway-set X-Internal-Secret matches.

    Fail-CLOSED: empty INTERNAL_SECRET is treated as a misconfiguration
    and every request gets 503. The previous fail-open behaviour meant a
    typo'd / unset env var silently disabled the only auth on the service.
    """
    if not INTERNAL_SECRET:
        raise HTTPException(503, "FitCoach is misconfigured: INTERNAL_API_SECRET not set")
    token = request.headers.get("X-Internal-Secret", "")
    if not hmac.compare_digest(token, INTERNAL_SECRET):
        raise HTTPException(401, "Unauthorized")


def _require_user_id(request: Request) -> str:
    """Return the gateway-injected user identity, or 401.

    The gateway runs JwtAuthenticationFilter + CoachEntitlementFilter
    BEFORE proxying; the user's JWT is verified there and the resolved
    UUID is put into X-User-Id. We trust ONLY this header — body / form
    `user_id` is ignored because a logged-in user A would otherwise be
    able to read/write user B's memory by spoofing the body field.
    """
    uid = (request.headers.get("X-User-Id") or "").strip()
    if not uid:
        raise HTTPException(401, "Missing X-User-Id (must come from the gateway)")
    return uid


# #4 — schedule_contract day_index is 0=Monday..6=Sunday; the schedule service's
# Weekday enum is SUN..SAT, so map by index.
_WEEKDAY_BY_INDEX = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"]


def _schedule_changes_to_items(schedule_changes: dict) -> list[dict]:
    """Translate FitCoach schedule_changes (days[].exercises[]) into the schedule
    service's AddItemRequest shape: {tableType, weekday, content}."""
    items: list[dict] = []
    for day in (schedule_changes.get("days") or []):
        if day.get("rest_day"):
            continue
        idx = day.get("day_index", -1)
        if not isinstance(idx, int) or idx < 0 or idx > 6:
            continue
        weekday = _WEEKDAY_BY_INDEX[idx]
        for ex in (day.get("exercises") or []):
            name = (ex.get("name") or "").strip()
            if not name:
                continue
            content = f"{name} — {ex.get('sets', 3)}x{ex.get('reps', '10')}"
            rest = ex.get("rest_sec")
            if rest:
                content += f", rest {rest}s"
            notes = (ex.get("notes") or "").strip()
            if notes:
                content += f" ({notes})"
            items.append({"tableType": "EXERCISE", "weekday": weekday, "content": content})
    return items


async def _sync_schedule(user_id: str, schedule_changes: dict | None) -> None:
    """Best-effort push of an AI-generated plan into the schedule service. Never
    raises — a schedule hiccup must not affect the chat response.

    Note: the schedule endpoint's `replace` is all-or-nothing (true clears ALL
    chatbot items). full_replace maps to replace=true; update_days maps to
    replace=false (append), which the schedule service can't scope per-day."""
    if not (schedule_changes and user_id and SCHEDULE_SERVICE_URL and INTERNAL_SECRET):
        return
    try:
        items = _schedule_changes_to_items(schedule_changes)
        if not items:
            return
        body = {"replace": schedule_changes.get("action") == "full_replace", "items": items}
        url = f"{SCHEDULE_SERVICE_URL}/internal/clients/{user_id}/chatbot/items"
        async with httpx.AsyncClient(timeout=5.0) as client:
            resp = await client.post(url, headers={"X-Internal-Secret": INTERNAL_SECRET}, json=body)
        if resp.status_code >= 300:
            logger.warning("Schedule sync user=%s -> HTTP %s: %s",
                           user_id, resp.status_code, resp.text[:200])
    except Exception as exc:
        logger.warning("Schedule sync failed for user=%s: %s", user_id, exc)

                                                                                

def _detect_lang(history: list, message: str) -> str:
    def _is_arabic(text: str) -> bool:
        return any("\u0600" <= c <= "\u06ff" for c in text)

    text = message.strip() if message else ""
    if _is_arabic(text):
        return "ar"
                                                                         
    for entry in reversed(history):
        content = entry.get("content", "") if isinstance(entry, dict) else ""
        if _is_arabic(content):
            return "ar"
        if content.strip():
            return "en"
    return "en"

_ERR_MSGS = {
    "general":      {"ar": "عذراً، حدث خطأ مؤقت. حاول تاني بعد شوية. 🙏",
                     "en": "Sorry, something went wrong. Please try again in a moment. 🙏"},
    "rate_limit":   {"ar": "الخدمة وصلت للحد المسموح دلوقتي. حاول تاني بعد دقيقة أو اتنين. ⏳",
                     "en": "The service has reached its limit right now. Please try again in a minute or two. ⏳"},
    "busy":         {"ar": "في ضغط على الخدمة دلوقتي. جرب تاني بعد شوية. ⏳",
                     "en": "The service is busy right now. Please try again shortly. ⏳"},
    "vision":       {"ar": "عذراً، حدث خطأ أثناء تحليل الملف. حاول تاني. 🙏",
                     "en": "Sorry, something went wrong during analysis. Please try again. 🙏"},
    "voice_unclear":{"ar": "معرفتش أفهم الصوت. ممكن تكتب رسالتك بدل كده؟ 🎙️",
                     "en": "I couldn't make out the audio. Could you type your message instead? 🎙️"},
    "voice_error":  {"ar": "حدث خطأ أثناء معالجة الصوت. حاول تاني. 🙏",
                     "en": "Something went wrong processing the audio. Please try again. 🙏"},
}

def _err(key: str, lang: str) -> str:
    return _ERR_MSGS.get(key, _ERR_MSGS["general"])[lang]

def _err_key_from_exc(exc: Exception) -> str:
    s = str(exc)
    if "429" in s or "rate" in s.lower(): return "rate_limit"
    if "502" in s or "503" in s:          return "busy"
    return "general"

def _check_keys():
    if not GEMINI_API_KEYS_LIST:
        raise HTTPException(500, "GEMINI_API_KEY not set")

                                                                                

@asynccontextmanager
async def lifespan(app: FastAPI):
    \
\
\
    import os as _os
    _raw  = _os.getenv("GEMINI_API_KEYS", "") or _os.getenv("GEMINI_API_KEY", "")
    _keys = [k.strip() for k in _raw.split(",") if k.strip()]
    key_manager.reload(_keys)
    logger.info("KeyManager: %d key(s) loaded", len(_keys))

    if INTERNAL_SECRET:
        logger.info("Auth: X-Internal-Secret enabled")
    else:
        logger.warning("Auth: INTERNAL_API_SECRET not set — endpoints are open (dev mode)")
    logger.info("CORS: allowed_origins=%s", ALLOWED_ORIGINS)

                                                              
    async def _warm():
        await asyncio.get_running_loop().run_in_executor(None, init_memory)
    asyncio.create_task(_warm())

    yield
                                     

app = FastAPI(title="FitCoach AI", version="1.3", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins     = ALLOWED_ORIGINS,
    allow_credentials = True,
    allow_methods     = ["*"],
    allow_headers     = ["*"],
)

                                                                                

class ChatRequest(BaseModel):
    user_id:       str
    message:       str
    profile:       dict = {}
    history:       list = []
    progress_logs: list = []
    plan_cache:    dict = {}

class ChatResponse(BaseModel):
    reply:              str
    tools_used:         list[str] = []
    profile:            dict = {}
    progress_logs:      list = []
    plan_cache:         dict = {}
    assistant_entry_id: str  = ""
    schedule_changes:   dict | None = None                                         

class FeedbackRequest(BaseModel):
    user_id:  str
    entry_id: str
    feedback: int                           
    note:     str = ""

class FeedbackResponse(BaseModel):
    ok:      bool
    message: str = ""

class VoiceResponse(BaseModel):
    reply:              str
    transcript:         str
    tools_used:         list[str] = []
    profile:            dict = {}
    progress_logs:      list = []
    plan_cache:         dict = {}
    assistant_entry_id: str  = ""
    schedule_changes:   dict | None = None

class AnalyzeResponse(BaseModel):
    reply:              str
    assistant_entry_id: str = ""                                                      

                                                                                

async def _run_chat(req, msg: str, lang: str) -> tuple:
    \
\
\
\
    api_key = key_manager.get()
    result = filter_message(msg, lang, api_key=api_key)
    if result.blocked:
        logger.warning("Blocked [%s] user=%s hard=%s", result.reason, req.user_id, result.is_hard)
        return (result.message,
                [], req.profile, req.plan_cache, req.progress_logs, "", None)
    try:
        loop = asyncio.get_running_loop()
        return await loop.run_in_executor(
            None,
            lambda: respond(
                api_key       = api_key,
                user_id       = req.user_id,
                message       = msg,
                profile       = req.profile,
                history       = req.history[-MAX_CHAT_HISTORY:],
                progress_logs = req.progress_logs,
                plan_cache    = req.plan_cache,
            )
        )
    except Exception as exc:
        logger.error("Chat error user=%s: %s", req.user_id, exc)
        return (_err(_err_key_from_exc(exc), lang),
                [], req.profile, req.plan_cache, req.progress_logs, "", None)

                                                                                

@app.post("/chat", response_model=ChatResponse)
async def chat(req: ChatRequest, request: Request, background_tasks: BackgroundTasks):
    _check_keys()
    _check_auth(request)
    # Override the body's user_id with the gateway-set X-User-Id — see
    # _require_user_id docstring for why we ignore the body field.
    req.user_id = _require_user_id(request)
    msg = req.message.strip()
    if not msg:
        raise HTTPException(400, "Empty message")

    lang = _detect_lang(req.history, msg)
    logger.info("CHAT user=%s lang=%s hist=%d msg=%r",
                req.user_id, lang, len(req.history or []), msg[:80])
    reply, tools_used, new_profile, new_plan_cache, new_progress_logs, entry_id, sched =\
        await _run_chat(req, msg, lang)

    if sched:
        background_tasks.add_task(_sync_schedule, req.user_id, sched)

    return ChatResponse(
        reply              = reply,
        tools_used         = tools_used,
        profile            = new_profile,
        plan_cache         = new_plan_cache,
        progress_logs      = new_progress_logs,
        assistant_entry_id = entry_id,
        schedule_changes   = sched,
    )

                                                                                

@app.post("/chat/stream")
async def chat_stream(req: ChatRequest, request: Request):
    \
\
\
\
\
\
\
    _check_keys()
    _check_auth(request)
    # Override body user_id with the gateway-set X-User-Id (anti-IDOR).
    req.user_id = _require_user_id(request)
    msg = req.message.strip()
    if not msg:
        raise HTTPException(400, "Empty message")

    lang = _detect_lang(req.history, msg)
    logger.info("CHAT-STREAM user=%s lang=%s hist=%d msg=%r",
                req.user_id, lang, len(req.history or []), msg[:80])

    fres = filter_message(msg, lang, api_key=key_manager.get())
    if fres.blocked:
        block_msg = fres.message
        logger.warning("Blocked [%s] user=%s", fres.reason, req.user_id)

        async def _blocked():
            yield f"data: {block_msg}\n\n"
            yield "data: [DONE]\n\n"
        return StreamingResponse(_blocked(), media_type="text/event-stream")

    mem          = get_user_memory(req.user_id)
    mem_entries  = mem.retrieve(msg, k=4)
    mem_ctx      = "\n".join(e["text"] for e in mem_entries) if mem_entries else ""
    system_msg   = _build_system_prompt(req.user_id, msg, req.profile, mem_ctx, history=req.history)

    messages = (
        [{"role": "system", "content": system_msg}]
        + req.history[-MAX_CHAT_HISTORY:]
        + [{"role": "user", "content": msg}]
    )
    state = {
        "user_id":       req.user_id,
        "profile":       req.profile,
        "progress_logs": req.progress_logs,
        "plan_cache":    req.plan_cache,
        "schedule_changes": None,
    }
    # Populated at stream end so the post-stream background task can sync the plan.
    sync_holder: dict = {}

    async def generate():
        api_key    = key_manager.get()
        full_reply = ""
        tools_used = []
        final_state = state

        try:
                                                                  
            loop = asyncio.get_running_loop()
            queue: asyncio.Queue = asyncio.Queue()

            def _producer():
                try:
                    for chunk_type, value in fc_loop_stream(api_key, messages, state):
                        loop.call_soon_threadsafe(queue.put_nowait, (chunk_type, value))
                except Exception as exc:
                    loop.call_soon_threadsafe(queue.put_nowait, ("error", str(exc)))
                finally:
                    loop.call_soon_threadsafe(queue.put_nowait, ("__done__", None))

            t = threading.Thread(target=_producer, daemon=True)
            t.start()

            while True:
                chunk_type, value = await queue.get()
                if chunk_type == "__done__":
                    break
                if chunk_type == "text":
                    full_reply += value
                    safe = value.replace("\n", "\\n")
                    yield f"data: {safe}\n\n"
                elif chunk_type == "tool_progress":
                                                                    
                                                                                 
                    safe = value.replace("\n", "\\n")
                    yield f"data: [PROGRESS]{safe}\n\n"
                elif chunk_type == "tools":
                    tools_used = value
                elif chunk_type == "state":
                    final_state = value
                elif chunk_type == "error":
                    err_key = _err_key_from_exc(Exception(value))
                    yield f"data: {_err(err_key, lang)}\n\n"
                    yield "data: [DONE]\n\n"
                    return

        except Exception as exc:
            logger.error("Stream error user=%s: %s", req.user_id, exc)
            yield f"data: {_err(_err_key_from_exc(exc), lang)}\n\n"
            yield "data: [DONE]\n\n"
            return

                         
                                                                        
        full_reply = re.sub(r"(?im)^THOUGHT:.*?(?=\n\n|\Z)",   "", full_reply).strip()
        full_reply = re.sub(r"(?im)^REASONING:.*?(?=\n\n|\Z)", "", full_reply).strip()
        full_reply = re.sub(r"(?im)^THINKING:.*?(?=\n\n|\Z)",  "", full_reply).strip()
        full_reply = re.sub(r"^[-—]{3,}\s*", "", full_reply, flags=re.MULTILINE).strip()
        entry_id = mem.store(full_reply, role="assistant") if len(full_reply.strip()) >= 15 else ""
        if len(msg.strip()) >= 15:
            mem.store(msg, role="user")

        sync_holder["user_id"] = req.user_id
        sync_holder["sched"]   = final_state.get("schedule_changes")
        meta = _json.dumps({
            "tools_used":         tools_used,
            "profile":            final_state.get("profile", req.profile),
            "plan_cache":         final_state.get("plan_cache", req.plan_cache),
            "progress_logs":      final_state.get("progress_logs", req.progress_logs),
            "assistant_entry_id": entry_id,
            "schedule_changes":   final_state.get("schedule_changes"),
        }, ensure_ascii=False)
        yield f"data: [META]{meta}\n\n"
        yield "data: [DONE]\n\n"

    async def _sync_after_stream():
        await _sync_schedule(sync_holder.get("user_id"), sync_holder.get("sched"))

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
        background=BackgroundTask(_sync_after_stream),
    )

                                                                                

@app.post("/feedback", response_model=FeedbackResponse)
def feedback(req: FeedbackRequest, request: Request):
    _check_auth(request)
    # Anti-IDOR: gateway identity beats body identity. Otherwise user A
    # could rewrite the feedback weights on user B's stored answers.
    req.user_id = _require_user_id(request)
    if req.feedback not in (-1, 0, 1):
        raise HTTPException(400, "feedback must be 1, 0, or -1")
    if not req.entry_id:
        raise HTTPException(400, "entry_id is required")

    mem = get_user_memory(req.user_id)
    ok  = mem.apply_feedback(req.entry_id, req.feedback, req.note)
    if not ok:
        return FeedbackResponse(ok=False, message="Entry not found")

    label = {1: "positive", -1: "negative", 0: "neutral"}[req.feedback]
    logger.info("Feedback: user=%s entry=%s → %s", req.user_id, req.entry_id, label)
    return FeedbackResponse(ok=True, message=f"Feedback recorded as {label}")

                                                                                

AUDIO_MIME_TO_EXT = {
    "audio/mpeg":  "mp3", "audio/mp3":   "mp3",
    "audio/wav":   "wav", "audio/x-wav": "wav",
    "audio/ogg":   "ogg", "audio/mp4":   "m4a",
    "audio/x-m4a": "m4a", "audio/aac":   "aac",
    "audio/flac":  "flac","audio/webm":  "webm",
    "video/webm":  "webm",
}

@app.post("/voice", response_model=VoiceResponse)
async def voice(
    request:       Request,
    background_tasks: BackgroundTasks,
    file:          UploadFile = File(...),
    user_id:       str        = Form(default=""),
    profile:       str        = Form(default="{}"),
    history:       str        = Form(default="[]"),
    progress_logs: str        = Form(default="[]"),
    plan_cache:    str        = Form(default="{}"),
):
    _check_keys()
    _check_auth(request)
    # Anti-IDOR: shadow the Form-supplied user_id with the gateway's value.
    user_id = _require_user_id(request)

    try:
        profile_dict       = _json.loads(profile)
        history_list       = _json.loads(history)
        progress_logs_list = _json.loads(progress_logs)
        plan_cache_dict    = _json.loads(plan_cache)
    except Exception:
        raise HTTPException(400, "Invalid JSON in form fields")

    filename = file.filename or ""
    ext      = filename.rsplit(".", 1)[-1].lower() if "." in filename else ""
    if ext not in ALLOWED_AUDIO_EXT:
        ext = AUDIO_MIME_TO_EXT.get((file.content_type or "").lower(), ext)
    if ext not in ALLOWED_AUDIO_EXT:
        raise HTTPException(400, f"Unsupported audio format '{ext}'. "
                                 f"Allowed: {', '.join(sorted(ALLOWED_AUDIO_EXT))}")

    data = await file.read()
    if len(data) / 1024 / 1024 > MAX_AUDIO_MB:
        raise HTTPException(413, f"Audio too large. Max {MAX_AUDIO_MB}MB.")

    with tempfile.NamedTemporaryFile(delete=False, suffix=f".{ext}") as tmp:
        tmp.write(data); tmp_path = tmp.name

                                                                                  
                                                    
    lang = _detect_lang(history_list, "")
    try:
        # transcribe() uses the synchronous `requests` library and can take
        # 5–30s. Calling it directly from an async handler would block the
        # whole event loop — no other request can be served during that
        # window. Run it on the default executor so other concurrent
        # requests stay responsive.
        loop       = asyncio.get_running_loop()
        transcript = await loop.run_in_executor(
            None,
            lambda: transcribe(api_key=key_manager.get(), file_path=tmp_path, ext=ext),
        )
    except Exception as exc:
        logger.error("Transcription error: %s", exc)
        return VoiceResponse(reply=_err("voice_error", lang), transcript="")
    finally:
        try: os.unlink(tmp_path)
        except Exception: pass

    if not transcript:
        return VoiceResponse(reply=_err("voice_unclear", lang), transcript="")

    lang = _detect_lang(history_list, transcript)

    _req = SimpleNamespace(
        user_id       = user_id,
        profile       = profile_dict,
        history       = history_list,
        progress_logs = progress_logs_list,
        plan_cache    = plan_cache_dict,
    )

    reply, tools_used, new_profile, new_plan_cache, new_progress_logs, entry_id, sched =\
        await _run_chat(_req, transcript, lang)

    if sched:
        background_tasks.add_task(_sync_schedule, user_id, sched)

    return VoiceResponse(
        reply              = reply,
        transcript         = transcript,
        tools_used         = tools_used,
        profile            = new_profile,
        plan_cache         = new_plan_cache,
        progress_logs      = new_progress_logs,
        assistant_entry_id = entry_id,
        schedule_changes   = sched,
    )

                                                                                

@app.post("/analyze", response_model=AnalyzeResponse)
async def analyze(
    request:          Request,
    file:             UploadFile = File(...),
    message:          str        = Form(default=""),
    is_first_message: bool       = Form(default=False),
                                                                    
    profile:          str        = Form(default="{}"),
    history:          str        = Form(default="[]"),
):
    _check_keys()
    _check_auth(request)

    try:
        profile_dict = _json.loads(profile)
        history_list = _json.loads(history)
    except Exception:
        raise HTTPException(400, "Invalid JSON in profile/history")

    filename = file.filename or ""
    ext      = filename.rsplit(".", 1)[-1].lower() if "." in filename else ""
    if ext not in ALLOWED_EXT:
        ext = MIME_TO_EXT.get((file.content_type or "").lower(), ext)
    if ext not in ALLOWED_EXT:
        raise HTTPException(400, f"Unsupported file type '{ext}'.")

    data = await file.read()
    if len(data) / 1024 / 1024 > MAX_FILE_MB:
        raise HTTPException(413, f"File too large. Max {MAX_FILE_MB}MB.")

    lang    = _detect_lang(history_list, message)
    # Memory writes are tied to user_id — require the gateway-set value
    # so the analysis reply isn't filed into another user's memory bucket.
    user_id = _require_user_id(request)

    tmp_path = None
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=f".{ext}") as tmp:
            tmp.write(data)
            tmp_path = tmp.name
        reply = await analyze_media(
            api_key          = key_manager.get(),
            file_path        = tmp_path,
            user_message     = message.strip(),
            is_first_message = is_first_message,
            profile          = profile_dict,
            history          = history_list,
        )
    except Exception as exc:
        logger.error("Vision error: %s", exc)
        reply = _err("vision", lang)
    finally:
        if tmp_path:
            try: os.unlink(tmp_path)
            except Exception: pass

                                                         
    entry_id = ""
    if user_id and reply and len(reply.strip()) >= 15:
        mem      = get_user_memory(user_id)
        entry_id = mem.store(reply, role="assistant")

    return AnalyzeResponse(reply=reply, assistant_entry_id=entry_id)

                                                                                

@app.post("/analyze/stream")
async def analyze_stream(
    request:          Request,
    file:             UploadFile = File(...),
    message:          str        = Form(default=""),
    is_first_message: bool       = Form(default=False),
    profile:          str        = Form(default="{}"),
    history:          str        = Form(default="[]"),
):
    _check_keys()
    _check_auth(request)

    try:
        profile_dict = _json.loads(profile)
        history_list = _json.loads(history)
    except Exception:
        raise HTTPException(400, "Invalid JSON in profile/history")

    filename = file.filename or ""
    ext      = filename.rsplit(".", 1)[-1].lower() if "." in filename else ""
    if ext not in ALLOWED_EXT:
        ext = MIME_TO_EXT.get((file.content_type or "").lower(), ext)
    if ext not in ALLOWED_EXT:
        raise HTTPException(400, f"Unsupported file type '{ext}'.")

    data = await file.read()
    if len(data) / 1024 / 1024 > MAX_FILE_MB:
        raise HTTPException(413, f"File too large. Max {MAX_FILE_MB}MB.")

    # Memory writes use this — gateway-set, not client-supplied.
    user_id = _require_user_id(request)

    tmp_path = None
    with tempfile.NamedTemporaryFile(delete=False, suffix=f".{ext}") as tmp:
        tmp.write(data)
        tmp_path = tmp.name

    async def generate():
        full_reply = ""
        try:
            async for chunk in analyze_media_stream(
                api_key          = key_manager.get(),
                file_path        = tmp_path,
                user_message     = message.strip(),
                is_first_message = is_first_message,
                profile          = profile_dict,
                history          = history_list,
            ):
                full_reply += chunk.replace("\\n", "\n")
                yield f"data: {chunk.replace(chr(10), chr(92)+'n')}\n\n"
                await asyncio.sleep(0)
        except Exception as exc:
            logger.error("Stream vision error: %s", exc)
            yield f"data: {_err('vision', _detect_lang(history_list, message))}\n\n"
        finally:
            if tmp_path:
                try: os.unlink(tmp_path)
                except Exception: pass

                                                            
        entry_id = ""
        if user_id and full_reply and len(full_reply.strip()) >= 15:
            mem      = get_user_memory(user_id)
            entry_id = mem.store(full_reply, role="assistant")

        meta = _json.dumps({"assistant_entry_id": entry_id}, ensure_ascii=False)
        yield f"data: [META]{meta}\n\n"
        yield "data: [DONE]\n\n"

    return StreamingResponse(generate(), media_type="text/event-stream",
                             headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"})

                                                                                

@app.get("/health")
def health():
    keys_count = key_manager.count if hasattr(key_manager, "count") else len(GEMINI_API_KEYS_LIST)
    return {
        "status":    "ok" if keys_count > 0 else "degraded",
        "keys_loaded": keys_count,
        "keys_ok":   keys_count > 0,
    }

