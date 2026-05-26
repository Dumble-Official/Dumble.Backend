import json
import logging
import re
import time
from typing import Optional

import httpx

from prompts import SYSTEM_PROMPT
from key_manager import key_manager
from memory_store import get_user_memory
from tools import (
    TOOL_SCHEMAS,
    exec_update_profile,
    exec_get_bmi,
    exec_get_calories,
    exec_get_workout_plan,
    exec_get_nutrition_plan,
    exec_get_progress,
    exec_log_weight,
    exec_get_recommendations,
    exec_update_workout_day,
)
from schedule_contract import (
    build_full_replace,
    build_update_days,
    _normalise_day,
)

logger = logging.getLogger(__name__)

GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"

# Fallback chain — ordered by preference. Earlier entries (gemini-3-*)
# don't exist in the public Gemini API yet; they were placeholders and
# would 404 on every call, collapsing the "3-model fallback" into one
# real model. Use shipped model IDs only.
FC_MODELS: list[str]   = ["gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash"]
PLAN_MODELS: list[str] = ["gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash"]

MAX_TOKENS_CHAT = 2000
MAX_TOKENS_PLAN = 2500
MAX_FC_ROUNDS   = 4
TEMPERATURE     = 0.4 

def _post(api_key: str, models: list[str], messages: list, max_tokens: int,
          temperature: float, tools: Optional[list] = None) -> dict:
    last_err = "No models tried"
    body: dict = {
        "messages":         messages,
        "max_tokens":       max_tokens,
        "temperature":      temperature,
        # Disable Gemini's "thinking" budget on 2.5+ models so the response
        # tokens actually contain the reply instead of internal reasoning.
        # The previous body used `"thinking": {"type": "disabled"}` — that's
        # the Gemini-NATIVE field name, which the OpenAI-compat endpoint we
        # post to rejects with 400 "Unknown name 'thinking'". The compat
        # endpoint takes the OpenAI-style `reasoning_effort` knob.
        "reasoning_effort": "none",
    }
    if tools:
        body["tools"]       = tools
        body["tool_choice"] = "auto"

    # Track which keys we've already proven invalid this call so a tight
    # cycle of revoked keys can't spin forever.
    invalid_keys: set[str] = set()
    idx          = 0
    while idx < len(models):
        model = models[idx]
        try:
            resp = httpx.post(
                GEMINI_BASE_URL,
                headers={"Authorization": f"Bearer {api_key}",
                         "Content-Type": "application/json"},
                json={**body, "model": model},
                timeout=httpx.Timeout(10, read=90),
            )
            if resp.status_code == 429:
                key_manager.mark_rate_limited(api_key)
                wait = min(3 * (idx + 1), 10)
                logger.warning("429 on %s — waiting %ds", model, wait)
                time.sleep(wait)
                idx += 1
                continue
            if resp.status_code in (502, 503):
                logger.warning("HTTP %s on %s — waiting 2s", resp.status_code, model)
                time.sleep(2)
                idx += 1
                continue
            if resp.status_code == 401:
                # Don't fail the request — rotate to a fresh key and retry
                # the SAME model. One revoked key shouldn't kill in-flight
                # traffic; KeyManager already drops it from rotation.
                key_manager.mark_invalid(api_key)
                invalid_keys.add(api_key)
                try:
                    api_key = key_manager.get()
                except RuntimeError as exc:
                    raise RuntimeError("All API keys invalid") from exc
                if api_key in invalid_keys:
                    raise RuntimeError("All API keys invalid")
                logger.info("Rotated key after 401, retrying %s", model)
                continue  # same idx — retry this model with the new key
            if resp.status_code != 200:
                logger.warning("HTTP %s on %s", resp.status_code, model)
                idx += 1
                continue
            data    = resp.json()
            choices = data.get("choices") or []
            if not choices:
                logger.warning("Empty choices from %s", model)
                idx += 1
                continue
            logger.info("OK: %s", model)
            return data
        except httpx.ConnectTimeout:
            logger.warning("ConnectTimeout on %s", model); last_err = "ConnectTimeout"
            idx += 1
        except httpx.ReadTimeout:
            logger.warning("ReadTimeout on %s", model); last_err = "ReadTimeout"
            idx += 1
        except httpx.ConnectError as exc:
            logger.warning("ConnectError on %s: %s", model, exc); last_err = str(exc)
            idx += 1
        except RuntimeError:
            raise
        except Exception as exc:
            logger.warning("Exception on %s: %s", model, exc); last_err = str(exc)
            idx += 1

    raise RuntimeError(f"All models exhausted. Last: {last_err}")

def _post_plan(api_key: str, messages: list) -> str:
    data    = _post(api_key, PLAN_MODELS, messages, MAX_TOKENS_PLAN, 0.5)
    choices = data.get("choices") or []
    return choices[0]["message"]["content"] if choices else ""

def _stream_chunks(api_key: str, messages: list, tools: list):
    invalid_keys: set[str] = set()
    idx = 0
    while idx < len(FC_MODELS):
        model = FC_MODELS[idx]
        body = {
            "model":            model,
            "messages":         messages,
            "max_tokens":       MAX_TOKENS_CHAT,
            "temperature":      TEMPERATURE,
            "stream":           True,
            "tools":            tools,
            "tool_choice":      "auto",
            # Match _post — keep Gemini 2.5+ from spending response budget on
            # internal reasoning. With this set "none", a 2000-token cap leaves
            # all 2000 for the actual streamed answer.
            "reasoning_effort": "none",
        }
        try:
            with httpx.stream(
                "POST",
                GEMINI_BASE_URL,
                headers={"Authorization": f"Bearer {api_key}",
                         "Content-Type": "application/json"},
                json=body,
                timeout=httpx.Timeout(10, read=120),
            ) as resp:
                if resp.status_code == 429:
                    key_manager.mark_rate_limited(api_key)
                    wait = min(3 * (idx + 1), 10)
                    logger.warning("429 on %s — waiting %ds", model, wait)
                    time.sleep(wait)
                    idx += 1
                    continue
                if resp.status_code in (502, 503):
                    logger.warning("HTTP %s on %s — waiting 2s", resp.status_code, model)
                    time.sleep(2)
                    idx += 1
                    continue
                if resp.status_code == 401:
                    # Rotate to a fresh key and retry the same model rather
                    # than failing mid-stream. See _post for the same logic.
                    key_manager.mark_invalid(api_key)
                    invalid_keys.add(api_key)
                    try:
                        api_key = key_manager.get()
                    except RuntimeError as exc:
                        raise RuntimeError("All API keys invalid") from exc
                    if api_key in invalid_keys:
                        raise RuntimeError("All API keys invalid")
                    logger.info("Stream: rotated key after 401, retrying %s", model)
                    continue  # same idx
                if resp.status_code != 200:
                    logger.warning("HTTP %s on %s", resp.status_code, model)
                    idx += 1
                    continue
                for raw_line in resp.iter_lines():
                    if raw_line:
                        yield raw_line
                return
        except httpx.ConnectTimeout:
            logger.warning("ConnectTimeout on %s", model)
            idx += 1
        except httpx.ReadTimeout:
            logger.warning("ReadTimeout on %s", model)
            idx += 1
        except httpx.ConnectError as exc:
            logger.warning("ConnectError on %s: %s", model, exc)
            idx += 1
        except RuntimeError:
            raise

    raise RuntimeError("All models exhausted in stream.")

def _parse_stream_line(line) -> tuple:
    if isinstance(line, bytes):
        line = line.decode("utf-8", errors="replace")
    if not line.startswith("data:"):
        return "", False, []
    payload = line[5:].strip()
    if payload == "[DONE]":
        return "", True, []
    try:
        obj = json.loads(payload)
    except Exception:
        return "", False, []
    choices = obj.get("choices") or []
    if not choices:
        return "", False, []
    delta      = choices[0].get("delta", {})
    finish     = choices[0].get("finish_reason")
    text       = delta.get("content") or ""
    tool_calls = delta.get("tool_calls") or []
    is_done    = finish in ("stop", "tool_calls")
    return text, is_done, tool_calls

_TOOL_PROGRESS: dict[str, dict[str, str]] = {
    "get_workout_plan":   {"ar": "⏳ جاري تجهيز خطة التمرين...", "en": "⏳ Building your workout plan..."},
    "get_nutrition_plan": {"ar": "⏳ جاري تجهيز خطة الغذاء...", "en": "⏳ Building your nutrition plan..."},
    "get_recommendations":{"ar": "⏳ جاري تجهيز التوصيات...",   "en": "⏳ Getting your recommendations..."},
    "get_calories":       {"ar": "⏳ جاري حساب السعرات...",      "en": "⏳ Calculating your calories..."},
    "get_bmi":            {"ar": "⏳ جاري حساب الـ BMI...",       "en": "⏳ Calculating your BMI..."},
    "update_workout_day": {"ar": "⏳ جاري تعديل الخطة...",        "en": "⏳ Updating your plan..."},
}

def _tool_progress_msg(tool_name: str, lang: str) -> str:
    entry = _TOOL_PROGRESS.get(tool_name)
    if not entry:
        return ""
    return entry.get(lang, entry["en"])

def _detect_lang_from_messages(messages: list) -> str:
    for m in reversed(messages):
        if m.get("role") == "user":
            text = m.get("content") or ""
            if isinstance(text, str):
                return "ar" if any("\u0600" <= c <= "\u06ff" for c in text) else "en"
    return "ar"

def fc_loop_stream(api_key: str, messages: list, state: dict):
    msgs       = list(messages)
    tools_used = []

    for round_num in range(MAX_FC_ROUNDS + 1):
        accumulated_tool_calls: dict = {}
        text_buffer   = ""
        got_tool_call = False

        try:
            for raw_line in _stream_chunks(api_key, msgs, TOOL_SCHEMAS):
                text_delta, is_done, tc_deltas = _parse_stream_line(raw_line)

                if text_delta:
                    text_buffer += text_delta
                    yield ("text", text_delta)

                for tc in tc_deltas:
                    idx = tc.get("index", 0)
                    if idx not in accumulated_tool_calls:
                        accumulated_tool_calls[idx] = {
                            "id":       tc.get("id", f"call_{round_num}_{idx}"),
                            "type":     "function",
                            "function": {"name": "", "arguments": ""},
                        }
                    fn = tc.get("function", {})
                    if fn.get("name"):
                        accumulated_tool_calls[idx]["function"]["name"] += fn["name"]
                    if fn.get("arguments"):
                        accumulated_tool_calls[idx]["function"]["arguments"] += fn["arguments"]
                    got_tool_call = True

                if is_done:
                    break

        except RuntimeError as exc:
            yield ("error", str(exc))
            return
        except Exception as exc:
            logger.warning("Stream error round %d: %s", round_num, exc)
            yield ("error", str(exc))
            return

        if not got_tool_call:
            yield ("tools", tools_used)
            yield ("state", state)
            return

        tool_calls_list = [accumulated_tool_calls[i]
                           for i in sorted(accumulated_tool_calls)]
        msgs.append({
            "role":       "assistant",
            "content":    text_buffer or "",
            "tool_calls": tool_calls_list,
        })

        lang = _detect_lang_from_messages(msgs)

        # Run tools inline (instead of delegating to _run_tool_calls) so we
        # can yield each tool's progress message BEFORE the tool actually
        # runs. The previous structure pushed events into a list and
        # flushed them after _run_tool_calls returned — users saw the
        # spinner appear once the tool was already done, defeating its
        # purpose.
        for tc in tool_calls_list:
            tc_id   = tc.get("id") or f"call_{round_num}_{tc.get('index', 0)}"
            tc_name = tc["function"]["name"]

            progress_msg = _tool_progress_msg(tc_name, lang)
            if progress_msg:
                yield ("tool_progress", progress_msg)

            try:
                tc_args = json.loads(tc["function"].get("arguments", "{}"))
            except Exception:
                tc_args = {}
            logger.info("FC round %d: %s(%s)", round_num + 1, tc_name,
                        {k: v for k, v in tc_args.items() if k != "raw"})
            tools_used.append(tc_name)

            try:
                result, state = _execute_tool(tc_name, tc_args, state, api_key)
            except Exception as exc:
                logger.warning("Tool %s error: %s", tc_name, exc)
                result = json.dumps({"error": str(exc)})

            msgs.append({"role": "tool", "tool_call_id": tc_id, "content": result})

    yield ("tools", tools_used)
    yield ("state", state)

def _profile_context(profile: dict) -> str:
    if not profile: return "[No profile saved yet]"
    fields = []
    if profile.get("name"):             fields.append(f"name={profile['name']}")
    if profile.get("age"):              fields.append(f"age={profile['age']}")
    if profile.get("gender"):           fields.append(f"gender={profile['gender']}")
    if profile.get("weight"):           fields.append(f"weight={profile['weight']}kg")
    if profile.get("height"):           fields.append(f"height={profile['height']}cm")
    if profile.get("goal"):             fields.append(f"goal={profile['goal']}")
    if profile.get("injuries"):         fields.append(f"injuries={profile['injuries']}")
    if profile.get("workout_location"): fields.append(f"location={profile['workout_location']}")
    if profile.get("equipment"):        fields.append(f"equipment={profile['equipment']}")
    if profile.get("activity_level"):   fields.append(f"level={profile['activity_level']}")
    if profile.get("diet_type"):        fields.append(f"diet={profile['diet_type']}")
    return "[User profile — use this, never echo it as a list]\n" + " | ".join(fields)

def _feedback_context(user_id: str) -> str:
    mem      = get_user_memory(user_id)
    pos_list = mem.get_positive_summaries(limit=3)
    neg_list = mem.get_negative_topics(limit=3)

    parts = []
    if pos_list:
        parts.append(
            "[Replies this user rated positively — match this style and depth]\n"
            + "\n---\n".join(pos_list)
        )
    if neg_list:
        parts.append(
            "[Replies this user rated negatively — avoid these patterns]\n"
            + "\n---\n".join(neg_list)
        )
    return "\n\n".join(parts)

def _execute_tool(name: str, args: dict, state: dict, api_key: str) -> tuple[str, dict]:
    profile       = state.get("profile", {})
    progress_logs = state.get("progress_logs", [])
    plan_cache    = state.get("plan_cache", {})

    if name == "update_profile":
        profile, result = exec_update_profile(profile, args)
        state = {**state, "profile": profile}
        return result, state

    if name == "get_bmi":
        return exec_get_bmi(profile), state

    if name == "get_calories":
        return exec_get_calories(profile, args.get("activity", "moderate")), state

    if name == "get_workout_plan":
        raw, plan_cache, plan_hash = exec_get_workout_plan(
            profile, plan_cache,
            lambda msgs: _post_plan(api_key, msgs)
        )

        sched = build_full_replace(raw, plan_hash, state.get("user_id", ""))
        state = {**state, "plan_cache": plan_cache, "schedule_changes": sched}
        return raw, state

    if name == "get_nutrition_plan":
        raw, plan_cache = exec_get_nutrition_plan(
            profile, plan_cache,
            lambda msgs: _post_plan(api_key, msgs)
        )
        state = {**state, "plan_cache": plan_cache}
        return raw, state

    if name == "get_progress":
        return exec_get_progress(progress_logs), state

    if name == "log_weight":
        weight_raw = args.get("weight_kg")
        if weight_raw is None:
            return json.dumps({"error": "weight_kg is required"}), state
        try:
            weight_val = float(weight_raw)
        except (TypeError, ValueError):
            return json.dumps({"error": f"Invalid weight_kg value: {weight_raw!r}"}), state
        result, progress_logs, profile = exec_log_weight(
            progress_logs, profile, weight_val
        )
        state = {**state, "progress_logs": progress_logs, "profile": profile}
        return result, state

    if name == "get_recommendations":
        result = exec_get_recommendations(
            profile,
            lambda msgs: _post_plan(api_key, msgs)
        )
        return result, state

    if name == "update_workout_day":
        changes = args.get("changes", [])
        result, plan_cache, plan_hash, changed_days_raw = exec_update_workout_day(
            plan_cache, changes,
            lambda msgs: _post_plan(api_key, msgs),
            profile,
        )

        changed_normalised = []
        for d in changed_days_raw:
            nd = _normalise_day(d)
            if nd:
                changed_normalised.append(nd)
        sched = build_update_days(changed_normalised, plan_hash, state.get("user_id", ""))
        state = {**state, "plan_cache": plan_cache, "schedule_changes": sched}
        return result, state

    return json.dumps({"error": f"Unknown tool: {name}"}), state

def _run_tool_calls(
    tool_calls: list,
    msgs: list,
    state: dict,
    api_key: str,
    round_num: int,
    tools_used: list,
    progress_cb=None,  
) -> tuple[list, list, dict]:
    for tc in tool_calls:
        tc_id   = tc.get("id") or f"call_{round_num}_{tc.get('index', 0)}"
        tc_name = tc["function"]["name"]
        try:
            tc_args = json.loads(tc["function"].get("arguments", "{}"))
        except Exception:
            tc_args = {}
        logger.info("FC round %d: %s(%s)", round_num + 1, tc_name,
                    {k: v for k, v in tc_args.items() if k != "raw"})
        tools_used.append(tc_name)
        if progress_cb:
            progress_cb(tc_name)
        try:
            result, state = _execute_tool(tc_name, tc_args, state, api_key)
        except Exception as exc:
            logger.warning("Tool %s error: %s", tc_name, exc)
            result = json.dumps({"error": str(exc)})
        msgs.append({"role": "tool", "tool_call_id": tc_id, "content": result})
    return tools_used, msgs, state

def fc_loop(api_key: str, messages: list, state: dict) -> tuple[str, list[str], dict]:
    msgs       = list(messages)
    tools_used = []

    for round_num in range(MAX_FC_ROUNDS + 1):
        data    = _post(api_key, FC_MODELS, msgs, MAX_TOKENS_CHAT, TEMPERATURE, tools=TOOL_SCHEMAS)
        choices = data.get("choices") or []
        if not choices:
            return "Connection error. Please try again.", tools_used, state

        choice     = choices[0]
        msg        = choice.get("message")
        if not msg:
            logger.warning("Missing 'message' in choices[0] from model response")
            return "Connection error. Please try again.", tools_used, state
        finish     = choice.get("finish_reason", "")
        tool_calls = msg.get("tool_calls") or []

        if not tool_calls or finish == "stop":
            return (msg.get("content") or "").strip(), tools_used, state

        msgs.append({
            "role":       "assistant",
            "content":    msg.get("content") or "",
            "tool_calls": tool_calls
        })

        tools_used, msgs, state = _run_tool_calls(
            tool_calls, msgs, state, api_key, round_num, tools_used
        )

    content = msg.get("content") or ""
    if not content:
        logger.warning("FC loop exhausted after %d rounds with no text content", MAX_FC_ROUNDS)
        content = "Error. Please try again."
    return content.strip(), tools_used, state

_SUMMARY_THRESHOLD = 32
_SUMMARY_KEEP      = 6
assert _SUMMARY_KEEP < _SUMMARY_THRESHOLD, \
    f"_SUMMARY_KEEP ({_SUMMARY_KEEP}) must be less than _SUMMARY_THRESHOLD ({_SUMMARY_THRESHOLD})"

def _summarise_history(api_key: str, history: list) -> list:
    if len(history) <= _SUMMARY_THRESHOLD:
        return history

    to_summarise = history[:-_SUMMARY_KEEP]
    recent       = history[-_SUMMARY_KEEP:]

    conversation_text = "\n".join(
        f"{m['role'].upper()}: {m['content'][:300]}"
        for m in to_summarise
        if m.get("role") in ("user", "assistant") and isinstance(m.get("content"), str) and m["content"].strip()
    )

    prompt = (
        "Summarise the following conversation in 3-5 bullet points. "
        "Focus on: user's fitness profile facts, goals stated, plans discussed, "
        "and any important context. Be concise. Output ONLY the bullet list.\n\n"
        + conversation_text
    )
    try:
        data    = _post(api_key, PLAN_MODELS,
                        [{"role": "user", "content": prompt}],
                        max_tokens=300, temperature=0)
        choices = data.get("choices") or []
        summary = choices[0]["message"]["content"].strip() if choices else ""
    except Exception as exc:
        logger.warning("History summarisation failed: %s", exc)
        return history

    if not summary:
        return history

    summary_msg = {
        "role":    "system",
        "content": f"[Summary of earlier conversation]\n{summary}"
    }
    logger.info("History summarised: %d msgs → 1 summary + %d recent",
                len(to_summarise), len(recent))
    return [summary_msg] + recent

def respond(api_key: str, user_id: str, message: str,
            profile: dict, history: list,
            progress_logs: list, plan_cache: dict) -> tuple[str, list[str], dict, dict]:

    mem = get_user_memory(user_id)

    mem_entries = mem.retrieve(message, k=4)
    mem_ctx     = "\n".join(e["text"] for e in mem_entries) if mem_entries else ""

    system_content = _build_system_prompt(user_id, message, profile, mem_ctx, history=history)

    history = _summarise_history(api_key, history)

    messages = (
        [{"role": "system", "content": system_content}]
        + history
        + [{"role": "user", "content": message}]
    )

    state = {
        "user_id":          user_id,
        "profile":          profile,
        "progress_logs":    progress_logs,
        "plan_cache":       plan_cache,
        "schedule_changes": None,
    }

    reply, tools_used, state = fc_loop(api_key, messages, state)

    reply = re.sub(r"(?im)^THOUGHT:.*?(?=\n\n|\Z)", "", reply).strip()
    reply = re.sub(r"(?im)^REASONING:.*?(?=\n\n|\Z)", "", reply).strip()
    reply = re.sub(r"(?im)^THINKING:.*?(?=\n\n|\Z)", "", reply).strip()

    reply = re.sub(r"^[-—]{3,}\s*", "", reply, flags=re.MULTILINE).strip()

    mem.store(message, role="user")      if len(message.strip()) >= 15 else None
    assistant_entry_id = mem.store(reply, role="assistant") if len(reply.strip()) >= 15 else ""

    return reply, tools_used, state["profile"], state["plan_cache"], state["progress_logs"], assistant_entry_id, state.get("schedule_changes")

def init_memory() -> None:
    try:
        from memory_store import _get_embedder
        _get_embedder()
        logger.info("Memory embedder ready.")
    except Exception as exc:
        logger.warning("Memory embedder not available: %s", exc)

def _build_system_prompt(user_id: str, message: str, profile: dict, mem_ctx: str = "",
                         history: list | None = None) -> str:
    def _has_arabic(text: str) -> bool:
        return any("\u0600" <= c <= "\u06ff" for c in (text or ""))

    is_arabic = _has_arabic(message)
    if not is_arabic and history:
        for m in reversed(history[-4:]):
            if _has_arabic(m.get("content") or ""):
                is_arabic = True
                break

    lang_hint = (
        "مهم جداً: الرد بالعربية فقط بدون استثناء."
        if is_arabic
        else "IMPORTANT: Reply in ENGLISH only."
    )
    content = f"{SYSTEM_PROMPT}\n\n{_profile_context(profile)}\n\n{lang_hint}"
    if mem_ctx:
        content += f"\n\n[Relevant context from past conversations]\n{mem_ctx}"
    feedback_ctx = _feedback_context(user_id)
    if feedback_ctx:
        content += f"\n\n{feedback_ctx}"
    return content
