from fastapi import APIRouter, Depends

from ..auth import require_token
from ..ibkr import client
from ..models import AccountSummary, Position

router = APIRouter(prefix="/account", dependencies=[Depends(require_token)])

NUMERIC_TAGS = {
    "NetLiquidation": "net_liquidation",
    "TotalCashValue": "total_cash",
    "BuyingPower": "buying_power",
    "RealizedPnL": "realized_pnl",
    "UnrealizedPnL": "unrealized_pnl",
}


def _pnl_for_account(ib, account_id: str) -> dict[str, float | None]:
    for pnl in ib.pnl():
        if pnl.account == account_id:
            return {
                "daily_pnl": float(pnl.dailyPnL) if pnl.dailyPnL is not None and pnl.dailyPnL == pnl.dailyPnL else None,
                "unrealized_pnl": float(pnl.unrealizedPnL) if pnl.unrealizedPnL is not None and pnl.unrealizedPnL == pnl.unrealizedPnL else None,
                "realized_pnl": float(pnl.realizedPnL) if pnl.realizedPnL is not None and pnl.realizedPnL == pnl.realizedPnL else None,
            }
    return {"daily_pnl": None, "unrealized_pnl": None, "realized_pnl": None}


def _pnl_for_position(ib, account_id: str, con_id: int) -> dict[str, float | None]:
    for pnl in ib.pnlSingle():
        if pnl.account == account_id and pnl.conId == con_id:
            return {
                "daily_pnl": float(pnl.dailyPnL) if pnl.dailyPnL is not None and pnl.dailyPnL == pnl.dailyPnL else None,
            }
    return {"daily_pnl": None}


def _to_float(s: str) -> float:
    try:
        return float(s)
    except (TypeError, ValueError):
        return 0.0


@router.get("/summary", response_model=list[AccountSummary])
async def account_summary() -> list[AccountSummary]:
    ib = await client.ensure_connected()
    by_account: dict[str, dict] = {}

    for v in await ib.accountSummaryAsync():
        if v.tag not in NUMERIC_TAGS:
            continue
        bucket = by_account.setdefault(
            v.account,
            {"account_id": v.account, "currency": v.currency or "USD"},
        )
        bucket[NUMERIC_TAGS[v.tag]] = _to_float(v.value)

    # Merge in daily PnL from reqPnL stream (per-account).
    result: list[AccountSummary] = []
    for bucket in by_account.values():
        pnl = _pnl_for_account(ib, bucket["account_id"])
        if pnl["daily_pnl"] is not None:
            bucket["daily_pnl"] = pnl["daily_pnl"]
        result.append(AccountSummary(**bucket))
    return result


def _opt(x) -> float | None:
    if x is None:
        return None
    try:
        f = float(x)
    except (TypeError, ValueError):
        return None
    return None if f != f else f


@router.get("/positions", response_model=list[Position])
async def positions() -> list[Position]:
    ib = await client.ensure_connected()
    items = ib.portfolio()
    # Lazily subscribe to per-position PnL streams so future polls have data.
    subscribed_keys = {(p.account, p.conId) for p in ib.pnlSingle()}
    for p in items:
        key = (p.account, p.contract.conId)
        if key not in subscribed_keys and p.contract.conId:
            try:
                ib.reqPnLSingle(p.account, "", p.contract.conId)
            except Exception:
                pass

    out: list[Position] = []
    for p in items:
        single = _pnl_for_position(ib, p.account, p.contract.conId or 0)
        out.append(Position(
            account=p.account,
            symbol=p.contract.symbol,
            sec_type=p.contract.secType,
            exchange=p.contract.exchange or p.contract.primaryExchange or "",
            currency=p.contract.currency,
            position=float(p.position),
            avg_cost=float(p.averageCost),
            market_price=_opt(p.marketPrice),
            market_value=_opt(p.marketValue),
            unrealized_pnl=_opt(p.unrealizedPNL),
            realized_pnl=_opt(p.realizedPNL),
            daily_pnl=single["daily_pnl"],
        ))
    return out
