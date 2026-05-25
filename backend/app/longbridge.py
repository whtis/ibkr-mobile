"""Longbridge OpenAPI wrapper. Used for real-time quotes and historical K-lines.
Trading and account data still flow through IBKR (see ibkr.py)."""

import asyncio
import logging

from longport.openapi import AdjustType, Config, Period, QuoteContext

from .config import settings

log = logging.getLogger(__name__)

# Map our period strings to longport Period enums
PERIOD_MAP: dict[str, Period] = {
    "1min": Period.Min_1,
    "2min": Period.Min_2,
    "3min": Period.Min_3,
    "5min": Period.Min_5,
    "15min": Period.Min_15,
    "30min": Period.Min_30,
    "1h": Period.Min_60,
    "60min": Period.Min_60,
    "1d": Period.Day,
    "1w": Period.Week,
    "1mo": Period.Month,
    "1y": Period.Year,
}

# Custom virtual periods that map to (longport Period, count override)
VIRTUAL_PERIODS: dict[str, tuple[Period, int]] = {
    # 5日: 5 minute bars covering 5 trading days (~78 bars/day for US 6.5h × 12)
    "5d": (Period.Min_5, 400),
}

# Map IBKR currency to Longbridge symbol suffix
_CURRENCY_SUFFIX = {
    "USD": "US",
    "HKD": "HK",
    "SGD": "SG",
    "CNH": "SH",  # default to Shanghai; SZ via explicit override
    "CNY": "SH",
}


def to_lb_symbol(symbol: str, currency: str = "USD") -> str:
    """`(TSLA, USD)` -> `TSLA.US`. `(0700, HKD)` -> `0700.HK`."""
    suffix = _CURRENCY_SUFFIX.get(currency.upper(), currency.upper())
    return f"{symbol.upper()}.{suffix}"


class LongbridgeClient:
    def __init__(self) -> None:
        self._ctx: QuoteContext | None = None
        self._lock = asyncio.Lock()
        # Realtime push state
        self._main_loop: asyncio.AbstractEventLoop | None = None
        self._push_subscribers: list = []  # list[Callable[dict] -> Awaitable]
        self._sub_refs: dict[str, int] = {}
        self._push_attached = False

    def is_configured(self) -> bool:
        return bool(
            settings.longport_app_key
            and settings.longport_app_secret
            and settings.longport_access_token
        )

    async def ctx(self) -> QuoteContext:
        if self._ctx is not None:
            return self._ctx
        if not self.is_configured():
            raise RuntimeError("Longbridge not configured (set LONGPORT_* env vars)")
        async with self._lock:
            if self._ctx is None:
                cfg = Config(
                    app_key=settings.longport_app_key,
                    app_secret=settings.longport_app_secret,
                    access_token=settings.longport_access_token,
                )
                # QuoteContext is sync and may do I/O on construction; offload to thread.
                self._ctx = await asyncio.to_thread(QuoteContext, cfg)
                log.info("Longbridge QuoteContext connected.")
        return self._ctx

    async def quote(self, symbols: list[str]) -> list:
        ctx = await self.ctx()
        return await asyncio.to_thread(ctx.quote, symbols)

    async def candlesticks(
        self,
        symbol: str,
        period: str,
        count: int = 365,
    ) -> list:
        # Virtual periods (e.g. 5日) override the count.
        if period in VIRTUAL_PERIODS:
            lb_period, virtual_count = VIRTUAL_PERIODS[period]
            ctx = await self.ctx()
            return await asyncio.to_thread(
                ctx.candlesticks, symbol, lb_period, virtual_count, AdjustType.NoAdjust,
            )
        lb_period = PERIOD_MAP.get(period)
        if not lb_period:
            raise ValueError(f"unsupported period: {period}; valid: {list(PERIOD_MAP) + list(VIRTUAL_PERIODS)}")
        ctx = await self.ctx()
        return await asyncio.to_thread(
            ctx.candlesticks, symbol, lb_period, count, AdjustType.NoAdjust,
        )

    async def static_info(self, symbols: list[str]) -> list:
        ctx = await self.ctx()
        return await asyncio.to_thread(ctx.static_info, symbols)

    async def depth(self, symbol: str):
        ctx = await self.ctx()
        return await asyncio.to_thread(ctx.depth, symbol)

    async def intraday(self, symbol: str) -> list:
        from longport.openapi import TradeSessions
        ctx = await self.ctx()
        return await asyncio.to_thread(ctx.intraday, symbol, TradeSessions.All)

    async def trades(self, symbol: str, count: int = 30) -> list:
        ctx = await self.ctx()
        return await asyncio.to_thread(ctx.trades, symbol, count)

    async def option_expiry_dates(self, symbol: str) -> list:
        ctx = await self.ctx()
        return await asyncio.to_thread(ctx.option_chain_expiry_date_list, symbol)

    async def option_chain_info(self, symbol: str, expiry):
        ctx = await self.ctx()
        return await asyncio.to_thread(ctx.option_chain_info_by_date, symbol, expiry)

    async def option_quote(self, symbols: list[str]) -> list:
        ctx = await self.ctx()
        return await asyncio.to_thread(ctx.option_quote, symbols)

    # ---------- Realtime push ----------

    def set_main_loop(self, loop: asyncio.AbstractEventLoop) -> None:
        self._main_loop = loop

    def add_push_subscriber(self, fn) -> None:
        self._push_subscribers.append(fn)

    def remove_push_subscriber(self, fn) -> None:
        if fn in self._push_subscribers:
            self._push_subscribers.remove(fn)

    def _on_quote_push(self, symbol: str, event) -> None:
        """Runs in longport's worker thread. Bridge to asyncio loop."""
        if not self._main_loop:
            return
        try:
            snap = {
                "symbol": symbol,
                "last": float(event.last_done) if getattr(event, "last_done", None) is not None else None,
                "open": float(event.open) if getattr(event, "open", None) is not None else None,
                "high": float(event.high) if getattr(event, "high", None) is not None else None,
                "low": float(event.low) if getattr(event, "low", None) is not None else None,
                "volume": int(event.volume) if getattr(event, "volume", None) is not None else 0,
                "turnover": float(event.turnover) if getattr(event, "turnover", None) is not None else 0.0,
                "timestamp": event.timestamp.timestamp() if getattr(event, "timestamp", None) else None,
                "trade_status": str(event.trade_status) if getattr(event, "trade_status", None) else None,
            }
        except Exception:
            log.exception("push event encode failed")
            return
        for sub in list(self._push_subscribers):
            try:
                asyncio.run_coroutine_threadsafe(sub(snap), self._main_loop)
            except Exception:
                log.exception("push subscriber dispatch failed")

    async def _ensure_push_attached(self) -> None:
        if self._push_attached:
            return
        ctx = await self.ctx()
        await asyncio.to_thread(ctx.set_on_quote, self._on_quote_push)
        self._push_attached = True

    async def subscribe_quote(self, symbols: list[str]) -> list[str]:
        """Ref-counted subscription. Returns newly-subscribed symbols."""
        from longport.openapi import SubType
        if not symbols:
            return []
        await self._ensure_push_attached()
        new_subs: list[str] = []
        for s in symbols:
            cnt = self._sub_refs.get(s, 0)
            self._sub_refs[s] = cnt + 1
            if cnt == 0:
                new_subs.append(s)
        if new_subs:
            ctx = await self.ctx()
            await asyncio.to_thread(ctx.subscribe, new_subs, [SubType.Quote])
            log.info("LB subscribed: %s", new_subs)
        return new_subs

    async def unsubscribe_quote(self, symbols: list[str]) -> list[str]:
        from longport.openapi import SubType
        if not symbols:
            return []
        to_unsub: list[str] = []
        for s in symbols:
            cnt = self._sub_refs.get(s, 0)
            if cnt > 0:
                self._sub_refs[s] = cnt - 1
                if cnt - 1 == 0:
                    to_unsub.append(s)
                    self._sub_refs.pop(s, None)
        if to_unsub:
            ctx = await self.ctx()
            await asyncio.to_thread(ctx.unsubscribe, to_unsub, [SubType.Quote])
            log.info("LB unsubscribed: %s", to_unsub)
        return to_unsub


lb_client = LongbridgeClient()
