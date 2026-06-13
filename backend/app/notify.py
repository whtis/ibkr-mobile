"""Firebase Cloud Messaging sender.

Lazily initialises firebase-admin from the service-account JSON pointed to by
``settings.fcm_service_account_path``. When that path is unset or invalid, push
is a no-op (logged once) so the backend runs fine without Firebase configured.
"""
from __future__ import annotations

import logging

from . import db
from .config import settings

log = logging.getLogger(__name__)

_app = None          # firebase_admin.App once initialised
_init_failed = False


def _ensure_app():
    global _app, _init_failed
    if _app is not None or _init_failed:
        return _app
    path = settings.fcm_service_account_path
    if not path:
        _init_failed = True
        log.info("FCM disabled: fcm_service_account_path not set")
        return None
    try:
        import firebase_admin
        from firebase_admin import credentials

        cred = credentials.Certificate(path)
        _app = firebase_admin.initialize_app(cred)
        log.info("FCM initialised from %s", path)
    except Exception as e:  # noqa: BLE001 - any init failure should degrade gracefully
        _init_failed = True
        log.warning("FCM init failed (%s); push disabled", e)
    return _app


def enabled() -> bool:
    return _ensure_app() is not None


def send_to_all(title: str, body: str, data: dict | None = None) -> dict:
    """Send a notification to every registered device token.

    Prunes tokens that FCM reports as unregistered. Returns a small summary so
    callers (and the test endpoint) can see what happened.
    """
    app = _ensure_app()
    tokens = db.list_fcm_tokens()
    if app is None:
        return {"sent": 0, "failed": 0, "tokens": len(tokens), "reason": "fcm-disabled"}
    if not tokens:
        return {"sent": 0, "failed": 0, "tokens": 0, "reason": "no-tokens"}

    from firebase_admin import messaging

    sent = failed = 0
    str_data = {k: str(v) for k, v in (data or {}).items()}
    for token in tokens:
        msg = messaging.Message(
            token=token,
            notification=messaging.Notification(title=title, body=body),
            data=str_data,
            android=messaging.AndroidConfig(priority="high"),
        )
        try:
            messaging.send(msg)
            sent += 1
        except messaging.UnregisteredError:
            db.drop_fcm_token(token)
            failed += 1
            log.info("pruned unregistered FCM token")
        except Exception as e:  # noqa: BLE001
            failed += 1
            log.warning("FCM send failed for a token: %s", e)
    return {"sent": sent, "failed": failed, "tokens": len(tokens)}
