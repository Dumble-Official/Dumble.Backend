"""
content_filter.py — Bilingual (AR/EN) content moderation for FitCoach AI
"""

import logging
import re
import httpx
from dataclasses import dataclass
from typing import Optional

logger = logging.getLogger(__name__)


def _build_hard_patterns() -> list[tuple[re.Pattern, str]]:
    patterns: list[tuple[re.Pattern, str]] = []

    def _p(pats: list[str], reason: str, flags: int = re.I | re.UNICODE) -> None:
        combined = "|".join(f"(?:{p})" for p in pats)
        patterns.append((re.compile(combined, flags), reason))

    _p([
        r"\bsteroid(s)?\b", r"\banabolic\b", r"\btestosterone\s+inject",
        r"\bhgh\b", r"\bpeptide(s)?\b.*\b(inject|cycle|dose)",
        r"\bsarm(s)?\b", r"\bclenbuterol\b", r"\bwinstrol\b",
        r"\bdianabol\b", r"\btrenbolone\b", r"\bnandrolone\b",
        r"ستيرويد", r"منشطات", r"هرمون نمو",
        r"[إأا]بر.*عضل", r"حقن.*عضل",
    ], reason="peds")

    _p([
        r"\bself[\s\-]?harm\b", r"\bcut(ting)?\s+(myself|yourself)\b",
        r"\bsuicid", r"\bkill\s+my(self)?\b",
        r"\bwant\s+to\s+die\b", r"\bend\s+(my|this)\s+life\b",
        r"إيذاء\s+النفس", r"أؤذي\s+نفسي", r"أنهي\s+(حياتي|حياتى)",
        r"أقتل\s+نفسي", r"الانتحار", r"اتحرق", r"أجرح\s+نفسي",
    ], reason="self_harm")

    _p([
        r"\b(cocaine|heroin|meth|crack|fentanyl|lsd|mdma|ecstasy)\b",
        r"\b(drug|drugs)\s+(to\s+)?(lose\s+weight|burn\s+fat)\b",
        r"\b(كوكايين|هيروين|ميث|فنتانيل|حشيش|مخدرات)\b",
        r"مخدرات.*رياضة",
    ], reason="drugs")

    _p([
        r"\b(sex|porn|nude|naked|masturbat)\b",
        r"جنس|إباحي|عارية|تعري",
    ], reason="sexual")

    return patterns


def _build_scope_patterns() -> list[re.Pattern]:
    patterns: list[re.Pattern] = []

    def _sp(pats: list[str]) -> None:
        combined = "|".join(f"(?:{p})" for p in pats)
        patterns.append(re.compile(combined, re.I | re.UNICODE))

    _sp([
        r"\b(politic|election|president|government|war|army)\b",
        r"(سياسة|انتخابات|حكومة|حرب|جيش)",
        r"\b(religion|islam|christianity|fatwa)\b.*\b(workout|diet|haram|halal)\b",
        r"\b(stock|crypto|bitcoin|invest)\b",
        r"(بورصة|كريبتو|استثمار)",
        r"\b(diagnos|cancer|tumor|chemo|surgery)\b",
        r"(تشخيص|سرطان|ورم|عملية)",
    ])

    return patterns


_HARD_PATTERNS  = _build_hard_patterns()
_SCOPE_PATTERNS = _build_scope_patterns()


_CLASSIFIER_SYSTEM = """\
You are a content moderation classifier for a fitness coaching app.

Classify the user message into ONE of these categories:
  - "safe"       : fitness, nutrition, exercise, health, wellness, general chit-chat
  - "peds"       : performance-enhancing drugs, steroids, illegal supplements
  - "self_harm"  : self-harm, suicide, hurting oneself
  - "drugs"      : illegal recreational drugs
  - "sexual"     : sexual content
  - "off_topic"  : clearly unrelated to fitness (politics, finance, religion debates, etc.)

Rules:
- Legal supplements (protein, creatine, vitamins) → "safe"
- Injuries, pain, medical conditions in a fitness context → "safe"
- Calories, diet types, fasting → "safe"
- Short greetings, thanks, or vague messages → "safe"
- If unsure → "safe"

Respond with ONLY the category word. Nothing else."""

_VALID_LABELS = {"safe", "peds", "self_harm", "drugs", "sexual", "off_topic"}

_CLASSIFIER_MODELS = ["gemini-2.5-flash", "gemini-2.0-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash-lite"]


def _llm_classify(text: str, api_key: str) -> str:
    payload = {
        "max_tokens":  10,
        "temperature": 0,
        "messages": [
            {"role": "system", "content": _CLASSIFIER_SYSTEM},
            {"role": "user",   "content": text[:800]},
        ],
    }
    for model in _CLASSIFIER_MODELS:
        try:
            resp = httpx.post(
                "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
                headers={"Authorization": f"Bearer {api_key}",
                         "Content-Type": "application/json"},
                json={**payload, "model": model},
                timeout=httpx.Timeout(5, read=15),
            )
            if resp.status_code == 429:
                logger.warning("Classifier 429 on %s — trying next model", model)
                continue
            if resp.status_code != 200:
                logger.warning("Classifier HTTP %s on %s — trying next model",
                               resp.status_code, model)
                continue
            choices = resp.json().get("choices") or []
            if choices:
                raw   = choices[0].get("message") or {}
                label = (raw.get("content") or "").strip().lower()
                return label if label in _VALID_LABELS else "safe"
        except httpx.TimeoutException:
            logger.warning("Classifier timeout on %s — trying next model", model)
        except Exception as exc:
            logger.warning("Classifier error on %s: %s", model, exc)

    logger.warning("Classifier: all models exhausted — failing open (safe)")
    return "safe"


_BLOCK_MSGS: dict[str, dict[str, str]] = {
    "peds": {
        "ar": "⚠️ المنشطات والستيرويدات خارج نطاق تخصصي تماماً. أنا هنا عشان رحلتك الرياضية الطبيعية! 💪",
        "en": "⚠️ Performance-enhancing drugs are completely outside my scope. I'm here for your natural fitness journey! 💪",
    },
    "self_harm": {
        "ar": "⚠️ لاحظت إنك بتمر بوقت صعب. أنا مش متخصص في الدعم النفسي، لكن أرجوك تتواصل مع خط نجدة نفسية في بلدك. أنت مهم. 🙏",
        "en": "⚠️ It sounds like you're going through something difficult. Please reach out to a mental health professional or crisis line in your country. You matter. 🙏",
    },
    "drugs": {
        "ar": "⚠️ الموضوع ده خارج نطاق تخصصي. أنا هنا عشان رحلتك الرياضية! 💪",
        "en": "⚠️ This topic is outside my scope. I'm here for your fitness journey! 💪",
    },
    "sexual": {
        "ar": "⚠️ المحتوى ده مش مناسب هنا. أنا متخصص في الفيتنس والتغذية فقط — قولي عن رحلتك الرياضية! 💪",
        "en": "⚠️ This content isn't appropriate here. I specialise in fitness and nutrition — tell me about your fitness journey! 💪",
    },
    "off_topic": {
        "ar": "أنا متخصص في الفيتنس والتغذية فقط — لو عندك أي سؤال عن التمرين أو الأكل، أنا هنا! 💪",
        "en": "I specialise in fitness and nutrition only — ask me anything about training or food! 💪",
    },
}


def get_block_message(reason: str, lang: str) -> str:
    msgs = _BLOCK_MSGS.get(reason, _BLOCK_MSGS["off_topic"])
    return msgs.get(lang, msgs["en"])


@dataclass
class FilterResult:
    blocked: bool
    reason:  str  = ""
    is_hard: bool = False
    message: str  = ""


def filter_message(
    text:    str,
    lang:    str,
    api_key: Optional[str] = None,
    use_llm: bool = True,
) -> FilterResult:
    t = text.strip()

    for pattern, reason in _HARD_PATTERNS:
        if pattern.search(t):
            logger.warning("Hard block [%s]: %.60s", reason, t)
            msg = get_block_message(reason, lang)
            return FilterResult(blocked=True, reason=reason,
                                is_hard=True, message=msg)

    scope_hit = any(p.search(t) for p in _SCOPE_PATTERNS)

    if scope_hit:
        if api_key and use_llm:
            label = _llm_classify(t, api_key)
            if label != "safe":
                logger.info("LLM block [%s]: %.60s", label, t)
                msg = get_block_message(label, lang)
                return FilterResult(blocked=True, reason=label,
                                    is_hard=False, message=msg)
        else:
            logger.warning(
                "Scope hit detected but LLM classifier disabled "
                "(api_key=%s, use_llm=%s) — passing through: %.60s",
                bool(api_key), use_llm, t,
            )

    return FilterResult(blocked=False)
