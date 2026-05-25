from fastapi import APIRouter, Depends, HTTPException, Query

from ..auth import require_token
from ..longbridge import lb_client, to_lb_symbol
from ..models import Bar, Depth, DepthLevel, ExtendedQuote, IntradayPoint, Quote, StaticInfo, TradeTick


def _ext_quote(pq, prev_close):
    """Convert longbridge PrePostQuote to our ExtendedQuote."""
    if pq is None:
        return None
    last = float(pq.last_done) if getattr(pq, "last_done", None) is not None else None
    change = (last - prev_close) if (last is not None and prev_close is not None) else None
    change_pct = (change / prev_close * 100.0) if (change is not None and prev_close) else None
    return ExtendedQuote(
        last=last,
        prev_close=prev_close,
        change=change,
        change_pct=change_pct,
        volume=float(pq.volume) if getattr(pq, "volume", None) is not None else None,
        timestamp=pq.timestamp.isoformat() if getattr(pq, "timestamp", None) is not None else None,
    )

router = APIRouter(prefix="/quote", dependencies=[Depends(require_token)])
bars_router = APIRouter(prefix="/bars", dependencies=[Depends(require_token)])
quotes_router = APIRouter(prefix="/quotes", dependencies=[Depends(require_token)])
static_router = APIRouter(prefix="/static", dependencies=[Depends(require_token)])
depth_router = APIRouter(prefix="/depth", dependencies=[Depends(require_token)])
intraday_router = APIRouter(prefix="/intraday", dependencies=[Depends(require_token)])
trades_router = APIRouter(prefix="/trades", dependencies=[Depends(require_token)])


# ----------------------- /quote/{symbol} -----------------------

@router.get("/{symbol}", response_model=Quote)
async def quote(symbol: str, currency: str = "USD") -> Quote:
    if not lb_client.is_configured():
        raise HTTPException(503, "longbridge not configured")
    from ..cache import cached
    key = ("quote", symbol.upper(), currency)
    # 2s TTL — WebSocket push keeps the client live; HTTP quote is just for first paint.
    return await cached(key, 2.0, lambda: _quote_via_longbridge(symbol, currency))


async def _quote_via_longbridge(symbol: str, currency: str) -> Quote:
    lb_symbol = to_lb_symbol(symbol, currency)
    quotes = await lb_client.quote([lb_symbol])
    if not quotes:
        raise HTTPException(404, f"no quote for {lb_symbol}")
    q = quotes[0]

    last = float(q.last_done) if q.last_done is not None else None
    prev_close = float(q.prev_close) if q.prev_close is not None else None
    change = (last - prev_close) if (last is not None and prev_close is not None) else None
    change_pct = (change / prev_close * 100.0) if (change is not None and prev_close) else None

    return Quote(
        symbol=symbol.upper(),
        last=last,
        bid=None,  # use /depth for bid/ask
        ask=None,
        open=float(q.open) if q.open is not None else None,
        high=float(q.high) if q.high is not None else None,
        low=float(q.low) if q.low is not None else None,
        close=prev_close,
        prev_close=prev_close,
        volume=float(q.volume) if q.volume is not None else None,
        turnover=float(q.turnover) if getattr(q, "turnover", None) is not None else None,
        change=change,
        change_pct=change_pct,
        pre_market=_ext_quote(getattr(q, "pre_market_quote", None), prev_close),
        post_market=_ext_quote(getattr(q, "post_market_quote", None), prev_close),
        timestamp=q.timestamp.isoformat() if q.timestamp else datetime.now(timezone.utc).isoformat(),
    )


# ----------------------- /bars/{symbol} -----------------------

@bars_router.get("/{symbol}", response_model=list[Bar])
async def bars(
    symbol: str,
    period: str = Query("1d", description="1min|5min|15min|30min|1h|1d|1w|1mo"),
    currency: str = "USD",
) -> list[Bar]:
    if not lb_client.is_configured():
        raise HTTPException(503, "longbridge not configured")
    from ..cache import cached
    ttl = 10.0 if period in {"1min", "2min", "3min", "5min", "15min", "30min", "60min", "1h", "5d"} else 60.0
    key = ("bars", symbol.upper(), period, currency)
    return await cached(key, ttl, lambda: _bars_via_longbridge(symbol, period, currency))


async def _bars_via_longbridge(symbol: str, period: str, currency: str) -> list[Bar]:
    lb_symbol = to_lb_symbol(symbol, currency)
    candles = await lb_client.candlesticks(lb_symbol, period, count=500)
    return [
        Bar(
            time=int(c.timestamp.timestamp()),
            open=float(c.open),
            high=float(c.high),
            low=float(c.low),
            close=float(c.close),
            volume=float(c.volume),
        )
        for c in candles
    ]


# ----------------------- /quotes?symbols=A,B,C -----------------------

@quotes_router.get("", response_model=list[Quote])
async def quotes(
    symbols: str = Query(..., description="Comma-separated symbols, e.g. TSLA,AAPL,MSFT"),
    currency: str = "USD",
) -> list[Quote]:
    if not lb_client.is_configured():
        raise HTTPException(503, "longbridge not configured; batch quotes require longbridge")
    sym_list = [s.strip().upper() for s in symbols.split(",") if s.strip()]
    if not sym_list:
        return []
    lb_symbols = [to_lb_symbol(s, currency) for s in sym_list]
    raw = await lb_client.quote(lb_symbols)
    out: list[Quote] = []
    now_iso = datetime.now(timezone.utc).isoformat()
    for original, q in zip(sym_list, raw):
        last = float(q.last_done) if q.last_done is not None else None
        prev_close = float(q.prev_close) if q.prev_close is not None else None
        change = (last - prev_close) if (last is not None and prev_close is not None) else None
        change_pct = (change / prev_close * 100.0) if (change is not None and prev_close) else None
        out.append(Quote(
            symbol=original,
            last=last,
            bid=None,
            ask=None,
            open=float(q.open) if q.open is not None else None,
            high=float(q.high) if q.high is not None else None,
            low=float(q.low) if q.low is not None else None,
            close=prev_close,
            prev_close=prev_close,
            volume=float(q.volume) if q.volume is not None else None,
            change=change,
            change_pct=change_pct,
            timestamp=q.timestamp.isoformat() if q.timestamp else now_iso,
        ))
    return out


# ----------------------- /static/{symbol} -----------------------

@static_router.get("/{symbol}", response_model=StaticInfo)
async def static_info(symbol: str, currency: str = "USD") -> StaticInfo:
    if not lb_client.is_configured():
        raise HTTPException(503, "longbridge not configured")
    from ..cache import cached
    lb_symbol = to_lb_symbol(symbol, currency)
    return await cached(("static", lb_symbol), 600.0, lambda: _static_info_inner(symbol, lb_symbol))

async def _static_info_inner(symbol: str, lb_symbol: str) -> StaticInfo:
    items = await lb_client.static_info([lb_symbol])
    if not items:
        raise HTTPException(404, f"unknown symbol: {symbol}")
    s = items[0]
    return StaticInfo(
        symbol=symbol.upper(),
        name_cn=getattr(s, "name_cn", None),
        name_en=getattr(s, "name_en", None),
        name_hk=getattr(s, "name_hk", None),
        exchange=getattr(s, "exchange", None),
        currency=getattr(s, "currency", None),
        lot_size=getattr(s, "lot_size", None),
        total_shares=getattr(s, "total_shares", None),
        circulating_shares=getattr(s, "circulating_shares", None),
        eps=float(s.eps) if getattr(s, "eps", None) is not None else None,
        eps_ttm=float(s.eps_ttm) if getattr(s, "eps_ttm", None) is not None else None,
        bps=float(s.bps) if getattr(s, "bps", None) is not None else None,
        dividend_yield=float(s.dividend_yield) if getattr(s, "dividend_yield", None) is not None else None,
    )


# ----------------------- /depth/{symbol} -----------------------

@depth_router.get("/{symbol}", response_model=Depth)
async def depth(symbol: str, currency: str = "USD") -> Depth:
    if not lb_client.is_configured():
        raise HTTPException(503, "longbridge not configured")
    lb_symbol = to_lb_symbol(symbol, currency)
    raw = await lb_client.depth(lb_symbol)
    return Depth(
        symbol=symbol.upper(),
        bids=[DepthLevel(
            position=d.position,
            price=float(d.price) if d.price is not None else 0.0,
            volume=int(d.volume) if d.volume is not None else 0,
            order_num=int(d.order_num) if d.order_num is not None else 0,
        ) for d in (raw.bids or [])],
        asks=[DepthLevel(
            position=d.position,
            price=float(d.price) if d.price is not None else 0.0,
            volume=int(d.volume) if d.volume is not None else 0,
            order_num=int(d.order_num) if d.order_num is not None else 0,
        ) for d in (raw.asks or [])],
    )


# ----------------------- /intraday/{symbol} -----------------------

@intraday_router.get("/{symbol}", response_model=list[IntradayPoint])
async def intraday(symbol: str, currency: str = "USD") -> list[IntradayPoint]:
    if not lb_client.is_configured():
        raise HTTPException(503, "longbridge not configured")
    from ..cache import cached
    lb_symbol = to_lb_symbol(symbol, currency)
    key = ("intraday", lb_symbol)
    raw = await cached(key, 5.0, lambda: lb_client.intraday(lb_symbol))
    # Compute cumulative VWAP (orange average line) — running across all sessions.
    # Derive session from timestamp converted to US/Eastern (US-stock focused):
    #   04:00–09:30 → 1=Pre, 09:30–16:00 → 0=Normal, 16:00–20:00 → 2=Post, 20:00–04:00 → 3=Overnight.
    from zoneinfo import ZoneInfo
    et = ZoneInfo("America/New_York")

    cum_value = 0.0
    cum_volume = 0.0
    out: list[IntradayPoint] = []
    for p in raw:
        price = float(p.price)
        volume = float(getattr(p, "volume", 0) or 0)
        dt_utc = p.timestamp
        ts_unix = int(dt_utc.timestamp())
        turnover = float(getattr(p, "turnover", 0) or 0)
        value = turnover if turnover > 0 else price * volume
        cum_value += value
        cum_volume += volume
        avg = cum_value / cum_volume if cum_volume > 0 else None

        dt_et = dt_utc.astimezone(et)
        hm = dt_et.hour + dt_et.minute / 60.0
        if 4.0 <= hm < 9.5:
            line_type_val = 1   # Pre
        elif 9.5 <= hm < 16.0:
            line_type_val = 0   # Normal
        elif 16.0 <= hm < 20.0:
            line_type_val = 2   # Post
        else:
            line_type_val = 3   # Overnight

        out.append(IntradayPoint(
            time=ts_unix, price=price, avg_price=avg, volume=volume, line_type=line_type_val,
        ))
    return out


# ----------------------- /trades/{symbol} -----------------------

@trades_router.get("/{symbol}", response_model=list[TradeTick])
async def trades(symbol: str, count: int = 30, currency: str = "USD") -> list[TradeTick]:
    if not lb_client.is_configured():
        raise HTTPException(503, "longbridge not configured")
    lb_symbol = to_lb_symbol(symbol, currency)
    raw = await lb_client.trades(lb_symbol, count)
    out: list[TradeTick] = []
    for t in raw:
        direction = ""
        try:
            ts = getattr(t, "trade_session", None)
            ts_name = getattr(ts, "name", "") if ts is not None else ""
            tt = getattr(t, "trade_type", "") or ""
            direction = str(tt or ts_name)
        except Exception:
            pass
        out.append(TradeTick(
            time=int(t.timestamp.timestamp()),
            price=float(t.price),
            volume=float(getattr(t, "volume", 0) or 0),
            direction=direction,
        ))
    return out


