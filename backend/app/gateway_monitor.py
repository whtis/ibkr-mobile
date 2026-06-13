"""Background task that watches the IB Gateway container's logs and pushes a
phone reminder when 2FA is needed.

IBC opens a "Second Factor Authentication" dialog on every *cold* login — the
weekly Sunday restart, a container recreate, a crash. The gateway then waits
(up to ~3 min) for the user to approve the IB Key prompt on their phone. This
monitor tails ``docker logs`` for that dialog and fires an FCM push so the user
doesn't miss the window. It resets after "Login has completed" so the next 2FA
event notifies again, and a cooldown prevents spamming during IBC's retries.

Runs only in the live (non-mock) backend. Requires the backend process to be
able to run ``docker logs`` (the deploy user is in the docker group).
"""
from __future__ import annotations

import asyncio
import logging
import time

from . import notify
from .config import settings

log = logging.getLogger(__name__)

_TWOFA_MARK = "Second Factor Authentication"
_OPENED = "event=Opened"
_LOGIN_DONE = "Login has completed"


async def run() -> None:
    """Long-running loop. Restarts the docker-logs stream if it dies (e.g. the
    container is recreated)."""
    last_notify = 0.0
    while True:
        proc = None
        try:
            proc = await asyncio.create_subprocess_exec(
                "docker", "logs", "-f", "--since", "120s", settings.gateway_container,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.STDOUT,
            )
            log.info("2FA monitor: tailing logs of container %s", settings.gateway_container)
            assert proc.stdout is not None
            async for raw in proc.stdout:
                line = raw.decode("utf-8", "replace")
                if _LOGIN_DONE in line:
                    last_notify = 0.0  # a fresh 2FA later should notify again
                    continue
                if _TWOFA_MARK in line and _OPENED in line:
                    now = time.monotonic()
                    if now - last_notify < settings.twofa_notify_cooldown_s:
                        continue
                    last_notify = now
                    res = await notify.send_to_all(
                        title="IBKR 网关需要验证",
                        body="网关正在登录,请打开 IBKR Mobile 确认 IB Key 二次验证(约 3 分钟内)。",
                        data={"type": "gateway_2fa"},
                    )
                    log.info("2FA dialog detected -> push %s", res)
        except asyncio.CancelledError:
            if proc is not None and proc.returncode is None:
                proc.terminate()
            raise
        except Exception as e:  # noqa: BLE001
            log.warning("2FA monitor stream error: %s; retrying in 15s", e)
        finally:
            if proc is not None and proc.returncode is None:
                try:
                    proc.terminate()
                except ProcessLookupError:
                    pass
        await asyncio.sleep(15)
