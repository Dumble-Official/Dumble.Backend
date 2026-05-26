
import hashlib
import json
import logging
import re

logger = logging.getLogger(__name__)

__all__ = ["build_full_replace", "build_update_days"]

_DAY_INDEX: dict[str, int] = {

    "monday": 0, "tuesday": 1, "wednesday": 2, "thursday": 3,
    "friday": 4, "saturday": 5, "sunday": 6,

    "mon": 0, "tue": 1, "wed": 2, "thu": 3, "fri": 4, "sat": 5, "sun": 6,

    "الاثنين": 0, "الثلاثاء": 1, "الأربعاء": 2, "الخميس": 3,
    "الجمعة": 4, "السبت": 5, "الأحد": 6,

    "اثنين": 0, "ثلاثاء": 1, "أربعاء": 2, "اربعاء": 2, "خميس": 3,
    "جمعة": 4, "سبت": 5, "أحد": 6,
}

_REST_KEYWORDS = {"rest", "off", "راحة", "استرداد"}

_REST_PATTERN = re.compile(
    "|".join(r'\b' + re.escape(kw) + r'\b' for kw in _REST_KEYWORDS)
)

def _day_index(day_name: str) -> int:
    return _DAY_INDEX.get(day_name.strip().lower(), -1)

def _safe_int(val, default: int) -> int:
    try:
        return int(val)
    except (TypeError, ValueError):
        return default

def _is_rest_day(day: dict) -> bool:
    focus = (day.get("focus") or "").lower()
    return bool(_REST_PATTERN.search(focus))

def _normalise_exercise(ex: dict | str, order: int) -> dict:
    if isinstance(ex, str):
        ex = {"name": ex}
    return {
        "order":    order,
        "name":     ex.get("name", ""),
        "sets":     _safe_int(ex.get("sets", 3), 3),
        "reps":     str(ex.get("reps", "10")),
        "rest_sec": _safe_int(ex.get("rest_sec", 60), 60),
        "notes":    ex.get("notes", ""),
    }

def _normalise_day(day: dict) -> dict:
    day_name = day.get("day", "")
    idx      = _day_index(day_name)
    rest     = _is_rest_day(day)
    exs      = day.get("exercises") or []

    if not rest and len(exs) == 0:
        logger.warning(
            "Day '%s' has no exercises and no rest keyword — "
            "treating as non-rest; possible LLM parse error.", day_name
        )

    return {
        "day_index": idx,
        "day_name":  day_name,
        "focus":     day.get("focus", ""),
        "rest_day":  rest,
        "exercises": [
            _normalise_exercise(e, i + 1)
            for i, e in enumerate(exs)
            if isinstance(e, (dict, str)) and e
        ],
    }

def build_full_replace(plan_raw: str, plan_hash: str, user_id: str) -> dict | None:
    if not plan_hash:
        logger.warning("schedule_contract: empty plan_hash passed to build_full_replace — plan_id will be empty")

    try:
        data = json.loads(plan_raw)
    except Exception:
        logger.warning("schedule_contract: could not parse plan JSON")
        return None

    week = data.get("week_plan") or []
    if not week:
        return None

    days = []
    for i, d in enumerate(week):
        nd = _normalise_day(d)

        if nd["day_index"] == -1:
            nd["day_index"] = i % 7
            logger.warning(
                "Unrecognised day name '%s' at position %d — "
                "assigning fallback day_index=%d (may be inaccurate if days are out of order)",
                nd["day_name"], i, nd["day_index"],
            )
        days.append(nd)

    if not days:
        return None

    return {
        "action":  "full_replace",
        "user_id": user_id,
        "plan_id": plan_hash,
        "days":    days,
    }

def build_update_days(
    changed_days: list[dict],
    plan_hash: str,
    user_id: str,
) -> dict | None:
    if not plan_hash:
        logger.warning("schedule_contract: empty plan_hash passed to build_update_days — plan_id will be empty")

    if not changed_days:
        return None

    normalised = []
    for d in changed_days:
        if not d:
            continue
        nd = _normalise_day(d)
        if nd["day_index"] == -1:
            logger.warning(
                "build_update_days: unrecognised day name '%s' — "
                "dropping entry to avoid sending day_index=-1 to gateway",
                nd["day_name"],
            )
            continue
        normalised.append(nd)

    if not normalised:
        return None

    return {
        "action":  "update_days",
        "user_id": user_id,
        "plan_id": plan_hash,
        "days":    normalised,
    }
