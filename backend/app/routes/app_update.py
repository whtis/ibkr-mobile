"""In-app update support + a manual push test.

``GET /app/latest`` reports the newest GitHub release so the client can compare
it against its own versionName and offer an in-app update. ``POST /app/test-push``
fires a test FCM so the user can confirm the notification pipeline end to end.
"""
from __future__ import annotations

import logging

import httpx
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel

from .. import notify
from ..auth import require_signature
from ..config import settings

log = logging.getLogger(__name__)

router = APIRouter(prefix="/app", dependencies=[Depends(require_signature)])


class LatestRelease(BaseModel):
    version_name: str            # tag without leading "v", e.g. "0.0.2-beta.20260601"
    notes: str = ""
    apk_url: str | None = None
    prerelease: bool = False


@router.get("/latest", response_model=LatestRelease)
async def latest(channel: str = "stable") -> LatestRelease:
    """Newest GitHub release for the requested channel. The client treats
    `version_name != BuildConfig.VERSION_NAME` as 'update available'.

    - channel=stable (default): latest non-prerelease release.
    - channel=beta: most recent release, including pre-releases.
    """
    repo = settings.github_repo
    headers = {"Accept": "application/vnd.github+json"}
    if settings.github_token:
        headers["Authorization"] = f"Bearer {settings.github_token}"
    try:
        async with httpx.AsyncClient(timeout=10) as cx:
            if channel == "beta":
                r = await cx.get(
                    f"https://api.github.com/repos/{repo}/releases",
                    headers=headers, params={"per_page": 1},
                )
            else:
                # /releases/latest excludes pre-releases and drafts = the stable channel.
                r = await cx.get(f"https://api.github.com/repos/{repo}/releases/latest", headers=headers)
    except httpx.HTTPError as e:
        raise HTTPException(502, f"github unreachable: {e}") from e
    if r.status_code == 404:
        raise HTTPException(404, f"no release for channel {channel}")
    if r.status_code != 200:
        raise HTTPException(502, f"github releases error {r.status_code}")
    payload = r.json()
    rel = payload[0] if isinstance(payload, list) else payload
    if not rel:
        raise HTTPException(404, f"no release for channel {channel}")
    tag = rel.get("tag_name", "") or ""
    apk = next(
        (a["browser_download_url"] for a in rel.get("assets", [])
         if str(a.get("name", "")).endswith(".apk")),
        None,
    )
    # Route the download through the Cloudflare proxy so the phone isn't stuck
    # on GitHub's China-throttled release CDN (which truncates large APKs).
    if apk and settings.apk_proxy_base:
        marker = "/releases/download/"
        if marker in apk:
            apk = settings.apk_proxy_base.rstrip("/") + "/apk/" + apk.split(marker, 1)[1]
    return LatestRelease(
        version_name=tag[1:] if tag.startswith("v") else tag,
        notes=rel.get("body") or "",
        apk_url=apk,
        prerelease=bool(rel.get("prerelease")),
    )


@router.post("/test-push")
async def test_push() -> dict:
    """Fire a test notification to all registered devices."""
    res = await notify.send_to_all(
        title="IBKR 测试通知",
        body="推送通道工作正常 ✅",
        data={"type": "test"},
    )
    return {"ok": True, "fcm_enabled": notify.enabled(), "result": res}
