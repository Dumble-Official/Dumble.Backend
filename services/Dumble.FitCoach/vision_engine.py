import asyncio
import base64
import json
import logging
import os
import threading
import urllib.request

import cv2
import httpx
import mediapipe as mp
import numpy as np

from key_manager import key_manager

logger = logging.getLogger(__name__)

VISION_MODELS = ["gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash"]
_GEMINI_BASE  = "https://generativelanguage.googleapis.com/v1beta/models"

def _gemini_url(model: str, stream: bool = False) -> str:
    action = "streamGenerateContent" if stream else "generateContent"
    return f"{_GEMINI_BASE}/{model}:{action}"

MOVE_THRESH = 6.0

MOVE_THRESH_INITIAL = 1.0

JERK_THRESH = 20.0

MIN_ROM_DEG = 20.0

LANDMARK_INDEX = {
    "LEFT_SHOULDER":11,"RIGHT_SHOULDER":12,
    "LEFT_ELBOW":13,   "RIGHT_ELBOW":14,
    "LEFT_WRIST":15,   "RIGHT_WRIST":16,
    "LEFT_HIP":23,     "RIGHT_HIP":24,
    "LEFT_KNEE":25,    "RIGHT_KNEE":26,
    "LEFT_ANKLE":27,   "RIGHT_ANKLE":28,
}
LANDMARK_CONF = {
    "LEFT_HIP":0.6,"RIGHT_HIP":0.6,
    "LEFT_KNEE":0.6,"RIGHT_KNEE":0.6,
    "LEFT_ANKLE":0.55,"RIGHT_ANKLE":0.55,
    "LEFT_SHOULDER":0.5,"RIGHT_SHOULDER":0.5,
    "LEFT_ELBOW":0.5,"RIGHT_ELBOW":0.5,
    "LEFT_WRIST":0.45,"RIGHT_WRIST":0.45,
}
ALL_JOINTS = [
    ("LEFT_SHOULDER","LEFT_ELBOW","LEFT_WRIST"),
    ("RIGHT_SHOULDER","RIGHT_ELBOW","RIGHT_WRIST"),
    ("LEFT_HIP","LEFT_KNEE","LEFT_ANKLE"),
    ("RIGHT_HIP","RIGHT_KNEE","RIGHT_ANKLE"),
    ("LEFT_SHOULDER","LEFT_HIP","LEFT_KNEE"),
    ("RIGHT_SHOULDER","RIGHT_HIP","RIGHT_KNEE"),
    ("LEFT_HIP","LEFT_SHOULDER","LEFT_ELBOW"),
    ("LEFT_SHOULDER","LEFT_HIP","LEFT_ANKLE"),
]
READABLE = {
    "LEFT_SHOULDER-LEFT_ELBOW-LEFT_WRIST":   "left_elbow",
    "RIGHT_SHOULDER-RIGHT_ELBOW-RIGHT_WRIST":"right_elbow",
    "LEFT_HIP-LEFT_KNEE-LEFT_ANKLE":         "left_knee",
    "RIGHT_HIP-RIGHT_KNEE-RIGHT_ANKLE":      "right_knee",
    "LEFT_SHOULDER-LEFT_HIP-LEFT_KNEE":      "left_hip_flexion",
    "RIGHT_SHOULDER-RIGHT_HIP-RIGHT_KNEE":   "right_hip_flexion",
    "LEFT_HIP-LEFT_SHOULDER-LEFT_ELBOW":     "left_shoulder_tilt",
    "LEFT_SHOULDER-LEFT_HIP-LEFT_ANKLE":     "left_body_line",
}

class AngleFilter:
    def __init__(self, alpha: float = 0.5):
        self.alpha = alpha
        self.prev  = None

    def update(self, v):
        if v is None: return self.prev
        self.prev = v if self.prev is None else self.alpha*v + (1-self.alpha)*self.prev
        return round(self.prev, 1)

_det_img = None

def _load_model(mode):
    default_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "model/pose_full.task")
    path         = os.getenv("POSE_MODEL_PATH", default_path)
    if not os.path.exists(path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        logger.info("Downloading MediaPipe pose model (~30MB) to %s …", path)
        urllib.request.urlretrieve(
            "https://storage.googleapis.com/mediapipe-models/pose_landmarker/"
            "pose_landmarker_full/float16/latest/pose_landmarker_full.task", path)
        logger.info("Pose model downloaded.")
    opts = mp.tasks.vision.PoseLandmarkerOptions(
        base_options=mp.tasks.BaseOptions(model_asset_path=path),
        running_mode=mode,
        min_pose_detection_confidence=0.55,
        min_pose_presence_confidence=0.55,
        min_tracking_confidence=0.5,
    )
    return mp.tasks.vision.PoseLandmarker.create_from_options(opts)

_det_vid  = None
_det_lock = threading.Lock()

def img_detector():
    global _det_img
    if _det_img is None:
        with _det_lock:
            if _det_img is None:
                _det_img = _load_model(mp.tasks.vision.RunningMode.IMAGE)
                logger.info("MediaPipe IMAGE loaded.")
    return _det_img

def vid_detector():
    global _det_vid
    if _det_vid is None:
        with _det_lock:
            if _det_vid is None:
                _det_vid = _load_model(mp.tasks.vision.RunningMode.VIDEO)
                logger.info("MediaPipe VIDEO loaded.")
    return _det_vid

def angle_3d(a: tuple, b: tuple, c: tuple) -> float:
    a2, b2, c2 = np.array(a), np.array(b), np.array(c)
    ba, bc = a2 - b2, c2 - b2
    cos = np.dot(ba, bc) / (np.linalg.norm(ba) * np.linalg.norm(bc) + 1e-8)
    return round(float(np.degrees(np.arccos(np.clip(cos, -1, 1)))), 1)

def get_lm(lms: list, name: str) -> tuple | None:
    idx = LANDMARK_INDEX.get(name)
    if idx is None: return None
    lm = lms[idx]
    if getattr(lm, "visibility", 1) < LANDMARK_CONF.get(name, 0.5): return None
    return (lm.x, lm.y, getattr(lm, "z", 0))

def _smooth_series(series: list, window: int = 3) -> list:
    """Simple moving-average smoothing to reduce noise before peak/valley detection.
    Window of 3 removes single-frame spikes while preserving real rep shape."""
    if len(series) < window:
        return series
    half = window // 2
    smoothed = []
    for i in range(len(series)):
        lo = max(0, i - half)
        hi = min(len(series), i + half + 1)
        smoothed.append(round(sum(series[lo:hi]) / (hi - lo), 1))
    return smoothed

def analyze_curve(series: list, fps: float = 30, frame_step: int = 1) -> dict | None:
    if len(series) < 4: return None

    s_smooth = _smooth_series(series, window=3)
    s_raw = series
    s, n  = s_smooth, len(s_smooth)

    valleys, peaks = [], []
    for i in range(1, n - 1):
        if s[i-1] > s[i] < s[i+1]: valleys.append(i)
        if s[i-1] < s[i] > s[i+1]: peaks.append(i)

    rep_quality = None
    if len(valleys) >= 2:
        depths = [s[v] for v in valleys]
        diff   = round(max(depths) - min(depths), 1)
        rep_quality = "consistent ✅" if diff < 15 else f"inconsistent ⚠️ ({diff}° variation)"

    rep_intervals = [(valleys[i] - valleys[i-1]) * frame_step / fps
                     for i in range(1, len(valleys))]
    avg_tempo = round(sum(rep_intervals) / len(rep_intervals), 2) if rep_intervals else None

    phase_ratio = None
    if valleys and peaks:
        ecc, con = [], []
        for v in valleys:
            pp         = [p for p in peaks if p < v]
            next_peaks = [p for p in peaks if p > v]
            if pp:         ecc.append(v - max(pp))
            if next_peaks: con.append(min(next_peaks) - v)
        if ecc and con:
            er = round(sum(ecc) / len(ecc), 1)
            cr = round(sum(con) / len(con), 1)
            phase_ratio = {"eccentric_frames": er, "concentric_frames": cr,
                           "ratio": round(er / cr, 2) if cr else None}

    vels        = [abs(s_raw[i] - s_raw[i-1]) for i in range(1, len(s_raw))]
    avg_vel     = round(sum(vels) / len(vels), 2)
    max_vel     = round(max(vels), 2)
    jerky_count = sum(1 for v in vels if v > JERK_THRESH)

    if not valleys and not peaks: pattern = "static hold"
    elif valleys and peaks:       pattern = "dynamic repetitive"
    elif peaks:                   pattern = "concentric only"
    else:                         pattern = "eccentric only"

    return {
        "min_angle":        round(min(s_raw), 1),
        "max_angle":        round(max(s_raw), 1),
        "rom":              round(max(s_raw) - min(s_raw), 1),
        "valleys":          len(valleys),
        "peaks":            len(peaks),
        "pattern":          pattern,
        "rep_quality":      rep_quality,
        "avg_tempo_sec":    avg_tempo,
        "phase_ratio":      phase_ratio,
        "avg_velocity":     avg_vel,
        "max_velocity":     max_vel,
        "jerky_frames":     jerky_count,
        "movement_control": "jerky ⚠️" if (max_vel > 25 or avg_vel > 12) else "controlled ✅",
    }

def get_pose_data(all_frames_bgr: list, fps: float = 30, is_video: bool = True) -> dict:
    try:
        use_video   = is_video and len(all_frames_bgr) > 1
        det         = vid_detector() if use_video else img_detector()
        filters     = {f"{a}-{b}-{c}": AngleFilter(0.5) for a, b, c in ALL_JOINTS}
        frame_ms    = max(1, int(1000 / fps))
        time_series = {f"{a}-{b}-{c}": [] for a, b, c in ALL_JOINTS}
        all_a       = []

        for i, frame in enumerate(all_frames_bgr):
            rgb    = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            mp_img = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
            res    = det.detect_for_video(mp_img, i * frame_ms) if use_video else det.detect(mp_img)
            if not res.pose_landmarks: continue
            lms = res.pose_landmarks[0]

            fa = {}
            for a, b, c in ALL_JOINTS:
                pa, pb, pc = get_lm(lms, a), get_lm(lms, b), get_lm(lms, c)
                k = f"{a}-{b}-{c}"
                if pa and pb and pc:
                    v = filters[k].update(angle_3d(pa, pb, pc))
                    fa[k] = v
                    time_series[k].append(v)
            if fa: all_a.append(fa)

        if not all_a:
            return {"available": False}

        frame_step   = max(1, int(fps / 3))
        primary_keys = {
            "left_knee":          "LEFT_HIP-LEFT_KNEE-LEFT_ANKLE",
            "right_knee":         "RIGHT_HIP-RIGHT_KNEE-RIGHT_ANKLE",
            "left_elbow":         "LEFT_SHOULDER-LEFT_ELBOW-LEFT_WRIST",
            "right_elbow":        "RIGHT_SHOULDER-RIGHT_ELBOW-RIGHT_WRIST",
            "left_hip_flexion":   "LEFT_SHOULDER-LEFT_HIP-LEFT_KNEE",
            "left_shoulder_tilt": "LEFT_HIP-LEFT_SHOULDER-LEFT_ELBOW",
        }
        curves = {}
        for label, key in primary_keys.items():
            s = time_series.get(key, [])
            if len(s) >= 3:
                curves[label] = analyze_curve(s, fps=fps, frame_step=frame_step)

        def _joint_score(c: dict) -> float:
            """Composite score for primary joint selection.
            Combines valleys (rep count), ROM (range of motion), and avg_velocity
            to avoid picking a noisy joint with many fake valleys but little real movement.
            Weights empirically tuned: reps dominate, ROM breaks ties, velocity penalises noise.
            """
            valleys_score  = c.get("valleys", 0) * 10.0
            rom_score      = min(c.get("rom", 0) / 5.0, 20.0)
            vel            = c.get("avg_velocity", 0)
            velocity_score = max(0.0, 5.0 - abs(vel - 4.0))
            return valleys_score + rom_score + velocity_score

        best_label, best_score = None, -1.0
        for label, c in curves.items():
            if c:
                sc = _joint_score(c)
                if sc > best_score:
                    best_label, best_score = label, sc
        best_curve = curves.get(best_label) if best_label else None

        reps = best_curve["valleys"] if best_curve else 0
        rom  = best_curve["rom"]     if best_curve else None
        if rom and rom < MIN_ROM_DEG:
            reps       = 0
            best_label = None

        avg = {}
        for k in all_a[0]:
            vals = [f[k] for f in all_a if k in f]
            if vals: avg[k] = round(sum(vals) / len(vals), 1)

        sym = {}
        for lk_, rk_, label in [
            ("LEFT_SHOULDER-LEFT_ELBOW-LEFT_WRIST","RIGHT_SHOULDER-RIGHT_ELBOW-RIGHT_WRIST","elbow"),
            ("LEFT_HIP-LEFT_KNEE-LEFT_ANKLE","RIGHT_HIP-RIGHT_KNEE-RIGHT_ANKLE","knee"),
            ("LEFT_SHOULDER-LEFT_HIP-LEFT_KNEE","RIGHT_SHOULDER-RIGHT_HIP-RIGHT_KNEE","spine"),
        ]:
            if lk_ in avg and rk_ in avg:
                d = round(abs(avg[lk_] - avg[rk_]), 1)
                sym[label] = {"diff_deg": d, "status": "asymmetric ⚠️" if d > 15 else "symmetric ✅"}

        ts_summary = {}
        for label, key in primary_keys.items():
            s = time_series.get(key, [])
            if s:
                step = max(1, len(s) // 20)
                ts_summary[label] = [s[i] for i in range(0, len(s), step)][:20]

        return {
            "available":         True,
            "mode":              "video_temporal" if use_video else "image",
            "frames_analyzed":   len(all_a),
            "rep_count":         reps if reps > 0 else None,
            "primary_joint":     best_label,
            "range_of_motion":   rom,
            "joint_angles_avg":  {READABLE.get(k, k): v for k, v in avg.items()},
            "symmetry":          sym,
            "curve_analysis":    {k: v for k, v in curves.items() if v},
            "angle_time_series": ts_summary,
        }
    except Exception as e:
        logger.error("Pose error: %s", e)
        return {"available": False, "error": str(e)}

def extract_frames(path: str) -> tuple[list, list, float]:
    cap      = cv2.VideoCapture(path)
    fps      = cap.get(cv2.CAP_PROP_FPS) or 30
    total_f  = cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0
    duration = total_f / fps if fps else 0
    max_frames = max(12, min(60, int(duration * 3)))
    step       = max(1, int(fps * 0.3))
    all_frames, prev, idx = [], None, 0
    raw_backup = []

    while len(all_frames) < max_frames:
        ret, frame = cap.read()
        if not ret: break
        idx += 1
        if len(raw_backup) < 8:
            raw_backup.append(frame)
        if idx % step != 0: continue
        gray   = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        thresh = MOVE_THRESH if len(all_frames) >= 5 else MOVE_THRESH_INITIAL
        if prev is not None and np.mean(cv2.absdiff(prev, gray)) < thresh and all_frames:
            prev = gray; continue
        prev = gray
        all_frames.append(frame)

    cap.release()

    if len(all_frames) < 4:
        step2      = max(1, len(raw_backup) // 6)
        all_frames = [raw_backup[i] for i in range(0, len(raw_backup), step2)][:8]

    logger.info("Frames for MediaPipe: %d", len(all_frames))

    key_frames = all_frames
    if len(all_frames) > 3:
        det = img_detector()
        scored = []
        for i, f in enumerate(all_frames):
            rgb = cv2.cvtColor(f, cv2.COLOR_BGR2RGB)
            res = det.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb))
            if not res.pose_landmarks: scored.append((i, 90.)); continue
            lms = res.pose_landmarks[0]
            lh, lk, la = get_lm(lms,"LEFT_HIP"), get_lm(lms,"LEFT_KNEE"), get_lm(lms,"LEFT_ANKLE")
            a = angle_3d(lh, lk, la) if (lh and lk and la) else 90.
            scored.append((i, a))
        scored.sort(key=lambda x: x[1])
        n = len(scored)
        idxs = sorted({scored[0][0], scored[n//4][0], scored[n//2][0],
                       scored[3*n//4][0], scored[-1][0]})
        key_frames = [all_frames[i] for i in idxs]

    key_b64 = []
    for f in key_frames[:6]:
        _, buf = cv2.imencode(".jpg", f, [cv2.IMWRITE_JPEG_QUALITY, 85])
        key_b64.append(base64.b64encode(buf).decode())

    logger.info("Key frames for Gemini: %d", len(key_b64))
    return all_frames, key_b64, fps

def load_image_file(path: str) -> tuple[list, list]:
    with open(path, "rb") as f: raw = f.read()
    arr = np.frombuffer(raw, np.uint8)
    bgr = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if bgr is None:
        try:
            from PIL import Image
            import io
            img    = Image.open(path).convert("RGB")
            buf_io = io.BytesIO()
            img.save(buf_io, format="JPEG", quality=85)
            buf_io.seek(0)
            bgr = cv2.imdecode(np.frombuffer(buf_io.read(), np.uint8), cv2.IMREAD_COLOR)
            logger.info("Loaded via PIL: %s", path)
        except Exception as e:
            logger.error("load_image_file error: %s", e)
            return [], []
    _, buf = cv2.imencode(".jpg", bgr, [cv2.IMWRITE_JPEG_QUALITY, 85])
    return [bgr], [base64.b64encode(buf).decode()]

async def _call_gemini(api_key: str, prompt: str,
                       images=None, video_b64=None,
                       video_mime="video/mp4", max_tokens=3000):
    parts = []
    if video_b64:
        parts.append({"inline_data": {"mime_type": video_mime, "data": video_b64}})
    elif images:
        parts += [{"inline_data": {"mime_type": "image/jpeg", "data": f}} for f in images]
    parts.append({"text": prompt})

    payload = {
        "system_instruction": {"parts": [{"text": _GEMINI_SYSTEM_PROMPT}]},
        "contents": [{"parts": parts}],
        "generationConfig": {"temperature": 0.25, "maxOutputTokens": max_tokens},
    }

    for model in VISION_MODELS:
        try:
            async with httpx.AsyncClient(timeout=httpx.Timeout(30, read=120)) as client:
                r = await client.post(
                    _gemini_url(model),
                    headers={"Content-Type": "application/json", "x-goog-api-key": api_key},
                    json=payload,
                )
            if r.status_code == 429:
                key_manager.mark_rate_limited(api_key)
                logger.warning("Gemini 429 on %s — trying next model", model)
                continue
            if r.status_code == 401:
                key_manager.mark_invalid(api_key)
                logger.error("Gemini 401 invalid vision key ...%s", api_key[-6:])
                return None
            if r.status_code != 200:
                logger.error("Gemini %s error: %s %s", model, r.status_code, r.text[:150])
                continue
            cand = r.json().get("candidates", [{}])[0]
            out  = cand.get("content", {}).get("parts", [])
            if not out:
                logger.warning("Gemini %s empty. reason: %s", model, cand.get("finishReason"))
                continue
            return out[0].get("text", "")
        except Exception as e:
            logger.error("_call_gemini %s error: %s", model, e)
            continue

    return None

def _build_prompt(user_message: str, pose_json: str, is_first_message: bool = False,
                  profile: dict | None = None, history: list | None = None) -> str:
    has_q = bool(user_message.strip())

    profile_ctx = ""
    if profile:
        pts = []
        if profile.get("injuries"):       pts.append(f"injuries={profile['injuries']}")
        if profile.get("goal"):           pts.append(f"goal={profile['goal']}")
        if profile.get("activity_level"): pts.append(f"level={profile['activity_level']}")
        if profile.get("name"):           pts.append(f"name={profile['name']}")
        if pts:
            profile_ctx = "USER PROFILE (use to personalise — never echo as list):\n" + " | ".join(pts)

    history_ctx = ""
    if history:
        recent = [m for m in history[-4:] if isinstance(m.get("content"), str) and m.get("content", "").strip()]
        if recent:
            lines = "\n".join(f"{m['role'].upper()}: {m['content'][:200]}" for m in recent)
            history_ctx = f"\nRECENT CONVERSATION CONTEXT (for continuity only):\n{lines}"

    no_msg_instruction = (
        "The user sent media without a message. Since it's their first message, give a very brief opener (2-3 words, e.g. 'أهلاً! '), then go straight into form analysis as if they asked 'كيف الأداء؟'."
        if (not has_q and is_first_message) else
        "The user sent media without a message. Give your analysis directly — no greeting, no asking what they want."
        if not has_q else ""
    )

    greeting_rule = (
        "This is the user's first message — start with a very brief warm opener only (2-3 words max, e.g. 'أهلاً! ' or 'Hey! '), then go straight into your analysis. No full sentences, no elaborate welcome."
        if is_first_message else
        "Do NOT greet. No 'أهلاً', no 'Hello', no opener of any kind. Jump straight into your analysis or answer."
    )

    return f"""You are FitCoach AI — a specialist fitness coach analyzing media sent by a user.

GREETING RULE (CRITICAL): {greeting_rule}

USER MESSAGE: "{user_message if has_q else '(no message)'}"
{"NO-MESSAGE RULE: " + no_msg_instruction if no_msg_instruction else ""}

BIOMECHANICAL DATA:
{pose_json}
{profile_ctx}{history_ctx}

HOW TO READ THIS DATA:
- left_hip_flexion / right_hip_flexion: hip angle (shoulder-hip-knee) — key for leg raises, deadlifts, squats
- angle_time_series: angle curve over time, e.g. [160,130,90,130,160] = one full squat rep
- curve_analysis.pattern: "dynamic repetitive" / "static hold" / etc.
- curve_analysis.rep_quality: are reps consistent or not?
- curve_analysis.avg_tempo_sec: seconds per rep
- curve_analysis.phase_ratio: eccentric vs concentric time
- curve_analysis.movement_control: "controlled" or "jerky"
- symmetry.diff_deg: left vs right difference (>15° = notable)
- joint_angles_avg: average position across all frames

INSTRUCTIONS:
1. Identify what is in the media: exercise / food / equipment / body photo / meal plan / other
2. Reply in the SAME LANGUAGE the user wrote in — Arabic or English, never both mixed in one reply.
   If Arabic: talk like you're texting a friend at the gym — natural Egyptian Arabic, no stiffness.
     • Words people actually say: فورم، سيتس، ريبس، سكوات، ديدليفت، بنش برس، بايسبس، تراي، كارديو، كور
     • Words people DON'T say → use natural Arabic instead:
         range of motion → "الحركة مش كاملة" / "نزل لحد الآخر"
         elbows → "كوعيك" / "الكوع"
         shoulders → "كتفيك" / "الكتف"
         glutes → "الأرداف" / "المؤخرة"
         momentum → "زخمة"
         contraction → "انقباض العضلة"
     • NEVER write English words in Latin script in an Arabic reply
     • NEVER use formal فصحى translations (القرفصاء، الرفعة المميتة)
   If no message → detect language from conversation history and apply the same rule.
3. VISUAL FIRST: what you see is the truth. Numbers are hints, not facts. If something looks correct visually, do not flag it just because a number seems off.
4. BALANCED HONESTY: if form is good, say so clearly and move on. If there are real issues, name them once. Do NOT hunt for errors just to seem thorough. Do NOT praise everything either.

SCOPE RULE (CRITICAL):
If the image clearly has NO relation to fitness, exercise, food/nutrition, body, or sports equipment — respond briefly that you're a fitness coach and can only help with fitness-related content. Do NOT analyze or comment on the image itself.

INTENT RESOLUTION (CRITICAL):
Always combine what you SEE and what the USER asks, then decide the task.
- food + calories question → estimate directly
- food + health question → evaluate directly
- exercise + form question → analyze form
- equipment + usage question → explain usage
- meal plan → evaluate even if question is vague
- no message → describe what you see + one smart question
NEVER ignore the media. Prefer helpful answers over asking more questions.

IF EXERCISE — COACHING RULES:
You are a real coach watching an athlete, not writing a report.
- Start with your honest overall impression in one sentence.
- If form is solid: say so clearly, maybe one small tip if genuinely useful — then stop.
- If there's a real issue: name it once, explain why it matters, give one fix. That's it.
- Minor imperfections that don't affect safety or results: mention briefly or skip entirely — do NOT treat them as problems.
- Genuinely harmful form (e.g. knees caving heavily, spine rounding in deadlift): flag it clearly and firmly — this is important, don't soften it.
- Never list 4-5 corrections. If there's truly only one thing to fix, say one thing.
- End with 1 natural follow-up question.
- Response feels like a short voice note from a coach — not a structured breakdown.

IF FOOD — identify specifically, estimate calories directly even if approximate, mention protein/carbs/fats briefly, answer the exact question. Never refuse to estimate.
IF EQUIPMENT — name it, explain correct use, top mistake to avoid.
IF MEAL PLAN — honest overall assessment, then one or two specific actionable suggestions.

LANGUAGE & TONE:
- Confident, warm, direct — like a knowledgeable coach, not a robot.
- Never mix Arabic and English in one reply — reply fully in the user's language.
- When replying in Arabic: talk like a coach texting a friend — natural, warm, never stiff.
  Words people actually say → use them:
    ✓ فورم، سيتس، ريبس، سكوات، ديدليفت، بنش برس، بول أب، لانج، بلانك، كرانش، بايسبس، تراي، كور، كارديو
  Words people DON'T say → translate by meaning, not phonetically:
    ✓ "الحركة مش كاملة" (not رينج أوف موشن)
    ✓ "كوعيك" (not إيلبوز)
    ✓ "كتفيك" (not شولدرز)
    ✓ "الأرداف" (not جلوتس)
    ✓ "زخمة" (not مومنتم)
    ✓ "انقباض العضلة" (not كونتراكشن)
  Never use formal فصحى: ✗ القرفصاء، الرفعة المميتة، ضغط المقعد
  Never write any word in Latin script in an Arabic reply
- Use data naturally in conversation, never as raw labels
- Never mention MediaPipe, algorithms, pose detection, or any technical system
- Flowing paragraphs only — no bullet points, no numbered lists, no bold section headers

DATA RELIABILITY RULES (CRITICAL):
- Angle values are rough estimates — camera perspective and depth distortion make them imprecise
- If a ROM value is below 30°, treat it as a likely detection error — do NOT mention it
- Small angle numbers like 10°, 15°, 17° are almost certainly camera artifacts — never quote them
- Asymmetry is very often caused by camera angle — ONLY mention it if unmistakably obvious visually
- NEVER rely on numbers that contradict what is visually clear — visual judgment always wins

EXERCISE-SPECIFIC RULES:
- For hanging/machine exercises (leg raises, pull-ups, dips), elbow asymmetry is normal — ignore it
- For lat pulldown and rowing, slight backward lean is intentional — only flag excessive swinging
- Upper body exercises (curls, press, fly, rows, pulldowns): ignore knee and ankle data entirely
- Lower body exercises (squats, lunges, deadlifts, leg raises): ignore elbow and wrist data entirely
- Only comment on joints directly involved in the exercise being performed
"""

_GEMINI_SYSTEM_PROMPT = "You are FitCoach AI — a warm, specialist fitness coach."

async def _call_gemini_stream(api_key: str, prompt: str,
                              images=None, video_b64=None,
                              video_mime="video/mp4", max_tokens=3000):
    """Yields text chunks — tries each model in VISION_MODELS on failure."""
    parts = []
    if video_b64:
        parts.append({"inline_data": {"mime_type": video_mime, "data": video_b64}})
    elif images:
        parts += [{"inline_data": {"mime_type": "image/jpeg", "data": f}} for f in images]
    parts.append({"text": prompt})

    payload = {
        "system_instruction": {"parts": [{"text": _GEMINI_SYSTEM_PROMPT}]},
        "contents": [{"parts": parts}],
        "generationConfig": {"temperature": 0.25, "maxOutputTokens": max_tokens},
    }

    for model in VISION_MODELS:
        got_any = False
        try:
            async with httpx.AsyncClient(timeout=httpx.Timeout(30, read=120)) as client:
                async with client.stream(
                    "POST",
                    _gemini_url(model, stream=True),
                    headers={"Content-Type": "application/json", "x-goog-api-key": api_key},
                    json=payload,
                ) as r:
                    if r.status_code == 429:
                        key_manager.mark_rate_limited(api_key)
                        logger.warning("Gemini stream 429 on %s — trying next model", model)
                        continue
                    if r.status_code == 401:
                        key_manager.mark_invalid(api_key)
                        logger.error("Gemini stream 401 invalid key ...%s", api_key[-6:])
                        return
                    if r.status_code != 200:
                        body = await r.aread()
                        logger.error("Gemini stream %s error: %s %s", model, r.status_code, body[:150])
                        continue

                    buffer = ""
                    async for raw_chunk in r.aiter_text():
                        buffer += raw_chunk
                        while "\n" in buffer:
                            line, buffer = buffer.split("\n", 1)
                            line = line.strip()
                            if line.startswith(","): line = line[1:].strip()
                            if not line or line in (",", "[", "]"):
                                continue
                            try:
                                obj  = json.loads(line)
                                text = (obj.get("candidates", [{}])[0]
                                           .get("content", {})
                                           .get("parts", [{}])[0]
                                           .get("text", ""))
                                if text:
                                    got_any = True
                                    yield text
                            except json.JSONDecodeError:
                                buffer = line + "\n" + buffer
                                break

                    remainder = buffer.strip()
                    if remainder:
                        try:
                            parsed = json.loads(remainder)
                            items = parsed if isinstance(parsed, list) else [parsed]
                            for obj in items:
                                text = (obj.get("candidates", [{}])[0]
                                           .get("content", {})
                                           .get("parts", [{}])[0]
                                           .get("text", ""))
                                if text:
                                    got_any = True
                                    yield text
                        except json.JSONDecodeError:
                            logger.warning("_call_gemini_stream: unparseable trailing buffer (%d chars)", len(remainder))

            if got_any:
                return

        except Exception as e:
            logger.error("_call_gemini_stream %s error: %s", model, e)
            continue

async def _prepare_media(file_path: str, user_message: str, is_first_message: bool,
                         profile: dict | None, history: list | None) -> dict:
    """
    Run all blocking CPU work (frame extraction, pose detection) in an executor,
    then build the prompt. Returns a dict with everything both analyze_media and
    analyze_media_stream need — the only difference between them is the Gemini call.
    """
    ext_low  = file_path.lower().rsplit(".", 1)[-1]
    is_image = ext_low in ("webp", "png", "jpg", "jpeg", "bmp", "gif")
    is_video = not is_image and ext_low in ("mp4", "mov", "avi", "mkv")

    loop = asyncio.get_running_loop()
    video_b64, video_mime, key_b64, fps = None, "video/mp4", [], 30

    if is_video:
        all_frames, key_b64, fps = await loop.run_in_executor(
            None, extract_frames, file_path)
        size = os.path.getsize(file_path)
        if size <= 20 * 1024 * 1024:
            mime_map = {"mp4": "video/mp4", "mov": "video/quicktime",
                        "avi": "video/x-msvideo", "mkv": "video/x-matroska"}
            video_mime = mime_map.get(ext_low, "video/mp4")
            with open(file_path, "rb") as f:
                video_b64 = base64.b64encode(f.read()).decode()
            logger.info("Full video sent to Gemini (%dKB)", size // 1024)
        else:
            logger.info("Video too large (%dMB) — using key frames", size // 1024 // 1024)
    else:
        all_frames, key_b64 = await loop.run_in_executor(
            None, load_image_file, file_path)

    pose = await loop.run_in_executor(None, get_pose_data, all_frames, fps, is_video)

    primary    = pose.get("primary_joint", "") or ""
    is_upper   = primary in ("left_elbow", "right_elbow", "left_shoulder_tilt")
    is_lower   = primary in ("left_knee", "right_knee", "left_hip_flexion", "right_hip_flexion")
    angles_all = pose.get("joint_angles_avg", {})
    curves_all = pose.get("curve_analysis", {})
    series_all = pose.get("angle_time_series", {})
    sym_all    = pose.get("symmetry", {})

    if not is_upper and not is_lower:
        elbow_v = max((curves_all.get("left_elbow")  or {}).get("valleys", 0),
                      (curves_all.get("right_elbow") or {}).get("valleys", 0))
        knee_v  = max((curves_all.get("left_knee")        or {}).get("valleys", 0),
                      (curves_all.get("right_knee")       or {}).get("valleys", 0),
                      (curves_all.get("left_hip_flexion") or {}).get("valleys", 0))
        if elbow_v != knee_v:
            is_upper, is_lower = elbow_v > knee_v, knee_v >= elbow_v
        else:
            hip_rom   = (curves_all.get("left_hip_flexion") or {}).get("rom", 0)
            elbow_rom = max((curves_all.get("left_elbow")  or {}).get("rom", 0),
                            (curves_all.get("right_elbow") or {}).get("rom", 0))
            is_lower  = hip_rom >= elbow_rom
            is_upper  = not is_lower

    if is_upper:
        keep, keep_sym = {"left_elbow", "right_elbow", "left_shoulder_tilt"}, {"elbow"}
    else:
        keep     = {"left_knee", "right_knee", "left_hip_flexion", "right_hip_flexion", "left_body_line"}
        keep_sym = {"knee", "spine"}

    pose_json = _json_dumps_pose(pose, primary, keep, keep_sym,
                                 angles_all, curves_all, series_all, sym_all)
    prompt    = _build_prompt(user_message, pose_json,
                              is_first_message=is_first_message,
                              profile=profile, history=history)

    return {
        "prompt":     prompt,
        "video_b64":  video_b64,
        "video_mime": video_mime,
        "key_b64":    key_b64,
    }

async def analyze_media_stream(api_key: str, file_path: str, user_message: str = "",
                               is_first_message: bool = False,
                               profile: dict | None = None, history: list | None = None):
    """Async generator — yields text chunks for streaming responses."""
    prepared = await _prepare_media(file_path, user_message, is_first_message, profile, history)
    prompt, video_b64, video_mime, key_b64 = (
        prepared["prompt"], prepared["video_b64"],
        prepared["video_mime"], prepared["key_b64"],
    )

    for attempt in range(3):
        if attempt > 0:
            await asyncio.sleep(attempt * 5)
        try:
            got_any = False
            if video_b64:
                async for chunk in _call_gemini_stream(api_key, prompt, video_b64=video_b64, video_mime=video_mime):
                    got_any = True
                    yield chunk
                if not got_any and key_b64:
                    logger.info("Stream fallback to key frames...")
                    async for chunk in _call_gemini_stream(api_key, prompt, images=key_b64):
                        got_any = True
                        yield chunk
            else:
                async for chunk in _call_gemini_stream(api_key, prompt, images=key_b64):
                    got_any = True
                    yield chunk
            if got_any:
                return
        except Exception as exc:
            logger.warning("Stream attempt %d failed: %s", attempt, exc)

    yield "Gemini مشغول دلوقتي، جرب تاني بعد ثواني."

def _json_dumps_pose(pose, primary, keep, keep_sym, angles_all, curves_all, series_all, sym_all) -> str:
    return json.dumps({
        "available":           pose.get("available"),
        "mode":                pose.get("mode"),
        "frames_analyzed":     pose.get("frames_analyzed"),
        "rep_count":           pose.get("rep_count"),
        "primary_joint":       primary,
        "range_of_motion_deg": pose.get("range_of_motion"),
        "joint_angles_avg":    {k:v for k,v in angles_all.items() if k in keep},
        "symmetry":            {k:v for k,v in sym_all.items()    if k in keep_sym},
        "curve_analysis":      {k:v for k,v in curves_all.items()
                                if k in keep or k.replace("right_","left_") in keep},
        "angle_time_series":   {k:v for k,v in series_all.items()
                                if k in keep or k.replace("right_","left_") in keep},
    }, indent=2, ensure_ascii=False)

async def analyze_media(api_key: str, file_path: str, user_message: str = "",
                        is_first_message: bool = False,
                        profile: dict | None = None, history: list | None = None) -> str:
    """Non-streaming analysis — delegates shared preparation to _prepare_media()."""
    prepared = await _prepare_media(file_path, user_message, is_first_message, profile, history)
    prompt, video_b64, video_mime, key_b64 = (
        prepared["prompt"], prepared["video_b64"],
        prepared["video_mime"], prepared["key_b64"],
    )

    response = None
    for attempt in range(3):
        if attempt > 0:
            wait = attempt * 5
            logger.info("503 retry %d — waiting %ds", attempt, wait)
            await asyncio.sleep(wait)

        if video_b64:
            response = await _call_gemini(api_key, prompt, video_b64=video_b64, video_mime=video_mime)
            if not response and key_b64:
                logger.info("Falling back to key frames...")
                response = await _call_gemini(api_key, prompt, images=key_b64)
        else:
            response = await _call_gemini(api_key, prompt, images=key_b64)

        if response:
            break

    return response or "Gemini مشغول دلوقتي، جرب تاني بعد ثواني."
