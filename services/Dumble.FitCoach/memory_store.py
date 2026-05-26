
import json
import logging
import os
import threading
import time
import uuid
from collections import OrderedDict
from typing import Optional

logger = logging.getLogger(__name__)

MEMORY_DIR      = os.getenv("MEMORY_DIR", "memory_data")
MAX_ENTRIES     = 200
MAX_USERS_CACHE = 1000

_MIN_STORE_LEN = 15

WEIGHT_POSITIVE = 2.0
WEIGHT_NEGATIVE = 0.0
WEIGHT_NEUTRAL  = 1.0

def _user_path(user_id: str) -> str:
    safe = "".join(c for c in user_id if c.isalnum() or c in "-_")[:64]
    os.makedirs(MEMORY_DIR, exist_ok=True)
    return os.path.join(MEMORY_DIR, f"{safe}.json")

class UserMemory:

    def __init__(self, user_id: str):
        self.user_id  = user_id
        self._lock    = threading.Lock()
        self._entries: list[dict] = []
        self._dirty   = False

        self._emb_cache: dict = {}

        self._load()

    def _load(self) -> None:
        path = _user_path(self.user_id)
        if not os.path.exists(path):
            return
        try:
            with open(path, "r", encoding="utf-8") as f:
                data = json.load(f)
            self._entries = data.get("entries", [])
            logger.debug("Loaded %d entries for user %s",
                         len(self._entries), self.user_id)
        except Exception as exc:
            logger.warning("Failed to load memory for %s: %s", self.user_id, exc)

    def save(self) -> None:
        if not self._dirty:
            return

        with self._lock:
            snapshot = list(self._entries)
            self._dirty = False

        path = _user_path(self.user_id)
        try:
            tmp = path + ".tmp"
            with open(tmp, "w", encoding="utf-8") as f:
                json.dump({"entries": snapshot}, f,
                          ensure_ascii=False, indent=2)
            os.replace(tmp, path)
            logger.debug("Saved memory for user %s (%d entries)",
                         self.user_id, len(snapshot))
        except Exception as exc:

            with self._lock:
                self._dirty = True
            logger.warning("Failed to save memory for %s: %s", self.user_id, exc)

    def store(self, text: str, role: str = "user") -> str:
        if not text.strip() or len(text.strip()) < _MIN_STORE_LEN:
            return ""

        entry = {
            "id":            uuid.uuid4().hex[:8],
            "text":          text.strip(),
            "role":          role,
            "feedback":      0,
            "feedback_note": "",
            "timestamp":     int(time.time()),
            "weight":        WEIGHT_NEUTRAL,
        }

        with self._lock:
            self._entries.append(entry)

            if len(self._entries) > MAX_ENTRIES:

                ts_vals = [e["timestamp"] for e in self._entries]
                ts_min, ts_max = min(ts_vals), max(ts_vals)
                ts_range = (ts_max - ts_min) or 1
                self._entries.sort(
                    key=lambda e: (
                        e["weight"],
                        (e["timestamp"] - ts_min) / ts_range,
                    )
                )
                evicted = self._entries[:-MAX_ENTRIES]
                self._entries = self._entries[-MAX_ENTRIES:]

                for ev in evicted:
                    self._emb_cache.pop(ev["id"], None)

            self._dirty = True

        self.save()
        return entry["id"]

    def apply_feedback(self, entry_id: str, feedback: int,
                       note: str = "") -> bool:
        updated_entry = None

        with self._lock:
            for entry in self._entries:
                if entry["id"] == entry_id:
                    entry["feedback"]      = feedback
                    entry["feedback_note"] = note
                    entry["weight"] = (
                        WEIGHT_POSITIVE if feedback == 1  else
                        WEIGHT_NEGATIVE if feedback == -1 else
                        WEIGHT_NEUTRAL
                    )
                    updated_entry = entry
                    self._dirty   = True
                    break

        if updated_entry is None:
            return False

        self.save()
        logger.info("Feedback %+d applied to entry %s for user %s",
                    feedback, entry_id, self.user_id)
        return True

    def retrieve(self, query: str, k: int = 4) -> list[dict]:
        with self._lock:
            candidates = [e for e in self._entries if e["weight"] > 0]

        if not candidates:
            return []

        try:
            return self._semantic_retrieve(query, candidates, k)
        except Exception:
            pass

        return self._keyword_retrieve(query, candidates, k)

    def _semantic_retrieve(self, query: str,
                           candidates: list[dict], k: int) -> list[dict]:
        import numpy as np
        import faiss

        model = _get_embedder()

        to_encode_ids  = [e["id"] for e in candidates
                          if e["id"] not in self._emb_cache]
        to_encode_txts = [e["text"] for e in candidates
                          if e["id"] not in self._emb_cache]

        if to_encode_txts:
            new_vecs = model.encode(
                to_encode_txts, show_progress_bar=False
            ).astype("float32")
            for eid, vec in zip(to_encode_ids, new_vecs):
                self._emb_cache[eid] = vec

        vecs    = np.stack([self._emb_cache[e["id"]] for e in candidates])
        weights = np.array([e["weight"] for e in candidates], dtype="float32")

        vecs = vecs * weights[:, None]

        idx = faiss.IndexFlatL2(vecs.shape[1])
        idx.add(vecs)
        q_vec = model.encode([query], show_progress_bar=False).astype("float32")
        k_    = min(k, len(candidates))
        _, indices = idx.search(q_vec, k_)
        return [candidates[i] for i in indices[0] if 0 <= i < len(candidates)]

    def _keyword_retrieve(self, query: str,
                          candidates: list[dict], k: int) -> list[dict]:
        q_words = set(query.lower().split())
        scored  = [
            (len(q_words & set(e["text"].lower().split())) * e["weight"], e)
            for e in candidates
        ]
        scored.sort(key=lambda x: -x[0])
        return [e for _, e in scored[:k]]

    def get_positive_summaries(self, limit: int = 5) -> list[str]:
        with self._lock:
            pos = [e for e in self._entries
                   if e["feedback"] == 1 and e["role"] == "assistant"]
        pos.sort(key=lambda e: -e["timestamp"])
        return [e["text"] for e in pos[:limit]]

    def get_negative_topics(self, limit: int = 5) -> list[str]:
        with self._lock:
            neg = [e for e in self._entries
                   if e["feedback"] == -1 and e["role"] == "assistant"]
        neg.sort(key=lambda e: -e["timestamp"])
        return [e["text"][:120] for e in neg[:limit]]

_embedder      = None
_embedder_lock = threading.Lock()

def _get_embedder():
    global _embedder
    if _embedder is not None:
        return _embedder
    with _embedder_lock:
        if _embedder is None:
            from sentence_transformers import SentenceTransformer
            _embedder = SentenceTransformer("all-MiniLM-L6-v2")
            logger.info("Memory embedder loaded.")
    return _embedder

_users_lock = threading.Lock()
_users: OrderedDict[str, UserMemory] = OrderedDict()

def get_user_memory(user_id: str) -> UserMemory:
    # Construction is held under the lock instead of using the previous
    # "insert None placeholder, release lock, construct, reacquire" dance.
    # The old code raced: a second thread acquiring the lock between the
    # release and the reacquire would see _users[user_id] == None and
    # return None to its caller, which then crashed on the next attribute
    # access. UserMemory.__init__ does a small JSON file read (few KB,
    # millisecond-range) so holding the lock for the duration is cheap.
    with _users_lock:
        if user_id in _users:
            _users.move_to_end(user_id)
            return _users[user_id]

        try:
            mem = UserMemory(user_id)
        except Exception as exc:
            logger.error("Failed to create UserMemory for %s: %s", user_id, exc)
            raise

        _users[user_id] = mem
        _users.move_to_end(user_id)

        while len(_users) > MAX_USERS_CACHE:
            evicted_id, evicted_mem = _users.popitem(last=False)
            if evicted_mem is not None:
                try:
                    evicted_mem.save()
                except Exception as exc:
                    logger.error("Failed to flush evicted user %s: %s",
                                 evicted_id, exc)
            logger.debug("LRU evicted user memory: %s", evicted_id)

    return mem

def flush_all() -> None:
    with _users_lock:
        users_snapshot = list(_users.values())

    saved = 0
    for mem in users_snapshot:
        if mem is None:
            continue
        try:
            mem.save()
            saved += 1
        except Exception as exc:
            logger.error("flush_all: failed to save user %s: %s",
                         mem.user_id, exc)

    logger.info("memory_store: flushed %d user memories", saved)
