"""SQLite persistence for fills/executions. IBKR's `reqExecutions` only returns the last
~7 days of fills, so we mirror them into a local DB on every backend run and live-stream
new fills via `execDetailsEvent`. The chart can then show buy/sell markers months/years
after the trade happened, as long as the backend was running when it was filled.

Schema is tiny (one table). Single-user backend → no need for migrations framework."""

import logging
import sqlite3
import threading
from contextlib import contextmanager
from pathlib import Path

log = logging.getLogger(__name__)

# DB lives next to the app code; works for both the production deployment under
# /home/ubuntu/ibkr-backend and any local checkout.
DB_PATH = Path(__file__).resolve().parent.parent / "data" / "executions.db"
_lock = threading.Lock()


@contextmanager
def conn():
    """Single shared connection guarded by a lock. WAL keeps reads concurrent with writes."""
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    with _lock:
        c = sqlite3.connect(DB_PATH, timeout=10)
        try:
            c.row_factory = sqlite3.Row
            c.execute("PRAGMA journal_mode=WAL")
            c.execute("PRAGMA synchronous=NORMAL")
            yield c
            c.commit()
        finally:
            c.close()


def init() -> None:
    """Create the executions table on first run."""
    with conn() as c:
        c.executescript(
            """
            CREATE TABLE IF NOT EXISTS executions (
                exec_id   TEXT PRIMARY KEY,
                time      INTEGER NOT NULL,
                symbol    TEXT NOT NULL,
                sec_type  TEXT NOT NULL,
                side      TEXT NOT NULL,
                price     REAL NOT NULL,
                shares    REAL NOT NULL,
                exchange  TEXT,
                account   TEXT,
                con_id    INTEGER,
                inserted_at INTEGER DEFAULT (strftime('%s','now'))
            );
            CREATE INDEX IF NOT EXISTS idx_executions_symbol_time
                ON executions (symbol, time);
            """
        )
    log.info("execution DB initialized at %s", DB_PATH)


def upsert_execution(
    *,
    exec_id: str,
    time: int,
    symbol: str,
    sec_type: str,
    side: str,
    price: float,
    shares: float,
    exchange: str | None = None,
    account: str | None = None,
    con_id: int | None = None,
) -> bool:
    """Insert if new, ignore if `exec_id` already present. Returns True iff inserted."""
    with conn() as c:
        cur = c.execute(
            """
            INSERT OR IGNORE INTO executions
                (exec_id, time, symbol, sec_type, side, price, shares, exchange, account, con_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (exec_id, time, symbol.upper(), sec_type, side, price, shares, exchange, account, con_id),
        )
        return cur.rowcount > 0


def list_for_symbol(symbol: str, limit: int = 500) -> list[dict]:
    with conn() as c:
        rows = c.execute(
            "SELECT * FROM executions WHERE symbol = ? ORDER BY time ASC LIMIT ?",
            (symbol.upper(), limit),
        ).fetchall()
        return [dict(r) for r in rows]


def count() -> int:
    with conn() as c:
        return c.execute("SELECT COUNT(*) FROM executions").fetchone()[0]
