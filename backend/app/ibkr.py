import asyncio
import logging
from datetime import datetime

from ib_async import IB, ExecutionFilter

from . import db
from .config import settings

log = logging.getLogger(__name__)


def _persist_fill(trade, fill) -> None:
    """Event handler bound to `ib.execDetailsEvent` — writes each new fill into SQLite.
    IBKR fires this for fills that happen while we're connected; older fills come from
    the startup backfill via `reqExecutions`."""
    try:
        c = fill.contract
        e = fill.execution
        if not e or not e.execId:
            return
        raw = (e.side or "").upper()
        side = "BUY" if raw.startswith("B") else "SELL" if raw.startswith("S") else raw
        ts = int(e.time.timestamp()) if isinstance(e.time, datetime) else int(e.time)
        inserted = db.upsert_execution(
            exec_id=e.execId,
            time=ts,
            symbol=c.symbol,
            sec_type=c.secType,
            side=side,
            price=float(e.price),
            shares=float(e.shares),
            exchange=c.exchange or c.primaryExchange,
            account=e.acctNumber,
            con_id=c.conId,
        )
        if inserted:
            log.info("Persisted fill %s %s %.2f x %.2f", side, c.symbol, e.price, e.shares)
    except Exception:
        log.exception("failed to persist fill")


class IbkrClient:
    def __init__(self) -> None:
        self.ib = IB()
        self._lock = asyncio.Lock()

    async def connect(self) -> None:
        if self.ib.isConnected():
            return
        await self.ib.connectAsync(
            host=settings.ib_host,
            port=settings.ib_port,
            clientId=settings.ib_client_id,
            timeout=15,
        )
        log.info("Connected to IB Gateway at %s:%d", settings.ib_host, settings.ib_port)
        # Use delayed-frozen market data when no realtime subscription is active.
        # 1=live, 2=frozen, 3=delayed, 4=delayed-frozen.
        # Type 4 = delayed (15-min) + frozen fallback when market closed.
        self.ib.reqMarketDataType(4)
        # Subscribe to portfolio updates so ib.portfolio() returns market_price/market_value/pnl.
        # Fire as background task — the End event may never come for empty paper accounts and
        # we don't need to block startup waiting for the full download.
        for account in self.ib.managedAccounts():
            # `reqAccountUpdatesAsync` returns a Future that ib_async already schedules.
            # End event may never arrive for empty paper accounts — fire-and-forget and
            # don't block startup waiting for the full portfolio download.
            try:
                self.ib.reqAccountUpdatesAsync(account)
            except Exception:
                log.exception("reqAccountUpdatesAsync(%s) failed", account)
            # Subscribe to per-account daily PnL stream so ib.pnl() returns it.
            try:
                self.ib.reqPnL(account)
            except Exception as e:
                log.warning("reqPnL(%s) failed: %s", account, e)
        await asyncio.sleep(3)

        # Wire live-fill persistence + initial 7-day backfill.
        try:
            self.ib.execDetailsEvent += _persist_fill
            log.info("bound execDetailsEvent -> _persist_fill")
        except Exception:
            log.exception("failed to bind execDetailsEvent")
        # Await directly — small N (7 days of fills), shouldn't add much to startup.
        await self._backfill_executions()

    async def _backfill_executions(self) -> None:
        """One-shot: pull whatever IBKR still has (~7 days) and insert any new rows."""
        try:
            fills = await self.ib.reqExecutionsAsync(ExecutionFilter())
        except Exception as e:
            log.warning("execution backfill failed: %s", e)
            return
        new_count = 0
        for f in fills:
            try:
                c = f.contract
                e = f.execution
                if not e or not e.execId:
                    continue
                raw = (e.side or "").upper()
                side = "BUY" if raw.startswith("B") else "SELL" if raw.startswith("S") else raw
                ts = int(e.time.timestamp()) if isinstance(e.time, datetime) else int(e.time)
                if db.upsert_execution(
                    exec_id=e.execId,
                    time=ts,
                    symbol=c.symbol,
                    sec_type=c.secType,
                    side=side,
                    price=float(e.price),
                    shares=float(e.shares),
                    exchange=c.exchange or c.primaryExchange,
                    account=e.acctNumber,
                    con_id=c.conId,
                ):
                    new_count += 1
            except Exception:
                log.exception("backfill row failed")
        total = db.count()
        log.info("execution backfill: %d new, %d total rows", new_count, total)

    async def disconnect(self) -> None:
        if self.ib.isConnected():
            self.ib.disconnect()

    async def ensure_connected(self) -> IB:
        async with self._lock:
            if not self.ib.isConnected():
                await self.connect()
        return self.ib


client = IbkrClient()
