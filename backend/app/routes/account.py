import asyncio

from fastapi import APIRouter, Depends, HTTPException

from ..auth import require_signature
from ..ibkr import client
from ..models import AccountSummary, Position

router = APIRouter(prefix="/account", dependencies=[Depends(require_signature)])

NUMERIC_TAGS = {
    "NetLiquidation": "net_liquidation",
    "TotalCashValue": "total_cash",
    "BuyingPower": "buying_power",
    "RealizedPnL": "realized_pnl",
    "UnrealizedPnL": "unrealized_pnl",
}


def _to_float(s: str) -> float:
    try:
        return float(s)
    except (TypeError, ValueError):
        return 0.0


def _opt(x) -> float | None:
    """Coerce an IB numeric to float, mapping NaN and IB's 'unset' sentinel
    (~1.7977e308 == sys.float_info.max) to None."""
    if x is None:
        return None
    try:
        f = float(x)
    except (TypeError, ValueError):
        return None
    if f != f or abs(f) >= 1e300:
        return None
    return f


def _pnl_for_account(ib, account_id: str) -> dict[str, float | None]:
    for pnl in ib.pnl():
        if pnl.account == account_id:
            return {
                "daily_pnl": _opt(pnl.dailyPnL),
                "unrealized_pnl": _opt(pnl.unrealizedPnL),
                "realized_pnl": _opt(pnl.realizedPnL),
            }
    return {"daily_pnl": None, "unrealized_pnl": None, "realized_pnl": None}


@router.get("/accounts")
async def accounts() -> dict:
    """List the accounts visible to this login, for the holdings account switcher."""
    ib = await client.ensure_connected()
    accs = list(ib.managedAccounts())
    return {"accounts": accs, "default": accs[0] if accs else None}


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


@router.get("/positions", response_model=list[Position])
async def positions(account: str | None = None) -> list[Position]:
    """Holdings for one account. `account` defaults to the first managed account.

    Built from ib.positions() + reqPnLSingle rather than ib.portfolio(): with
    multiple linked accounts reqAccountUpdates/portfolio() fails to deliver
    portfolio items, while positions()/pnlSingle are multi-account safe.
    """
    ib = await client.ensure_connected()
    managed = list(ib.managedAccounts())
    if account is not None and account not in managed:
        raise HTTPException(status_code=404, detail=f"unknown account {account}")
    acct = account or (managed[0] if managed else "")

    pos = ib.positions(acct) if acct else ib.positions()

    # Lazily subscribe to per-position PnL streams (value + unrealized/daily/realized PnL).
    subscribed = {(s.account, s.conId) for s in ib.pnlSingle()}
    newly = 0
    for p in pos:
        con_id = p.contract.conId
        if con_id and (p.account, con_id) not in subscribed:
            try:
                ib.reqPnLSingle(p.account, "", con_id)
                newly += 1
            except Exception:
                pass
    if newly:
        # Let the first batch of pnlSingle snapshots arrive so the first poll has data.
        await asyncio.sleep(1.5)

    pnl_by_key = {(s.account, s.conId): s for s in ib.pnlSingle()}

    out: list[Position] = []
    for p in pos:
        c = p.contract
        s = pnl_by_key.get((p.account, c.conId))
        try:
            mult = float(c.multiplier) if c.multiplier else 1.0
        except (TypeError, ValueError):
            mult = 1.0
        market_value = _opt(s.value) if s else None
        market_price = None
        if market_value is not None and p.position and (p.position * mult):
            market_price = market_value / (p.position * mult)
        out.append(Position(
            account=p.account,
            symbol=c.symbol,
            sec_type=c.secType,
            exchange=c.exchange or c.primaryExchange or "",
            currency=c.currency,
            position=float(p.position),
            avg_cost=float(p.avgCost),
            market_price=market_price,
            market_value=market_value,
            unrealized_pnl=_opt(s.unrealizedPnL) if s else None,
            realized_pnl=_opt(s.realizedPnL) if s else None,
            daily_pnl=_opt(s.dailyPnL) if s else None,
        ))
    return out
