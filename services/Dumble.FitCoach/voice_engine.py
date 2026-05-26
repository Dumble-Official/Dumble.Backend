
import logging
import os
import threading
import time
from urllib.parse import urlparse

import requests

logger = logging.getLogger(__name__)

ALLOWED_AUDIO_EXT = {"mp3", "wav", "ogg", "m4a", "aac", "flac", "webm", "opus"}

AUDIO_MIME: dict[str, str] = {
    "mp3":   "audio/mpeg",
    "wav":   "audio/wav",
    "ogg":   "audio/ogg",
    "m4a":   "audio/mp4",
    "aac":   "audio/aac",
    "flac":  "audio/flac",
    "webm":  "audio/webm",
    "opus":  "audio/ogg",
}

_FILES_UPLOAD_URL = "https://generativelanguage.googleapis.com/upload/v1beta/files"
_GENERATE_URL     = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
_FILES_BASE_URL   = "https://generativelanguage.googleapis.com/v1beta/files"

_UPLOAD_TIMEOUT   = (15, 120)
_GENERATE_TIMEOUT = (10, 120)
_STATUS_TIMEOUT   = (5, 15)
_DELETE_TIMEOUT   = (5, 10)
_WHISPER_TIMEOUT  = (15, 120)

_POLL_MAX_WAIT_SEC = 30

_POLL_INTERVAL_SEC = 1

_TRANSCRIBE_PROMPT = (
    "Listen to the audio and transcribe it word for word. "
    "Keep the original language (Arabic or English — do NOT translate). "
    "Output ONLY the spoken words. No timestamps, no labels, no explanations. "
    "If the audio is silent, inaudible, or contains no speech, output exactly: [unclear]"
)

def _gemini_headers(api_key: str) -> dict:
    return {
        "x-goog-api-key": api_key,
        "Content-Type":   "application/json",
    }

def _gemini_files_transcribe(api_key: str, file_path: str, ext: str) -> str:
    mime = AUDIO_MIME.get(ext.lower(), "audio/mpeg")
    filesize = os.path.getsize(file_path)

    with open(file_path, "rb") as _fh:
        upload_resp = requests.post(
            _FILES_UPLOAD_URL,
            headers={
                "x-goog-api-key":                      api_key,
                "X-Goog-Upload-Command":               "start, upload, finalize",
                "X-Goog-Upload-Header-Content-Type":   mime,
                "X-Goog-Upload-Header-Content-Length": str(filesize),
                "Content-Type": mime,
            },
            data=_fh,
            timeout=_UPLOAD_TIMEOUT,
        )

    if upload_resp.status_code not in (200, 201):
        logger.error("Files API upload HTTP %s: %s",
                     upload_resp.status_code, upload_resp.text[:300])
        raise RuntimeError(f"Upload failed: HTTP {upload_resp.status_code}")

    file_data = upload_resp.json()
    file_uri  = (file_data.get("file") or file_data).get("uri")
    if not file_uri:
        raise RuntimeError(f"No file URI in upload response: {file_data}")

    logger.info("Uploaded to Files API: %s (%.0fkB)", file_uri, filesize / 1024)

    file_name = urlparse(file_uri).path.split("/files/")[-1]

    deadline = time.monotonic() + _POLL_MAX_WAIT_SEC
    while time.monotonic() < deadline:
        st = requests.get(
            f"{_FILES_BASE_URL}/{file_name}",
            headers={"x-goog-api-key": api_key},
            timeout=_STATUS_TIMEOUT,
        )
        if st.status_code == 404:
            raise RuntimeError("File disappeared from Gemini Files API (404)")
        if st.status_code != 200:
            logger.warning("File status check HTTP %s — will retry", st.status_code)
            time.sleep(_POLL_INTERVAL_SEC)
            continue

        state = st.json().get("state", "PROCESSING")
        if state == "ACTIVE":
            break
        if state == "FAILED":
            raise RuntimeError("Gemini file processing FAILED")
        time.sleep(_POLL_INTERVAL_SEC)
    else:
        raise RuntimeError(
            f"File never reached ACTIVE state within {_POLL_MAX_WAIT_SEC}s"
        )

    try:
        gen_resp = requests.post(
            _GENERATE_URL,
            headers=_gemini_headers(api_key),
            json={
                "contents": [{
                    "parts": [
                        {"file_data": {"mime_type": mime, "file_uri": file_uri}},
                        {"text": _TRANSCRIBE_PROMPT},
                    ]
                }],
                "generationConfig": {"temperature": 0, "maxOutputTokens": 1000},
            },
            timeout=_GENERATE_TIMEOUT,
        )
    finally:

        try:
            requests.delete(
                f"{_FILES_BASE_URL}/{file_name}",
                headers={"x-goog-api-key": api_key},
                timeout=_DELETE_TIMEOUT,
            )
        except Exception:
            pass

    if gen_resp.status_code != 200:
        logger.error("Gemini generate HTTP %s: %s",
                     gen_resp.status_code, gen_resp.text[:300])
        raise RuntimeError(f"Generation failed: HTTP {gen_resp.status_code}")

    try:
        text = gen_resp.json()["candidates"][0]["content"]["parts"][0]["text"].strip()
    except (KeyError, IndexError) as exc:
        raise RuntimeError("Could not parse Gemini response") from exc

    return "" if (not text or text.lower() == "[unclear]") else text

def _whisper_api_transcribe(api_key: str, file_path: str, ext: str) -> str:
    openai_key = api_key or os.getenv("OPENAI_API_KEY", "")
    if not openai_key:
        raise RuntimeError("No OpenAI API key available")

    mime = AUDIO_MIME.get(ext.lower(), "audio/mpeg")
    with open(file_path, "rb") as f:
        resp = requests.post(
            "https://api.openai.com/v1/audio/transcriptions",
            headers={"Authorization": f"Bearer {openai_key}"},
            files={"file": (f"audio.{ext}", f, mime)},
            data={"model": "whisper-1", "response_format": "text"},
            timeout=_WHISPER_TIMEOUT,
        )

    if resp.status_code != 200:
        raise RuntimeError(f"Whisper API HTTP {resp.status_code}: {resp.text[:200]}")

    return resp.text.strip()

_faster_whisper_model = None
_openai_whisper_model = None
_whisper_lock         = threading.Lock()

def _whisper_local_transcribe(file_path: str) -> str:
    global _faster_whisper_model, _openai_whisper_model

    try:
        from faster_whisper import WhisperModel
        if _faster_whisper_model is None:
            with _whisper_lock:
                if _faster_whisper_model is None:
                    logger.info("Loading faster-whisper model (first call)...")
                    _faster_whisper_model = WhisperModel("base", device="cpu", compute_type="int8")
        segments, _ = _faster_whisper_model.transcribe(file_path, beam_size=3)
        return " ".join(s.text.strip() for s in segments).strip()
    except ImportError:
        pass

    try:
        import whisper
        if _openai_whisper_model is None:
            with _whisper_lock:
                if _openai_whisper_model is None:
                    logger.info("Loading openai-whisper model (first call)...")
                    _openai_whisper_model = whisper.load_model("base")
        result = _openai_whisper_model.transcribe(file_path)
        return result.get("text", "").strip()
    except ImportError:
        pass

    raise RuntimeError("No local Whisper package available")

def transcribe(api_key: str, file_path: str, ext: str,
               openai_api_key: str = "") -> str:

    ext = ext.lower().lstrip(".")
    if ext not in ALLOWED_AUDIO_EXT:
        raise ValueError(f"Unsupported audio format: {ext}")

    errors: list[str] = []

    try:
        text = _gemini_files_transcribe(api_key, file_path, ext)
        logger.info("Transcribed via Gemini Files API (%s, %.0fkB): %.80s",
                    ext, os.path.getsize(file_path) / 1024, text)
        return text
    except Exception as exc:
        logger.warning("Gemini Files API failed: %s", exc)
        errors.append(f"Gemini: {exc}")

    try:
        text = _whisper_api_transcribe(openai_api_key, file_path, ext)
        logger.info("Transcribed via Whisper API (%s): %.80s", ext, text)
        return text
    except Exception as exc:
        logger.warning("Whisper API failed: %s", exc)
        errors.append(f"Whisper API: {exc}")

    try:
        text = _whisper_local_transcribe(file_path)
        logger.info("Transcribed via local Whisper (%s): %.80s", ext, text)
        return text
    except Exception as exc:
        logger.warning("Local Whisper failed: %s", exc)
        errors.append(f"Local: {exc}")

    raise RuntimeError("All transcription strategies failed:\n" + "\n".join(errors))
