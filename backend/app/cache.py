"""Minimal async TTL cache shared across route handlers. In-memory only — fine for the
single-process backend; nothing here needs to survive restarts."""

import asyncio
import time
from typing import Awaitable, Callable, Hashable

_lock = asyncio.Lock()
_store: dict[Hashable, tuple[object, float]] = {}


async def cached(key: Hashable, ttl: float, fn: Callable[[], Awaitable]):
    """Return cached value for `key` if younger than `ttl` seconds; otherwise call `fn()`,
    store its result, and return that. Concurrent callers requesting the same key while a
    compute is in-flight just wait on the same async lock — no thundering-herd."""
    now = time.time()
    async with _lock:
        hit = _store.get(key)
        if hit is not None and now - hit[1] < ttl:
            return hit[0]
    value = await fn()
    async with _lock:
        _store[key] = (value, time.time())
    return value
