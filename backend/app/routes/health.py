from fastapi import APIRouter

from ..config import settings

router = APIRouter()


@router.get("/health")
async def health() -> dict:
    if settings.mock_mode:
        return {"ok": True, "ib_connected": False, "mock_mode": True}
    from ..ibkr import client
    return {
        "ok": True,
        "ib_connected": client.ib.isConnected(),
        "mock_mode": False,
    }
