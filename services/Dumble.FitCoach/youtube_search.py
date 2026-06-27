"""Keyless YouTube search.

The coach needs a REAL, playable video id to attach to a schedule item — the
LLM guessing a url produces dead/wrong links. We have no YouTube Data API key,
so we query the public results page and pull the first real video id out of the
embedded `ytInitialData`. No api key, no quota; the trade-off is fragility if
YouTube changes its markup, so every failure degrades gracefully to "no video".
"""

import logging
import re

import httpx

logger = logging.getLogger(__name__)

# The results HTML embeds a big `ytInitialData` JSON blob; the first
# `"videoId":"<id>"` in document order is the top search result.
_VIDEO_ID_RE = re.compile(r'"videoId":"([A-Za-z0-9_-]{11})"')

# A desktop UA + English locale gets the standard results markup (mobile/region
# variants can wrap the ids differently).
_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    ),
    "Accept-Language": "en-US,en;q=0.9",
}

_RESULTS_URL = "https://www.youtube.com/results"


def search_youtube_id(query: str) -> str | None:
    """Return the top matching YouTube video id for `query`, or None.

    Never raises — a search hiccup must not break the chat turn."""
    q = (query or "").strip()
    if not q:
        return None
    try:
        # sp=EgIQAQ%3D%3D filters results to *videos* only (no channels/playlists),
        # so the first id we scrape is always a playable video.
        resp = httpx.get(
            _RESULTS_URL,
            params={"search_query": q, "hl": "en", "sp": "EgIQAQ%3D%3D"},
            headers=_HEADERS,
            timeout=6.0,
            follow_redirects=True,
        )
        if resp.status_code != 200:
            logger.warning("youtube search HTTP %s for %r", resp.status_code, q[:60])
            return None
        m = _VIDEO_ID_RE.search(resp.text)
        if not m:
            logger.warning("youtube search: no videoId parsed for %r", q[:60])
            return None
        return m.group(1)
    except Exception as exc:  # network error, timeout, parse — all non-fatal
        logger.warning("youtube search failed for %r: %s", q[:60], exc)
        return None


def to_watch_url(video_id: str) -> str:
    """Canonical watch url the inline player can parse."""
    return f"https://www.youtube.com/watch?v={video_id}"
