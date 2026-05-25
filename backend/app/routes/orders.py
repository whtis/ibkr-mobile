import asyncio
import logging
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException
from ib_async import LimitOrder, MarketOrder, Option, Stock
from pydantic import BaseModel

from ..auth import require_token
from ..ibkr import client

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
    )


@router.post("", response_model=OrderResponse)
async def place_order(req: PlaceOrderRequest) -> OrderResponse:
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
    await asyncio.sleep(0.6)

    status = trade.orderStatus.status
    if status in ("Rejected", "ApiCancelled", "Cancelled"):
        msg = trade.log[-1].message if trade.log else "(no detail)"
        raise HTTPException(400, f"order rejected: {status} — {msg}")
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
