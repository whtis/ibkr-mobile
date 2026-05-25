"""WebSocket endpoint for realtime quote push from Longbridge.

Client protocol (JSON over WS):
- Outgoing (server -> client): `{"symbol": "AAPL", "last": 220.1, "high": ..., ...}`
- Incoming (client -> server):
  - `{"action": "subscribe", "symbols": ["AAPL", "TSLA"], "currency": "USD"}`
  - `{"action": "unsubscribe", "symbols": ["AAPL"], "currency": "USD"}`
  - `{"action": "ping"}` → server replies `{"action": "pong"}`
"""

import asyncio
import logging

from fastapi import APIRouter, Query, WebSocket, WebSocketDisconnect

from ..config import settings
from ..longbridge import lb_client, to_lb_symbol

log = logging.getLogger(__name__)

router = APIRouter()


@router.websocket("/ws/quotes")
async def ws_quotes(ws: WebSocket, token: str = Query(...)) -> None:
    if token != settings.api_token:
        await ws.close(code=4401, reason="invalid token")
        return
    if not lb_client.is_configured():
        await ws.close(code=4503, reason="longbridge not configured")
        return

    await ws.accept()
    # Each client tracks the LB-formatted symbols (e.g. "AAPL.US") and a reverse
    # map from LB symbol -> raw symbol for outgoing payload.
    client_subs: set[str] = set()
    reverse: dict[str, str] = {}
    out_queue: asyncio.Queue = asyncio.Queue()

    # Ensure the lb client knows the running loop for its bridge.
    lb_client.set_main_loop(asyncio.get_running_loop())

    async def on_push(snapshot: dict) -> None:
        sym = snapshot.get("symbol")
        if sym in client_subs:
            raw = reverse.get(sym, sym)
            payload = dict(snapshot)
            payload["symbol"] = raw  # client expects raw symbol
            payload["lb_symbol"] = sym
            await out_queue.put(payload)

    lb_client.add_push_subscriber(on_push)

    async def sender() -> None:
        try:
            while True:
                msg = await out_queue.get()
                await ws.send_json(msg)
        except Exception:
            return

    sender_task = asyncio.create_task(sender())

    try:
        while True:
            data = await ws.receive_json()
            action = data.get("action")
            currency = data.get("currency", "USD")
            syms = data.get("symbols") or []
            if action == "ping":
                await ws.send_json({"action": "pong"})
            elif action == "subscribe":
                lb_syms = [to_lb_symbol(s, currency) for s in syms]
                new_pairs = [(raw, lb) for raw, lb in zip(syms, lb_syms) if lb not in client_subs]
                for raw, lb in new_pairs:
                    client_subs.add(lb)
                    reverse[lb] = raw
                if new_pairs:
                    await lb_client.subscribe_quote([lb for _, lb in new_pairs])
            elif action == "unsubscribe":
                lb_syms = [to_lb_symbol(s, currency) for s in syms]
                to_drop = [lb for lb in lb_syms if lb in client_subs]
                for lb in to_drop:
                    client_subs.discard(lb)
                    reverse.pop(lb, None)
                if to_drop:
                    await lb_client.unsubscribe_quote(to_drop)
    except WebSocketDisconnect:
        pass
    except Exception:
        log.exception("ws handler error")
    finally:
        sender_task.cancel()
        lb_client.remove_push_subscriber(on_push)
        # Release client's outstanding subscriptions
        if client_subs:
            await lb_client.unsubscribe_quote(list(client_subs))
