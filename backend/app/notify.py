"""Push notifications.

Primary path: POST to a Cloudflare Worker relay (``settings.fcm_relay_url``)
which forwards to FCM — the NAS backend can't reach Google directly (GFW).
Fallback: firebase-admin direct send (only where Google is reachable). When
neither is configured, push is a no-op.
"""
from __future__ import annotations

import asyncio
import logging

import httpx

from . import db
from .config import settings

log = logging.getLogger(__name__)

_app = None
_init_failed = False


def _ensure_app():
    global _app, _init_failed
    if _app is not None or _init_failed:
        return _app
    path = settings.fcm_service_account_path
    if not path:
        _init_failed = True
        return None
    try:
        import firebase_admin
        from firebase_admin import credentials

        _app = firebase_admin.initialize_app(credentials.Certificate(path))
        log.info("FCM (direct) initialised from %s", path)
    except Exception as e:  # noqa: BLE001
        _init_failed = True
        log.warning("FCM direct init failed (%s)", e)
    return _app


def enabled() -> bool:
    return bool(settings.fcm_relay_url) or _ensure_app() is not None


async def send_to_all(title: str, body: str, data: dict | None = None) -> dict:
    """Notify every registered device. Uses the relay if configured, else the
    direct firebase-admin path. Prunes tokens that FCM reports as unregistered."""
    tokens = db.list_fcm_tokens()
    if not tokens:
        return {"sent": 0, "failed": 0, "tokens": 0, "reason": "no-tokens"}
    str_data = {k: str(v) for k, v in (data or {}).items()}
    if settings.fcm_relay_url:
        return await _send_via_relay(tokens, title, body, str_data)
    if _ensure_app() is None:
        return {"sent": 0, "failed": 0, "tokens": len(tokens), "reason": "fcm-disabled"}
    return await asyncio.to_thread(_send_direct, tokens, title, body, str_data)


async def _send_via_relay(tokens: list[str], title: str, body: str, data: dict) -> dict:
    sent = failed = 0
    headers = {}
    if settings.fcm_relay_secret:
        headers["Authorization"] = f"Bearer {settings.fcm_relay_secret}"
    async with httpx.AsyncClient(timeout=15) as cx:
        for token in tokens:
            try:
                r = await cx.post(
                    settings.fcm_relay_url,
                    headers=headers,
                    json={"token": token, "title": title, "body": body, "data": data},
                )
                j: dict = {}
                try:
                    j = r.json()
                except Exception:  # noqa: BLE001
                    pass
                if r.status_code == 200 and j.get("ok"):
                    sent += 1
                elif j.get("unregistered"):
                    db.drop_fcm_token(token)
                    failed += 1
                    log.info("pruned unregistered FCM token (relay)")
                else:
                    failed += 1
                    log.warning("relay push failed: %s %s", r.status_code, r.text[:160])
            except Exception as e:  # noqa: BLE001
                failed += 1
                log.warning("relay push error: %s", e)
    return {"sent": sent, "failed": failed, "tokens": len(tokens), "via": "relay"}


def _send_direct(tokens: list[str], title: str, body: str, data: dict) -> dict:
    from firebase_admin import messaging

    sent = failed = 0
    for token in tokens:
        msg = messaging.Message(
            token=token,
            notification=messaging.Notification(title=title, body=body),
            data=data,
            android=messaging.AndroidConfig(priority="high"),
        )
        try:
            messaging.send(msg)
            sent += 1
        except messaging.UnregisteredError:
            db.drop_fcm_token(token)
            failed += 1
        except Exception as e:  # noqa: BLE001
            failed += 1
            log.warning("FCM direct send failed: %s", e)
    return {"sent": sent, "failed": failed, "tokens": len(tokens), "via": "direct"}
