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
async def latest() -> LatestRelease:
    """Most recent GitHub release (including pre-releases). The client treats
    `version_name != BuildConfig.VERSION_NAME` as 'update available' — the most
    recent release is by definition the newest."""
    url = f"https://api.github.com/repos/{settings.github_repo}/releases"
    headers = {"Accept": "application/vnd.github+json"}
    if settings.github_token:
        headers["Authorization"] = f"Bearer {settings.github_token}"
    try:
        async with httpx.AsyncClient(timeout=10) as cx:
            r = await cx.get(url, headers=headers, params={"per_page": 1})
    except httpx.HTTPError as e:
        raise HTTPException(502, f"github unreachable: {e}") from e
    if r.status_code != 200:
        raise HTTPException(502, f"github releases error {r.status_code}")
    rels = r.json()
    if not rels:
        raise HTTPException(404, "no releases found")
    rel = rels[0]
    tag = rel.get("tag_name", "") or ""
    apk = next(
        (a["browser_download_url"] for a in rel.get("assets", [])
         if str(a.get("name", "")).endswith(".apk")),
        None,
    )
    return LatestRelease(
        version_name=tag[1:] if tag.startswith("v") else tag,
        notes=rel.get("body") or "",
        apk_url=apk,
        prerelease=bool(rel.get("prerelease")),
    )


@router.post("/test-push")
async def test_push() -> dict:
    """Fire a test notification to all registered devices."""
    res = notify.send_to_all(
        title="IBKR 测试通知",
        body="推送通道工作正常 ✅",
        data={"type": "test"},
    )
    return {"ok": True, "fcm_enabled": notify.enabled(), "result": res}
