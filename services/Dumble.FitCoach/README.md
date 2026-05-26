# 🏋️ FitCoach AI Service

AI-powered fitness coach service. **Stateless** — no database. All user data is sent per request and returned updated.

---

## 🚀 Quick Start

### With Docker (Recommended)

```bash
# 1. Build
docker build -t fitcoach-ai .

# 2. Run
docker run -d \
  --name fitcoach \
  -p 8000:8000 \
  --env-file .env \
  fitcoach-ai
```

### Without Docker

```bash
pip install -r requirements.txt
uvicorn api:app --host 0.0.0.0 --port 8000 --reload
```

---

## ⚙️ Environment Variables

Create a `.env` file in the project root:

```env
GEMINI_API_KEYS=key1,key2,key3
INTERNAL_API_SECRET=your-secret-here
ALLOWED_ORIGINS=https://your-app.com,https://your-admin.com
MEMORY_DIR=memory_data
POSE_MODEL_PATH=/app/model/pose_full.task
```

| Variable | Required | Description |
|----------|----------|-------------|
| `GEMINI_API_KEYS` | ✅ | Comma-separated Gemini API keys — auto round-robin + rate-limit recovery |
| `GEMINI_API_KEY` | Fallback | Single key (used if `GEMINI_API_KEYS` not set) |
| `INTERNAL_API_SECRET` | ⚠️ Recommended | Shared secret between your Gateway and this service. Set in production to prevent quota drain. If empty → open (dev mode only) |
| `ALLOWED_ORIGINS` | ⚠️ Recommended | Comma-separated allowed CORS origins. Defaults to `*` if not set |
| `MEMORY_DIR` | ❌ | Directory for per-user memory files. Default: `memory_data/` |
| `POSE_MODEL_PATH` | ❌ | Path to MediaPipe pose model. Bake into Docker image to avoid runtime download |

---

## 🔐 Authentication

All endpoints require the `X-Internal-Secret` header when `INTERNAL_API_SECRET` is set:

```
X-Internal-Secret: your-secret-here
```

Vision endpoints also require `X-User-Id` for feedback support:

```
X-User-Id: user_abc123
```

---

## 📡 Endpoints

### `POST /chat`

Main chat endpoint. Send user message → receive AI reply + updated state.

**Request**
```json
{
  "user_id": "user_abc123",
  "message": "create a workout plan for me",
  "profile": {
    "age": 25,
    "weight": 72.0,
    "height": 175.0,
    "goal": "lose weight",
    "workout_location": "home",
    "activity_level": "beginner"
  },
  "history": [
    {"role": "user", "content": "hi"},
    {"role": "assistant", "content": "Hello! How can I help?"}
  ],
  "progress_logs": [],
  "plan_cache": {}
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `user_id` | string | ✅ | Unique user identifier |
| `message` | string | ✅ | User's message |
| `profile` | object | ✅ | User's fitness profile (send `{}` for new user) |
| `history` | list | ❌ | Last N messages `[{role, content}]` — send last 10 |
| `progress_logs` | list | ❌ | Weight history `[{week, weight}]` |
| `plan_cache` | object | ❌ | Cached plans — send back exactly what you received |

**Response**
```json
{
  "reply": "Here is your personalized 7-day workout plan...",
  "tools_used": ["get_workout_plan"],
  "profile": { "age": 25, "weight": 72.0, "goal": "lose weight" },
  "plan_cache": { "workout": { "hash": "abc123", "raw": "..." } },
  "progress_logs": [],
  "assistant_entry_id": "a1b2c3d4",
  "schedule_changes": {
    "action": "full_replace",
    "plan_id": "abc123",
    "days": [...]
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `reply` | string | AI response — display this to the user |
| `tools_used` | list | Tools called internally (e.g. `["get_workout_plan"]`) |
| `profile` | object | ⚠️ **Updated profile — PERSIST THIS** |
| `plan_cache` | object | ⚠️ **Updated plan cache — PERSIST THIS** |
| `progress_logs` | list | ⚠️ **Weight logs — PERSIST THIS** |
| `assistant_entry_id` | string | ID for submitting feedback on this reply via `/feedback` |
| `schedule_changes` | object \| null | Schedule sync payload — `null` if no plan was created/changed. See [Schedule Sync](#-schedule-sync) |

---

### `POST /chat/stream`

Streaming version of `/chat`. Same request body. Returns **Server-Sent Events (SSE)**.

```
data: Here is \n\n
data: your plan...\n\n
data: [PROGRESS]⏳ جاري تجهيز خطة التمرين...\n\n
data: [META]{"tools_used":[...],"profile":{...},"plan_cache":{...},"progress_logs":[...],"assistant_entry_id":"...","schedule_changes":{...}}\n\n
data: [DONE]\n\n
```

| Event | Description |
|-------|-------------|
| `data: <text>` | Token chunk — append to display |
| `data: [PROGRESS]<msg>` | Tool in progress — show as temporary status (e.g. spinner). Replaced by real text when ready |
| `data: [META]{...}` | Final metadata — parse and persist `profile`, `plan_cache`, `progress_logs`, `schedule_changes` |
| `data: [DONE]` | Stream ended |

---

### `POST /voice`

Transcribe audio → run through chat pipeline → return reply + transcript.

**Request:** `multipart/form-data`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `file` | File | ✅ | Audio file |
| `user_id` | string | ✅ | Unique user identifier |
| `profile` | string | ❌ | JSON string — same as `/chat` |
| `history` | string | ❌ | JSON string |
| `progress_logs` | string | ❌ | JSON string |
| `plan_cache` | string | ❌ | JSON string |

**Supported formats:** `mp3`, `wav`, `ogg`, `m4a`, `aac`, `flac`, `webm`, `opus`

**Max file size:** 25 MB

**Response**
```json
{
  "reply": "تمام، هجهز لك خطة تمرين أسبوعية...",
  "transcript": "عايز خطة تمرين في البيت",
  "tools_used": ["get_workout_plan"],
  "profile": {},
  "progress_logs": [],
  "plan_cache": {},
  "assistant_entry_id": "a1b2c3d4",
  "schedule_changes": null
}
```

| Field | Type | Description |
|-------|------|-------------|
| `reply` | string | AI response |
| `transcript` | string | What the AI heard — show to user for confirmation |
| `assistant_entry_id` | string | Use for `/feedback` |
| `schedule_changes` | object \| null | Same as `/chat` |

---

### `POST /analyze`

Analyze a workout photo or video. Returns AI coach feedback.

**Request:** `multipart/form-data`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `file` | File | ✅ | Image or video file |
| `message` | string | ❌ | Optional question, e.g. `"how is my squat form?"` |
| `is_first_message` | bool | ❌ | Send `true` **only for the first media upload in a session** — controls the greeting. Do NOT send `true` on every request or the AI will re-greet the user each time |
| `profile` | string | ❌ | JSON string — used to personalise feedback (e.g. factor in injuries) |
| `history` | string | ❌ | JSON string — conversation context |

**Headers:** `X-User-Id: <user_id>` (required for feedback support)

**Supported formats:** `jpg`, `jpeg`, `png`, `webp`, `bmp`, `mp4`, `mov`, `avi`, `mkv`

**Max file size:** 100 MB

**Response**
```json
{
  "reply": "Your squat form looks good! However, I noticed your knees are slightly caving in...",
  "assistant_entry_id": "x9y8z7w6"
}
```

---

### `POST /analyze/stream`

Streaming version of `/analyze`. Same request + headers. Returns SSE tokens.

```
data: Your squat form...\n\n
data: [META]{"assistant_entry_id":"x9y8z7w6"}\n\n
data: [DONE]\n\n
```

> Newlines inside chunks are escaped as `\n` to keep SSE frames intact.

---

### `POST /feedback`

Submit feedback on any AI reply (chat, voice, or vision).

**Request**
```json
{
  "user_id": "user_abc123",
  "entry_id": "a1b2c3d4",
  "feedback": 1,
  "note": ""
}
```

| Field | Type | Description |
|-------|------|-------------|
| `user_id` | string | Must match the user who received the reply |
| `entry_id` | string | `assistant_entry_id` from any response |
| `feedback` | int | `1` = 👍 positive, `-1` = 👎 negative, `0` = reset |
| `note` | string | Optional free-text reason |

**Effect on future responses:**
- 👍 → reply style used as a positive example in future prompts for this user
- 👎 → reply excluded from memory retrieval, flagged as pattern to avoid
- Feedback is **per-user only** — never affects other users

**Response**
```json
{ "ok": true, "message": "Feedback recorded as positive" }
```

---

### `GET /health`

```json
{
  "status": "ok",
  "keys_loaded": 3,
  "keys_ok": true
}
```

| Field | Type | Description |
|-------|------|-------------|
| `status` | string | `"ok"` if keys are loaded, `"degraded"` if all keys are exhausted |
| `keys_loaded` | int | Number of API keys currently in rotation |
| `keys_ok` | bool | `false` if no valid keys remain — treat as unhealthy |

Use for Docker health checks and load-balancer probes. Consider the service unhealthy if `keys_ok` is `false`.

---

## 🗄️ State Management (Backend Responsibility)

The AI service is **stateless**. The backend must:

1. **Load** `profile` + last 10 `history` messages + `progress_logs` + `plan_cache` from DB before each request
2. **Send** them to `/chat` or `/voice`
3. **Save** the returned `profile`, `plan_cache`, `progress_logs` back to DB
4. **Apply** `schedule_changes` to the schedule table if present

```
Flutter App
    │
    ▼
Backend (your API + PostgreSQL)
    │  sends:    profile + history + progress_logs + plan_cache
    │  receives: reply + updated state + schedule_changes → saves to DB
    ▼
FitCoach AI Service  ← this repo  (port 8000)
    │
    ▼
Gemini API
```

---

## 📅 Schedule Sync

When a workout plan is created or a day is edited, the response includes `schedule_changes`.

### `full_replace` — new plan generated

Delete old schedule rows for this user and insert all days from the payload.

```json
{
  "action": "full_replace",
  "user_id": "user_abc123",
  "plan_id": "abc123def456",
  "days": [
    {
      "day_index": 0,
      "day_name": "Monday",
      "focus": "Chest & Triceps",
      "rest_day": false,
      "exercises": [
        { "order": 1, "name": "Push-ups", "sets": 3, "reps": "12", "rest_sec": 60, "notes": "" }
      ]
    }
  ]
}
```

### `update_days` — specific days changed

Update only the rows whose `day_index` appears in `days`.

```json
{
  "action": "update_days",
  "user_id": "user_abc123",
  "plan_id": "abc123def456",
  "days": [
    { "day_index": 2, "day_name": "Wednesday", "focus": "Cardio", "rest_day": false, "exercises": [...] }
  ]
}
```

### Gateway logic

```python
if response.schedule_changes:
    action = response.schedule_changes["action"]
    if action == "full_replace":
        db.delete_schedule(user_id)
        db.insert_days(response.schedule_changes["days"])
    elif action == "update_days":
        for day in response.schedule_changes["days"]:
            db.upsert_day(user_id, day["day_index"], day)
```

`plan_id` is stable for the same plan — use it for idempotent upserts.

| Field | Type | Description |
|-------|------|-------------|
| `day_index` | int | 0 = Monday … 6 = Sunday |
| `day_name` | string | English day name |
| `focus` | string | Muscle group or theme |
| `rest_day` | bool | True = no exercises |
| `exercises[].order` | int | Display order starting from 1 |
| `exercises[].sets` | int | Number of sets |
| `exercises[].reps` | string | e.g. `"12"` or `"30 sec"` |
| `exercises[].rest_sec` | int | Rest between sets in seconds |

---

## 👤 Profile Fields

| Field | Type | Values |
|-------|------|--------|
| `name` | string | Free text |
| `age` | integer | e.g. `25` |
| `gender` | string | `male` / `female` / `other` |
| `weight` | float | kg, e.g. `72.5` |
| `height` | float | cm, e.g. `175.0` |
| `goal` | string | `lose weight` / `gain weight` / `maintain weight` |
| `injuries` | string | Free text, e.g. `"knee pain"` |
| `workout_location` | string | `home` / `gym` / `outdoor` |
| `equipment` | string | Free text, e.g. `"dumbbells, mat"` |
| `activity_level` | string | `beginner` / `intermediate` / `advanced` |
| `diet_type` | string | Free text, e.g. `"vegan"`, `"keto"` |

---

## 🤖 AI Tools (Internal)

These tools are called automatically by the AI — no action needed from the backend.

| Tool | Triggers when |
|------|--------------|
| `update_profile` | User mentions name, age, weight, height, goal, injuries, etc. |
| `get_bmi` | User asks about BMI |
| `get_calories` | User asks about daily calorie needs |
| `get_workout_plan` | User asks for a workout plan |
| `get_nutrition_plan` | User asks for a diet or meal plan |
| `get_progress` | User asks about their weight progress |
| `log_weight` | User states their current weight with a unit (kg) |
| `get_recommendations` | User asks for exercise suggestions |
| `update_workout_day` | User asks to change a specific day (e.g. "change Tuesday to chest") |

---

## 🧠 Per-User Memory & Feedback

The service maintains a persistent memory file per user in `MEMORY_DIR`.

- Every message and reply is stored with a weight (default 1.0)
- 👍 feedback → weight 2.0 (style emulated in future prompts)
- 👎 feedback → weight 0.0 (excluded from retrieval, pattern avoided)
- Memory is retrieved semantically per query — only relevant context is injected
- Memory is **isolated per user** — User A's data never affects User B

---

## 🌐 Language Detection

The AI auto-detects language from the message:
- **Arabic** (any Arabic character) → replies in Arabic
- **Otherwise** → replies in English

Short words like `"ok"`, `"yes"`, `"تمام"` don't trigger a language switch — the AI looks at the full conversation pattern.

---

## ⚠️ Error Handling

The service always returns user-friendly messages — the `reply` field is always safe to display.

| HTTP | Scenario |
|------|----------|
| `200` | Success |
| `400` | Empty message or unsupported file type |
| `401` | Missing or invalid `X-Internal-Secret` |
| `413` | File exceeds size limit |
| `500` | API key missing or internal failure |

Content blocking (steroids, self-harm — Arabic + English) returns HTTP `200` with a safe redirect message.
