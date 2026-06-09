"""Authentication.

Two ways to authenticate:

  * Bearer token (Authorization header) — legacy + only used by /devices/pair now.
    A static secret; if it leaks, the holder can pair a device and from then on
    speak as that device.

  * HMAC signature — every authed endpoint requires X-Timestamp + X-Signature
    headers, where signature = HMAC_SHA256(device_hmac_key, canonical_string)
    and canonical_string = METHOD + "\n" + PATH_WITH_QUERY + "\n" + TIMESTAMP_MS
    + "\n" + sha256_hex(body). The key lives in Android Keystore (hardware-backed)
    on the phone and in the devices table on the backend; neither side leaks it.
    Replay protection: timestamp must be within ±60s of server time, and the
    signature itself is cached for 60s (any second attempt is rejected).
"""
import hashlib
import hmac
import logging
import time
from collections import OrderedDict

from fastapi import Header, HTTPException, Request, status

from . import db
from .config import settings

log = logging.getLogger(__name__)

# Window (seconds) inside which a timestamp is acceptable.
TIMESTAMP_WINDOW_S = 60

# In-process LRU-ish nonce cache. Key: signature hex. Value: epoch when it expires.
# Bounded to avoid runaway memory: oldest get evicted past the cap.
_NONCE_CAP = 2048
_nonce_cache: OrderedDict[str, float] = OrderedDict()


def require_token(authorization: str | None = Header(None)) -> None:
    """Bearer token guard. Kept only for /devices/pair."""
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "missing bearer token")
    if authorization.removeprefix("Bearer ") != settings.api_token:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "invalid token")


def _canonical(method: str, path_and_query: str, ts: str, body: bytes) -> bytes:
    body_hash = hashlib.sha256(body).hexdigest()
    return f"{method}\n{path_and_query}\n{ts}\n{body_hash}".encode()


def _check_nonce(sig_hex: str) -> None:
    """Reject signature replays inside the 60s acceptance window. Best-effort
    bounded cache — even under purge the timestamp check still bounds replay."""
    now = time.time()
    # Evict expired
    while _nonce_cache and next(iter(_nonce_cache.values())) <= now:
        _nonce_cache.popitem(last=False)
    if sig_hex in _nonce_cache:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "replay rejected")
    _nonce_cache[sig_hex] = now + TIMESTAMP_WINDOW_S
    while len(_nonce_cache) > _NONCE_CAP:
        _nonce_cache.popitem(last=False)


async def require_signature(
    request: Request,
    x_timestamp: str | None = Header(None),
    x_signature: str | None = Header(None),
    x_device_id: str | None = Header(None),
) -> None:
    """Verify HMAC-SHA256 signature on the request.

    Lookup order for the HMAC key:
      1. If X-Device-ID header present, look up that key only.
      2. Else, iterate over all paired devices and accept the first match.

    All failures collapse to 401 with no detail beyond "auth failed" to avoid
    leaking which check tripped (timestamp window vs replay vs bad signature
    vs unknown device). Logs carry the real reason on the server side.
    """
    fail = HTTPException(status.HTTP_401_UNAUTHORIZED, "auth failed")
    if not x_timestamp or not x_signature:
        log.warning("require_signature: missing X-Timestamp or X-Signature")
        raise fail
    try:
        ts_ms = int(x_timestamp)
    except ValueError:
        log.warning("require_signature: bad X-Timestamp not int")
        raise fail
    now_ms = int(time.time() * 1000)
    if abs(now_ms - ts_ms) > TIMESTAMP_WINDOW_S * 1000:
        log.warning("require_signature: timestamp out of window: ts=%d now=%d", ts_ms, now_ms)
        raise fail

    body = await request.body()
    method = request.method.upper()
    raw_path = request.url.path
    if request.url.query:
        raw_path = f"{raw_path}?{request.url.query}"
    canonical = _canonical(method, raw_path, x_timestamp, body)

    # Find matching key
    matched_id: str | None = None
    sig_bytes_target: bytes
    try:
        sig_bytes_target = bytes.fromhex(x_signature)
    except ValueError:
        log.warning("require_signature: bad signature hex")
        raise fail

    keys_to_try: list[tuple[str, bytes]] = []
    if x_device_id:
        k = db.get_device_key(x_device_id)
        if k is None:
            log.warning("require_signature: unknown device_id=%s", x_device_id)
            raise fail
        keys_to_try.append((x_device_id, k))
    else:
        for d in db.list_all_devices():
            keys_to_try.append((d["device_id"], bytes(d["hmac_key"])))

    if not keys_to_try:
        log.warning("require_signature: no devices paired yet")
        raise fail

    for did, key in keys_to_try:
        expected = hmac.new(key, canonical, hashlib.sha256).digest()
        if hmac.compare_digest(expected, sig_bytes_target):
            matched_id = did
            break

    if matched_id is None:
        log.warning("require_signature: no device key matched (tried %d)", len(keys_to_try))
        raise fail

    # Replay protection. Bind nonce to (device_id, sig) so two devices with
    # different keys producing the same sig would only collide pathologically.
    _check_nonce(f"{matched_id}:{x_signature}")
    db.touch_device(matched_id)
