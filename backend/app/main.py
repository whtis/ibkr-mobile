import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from . import db
from .ibkr import client
from .routes import account, executions, health, options, orders, quote, ws_quotes

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)
log = logging.getLogger("ibkr-backend")


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Persistent execution DB — must exist before client.connect() because connect()
    # binds execDetailsEvent which writes into it.
    db.init()
    try:
        await client.connect()
    except Exception as e:
        log.warning("Initial IB connection failed (will retry on first request): %s", e)
    yield
    await client.disconnect()


app = FastAPI(title="IBKR Mobile Backend", version="0.1.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health.router)
app.include_router(account.router)
app.include_router(quote.router)
app.include_router(quote.bars_router)
app.include_router(quote.quotes_router)
app.include_router(quote.static_router)
app.include_router(quote.depth_router)
app.include_router(quote.intraday_router)
app.include_router(quote.trades_router)
app.include_router(options.expiry_router)
app.include_router(options.chain_router)
app.include_router(orders.router)
app.include_router(executions.router)
app.include_router(ws_quotes.router)
