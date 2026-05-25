from fastapi import APIRouter

from ..ibkr import client

router = APIRouter()


@router.get("/health")
async def health() -> dict:
    return {
        "ok": True,
        "ib_connected": client.ib.isConnected(),
    }
