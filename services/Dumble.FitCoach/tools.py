import hashlib
import json
import logging
import os
import re as _re
from datetime import datetime
from typing import Callable

import httpx

from youtube_search import search_youtube_id, to_watch_url

PostPlanFn = Callable[[list[dict]], str]

logger = logging.getLogger(__name__)

_BACK_BLOCKS     = ["deadlift","good morning","bent-over row","hyperextension","sit-up"]
_KNEE_BLOCKS     = ["squat","lunge","leg press","jumping","running"]
_SHOULDER_BLOCKS = ["overhead press","military press","upright row","behind neck","pull-up"]

INJURY_BLOCKS: dict[str, list[str]] = {
    "back":     _BACK_BLOCKS,
    "knee":     _KNEE_BLOCKS,
    "shoulder": _SHOULDER_BLOCKS,
    "ركبة":     _KNEE_BLOCKS,
    "ظهر":      _BACK_BLOCKS,
    "كتف":      _SHOULDER_BLOCKS,
}

_WORKOUT_PROFILE_KEYS   = frozenset(("weight","height","goal","injuries"))
_NUTRITION_PROFILE_KEYS = frozenset(("weight","goal"))

_CALORIE_ADJUSTMENT = 400

def _strip_json(text: str) -> str:
    return _re.sub(r'^```(?:json)?\s*|\s*```$', '', text.strip())

TOOL_SCHEMAS: list[dict] = [
    {
        "type": "function",
        "function": {
            "name": "update_profile",
            "description": (
                "Save user information to their profile. Call this whenever the user mentions "
                "their name, age, weight, height, goal, injuries, workout location, equipment, "
                "activity level, or diet type. Only include fields that were explicitly mentioned."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "name":             {"type":"string",  "description":"User's name"},
                    "age":              {"type":"integer", "description":"Age in years"},
                    "gender":           {"type":"string",  "enum":["male","female","other"], "description":"Gender (male/female/other)"},
                    "weight":           {"type":"number",  "description":"Weight in kg"},
                    "height":           {"type":"number",  "description":"Height in cm"},
                    "goal":             {"type":"string",  "enum":["lose weight","gain weight","maintain weight"],
                                        "description":"Fitness goal"},
                    "injuries":         {"type":"string",  "description":"Any injuries or pain areas"},
                    "workout_location": {"type":"string",  "enum":["home","gym","outdoor"],
                                        "description":"Where they train"},
                    "equipment":        {"type":"string",  "description":"Available equipment"},
                    "activity_level":   {"type":"string",  "enum":["beginner","intermediate","advanced"],
                                        "description":"Fitness level"},
                    "diet_type":        {"type":"string",  "description":"Dietary preference (vegan, keto, etc.)"},
                },
                "required": []
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "get_bmi",
            "description": "Calculate the user's BMI from their saved profile.",
            "parameters": {"type":"object","properties":{},"required":[]}
        }
    },
    {
        "type": "function",
        "function": {
            "name": "get_calories",
            "description": "Calculate the user's daily calorie needs (BMR and TDEE).",
            "parameters": {
                "type": "object",
                "properties": {
                    "activity": {
                        "type": "string",
                        "enum": ["sedentary","light","moderate","active","very_active"],
                        "description": "Activity level for TDEE calculation"
                    }
                },
                "required": []
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "get_workout_plan",
            "description": (
                "Generate a NEW personalized 7-day workout plan and REPLACE the user's "
                "entire saved schedule with it. Only call this to create a fresh plan or "
                "when the user explicitly asks to rebuild/regenerate — NEVER to view or "
                "check what they already have (use get_schedule for that). "
                "Also only call when you know AT LEAST the user's goal AND workout "
                "location (home/gym); if either is missing, ask first."
            ),
            "parameters": {"type":"object","properties":{},"required":[]}
        }
    },
    {
        "type": "function",
        "function": {
            "name": "get_schedule",
            "description": (
                "Read the user's CURRENT saved schedule — their actual workout and meal "
                "items per weekday, exactly as stored. This is READ-ONLY and changes "
                "nothing. Call it to answer any question about what they already have "
                "planned, e.g. 'what do I have today', 'show my schedule', "
                "'what's my leg day'. Do NOT use get_workout_plan for viewing — that "
                "REPLACES the whole plan."
            ),
            "parameters": {"type":"object","properties":{},"required":[]}
        }
    },
    {
        "type": "function",
        "function": {
            "name": "get_nutrition_plan",
            "description": "Generate a daily nutrition/meal plan based on the user's profile and goals.",
            "parameters": {"type":"object","properties":{},"required":[]}
        }
    },
    {
        "type": "function",
        "function": {
            "name": "get_progress",
            "description": "Get the user's weight progress data and trend analysis.",
            "parameters": {"type":"object","properties":{},"required":[]}
        }
    },
    {
        "type": "function",
        "function": {
            "name": "log_weight",
            "description": "Log the user's current weight. Only call when user explicitly states their weight with a unit (kg/كيلو).",
            "parameters": {
                "type": "object",
                "properties": {
                    "weight_kg": {"type":"number","description":"Weight in kilograms"}
                },
                "required": ["weight_kg"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "get_recommendations",
            "description": "Get personalized exercise recommendations based on user's profile, goals, and equipment.",
            "parameters": {"type":"object","properties":{},"required":[]}
        }
    },
    {
        "type": "function",
        "function": {
            "name": "update_workout_day",
            "description": (
                "Edit one or more specific days in the existing workout plan. "
                "Call this when the user asks to change a specific day — e.g. 'change Tuesday to chest', "
                "'replace Wednesday with cardio', 'I want to swap Thursday'. "
                "Do NOT call this to generate a full new plan — use get_workout_plan for that."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "changes": {
                        "type": "array",
                        "description": "List of day changes to apply",
                        "items": {
                            "type": "object",
                            "properties": {
                                "day_name":  {"type": "string",  "description": "Day name, e.g. Monday / الاثنين"},
                                "focus":     {"type": "string",  "description": "New focus/muscle group for this day"},
                                "rest_day":  {"type": "boolean", "description": "Set to true to make this a rest day"},
                                "exercises": {
                                    "type": "array",
                                    "description": "New exercises for this day (omit to keep existing)",
                                    "items": {
                                        "type": "object",
                                        "properties": {
                                            "name":     {"type": "string"},
                                            "sets":     {"type": "integer"},
                                            "reps":     {"type": "string"},
                                            "rest_sec": {"type": "integer"},
                                            "notes":    {"type": "string"}
                                        },
                                        "required": ["name", "sets", "reps"]
                                    }
                                }
                            },
                            "required": ["day_name"]
                        }
                    }
                },
                "required": ["changes"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "add_exercises",
            "description": (
                "APPEND one or more exercises to a specific day in the user's schedule "
                "(does NOT replace the day or the whole plan). Use this when the user asks "
                "to ADD specific exercise(s) to a day — e.g. 'add 3 sets of squats to "
                "Saturday', 'put bicycle crunches on my Monday'. For a brand-new full "
                "weekly plan use get_workout_plan; to rebuild one whole day use update_workout_day."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "day": {"type": "string", "description": "Weekday to add to, e.g. Saturday / Sat / السبت"},
                    "exercises": {
                        "type": "array",
                        "description": "Exercises to append to that day",
                        "items": {
                            "type": "object",
                            "properties": {
                                "name":  {"type": "string"},
                                "sets":  {"type": "integer"},
                                "reps":  {"type": "string"},
                                "video_query": {"type": "string",
                                    "description": "If set, search YouTube for this and attach a demo video to the exercise"}
                            },
                            "required": ["name"]
                        }
                    }
                },
                "required": ["day", "exercises"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "add_meals",
            "description": (
                "Add one or more meals to a specific day's Meals section in the user's "
                "schedule. Use this whenever the user asks to ADD meals / a meal plan to "
                "their schedule — e.g. 'add my breakfast and lunch to Sunday', 'put this "
                "meal plan on the schedule'. This is for MEALS, never exercises."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "day": {"type": "string", "description": "Weekday to add to, e.g. Sunday / Sun / الأحد"},
                    "meals": {
                        "type": "array",
                        "description": "Meals to append to that day",
                        "items": {
                            "type": "object",
                            "properties": {
                                "content": {"type": "string",
                                    "description": "The meal text, e.g. 'Breakfast — 3 eggs, oats, banana'"},
                                "video_query": {"type": "string",
                                    "description": "Optional: search YouTube for a recipe video to attach"}
                            },
                            "required": ["content"]
                        }
                    }
                },
                "required": ["day", "meals"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "set_nutrition_goals",
            "description": (
                "Set the user's daily nutrition targets for a weekday: the calorie (kcal) "
                "target and/or protein, carbs, and fat in grams. Use this when the user asks "
                "to set or adjust their nutrition goals / macros / calorie target — e.g. "
                "'set my Monday goal to 2000 kcal and 150g protein'. Only include the fields "
                "the user wants to set; omit the rest (omitted fields are left unchanged)."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "day":       {"type": "string",  "description": "Weekday, e.g. Monday / Mon / الاثنين"},
                    "calories":  {"type": "integer", "description": "Daily kcal target"},
                    "protein_g": {"type": "integer", "description": "Protein target in grams"},
                    "carbs_g":   {"type": "integer", "description": "Carbs target in grams"},
                    "fat_g":     {"type": "integer", "description": "Fat target in grams"}
                },
                "required": ["day"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "attach_video",
            "description": (
                "Find a YouTube video and attach it to an EXISTING item the coach already "
                "added on a given day, matched by name. Use this when the user asks you to "
                "find/attach a demonstration or form video for something already on their "
                "schedule — e.g. 'add a video for my squats on Saturday'. To add a NEW "
                "exercise WITH a video in one step, use add_exercises with video_query instead."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "day":          {"type": "string", "description": "Weekday the item is on"},
                    "item_name":    {"type": "string", "description": "Name/text of the item to attach to, e.g. 'squat'"},
                    "search_query": {"type": "string",
                        "description": "What to search on YouTube; defaults to the item name + 'tutorial'"},
                    "table":        {"type": "string", "enum": ["EXERCISE", "MEAL"],
                        "description": "Which list the item is in (optional)"}
                },
                "required": ["day", "item_name"]
            }
        }
    },
]

def _apply_safety(plan: dict, injuries: str) -> tuple[dict, list]:
    if not injuries: return plan, []
    inj_lower = injuries.lower()
    blocked   = set()
    for key, lst in INJURY_BLOCKS.items():
        pattern = r'\b' + _re.escape(key) + r'\b'
        if _re.search(pattern, inj_lower):
            blocked.update(b.lower() for b in lst)
    if not blocked:
        return plan, []
    all_removed = []
    for day in plan.get("week_plan", []):
        safe, removed = [], []
        for ex in day.get("exercises", []):
            name = (ex if isinstance(ex, str) else ex.get("name","")).lower()

            if any(_re.search(r'\b' + _re.escape(b) + r'\b', name) for b in blocked):
                removed.append(ex if isinstance(ex, str) else ex.get("name", str(ex)))
            else:
                safe.append(ex)
        day["exercises"] = safe
        all_removed.extend(removed)
    return plan, all_removed

def exec_update_profile(profile: dict, fields: dict) -> tuple[dict, str]:
    allowed = {"name","age","gender","weight","height","goal","injuries",
               "workout_location","equipment","activity_level","diet_type"}
    clean   = {k: v for k, v in fields.items() if k in allowed and v is not None}
    updated = {**profile, **clean}
    return updated, json.dumps({"saved": list(clean.keys()), "status": "ok"})

def exec_get_bmi(profile: dict) -> str:
    w, h = profile.get("weight"), profile.get("height")
    if not w: return json.dumps({"error": "missing_weight"})
    if not h: return json.dumps({"error": "missing_height"})
    bmi = round(w / (h / 100) ** 2, 2)
    cat = ("Underweight" if bmi < 18.5 else "Normal weight" if bmi < 25
           else "Overweight" if bmi < 30 else "Obese")
    return json.dumps({"bmi": bmi, "category": cat, "weight_kg": w, "height_cm": h})

def exec_get_calories(profile: dict, activity: str = "moderate") -> str:
    w, h, age, gender, goal = (profile.get("weight"), profile.get("height"),
                                profile.get("age"), profile.get("gender"), profile.get("goal",""))
    missing = [f for f, v in [("weight",w),("height",h),("age",age),("gender",gender)] if not v]
    if missing: return json.dumps({"error": "missing_fields", "missing": missing})

    gender_offset = 5 if gender == "male" else -161 if gender == "female" else -78
    bmr    = 10*w + 6.25*h - 5*age + gender_offset
    muls   = {"sedentary":1.2,"light":1.375,"moderate":1.55,"active":1.725,"very_active":1.9}
    tdee   = bmr * muls.get(activity, 1.55)
    target = tdee-_CALORIE_ADJUSTMENT if "lose" in goal else tdee+_CALORIE_ADJUSTMENT if "gain" in goal else tdee
    return json.dumps({"bmr": round(bmr), "tdee": round(tdee),
                       "recommended_kcal": round(target), "goal": goal or "maintain"})

def exec_get_workout_plan(profile: dict, plan_cache: dict, post_plan_fn: PostPlanFn) -> tuple[str, dict, str]:
    raw_hash = json.dumps({k: profile.get(k) for k in _WORKOUT_PROFILE_KEYS}, sort_keys=True)
    current_hash = hashlib.sha256(raw_hash.encode()).hexdigest()[:32]
    cached = plan_cache.get("workout")
    if cached and cached.get("hash") == current_hash:
        return cached["raw"], plan_cache, current_hash

    injuries = profile.get("injuries", "")
    injury_instruction = (
        f"CRITICAL SAFETY — The user has this injury/condition: \"{injuries}\". "
        "Use your medical and biomechanical knowledge to assess EVERY exercise individually. "
        "Exclude any movement that could aggravate, stress, or load the affected area — "
        "even indirectly. Do NOT rely on exercise names alone; consider the movement pattern. "
        "For each affected muscle group, substitute with a safe alternative that achieves a similar training effect."
        if injuries else
        "No injuries reported."
    )
    prompt = (
        "Create a safe 7-day workout plan. Return ONLY valid JSON, no markdown.\n"
        f"{injury_instruction}\n"
        f"weight={profile.get('weight','?')}kg height={profile.get('height','?')}cm "
        f"goal={profile.get('goal','general fitness')} "
        f"level={profile.get('activity_level','beginner')} "
        f"location={profile.get('workout_location','home')} equipment={profile.get('equipment','none')}\n"
        'Each exercise must have name/sets/reps/rest_sec. JSON format:\n'
        '{"week_plan":[{"day":"Monday","focus":"Chest","exercises":[{"name":"Push-ups","sets":3,"reps":"12","rest_sec":60}]}]}'
    )
    raw = post_plan_fn([
        {"role": "system", "content": "You are a certified fitness expert and sports physiotherapist. Return ONLY valid JSON."},
        {"role": "user",   "content": prompt}
    ])
    raw = _strip_json(raw)
    data = None
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as e:
        logger.warning("Workout plan JSON parse failed: %s | raw[:120]: %s", e, raw[:120])

    if data and profile.get("injuries"):
        data, removed = _apply_safety(data, profile["injuries"])
        if removed:
            logger.warning("Safety fallback removed exercises the LLM missed: %s", removed)
        raw = json.dumps(data, ensure_ascii=False)

    if data:
        plan_cache["workout"] = {"hash": current_hash, "raw": raw}
        return raw, plan_cache, current_hash

    logger.warning("exec_get_workout_plan: returning error JSON (plan parse failed)")
    return json.dumps({"error": "plan_generation_failed", "message":
        "Could not generate plan — please ask the user to try again or provide more details."}
    ), plan_cache, current_hash

def exec_update_workout_day(
    plan_cache: dict, changes: list[dict], post_plan_fn: PostPlanFn, profile: dict
) -> tuple[str, dict, str, list[dict]]:
    cached = plan_cache.get("workout", {})
    raw    = cached.get("raw", "")
    plan_hash = cached.get("hash", "")

    try:
        plan_data = json.loads(raw) if raw else {}
    except Exception:
        plan_data = {}

    week = plan_data.get("week_plan") or []

    day_index_map: dict[str, int] = {}
    for i, d in enumerate(week):
        day_index_map[d.get("day", "").lower()] = i

    changed_days_raw = []

    for change in changes:
        day_name  = change.get("day_name", "").strip()
        day_lower = day_name.lower()
        focus     = change.get("focus", "")
        rest_day  = change.get("rest_day", False)
        exercises = change.get("exercises") 

        if rest_day:
            day_entry = {"day": day_name, "focus": "Rest", "exercises": []}

        elif exercises is not None:
            day_entry = {"day": day_name, "focus": focus or day_name, "exercises": exercises}

        else:
            _EXERCISE_EXAMPLE = '[{"name":"Push-ups","sets":3,"reps":"12","rest_sec":60}]'
            _inj = profile.get("injuries", "")
            _injury_instruction = (
                f"CRITICAL SAFETY — The user has this injury/condition: \"{_inj}\". "
                "Use your medical and biomechanical knowledge. "
                "Exclude any exercise that could aggravate the affected area — even indirectly. "
                "Consider the full movement pattern, not just the exercise name."
                if _inj else
                "No injuries reported."
            )
            prompt = (
                f"Generate 4-6 exercises for a {focus or day_name} workout day. "
                f"Return ONLY a JSON array, no markdown.\n"
                f"{_injury_instruction}\n"
                f"level={profile.get('activity_level','beginner')} "
                f"location={profile.get('workout_location','home')} "
                f"equipment={profile.get('equipment','none')}\n"
                + _EXERCISE_EXAMPLE
            )
            try:
                llm_raw = post_plan_fn([
                    {"role": "system", "content": "You are a certified fitness expert and sports physiotherapist. Return ONLY valid JSON array."},
                    {"role": "user",   "content": prompt},
                ])
                llm_raw = _strip_json(llm_raw)
                new_exs = json.loads(llm_raw)
            except Exception as e:
                logger.warning("update_workout_day LLM error: %s", e)
                new_exs = []

            if new_exs and profile.get("injuries"):
                tmp_plan = {"week_plan": [{"day": day_name, "focus": focus, "exercises": new_exs}]}
                tmp_plan, removed = _apply_safety(tmp_plan, profile["injuries"])
                if removed:
                    logger.warning("Safety fallback removed exercises the LLM missed (update_day): %s", removed)
                new_exs = tmp_plan["week_plan"][0]["exercises"]

            day_entry = {"day": day_name, "focus": focus or day_name, "exercises": new_exs}

        if day_lower in day_index_map:
            week[day_index_map[day_lower]] = day_entry
        else:

            week.append(day_entry)
            day_index_map[day_lower] = len(week) - 1

        changed_days_raw.append(day_entry)

    old_cache = plan_cache.get("workout")
    try:
        plan_cache["workout"] = None
        plan_data["week_plan"] = week
        new_raw   = json.dumps(plan_data, ensure_ascii=False)
        new_hash  = hashlib.sha256(new_raw.encode()).hexdigest()[:32]
        plan_cache["workout"] = {"hash": new_hash, "raw": new_raw}
    except Exception:
        plan_cache["workout"] = old_cache
        raise

    result = json.dumps({
        "status":       "updated",
        "days_changed": [c.get("day_name") for c in changes],
        "plan_hash":    new_hash,
    })
    return result, plan_cache, new_hash, changed_days_raw

def exec_get_nutrition_plan(profile: dict, plan_cache: dict, post_plan_fn: PostPlanFn) -> tuple[str, dict]:
    raw_hash = json.dumps({k: profile.get(k) for k in _NUTRITION_PROFILE_KEYS}, sort_keys=True)
    current_hash = hashlib.sha256(raw_hash.encode()).hexdigest()[:32]
    cached = plan_cache.get("nutrition")
    if cached and cached.get("hash") == current_hash:
        return cached["raw"], plan_cache

    _activity_map = {"beginner": "light", "intermediate": "moderate", "advanced": "active"}
    _activity     = _activity_map.get(profile.get("activity_level", ""), "moderate")
    cal_raw = exec_get_calories(profile, activity=_activity)
    prompt = (
        "Create a healthy daily nutrition plan. Return ONLY valid JSON, no markdown.\n"
        f"weight={profile.get('weight','?')}kg goal={profile.get('goal','healthy')} "
        f"diet={profile.get('diet_type','none')}\nCalorie data: {cal_raw}\n"
        '{"meals":[{"meal":"breakfast","foods":[{"item":"","amount":""}]},{"meal":"lunch","foods":[]},{"meal":"dinner","foods":[]},{"meal":"snacks","foods":[]}]}'
    )
    raw = post_plan_fn([
        {"role":"system","content":"You are a nutritionist. Return ONLY valid JSON."},
        {"role":"user",  "content":prompt}
    ])
    raw = _strip_json(raw)
    try:
        json.loads(raw)
    except json.JSONDecodeError as e:
        logger.warning("Nutrition plan JSON parse failed: %s | raw[:120]: %s", e, raw[:120])
        return raw, plan_cache
    plan_cache["nutrition"] = {"hash": current_hash, "raw": raw}
    return raw, plan_cache

def exec_get_progress(progress_logs: list) -> str:
    if len(progress_logs) < 2:
        return json.dumps({"status":"insufficient_data","logs_count":len(progress_logs)})
    diff = progress_logs[-1]["weight"] - progress_logs[-2]["weight"]
    return json.dumps({
        "latest_weight":   progress_logs[-1]["weight"],
        "previous_weight": progress_logs[-2]["weight"],
        "change_kg":       round(diff, 1),
        "trend":           "down" if diff < 0 else "up" if diff > 0 else "stable",
        "weeks_logged":    len(progress_logs),

        "recent_logs":     progress_logs[-10:],
        "total_entries":   len(progress_logs),
    })

def exec_log_weight(progress_logs: list, profile: dict, weight_kg: float) -> tuple[str, list, dict]:
    week          = (progress_logs[-1]["week"] + 1) if progress_logs else 1
    progress_logs = progress_logs + [{"week": week, "weight": weight_kg}]
    profile       = {**profile, "weight": weight_kg}
    result        = {"logged_kg": weight_kg, "week": week}
    if len(progress_logs) >= 2:
        diff = progress_logs[-1]["weight"] - progress_logs[-2]["weight"]
        result["change_kg"] = round(diff, 1)
        result["trend"]     = "down" if diff < 0 else "up" if diff > 0 else "stable"
    return json.dumps(result), progress_logs, profile

def exec_get_recommendations(profile: dict, post_plan_fn: PostPlanFn) -> str:
    prompt = (
        "Recommend 5 specific exercises as a JSON array. Return ONLY valid JSON, no markdown.\n"
        f"goal={profile.get('goal','general fitness')} location={profile.get('workout_location','home')} "
        f"equipment={profile.get('equipment','none')} level={profile.get('activity_level','beginner')} "
        f"injuries={profile.get('injuries','none')}\n"
        'Format: [{"name":"Push-ups","why":"builds chest and triceps","sets":3,"reps":"12"}]'
    )
    try:
        raw = post_plan_fn([
            {"role": "system", "content": "You are a personal trainer. Return ONLY valid JSON array."},
            {"role": "user",   "content": prompt}
        ])
        raw = _strip_json(raw)
        json.loads(raw)
        return raw
    except (json.JSONDecodeError, ValueError, RuntimeError) as e:
        logger.warning("get_recommendations error: %s", e)
        return json.dumps([
            {"name": "Push-ups",    "why": "تقوية الصدر والكتفين", "sets": 3, "reps": "12"},
            {"name": "Squats",      "why": "تقوية الأرجل والمؤخرة", "sets": 3, "reps": "15"},
            {"name": "Plank",       "why": "تقوية عضلات الكور",    "sets": 3, "reps": "30 sec"},
            {"name": "Lunges",      "why": "تقوية الفخذين والتوازن","sets": 3, "reps": "10 each"},
            {"name": "Glute Bridge","why": "تقوية المؤخرة والظهر", "sets": 3, "reps": "15"},
        ], ensure_ascii=False)

_WEEKDAY_ORDER = ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"]

def exec_get_schedule(user_id: str) -> str:
    """Read the user's current saved schedule from the Schedule service (read-only),
    so the coach can answer 'what do I have today' without regenerating anything."""
    base   = os.getenv("SCHEDULE_SERVICE_URL", "").rstrip("/")
    secret = os.getenv("INTERNAL_API_SECRET", "")
    if not user_id or not base or not secret:
        return json.dumps({"error": "schedule_unavailable"})

    url = f"{base}/internal/clients/{user_id}/chatbot/items"
    try:
        resp = httpx.get(url, headers={"X-Internal-Secret": secret}, timeout=5.0)
        if resp.status_code != 200:
            logger.warning("get_schedule HTTP %s", resp.status_code)
            return json.dumps({"error": f"schedule_http_{resp.status_code}"})
        data = resp.json()
    except Exception as e:
        logger.warning("get_schedule fetch error: %s", e)
        return json.dumps({"error": "schedule_fetch_failed"})

    def _items_for(days, weekday):
        for d in days or []:
            if d.get("weekday") == weekday:
                return [(it.get("content") or "").strip()
                        for it in (d.get("items") or []) if it.get("content")]
        return []

    week = {}
    for wd in _WEEKDAY_ORDER:
        exercises = _items_for(data.get("exercises"), wd)
        meals     = _items_for(data.get("meals"), wd)
        if exercises or meals:
            week[wd] = {"exercises": exercises, "meals": meals}

    today = datetime.now().strftime("%a").upper()  # MON..SUN
    if not week:
        return json.dumps({"today": today, "empty": True,
                           "message": "No schedule items are saved yet."})
    return json.dumps({"today": today, "week": week}, ensure_ascii=False)


# ── Schedule-writing tools (meals, nutrition goals, video attach) ─────────────
# These call the Schedule service's internal chatbot endpoints directly (like
# exec_get_schedule reads it), so the write lands and is confirmed synchronously
# in the tool result — the model then confirms it to the user. Items are stamped
# CHATBOT by the schedule service. Same env as exec_get_schedule.

# Schedule Weekday enum is SUN..SAT. Map day names (EN/AR, full + short) to it.
_WEEKDAY_ENUM: dict[str, str] = {
    "monday": "MON", "tuesday": "TUE", "wednesday": "WED", "thursday": "THU",
    "friday": "FRI", "saturday": "SAT", "sunday": "SUN",
    "mon": "MON", "tue": "TUE", "tues": "TUE", "wed": "WED", "weds": "WED",
    "thu": "THU", "thur": "THU", "thurs": "THU", "fri": "FRI", "sat": "SAT", "sun": "SUN",
    "الاثنين": "MON", "الإثنين": "MON", "اثنين": "MON",
    "الثلاثاء": "TUE", "ثلاثاء": "TUE",
    "الأربعاء": "WED", "الاربعاء": "WED", "أربعاء": "WED", "اربعاء": "WED",
    "الخميس": "THU", "خميس": "THU",
    "الجمعة": "FRI", "جمعة": "FRI",
    "السبت": "SAT", "سبت": "SAT",
    "الأحد": "SUN", "الاحد": "SUN", "أحد": "SUN", "احد": "SUN",
}


def _to_weekday(day: str) -> str | None:
    return _WEEKDAY_ENUM.get((day or "").strip().lower())


def _schedule_endpoint() -> tuple[str, str] | None:
    base   = os.getenv("SCHEDULE_SERVICE_URL", "").rstrip("/")
    secret = os.getenv("INTERNAL_API_SECRET", "")
    if not base or not secret:
        return None
    return base, secret


def _resolve_video(item: dict) -> str | None:
    """A pre-supplied youtube_url wins; otherwise search YouTube for video_query."""
    url = (item.get("youtube_url") or "").strip()
    if url:
        return url
    query = (item.get("video_query") or "").strip()
    if not query:
        return None
    vid = search_youtube_id(query)
    return to_watch_url(vid) if vid else None


def _add_chatbot_items(user_id: str, req_items: list[dict]) -> tuple[bool, str]:
    """Append-only POST of chatbot items (replace=False, so existing items stay)."""
    ep = _schedule_endpoint()
    if not user_id or ep is None:
        return False, "schedule_unavailable"
    if not req_items:
        return False, "no_items"
    base, secret = ep
    url = f"{base}/internal/clients/{user_id}/chatbot/items"
    try:
        resp = httpx.post(url, headers={"X-Internal-Secret": secret},
                          json={"replace": False, "items": req_items}, timeout=6.0)
    except Exception as e:
        logger.warning("add chatbot items error: %s", e)
        return False, "schedule_fetch_failed"
    if resp.status_code >= 300:
        logger.warning("add chatbot items HTTP %s: %s", resp.status_code, resp.text[:200])
        return False, f"schedule_http_{resp.status_code}"
    return True, "ok"


def exec_add_exercises(user_id: str, day: str, exercises: list) -> str:
    wd = _to_weekday(day)
    if not wd:
        return json.dumps({"error": "unknown_day", "day": day})
    req_items, added = [], []
    for ex in exercises or []:
        if isinstance(ex, str):
            ex = {"name": ex}
        if not isinstance(ex, dict):
            continue
        name = (ex.get("name") or ex.get("content") or "").strip()
        if not name:
            continue
        sets, reps = ex.get("sets"), ex.get("reps")
        if sets and reps:
            content = f"{name} — {sets}x{reps}"
        elif reps:
            content = f"{name} — {reps}"
        else:
            content = name
        yt = _resolve_video(ex)
        item = {"tableType": "EXERCISE", "weekday": wd, "content": content}
        if yt:
            item["youtubeLink"] = yt
        req_items.append(item)
        added.append({"content": content, "video": bool(yt)})
    if not req_items:
        return json.dumps({"error": "no_exercises"})
    ok, detail = _add_chatbot_items(user_id, req_items)
    if not ok:
        return json.dumps({"error": detail})
    return json.dumps({"status": "added", "table": "EXERCISE", "weekday": wd,
                       "count": len(added), "items": added}, ensure_ascii=False)


def exec_add_meals(user_id: str, day: str, meals: list) -> str:
    wd = _to_weekday(day)
    if not wd:
        return json.dumps({"error": "unknown_day", "day": day})
    req_items, added = [], []
    for m in meals or []:
        if isinstance(m, str):
            m = {"content": m}
        if not isinstance(m, dict):
            continue
        content = (m.get("content") or m.get("name") or m.get("meal") or "").strip()
        if not content:
            continue
        yt = _resolve_video(m)
        item = {"tableType": "MEAL", "weekday": wd, "content": content}
        if yt:
            item["youtubeLink"] = yt
        req_items.append(item)
        added.append({"content": content, "video": bool(yt)})
    if not req_items:
        return json.dumps({"error": "no_meals"})
    ok, detail = _add_chatbot_items(user_id, req_items)
    if not ok:
        return json.dumps({"error": detail})
    return json.dumps({"status": "added", "table": "MEAL", "weekday": wd,
                       "count": len(added), "items": added}, ensure_ascii=False)


def exec_set_nutrition_goals(user_id: str, day: str, calories=None,
                             protein_g=None, carbs_g=None, fat_g=None) -> str:
    wd = _to_weekday(day)
    if not wd:
        return json.dumps({"error": "unknown_day", "day": day})
    ep = _schedule_endpoint()
    if not user_id or ep is None:
        return json.dumps({"error": "schedule_unavailable"})
    base, secret = ep
    body: dict[str, int] = {}
    for key, val in (("calories", calories), ("proteinG", protein_g),
                     ("carbsG", carbs_g), ("fatG", fat_g)):
        if val is None:
            continue
        try:
            iv = int(round(float(val)))
        except (TypeError, ValueError):
            continue
        if iv < 0:
            continue
        body[key] = iv
    if not body:
        return json.dumps({"error": "no_targets"})
    url = f"{base}/internal/clients/{user_id}/chatbot/meal-targets/{wd}"
    try:
        resp = httpx.put(url, headers={"X-Internal-Secret": secret}, json=body, timeout=6.0)
    except Exception as e:
        logger.warning("set nutrition goals error: %s", e)
        return json.dumps({"error": "schedule_fetch_failed"})
    if resp.status_code >= 300:
        logger.warning("set nutrition goals HTTP %s: %s", resp.status_code, resp.text[:200])
        return json.dumps({"error": f"schedule_http_{resp.status_code}"})
    return json.dumps({"status": "set", "weekday": wd, "targets": body}, ensure_ascii=False)


def exec_attach_video(user_id: str, day: str, item_name: str,
                      search_query: str = "", table: str | None = None) -> str:
    wd = _to_weekday(day)
    if not wd:
        return json.dumps({"error": "unknown_day", "day": day})
    name = (item_name or "").strip()
    if not name:
        return json.dumps({"error": "missing_item_name"})
    ep = _schedule_endpoint()
    if not user_id or ep is None:
        return json.dumps({"error": "schedule_unavailable"})
    base, secret = ep
    query = (search_query or "").strip() or f"{name} tutorial"
    vid = search_youtube_id(query)
    if not vid:
        return json.dumps({"error": "no_video_found", "query": query})
    watch_url = to_watch_url(vid)
    body = {"weekday": wd, "contentQuery": name, "youtubeLink": watch_url}
    if table in ("EXERCISE", "MEAL"):
        body["tableType"] = table
    url = f"{base}/internal/clients/{user_id}/chatbot/items/video"
    try:
        resp = httpx.put(url, headers={"X-Internal-Secret": secret}, json=body, timeout=6.0)
    except Exception as e:
        logger.warning("attach video error: %s", e)
        return json.dumps({"error": "schedule_fetch_failed"})
    if resp.status_code == 404:
        # No matching item — tell the model so it can offer to add it instead.
        return json.dumps({"attached": False, "reason": "item_not_found",
                           "item": name, "weekday": wd, "video_url": watch_url})
    if resp.status_code >= 300:
        logger.warning("attach video HTTP %s: %s", resp.status_code, resp.text[:200])
        return json.dumps({"error": f"schedule_http_{resp.status_code}"})
    return json.dumps({"attached": True, "item": name, "weekday": wd,
                       "video_url": watch_url}, ensure_ascii=False)
