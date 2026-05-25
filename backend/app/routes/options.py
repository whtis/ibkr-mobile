import logging
from datetime import date, datetime

from fastapi import APIRouter, Depends, HTTPException, Query

from ..auth import require_token
from ..longbridge import lb_client, to_lb_symbol
from ..models import OptionContract, OptionExpiry

log = logging.getLogger(__name__)

expiry_router = APIRouter(prefix="/options/expiries", dependencies=[Depends(require_token)])
chain_router = APIRouter(prefix="/options/chain", dependencies=[Depends(require_token)])


@expiry_router.get("/{symbol}", response_model=list[OptionExpiry])
async def expiries(symbol: str, currency: str = "USD") -> list[OptionExpiry]:
    if not lb_client.is_configured():
        raise HTTPException(503, "longbridge not configured")
    lb_symbol = to_lb_symbol(symbol, currency)
    dates = await lb_client.option_expiry_dates(lb_symbol)
    out: list[OptionExpiry] = []
    for d in dates:
        if isinstance(d, date):
            out.append(OptionExpiry(expiry=d.isoformat()))
        else:
            out.append(OptionExpiry(expiry=str(d)))
    return out


@chain_router.get("/{symbol}", response_model=list[OptionContract])
async def chain(
    symbol: str,
    expiry: str = Query(..., description="YYYY-MM-DD"),
    currency: str = "USD",
) -> list[OptionContract]:
    if not lb_client.is_configured():
        raise HTTPException(503, "longbridge not configured")
    lb_symbol = to_lb_symbol(symbol, currency)
    try:
        exp_date = datetime.strptime(expiry, "%Y-%m-%d").date()
    except ValueError:
        raise HTTPException(400, f"bad expiry format: {expiry} (want YYYY-MM-DD)")
    strikes = await lb_client.option_chain_info(lb_symbol, exp_date)
    contract_syms: list[str] = []
    by_strike: list[tuple[float, str | None, str | None]] = []
    for s in strikes:
        strike = float(s.price)
        cs = getattr(s, "call_symbol", None)
        ps = getattr(s, "put_symbol", None)
        by_strike.append((strike, cs, ps))
        if cs:
            contract_syms.append(cs)
        if ps:
            contract_syms.append(ps)
    quotes = []
    if contract_syms:
        # option_quote takes 1-200 symbols at a time; chunk if needed
        chunk = 200
        for i in range(0, len(contract_syms), chunk):
            try:
                quotes.extend(await lb_client.option_quote(contract_syms[i:i + chunk]))
            except Exception as e:
                log.warning("option_quote chunk failed: %s", e)
    qmap = {getattr(q, "symbol", ""): q for q in quotes}

    out: list[OptionContract] = []
    for strike, cs, ps in by_strike:
        if cs:
            q = qmap.get(cs)
            out.append(_to_contract(cs, symbol, expiry, strike, "C", q))
        if ps:
            q = qmap.get(ps)
            out.append(_to_contract(ps, symbol, expiry, strike, "P", q))
    out.sort(key=lambda c: (c.strike, c.right))
    return out


def _to_contract(opt_symbol: str, underlying: str, expiry: str, strike: float, right: str, q):
    def f(name):
        v = getattr(q, name, None) if q is not None else None
        try:
            if v is None:
                return None
            x = float(v)
            return None if x != x else x
        except (TypeError, ValueError):
            return None
    last = f("last_done")
    prev = f("prev_close")
    change_pct = None
    if last is not None and prev:
        change_pct = (last - prev) / prev * 100.0
    return OptionContract(
        symbol=opt_symbol,
        underlying=underlying.upper(),
        expiry=expiry,
        strike=strike,
        right=right,
        last=last,
        change_pct=change_pct,
        iv=f("implied_volatility"),
        delta=f("delta"),
        gamma=f("gamma"),
        theta=f("theta"),
        vega=f("vega"),
        open_interest=int(getattr(q, "open_interest", 0) or 0) if q else None,
        volume=int(getattr(q, "volume", 0) or 0) if q else None,
    )
