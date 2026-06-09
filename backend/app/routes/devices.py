"""Device pairing & management.

The pair endpoint accepts a Bearer token, generates a fresh 32-byte HMAC key,
stores it in the devices table, and returns it to the client. From that point
on, the client uses HMAC signatures (see app/auth.require_signature) for every
other call.

Pair is the only endpoint that still trusts the static Bearer token. Once a
device has paired, the user should rotate the token (so an old leaked token
cannot pair a second adversary device).
"""
from __future__ import annotations

import logging
import secrets

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel

from .. import db
from ..auth import require_signature, require_token

log = logging.getLogger(__name__)

router = APIRouter(prefix="/devices")


class PairRequest(BaseModel):
    label: str | None = None


class PairResponse(BaseModel):
    device_id: str
    hmac_key_hex: str  # 64 hex chars = 32 bytes


class DeviceInfo(BaseModel):
    device_id: str
    label: str | None = None
    created_at: int
    last_used_at: int | None = None


@router.post("/pair", response_model=PairResponse, dependencies=[Depends(require_token)])
async def pair(req: PairRequest) -> PairResponse:
    """Bearer token guarded. Generates a key + device_id; persists; returns once.
    The key is never stored or echoed again after this call."""
    device_id = secrets.token_hex(8)  # 16 hex chars
    hmac_key = secrets.token_bytes(32)
    db.create_device(device_id, hmac_key, req.label)
    log.info("device paired: %s (label=%r)", device_id, req.label)
    return PairResponse(device_id=device_id, hmac_key_hex=hmac_key.hex())


@router.get("", response_model=list[DeviceInfo], dependencies=[Depends(require_signature)])
async def list_devices() -> list[DeviceInfo]:
    return [DeviceInfo(**d) for d in db.list_devices_safe()]


@router.delete("/{device_id}", dependencies=[Depends(require_signature)])
async def revoke(device_id: str) -> dict:
    if not db.delete_device(device_id):
        raise HTTPException(404, "device not found")
    return {"ok": True}
