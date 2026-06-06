import asyncio
import logging
import time
from collections import deque
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException
from ib_async import LimitOrder, MarketOrder, Option, Stock
from pydantic import BaseModel

from ..auth import require_token
from ..config import settings
from ..ibkr import client
from ..longbridge import lb_client, to_lb_symbol

log = logging.getLogger(__name__)

router = APIRouter(prefix="/orders", dependencies=[Depends(require_token)])


class PlaceOrderRequest(BaseModel):
    symbol: str
    exchange: str = "SMART"
    currency: str = "USD"
    sec_type: Literal["STK", "OPT"] = "STK"
    # Option-only fields:
    expiry: str | None = None   # YYYYMMDD
    strike: float | None = None
    right: Literal["C", "P"] | None = None
    side: Literal["BUY", "SELL"]
    order_type: Literal["LMT", "MKT"]
    quantity: float
    price: float | None = None
    tif: Literal["DAY", "GTC"] = "DAY"
    outside_rth: bool = False


class OrderResponse(BaseModel):
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
    message: str | None = None  # latest IB log/validation message, if any


def _last_log_message(trade) -> str | None:
    if not trade.log:
        return None
    texts = [e.message for e in trade.log if getattr(e, "message", None)]
    return texts[-1] if texts else None


def _trade_to_response(trade) -> OrderResponse:
    o = trade.order
    s = trade.orderStatus
    return OrderResponse(
        order_id=int(o.orderId),
        perm_id=int(o.permId) if o.permId else None,
        status=str(s.status),
        symbol=trade.contract.symbol,
        side=str(o.action),
        quantity=float(o.totalQuantity),
        filled=float(s.filled or 0.0),
        avg_fill_price=float(s.avgFillPrice) if s.avgFillPrice else None,
        type=str(o.orderType),
        price=float(o.lmtPrice) if o.lmtPrice else None,
        tif=str(o.tif or "DAY"),
        outside_rth=bool(o.outsideRth),
        message=_last_log_message(trade),
    )



# --- Safety guards: per-order qty + notional cap, plus per-process rate limit.
#
# Rationale: even on tailnet-only with a rotated token, a stolen token still
# means anybody can hit /orders. These guards bound the worst-case blast
# radius — a single mistake or compromised token cannot drain the account.
# Tunable via .env (see app/config.py).

_order_timestamps: deque[float] = deque()


def _rate_limit_check() -> None:
    """Sliding-1-min window. In-process state — fine as long as we run one uvicorn."""
    limit = settings.order_rate_limit_per_min
    if not settings.enable_order_guards or limit <= 0:
        return
    now = time.monotonic()
    cutoff = now - 60.0
    while _order_timestamps and _order_timestamps[0] < cutoff:
        _order_timestamps.popleft()
    if len(_order_timestamps) >= limit:
        log.warning("order rate limit hit (%d in last 60s)", len(_order_timestamps))
        raise HTTPException(429, f"order rate limit exceeded: {limit}/minute")
    _order_timestamps.append(now)


async def _guard_quantity_and_notional(req: "PlaceOrderRequest") -> None:
    """Reject before reaching IB if quantity or estimated notional is too large.

    For LMT we use the limit price; for MKT we fetch the Longbridge last quote.
    Notional check is only strict when currency == USD (we don't FX-convert);
    other currencies only log so the order still goes through after qty check.
    """
    if not settings.enable_order_guards:
        return
    if req.quantity <= 0:
        raise HTTPException(422, "quantity must be > 0")
    if req.quantity > settings.max_order_qty:
        raise HTTPException(
            422,
            f"quantity {req.quantity} exceeds per-order max {settings.max_order_qty}",
        )

    # Best-effort estimate of price for notional calculation.
    est_price: float | None = req.price
    if est_price is None and lb_client.is_configured():
        try:
            lb_sym = to_lb_symbol(req.symbol, req.currency)
            quotes = await lb_client.quote([lb_sym])
            if quotes:
                last = getattr(quotes[0], "last_done", None)
                if last is not None:
                    est_price = float(last)
        except Exception as e:  # noqa: BLE001
            log.warning("notional guard: quote fetch failed for %s: %s", req.symbol, e)

    if est_price is None:
        log.warning(
            "notional guard: could not determine est_price for %s (MKT order, no quote) — qty-only check",
            req.symbol,
        )
        return

    multiplier = 100 if req.sec_type == "OPT" else 1
    notional = req.quantity * est_price * multiplier

    if req.currency == "USD":
        if notional > settings.max_order_notional_usd:
            raise HTTPException(
                422,
                f"estimated notional {notional:.0f} USD exceeds per-order max "
                f"{settings.max_order_notional_usd:.0f} (qty={req.quantity}, est_price={est_price})",
            )
    else:
        # Non-USD: don't FX-convert; log loudly so a runaway is still visible.
        log.warning(
            "non-USD order: notional=%.0f %s (not converted to USD for cap check). "
            "qty=%s est_price=%s symbol=%s",
            notional, req.currency, req.quantity, est_price, req.symbol,
        )


@router.post("", response_model=OrderResponse)
async def place_order(req: PlaceOrderRequest) -> OrderResponse:
    _rate_limit_check()
    await _guard_quantity_and_notional(req)
    ib = await client.ensure_connected()
    if req.sec_type == "OPT":
        if not (req.expiry and req.strike is not None and req.right):
            raise HTTPException(400, "expiry, strike, right required for option order")
        # Normalize expiry to YYYYMMDD (Longbridge returns YYYY-MM-DD)
        expiry_norm = req.expiry.replace("-", "")
        # Option exchange defaults to SMART; multiplier 100 standard for US equity options
        opt_exchange = req.exchange if req.exchange and req.exchange != "SMART" else "SMART"
        contract = Option(
            req.symbol.upper(),
            expiry_norm,
            req.strike,
            req.right,
            opt_exchange,
            currency=req.currency,
            multiplier="100",
        )
    else:
        contract = Stock(req.symbol.upper(), req.exchange, req.currency)
    details = await ib.reqContractDetailsAsync(contract)
    if not details:
        raise HTTPException(404, f"unknown contract for {req.symbol}")
    resolved = details[0].contract

    if req.order_type == "LMT":
        if req.price is None:
            raise HTTPException(400, "price required for LMT order")
        order = LimitOrder(action=req.side, totalQuantity=req.quantity, lmtPrice=req.price)
    else:  # MKT
        order = MarketOrder(action=req.side, totalQuantity=req.quantity)
    order.tif = req.tif
    order.outsideRth = req.outside_rth

    log.info(
        "Placing %s %s %s qty=%.4f price=%s tif=%s outsideRth=%s",
        req.side, req.symbol, req.order_type, req.quantity, req.price, req.tif, req.outside_rth,
    )
    trade = ib.placeOrder(resolved, order)

    # Wait for the order to settle past the transient submit phase. ib_async can
    # briefly report "ValidationError" (PreSubmitted -> ValidationError -> Submitted
    # when the validation is auto-ignorable), so a fixed short sleep would snapshot
    # that transient state and mislead the client. Poll until it settles.
    settled = {"Submitted", "Filled", "Cancelled", "ApiCancelled", "Inactive", "PendingCancel"}
    for _ in range(40):  # up to ~4s
        await asyncio.sleep(0.1)
        if trade.orderStatus.status in settled:
            break

    status = trade.orderStatus.status
    msg = _last_log_message(trade)
    if status in ("Rejected", "ApiCancelled", "Cancelled", "Inactive"):
        raise HTTPException(400, f"order rejected: {status} — {msg or '(no detail)'}")
    # A lingering ValidationError isn't necessarily fatal (order may still be live),
    # but surface IB's message so the client can show why instead of a bare status.
    return _trade_to_response(trade)


@router.get("/active", response_model=list[OrderResponse])
async def active_orders() -> list[OrderResponse]:
    ib = await client.ensure_connected()
    return [_trade_to_response(t) for t in ib.openTrades()]


@router.delete("/{order_id}")
async def cancel_order(order_id: int) -> dict:
    ib = await client.ensure_connected()
    for trade in ib.openTrades():
        if int(trade.order.orderId) == order_id:
            ib.cancelOrder(trade.order)
            await asyncio.sleep(0.4)
            return {"ok": True, "status": trade.orderStatus.status}
    raise HTTPException(404, f"order not found or already terminal: {order_id}")
