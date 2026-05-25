"""Fill history backed by SQLite. The IBKR client streams live fills into the DB via
`execDetailsEvent`, plus a one-shot backfill of the last ~7 days at startup. This
endpoint just reads from the DB — no IBKR round-trip per request."""

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from .. import db
from ..auth import require_token

router = APIRouter(prefix="/executions", dependencies=[Depends(require_token)])


class ExecutionTick(BaseModel):
    time: int
    symbol: str
    sec_type: str
    side: str       # "BUY" or "SELL"
    price: float
    shares: float
    exchange: str | None = None


@router.get("/{symbol}", response_model=list[ExecutionTick])
async def executions_for(symbol: str, limit: int = 500) -> list[ExecutionTick]:
    rows = db.list_for_symbol(symbol, limit=limit)
    return [
        ExecutionTick(
            time=r["time"],
            symbol=r["symbol"],
            sec_type=r["sec_type"],
            side=r["side"],
            price=r["price"],
            shares=r["shares"],
            exchange=r["exchange"],
        )
        for r in rows
    ]


@router.get("", response_model=dict)
async def executions_status() -> dict:
    """Quick diagnostic: how many rows in the DB right now."""
    return {"total": db.count()}
