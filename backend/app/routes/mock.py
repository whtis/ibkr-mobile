"""Mock router — serves seeded fake data so contributors can run the whole stack
without any IBKR or Longbridge credentials.

Activated by `MOCK_MODE=yes` in the backend `.env`. When active, `main.py` registers
this router instead of the real account / quote / orders / etc. routers. The
shape of every response mirrors the real models exactly.

Determinism: charts are seeded by symbol so AAPL always looks like AAPL, but the
realtime WebSocket pushes a small random jitter to keep the UI visibly alive.
"""

from __future__ import annotations

import asyncio
import math
import random
import time
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, Query, WebSocket, WebSocketDisconnect
from pydantic import BaseModel

from ..auth import require_token
from ..config import settings
from ..models import (
    AccountSummary,
    Bar,
    Depth,
    DepthLevel,
    ExtendedQuote,
    IntradayPoint,
    OptionContract,
    OptionExpiry,
    Position,
    Quote,
    StaticInfo,
    TradeTick,
)

# ---------------------------------------------------------------------------
# Seeded universe — anchors for prices, names, sectors.
# ---------------------------------------------------------------------------

_UNIVERSE: dict[str, dict] = {
    "AAPL":  {"name_cn": "苹果",           "name_en": "Apple Inc.",         "anchor": 232.50, "iv": 0.28, "exchange": "NASDAQ"},
    "TSLA":  {"name_cn": "特斯拉",         "name_en": "Tesla Inc.",         "anchor": 348.60, "iv": 0.55, "exchange": "NASDAQ"},
    "NVDA":  {"name_cn": "英伟达",         "name_en": "NVIDIA Corp.",       "anchor": 142.30, "iv": 0.42, "exchange": "NASDAQ"},
    "MSFT":  {"name_cn": "微软",           "name_en": "Microsoft Corp.",    "anchor": 418.20, "iv": 0.22, "exchange": "NASDAQ"},
    "GOOGL": {"name_cn": "谷歌-A",         "name_en": "Alphabet Inc.",      "anchor": 178.40, "iv": 0.25, "exchange": "NASDAQ"},
    "AMZN":  {"name_cn": "亚马逊",         "name_en": "Amazon.com Inc.",    "anchor": 215.80, "iv": 0.27, "exchange": "NASDAQ"},
    "META":  {"name_cn": "Meta",           "name_en": "Meta Platforms",     "anchor": 608.10, "iv": 0.31, "exchange": "NASDAQ"},
    "BABA":  {"name_cn": "阿里巴巴",       "name_en": "Alibaba Group",      "anchor":  94.20, "iv": 0.36, "exchange": "NYSE"},
    "PDD":   {"name_cn": "拼多多",         "name_en": "PDD Holdings",       "anchor": 110.45, "iv": 0.48, "exchange": "NASDAQ"},
    "SPY":   {"name_cn": "标普500 ETF",    "name_en": "SPDR S&P 500 ETF",   "anchor": 583.20, "iv": 0.14, "exchange": "ARCA"},
}

_DEFAULT_ANCHOR = 100.0


def _anchor(symbol: str) -> float:
    sym = symbol.upper()
    if sym in _UNIVERSE:
        return _UNIVERSE[sym]["anchor"]
    # Deterministic fallback so unknown symbols still render a plausible chart.
    rnd = random.Random(f"anchor-{sym}")
    return round(rnd.uniform(20.0, 400.0), 2)


def _info(symbol: str) -> dict:
    return _UNIVERSE.get(symbol.upper(), {
        "name_cn": symbol.upper(),
        "name_en": symbol.upper(),
        "anchor": _anchor(symbol),
        "iv": 0.30,
        "exchange": "NASDAQ",
    })


def _now_utc() -> datetime:
    return datetime.now(timezone.utc)


# ---------------------------------------------------------------------------
# /account
# ---------------------------------------------------------------------------

mock_account_router = APIRouter(prefix="/account", dependencies=[Depends(require_token)])


_FAKE_ACCOUNT = "DU1234567"


def _seeded_positions() -> list[Position]:
    rnd = random.Random("positions-v1")
    plan = [
        ("AAPL",  100,  198.40),
        ("TSLA",   50,  402.10),
        ("NVDA",  200,  118.55),
        ("MSFT",   25,  395.10),
        ("GOOGL",  60,  165.20),
        ("BABA",  120,  102.30),
        ("SPY",    30,  548.70),
    ]
    out: list[Position] = []
    for sym, qty, avg_cost in plan:
        last = _UNIVERSE[sym]["anchor"] * (1.0 + rnd.uniform(-0.02, 0.02))
        market_value = qty * last
        cost_basis = qty * avg_cost
        unrealized = market_value - cost_basis
        daily = market_value * rnd.uniform(-0.015, 0.020)
        out.append(Position(
            account=_FAKE_ACCOUNT,
            symbol=sym,
            sec_type="STK",
            exchange=_UNIVERSE[sym]["exchange"],
            currency="USD",
            position=float(qty),
            avg_cost=float(avg_cost),
            market_price=round(last, 2),
            market_value=round(market_value, 2),
            unrealized_pnl=round(unrealized, 2),
            realized_pnl=0.0,
            daily_pnl=round(daily, 2),
        ))
    return out


@mock_account_router.get("/summary", response_model=list[AccountSummary])
async def mock_account_summary() -> list[AccountSummary]:
    positions = _seeded_positions()
    market_value = sum(p.market_value or 0.0 for p in positions)
    unrealized = sum(p.unrealized_pnl or 0.0 for p in positions)
    daily = sum(p.daily_pnl or 0.0 for p in positions)
    cash = 18_420.55
    return [
        AccountSummary(
            account_id=_FAKE_ACCOUNT,
            currency="USD",
            net_liquidation=round(market_value + cash, 2),
            total_cash=cash,
            buying_power=round(cash * 4, 2),
            realized_pnl=0.0,
            unrealized_pnl=round(unrealized, 2),
            daily_pnl=round(daily, 2),
        )
    ]


@mock_account_router.get("/positions", response_model=list[Position])
async def mock_positions() -> list[Position]:
    return _seeded_positions()


# ---------------------------------------------------------------------------
# /quote, /quotes, /static, /depth, /intraday, /trades, /bars
# ---------------------------------------------------------------------------

mock_quote_router = APIRouter(prefix="/quote", dependencies=[Depends(require_token)])
mock_bars_router = APIRouter(prefix="/bars", dependencies=[Depends(require_token)])
mock_quotes_router = APIRouter(prefix="/quotes", dependencies=[Depends(require_token)])
mock_static_router = APIRouter(prefix="/static", dependencies=[Depends(require_token)])
mock_depth_router = APIRouter(prefix="/depth", dependencies=[Depends(require_token)])
mock_intraday_router = APIRouter(prefix="/intraday", dependencies=[Depends(require_token)])
mock_trades_router = APIRouter(prefix="/trades", dependencies=[Depends(require_token)])


def _build_quote(symbol: str) -> Quote:
    info = _info(symbol)
    anchor = info["anchor"]
    rnd = random.Random(f"quote-{symbol.upper()}-{int(time.time() // 5)}")
    last = round(anchor * (1.0 + rnd.uniform(-0.03, 0.03)), 2)
    prev_close = round(anchor * (1.0 + rnd.uniform(-0.02, 0.02)), 2)
    high = round(max(last, prev_close) * (1.0 + abs(rnd.uniform(0.0, 0.025))), 2)
    low = round(min(last, prev_close) * (1.0 - abs(rnd.uniform(0.0, 0.025))), 2)
    open_p = round(prev_close * (1.0 + rnd.uniform(-0.01, 0.01)), 2)
    change = round(last - prev_close, 2)
    change_pct = round(change / prev_close * 100.0, 3) if prev_close else None
    volume = round(rnd.uniform(2_000_000, 80_000_000), 0)
    turnover = round(volume * last, 0)
    ext_pre = ExtendedQuote(
        last=round(prev_close * (1.0 + rnd.uniform(-0.005, 0.005)), 2),
        prev_close=prev_close,
        change=round(rnd.uniform(-1.0, 1.0), 2),
        change_pct=round(rnd.uniform(-0.6, 0.6), 3),
        volume=round(rnd.uniform(20_000, 800_000), 0),
        timestamp=_now_utc().isoformat(),
    )
    ext_post = ExtendedQuote(
        last=round(last * (1.0 + rnd.uniform(-0.005, 0.005)), 2),
        prev_close=last,
        change=round(rnd.uniform(-1.0, 1.0), 2),
        change_pct=round(rnd.uniform(-0.6, 0.6), 3),
        volume=round(rnd.uniform(10_000, 400_000), 0),
        timestamp=_now_utc().isoformat(),
    )
    return Quote(
        symbol=symbol.upper(),
        last=last,
        bid=round(last - 0.02, 2),
        ask=round(last + 0.02, 2),
        open=open_p,
        high=high,
        low=low,
        close=prev_close,
        prev_close=prev_close,
        volume=volume,
        turnover=turnover,
        change=change,
        change_pct=change_pct,
        pre_market=ext_pre,
        post_market=ext_post,
        timestamp=_now_utc().isoformat(),
    )


@mock_quote_router.get("/{symbol}", response_model=Quote)
async def mock_quote(symbol: str, currency: str = "USD") -> Quote:
    return _build_quote(symbol)


@mock_quotes_router.get("", response_model=list[Quote])
async def mock_quotes_batch(symbols: str = Query(...), currency: str = "USD") -> list[Quote]:
    syms = [s.strip().upper() for s in symbols.split(",") if s.strip()]
    return [_build_quote(s) for s in syms]


@mock_static_router.get("/{symbol}", response_model=StaticInfo)
async def mock_static(symbol: str, currency: str = "USD") -> StaticInfo:
    info = _info(symbol)
    rnd = random.Random(f"static-{symbol.upper()}")
    return StaticInfo(
        symbol=symbol.upper(),
        name_cn=info["name_cn"],
        name_en=info["name_en"],
        name_hk=None,
        exchange=info["exchange"],
        currency="USD",
        lot_size=1,
        total_shares=int(rnd.uniform(1.0e9, 18.0e9)),
        circulating_shares=int(rnd.uniform(0.8e9, 16.0e9)),
        eps=round(rnd.uniform(0.5, 12.0), 2),
        eps_ttm=round(rnd.uniform(0.5, 12.0), 2),
        bps=round(rnd.uniform(5.0, 80.0), 2),
        dividend_yield=round(rnd.uniform(0.0, 0.03), 4),
    )


@mock_depth_router.get("/{symbol}", response_model=Depth)
async def mock_depth(symbol: str, currency: str = "USD") -> Depth:
    last = _build_quote(symbol).last or _anchor(symbol)
    bids = []
    asks = []
    for i in range(1, 6):
        bids.append(DepthLevel(position=i, price=round(last - i * 0.05, 2), volume=200 * i, order_num=i))
        asks.append(DepthLevel(position=i, price=round(last + i * 0.05, 2), volume=200 * i, order_num=i))
    return Depth(symbol=symbol.upper(), bids=bids, asks=asks)


# ----- bars (random walk seeded by symbol+period) -----

_PERIOD_SECONDS = {
    "1min": 60,
    "2min": 120,
    "3min": 180,
    "5min": 300,
    "15min": 900,
    "30min": 1800,
    "60min": 3600,
    "1h": 3600,
    "1d": 86_400,
    "1w": 604_800,
    "1mo": 2_592_000,
}


def _generate_bars(symbol: str, period: str, count: int = 200) -> list[Bar]:
    step = _PERIOD_SECONDS.get(period, 86_400)
    rnd = random.Random(f"bars-{symbol.upper()}-{period}")
    price = _anchor(symbol) * 0.85  # start a bit below today so we see growth
    vol_anchor = rnd.uniform(50_000, 5_000_000)
    drift = rnd.uniform(-0.0008, 0.0015)
    vol_sigma = 0.012 if step < 3600 else 0.022
    bars: list[Bar] = []
    end_ts = int(time.time()) // step * step
    for i in range(count, 0, -1):
        ts = end_ts - i * step
        shock = rnd.gauss(drift, vol_sigma)
        open_p = price
        close_p = max(0.1, price * (1.0 + shock))
        high = max(open_p, close_p) * (1.0 + abs(rnd.gauss(0, vol_sigma * 0.5)))
        low = min(open_p, close_p) * (1.0 - abs(rnd.gauss(0, vol_sigma * 0.5)))
        volume = max(1.0, vol_anchor * (1.0 + abs(rnd.gauss(0, 0.5))))
        bars.append(Bar(
            time=ts,
            open=round(open_p, 2),
            high=round(high, 2),
            low=round(low, 2),
            close=round(close_p, 2),
            volume=round(volume, 0),
        ))
        price = close_p
    return bars


@mock_bars_router.get("/{symbol}", response_model=list[Bar])
async def mock_bars(symbol: str, period: str = "1d", currency: str = "USD") -> list[Bar]:
    return _generate_bars(symbol, period, count=200)


# ----- intraday with 4-channel session classification -----

@mock_intraday_router.get("/{symbol}", response_model=list[IntradayPoint])
async def mock_intraday(symbol: str, currency: str = "USD") -> list[IntradayPoint]:
    """Generate a believable 1-min intraday tape for the current US trading day,
    spanning all four sessions (pre / regular / post / overnight)."""
    from zoneinfo import ZoneInfo

    et = ZoneInfo("America/New_York")
    now_et = _now_utc().astimezone(et)
    # Anchor "today" in ET regardless of UTC clock.
    day_start = now_et.replace(hour=4, minute=0, second=0, microsecond=0)

    info = _info(symbol)
    anchor = info["anchor"]
    rnd = random.Random(f"intraday-{symbol.upper()}-{now_et.date()}")

    price = anchor * (1.0 + rnd.uniform(-0.01, 0.01))
    cum_value = 0.0
    cum_volume = 0.0
    out: list[IntradayPoint] = []
    # 16 hours × 60 mins = 960 minutes from 04:00 ET to 20:00 ET
    for i in range(960):
        ts_et = day_start + timedelta(minutes=i)
        hm = ts_et.hour + ts_et.minute / 60.0
        if 4.0 <= hm < 9.5:
            line_type_val = 1   # Pre-market
            sigma = 0.0008
            vol_base = rnd.uniform(200, 1500)
        elif 9.5 <= hm < 16.0:
            line_type_val = 0   # Regular
            sigma = 0.0014
            vol_base = rnd.uniform(8000, 60000)
        elif 16.0 <= hm < 20.0:
            line_type_val = 2   # Post-market
            sigma = 0.0010
            vol_base = rnd.uniform(300, 2500)
        else:
            line_type_val = 3
            sigma = 0.0005
            vol_base = rnd.uniform(50, 500)

        price = max(0.1, price * (1.0 + rnd.gauss(0, sigma)))
        volume = max(1.0, vol_base * (1.0 + abs(rnd.gauss(0, 0.4))))
        turnover = price * volume
        cum_value += turnover
        cum_volume += volume
        avg = cum_value / cum_volume if cum_volume > 0 else None
        ts_unix = int(ts_et.astimezone(timezone.utc).timestamp())
        out.append(IntradayPoint(
            time=ts_unix,
            price=round(price, 2),
            avg_price=round(avg, 2) if avg is not None else None,
            volume=round(volume, 0),
            line_type=line_type_val,
        ))
        # Stop generating points in the future relative to ET wall-clock.
        if ts_et > now_et:
            break
    return out


@mock_trades_router.get("/{symbol}", response_model=list[TradeTick])
async def mock_trades(symbol: str, count: int = 30, currency: str = "USD") -> list[TradeTick]:
    rnd = random.Random(f"trades-{symbol.upper()}-{int(time.time() // 30)}")
    last = _build_quote(symbol).last or _anchor(symbol)
    now = int(time.time())
    out: list[TradeTick] = []
    for i in range(count):
        side_roll = rnd.random()
        direction = "Buy" if side_roll < 0.45 else ("Sell" if side_roll < 0.9 else "Neutral")
        out.append(TradeTick(
            time=now - i * 2,
            price=round(last * (1.0 + rnd.uniform(-0.0005, 0.0005)), 2),
            volume=round(rnd.uniform(50, 5000), 0),
            direction=direction,
        ))
    return out


# ---------------------------------------------------------------------------
# /options
# ---------------------------------------------------------------------------

mock_expiry_router = APIRouter(prefix="/options/expiries", dependencies=[Depends(require_token)])
mock_chain_router = APIRouter(prefix="/options/chain", dependencies=[Depends(require_token)])


def _next_fridays(n: int = 4) -> list[str]:
    today = datetime.now(timezone.utc).date()
    days_ahead = (4 - today.weekday()) % 7
    if days_ahead == 0:
        days_ahead = 7
    first = today + timedelta(days=days_ahead)
    return [(first + timedelta(weeks=i)).isoformat() for i in range(n)]


@mock_expiry_router.get("/{symbol}", response_model=list[OptionExpiry])
async def mock_option_expiries(symbol: str, currency: str = "USD") -> list[OptionExpiry]:
    return [OptionExpiry(expiry=d) for d in _next_fridays(4)]


@mock_chain_router.get("/{symbol}", response_model=list[OptionContract])
async def mock_option_chain(
    symbol: str,
    expiry: str = Query(...),
    currency: str = "USD",
) -> list[OptionContract]:
    info = _info(symbol)
    spot = info["anchor"]
    iv = info["iv"]
    try:
        exp_date = datetime.strptime(expiry, "%Y-%m-%d").date()
    except ValueError as e:
        raise HTTPException(400, f"bad expiry: {expiry}") from e
    days_to_exp = max(1, (exp_date - datetime.now(timezone.utc).date()).days)
    t_years = days_to_exp / 365.0

    strike_step = max(1.0, round(spot * 0.02, 0))
    strikes = [round(spot + (i - 10) * strike_step, 2) for i in range(21)]

    out: list[OptionContract] = []
    for k in strikes:
        for right in ("C", "P"):
            # Crude approximations: intrinsic + time value, IV bumped for far OTM
            moneyness = (k - spot) / spot
            otm_iv = iv * (1.0 + abs(moneyness) * 0.4)
            time_value = spot * otm_iv * math.sqrt(t_years) * 0.4
            if right == "C":
                intrinsic = max(0.0, spot - k)
                delta = 0.5 + moneyness * -2 / (1 + abs(moneyness * 10))
                delta = max(0.02, min(0.98, delta))
            else:
                intrinsic = max(0.0, k - spot)
                delta = -0.5 + moneyness * 2 / (1 + abs(moneyness * 10))
                delta = max(-0.98, min(-0.02, delta))
            last = max(0.05, round(intrinsic + time_value, 2))
            prev = max(0.05, round(last * (1.0 + random.Random(f"opt-{symbol}-{k}-{right}").uniform(-0.05, 0.05)), 2))
            change_pct = (last - prev) / prev * 100.0 if prev else None
            out.append(OptionContract(
                symbol=f"MOCK-{symbol.upper()}-{exp_date.strftime('%y%m%d')}{right}{int(k*1000):08d}",
                underlying=symbol.upper(),
                expiry=exp_date.isoformat(),
                strike=k,
                right=right,
                last=last,
                change_pct=round(change_pct, 3) if change_pct is not None else None,
                iv=round(otm_iv, 4),
                delta=round(delta, 4),
                gamma=round(0.05 / (1 + abs(moneyness) * 20), 4),
                theta=round(-time_value / max(1, days_to_exp), 4),
                vega=round(spot * math.sqrt(t_years) * 0.01, 4),
                open_interest=int(2000 * math.exp(-abs(moneyness) * 5)),
                volume=int(800 * math.exp(-abs(moneyness) * 6)),
            ))
    out.sort(key=lambda c: (c.strike, c.right))
    return out


# ---------------------------------------------------------------------------
# /orders — in-memory store
# ---------------------------------------------------------------------------

mock_orders_router = APIRouter(prefix="/orders", dependencies=[Depends(require_token)])

_next_order_id = 1000
_active_orders: list[dict] = []


class _MockOrderRequest(BaseModel):
    symbol: str
    exchange: str = "SMART"
    currency: str = "USD"
    sec_type: str = "STK"
    expiry: str | None = None
    strike: float | None = None
    right: str | None = None
    side: str
    order_type: str
    quantity: float
    price: float | None = None
    tif: str = "DAY"
    outside_rth: bool = False


class _MockOrderResponse(BaseModel):
    order_id: int
    perm_id: int | None = None
    status: str
    symbol: str
    side: str
    quantity: float
    filled: float = 0.0
    avg_fill_price: float | None = None
    type: str
    price: float | None = None
    tif: str = "DAY"
    outside_rth: bool = False


@mock_orders_router.post("", response_model=_MockOrderResponse)
async def mock_place_order(req: _MockOrderRequest) -> _MockOrderResponse:
    global _next_order_id
    oid = _next_order_id
    _next_order_id += 1
    if req.order_type == "LMT" and req.price is None:
        raise HTTPException(400, "price required for LMT order")
    # Market orders fill immediately at the synthesized last; limit orders sit pending.
    if req.order_type == "MKT":
        fill_price = _build_quote(req.symbol).last or _anchor(req.symbol)
        return _MockOrderResponse(
            order_id=oid,
            perm_id=oid * 10,
            status="Filled",
            symbol=req.symbol.upper(),
            side=req.side,
            quantity=req.quantity,
            filled=req.quantity,
            avg_fill_price=round(fill_price, 2),
            type="MKT",
            price=None,
            tif=req.tif,
            outside_rth=req.outside_rth,
        )
    pending = _MockOrderResponse(
        order_id=oid,
        perm_id=oid * 10,
        status="Submitted",
        symbol=req.symbol.upper(),
        side=req.side,
        quantity=req.quantity,
        filled=0.0,
        type="LMT",
        price=req.price,
        tif=req.tif,
        outside_rth=req.outside_rth,
    )
    _active_orders.append(pending.model_dump())
    return pending


@mock_orders_router.get("/active", response_model=list[_MockOrderResponse])
async def mock_active_orders() -> list[_MockOrderResponse]:
    return [_MockOrderResponse(**o) for o in _active_orders]


@mock_orders_router.delete("/{order_id}")
async def mock_cancel_order(order_id: int) -> dict:
    for i, o in enumerate(_active_orders):
        if o["order_id"] == order_id:
            _active_orders.pop(i)
            return {"ok": True, "status": "Cancelled"}
    raise HTTPException(404, f"order not found: {order_id}")


# ---------------------------------------------------------------------------
# /executions — synthesized history
# ---------------------------------------------------------------------------

mock_executions_router = APIRouter(prefix="/executions", dependencies=[Depends(require_token)])


class _MockExecutionTick(BaseModel):
    time: int
    symbol: str
    sec_type: str
    side: str
    price: float
    shares: float
    exchange: str | None = None


def _seeded_executions(symbol: str | None = None, limit: int = 500) -> list[_MockExecutionTick]:
    rnd = random.Random(f"executions-{(symbol or 'all').upper()}-v1")
    out: list[_MockExecutionTick] = []
    syms = [symbol.upper()] if symbol else ["AAPL", "TSLA", "NVDA", "MSFT", "GOOGL"]
    now = int(time.time())
    for i in range(min(limit, 80)):
        sym = rnd.choice(syms)
        anchor = _UNIVERSE.get(sym, {}).get("anchor", 100.0)
        out.append(_MockExecutionTick(
            time=now - (i + 1) * rnd.randint(900, 14_400),
            symbol=sym,
            sec_type="STK",
            side=rnd.choice(["BUY", "SELL"]),
            price=round(anchor * (1.0 + rnd.uniform(-0.05, 0.05)), 2),
            shares=float(rnd.choice([10, 25, 50, 100, 200])),
            exchange=_UNIVERSE.get(sym, {}).get("exchange", "NASDAQ"),
        ))
    return out


@mock_executions_router.get("/{symbol}", response_model=list[_MockExecutionTick])
async def mock_executions_for(symbol: str, limit: int = 500) -> list[_MockExecutionTick]:
    return _seeded_executions(symbol, limit)


@mock_executions_router.get("", response_model=dict)
async def mock_executions_status() -> dict:
    return {"total": 80, "mock_mode": True}


# ---------------------------------------------------------------------------
# /ws/quotes — jittered live stream
# ---------------------------------------------------------------------------

mock_ws_router = APIRouter()


@mock_ws_router.websocket("/ws/quotes")
async def mock_ws_quotes(ws: WebSocket, token: str = Query(...)) -> None:
    if token != settings.api_token:
        await ws.close(code=4401, reason="invalid token")
        return
    await ws.accept()
    subscribed: set[str] = set()

    async def emitter() -> None:
        while True:
            await asyncio.sleep(1.0)
            for sym in list(subscribed):
                anchor = _anchor(sym)
                rnd_local = random.Random(f"{sym}-{int(time.time())}")
                last = round(anchor * (1.0 + rnd_local.uniform(-0.005, 0.005)), 2)
                try:
                    await ws.send_json({
                        "symbol": sym,
                        "last": last,
                        "open": round(anchor * 0.995, 2),
                        "high": round(anchor * 1.012, 2),
                        "low": round(anchor * 0.988, 2),
                        "volume": rnd_local.randint(50_000, 5_000_000),
                        "turnover": last * rnd_local.randint(50_000, 5_000_000),
                        "timestamp": time.time(),
                    })
                except Exception:
                    return

    emitter_task = asyncio.create_task(emitter())
    try:
        while True:
            data = await ws.receive_json()
            action = data.get("action")
            syms = [s.upper() for s in (data.get("symbols") or [])]
            if action == "ping":
                await ws.send_json({"action": "pong"})
            elif action == "subscribe":
                subscribed.update(syms)
            elif action == "unsubscribe":
                for s in syms:
                    subscribed.discard(s)
    except WebSocketDisconnect:
        pass
    except Exception:
        pass
    finally:
        emitter_task.cancel()


# ---------------------------------------------------------------------------
# Bundled list of mock routers — main.py imports this.
# ---------------------------------------------------------------------------

ALL_MOCK_ROUTERS = (
    mock_account_router,
    mock_quote_router,
    mock_bars_router,
    mock_quotes_router,
    mock_static_router,
    mock_depth_router,
    mock_intraday_router,
    mock_trades_router,
    mock_expiry_router,
    mock_chain_router,
    mock_orders_router,
    mock_executions_router,
    mock_ws_router,
)
