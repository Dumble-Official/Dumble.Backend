
import logging
import threading
import time

logger = logging.getLogger(__name__)

COOLDOWN_SECONDS     = 60
MAX_COOLDOWN_SECONDS = 300

class KeyManager:
    def __init__(self, keys: list[str]):
        self._keys:      list[str]        = list(keys)
        self._index:     int              = 0
        self._cooldowns: dict[str, float] = {}
        self._lock       = threading.Lock()

    def reload(self, keys: list[str]) -> None:
        with self._lock:
            self._keys  = list(keys)
            self._index = 0
        logger.info("KeyManager: loaded %d key(s)", len(keys))

    def get(self) -> str:
        with self._lock:
            if not self._keys:
                raise RuntimeError("No API keys configured")

            self._prune_cooldowns()

            now   = time.time()
            total = len(self._keys)

            for _ in range(total):
                key         = self._keys[self._index % total]
                self._index = (self._index + 1) % total
                if now >= self._cooldowns.get(key, 0):
                    return key

            key = min(self._keys, key=lambda k: self._cooldowns.get(k, 0))
            logger.warning("All keys cooling — using soonest: ...%s", key[-6:])
            return key

    def mark_rate_limited(self, key: str,
                          retry_after: int = COOLDOWN_SECONDS) -> None:
        with self._lock:
            if key not in self._keys:
                logger.debug(
                    "mark_rate_limited called for unknown/removed key ...%s — ignored",
                    key[-6:],
                )
                return

            now          = time.time()
            remaining    = max(0.0, self._cooldowns.get(key, 0) - now)

            backoff      = max(retry_after, remaining * 2) if remaining > 0 else retry_after
            new_cooldown = min(backoff, MAX_COOLDOWN_SECONDS)
            until        = now + new_cooldown
            self._cooldowns[key] = until
            logger.warning(
                "Key ...%s rate-limited — cooling for %ds (until %s)",
                key[-6:], int(new_cooldown),
                time.strftime("%H:%M:%S", time.localtime(until)),
            )

    def mark_invalid(self, key: str) -> None:
        with self._lock:
            before     = len(self._keys)
            self._keys = [k for k in self._keys if k != key]
            removed    = before - len(self._keys)
            if removed:
                logger.error("Key ...%s is invalid — removed from rotation", key[-6:])
            if not self._keys:
                self._index = 0
                logger.error("KeyManager: no valid keys remaining!")

    @property
    def count(self) -> int:
        with self._lock:
            return len(self._keys)

    def _prune_cooldowns(self) -> None:
        now = time.time()
        self._cooldowns = {k: v for k, v in self._cooldowns.items() if v > now}

key_manager = KeyManager([])
