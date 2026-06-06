from fastapi import APIRouter

from ..config import settings

router = APIRouter()


def _account_and_mode() -> tuple[str | None, bool]:
    """Returns (account_id, is_live).

    is_live=False is the safe default — we only flip to True when we have
    positive evidence the account does not start with "DU" (IBKR's prefix
    for Demo User / paper accounts).
    """
    try:
        from ..ibkr import client
        if not client.ib.isConnected():
            return None, False
        accounts = list(client.ib.managedAccounts())
    except Exception:
        return None, False
    if not accounts:
        return None, False
    first = accounts[0]
    is_live = not first.startswith("DU")
    return first, is_live


@router.get("/health")
async def health() -> dict:
    if settings.mock_mode:
        return {
            "ok": True,
            "ib_connected": False,
            "mock_mode": True,
            "account_id": None,
            "is_live": False,
        }
    from ..ibkr import client
    account_id, is_live = _account_and_mode()
    return {
        "ok": True,
        "ib_connected": client.ib.isConnected(),
        "mock_mode": False,
        "account_id": account_id,
        "is_live": is_live,
    }
