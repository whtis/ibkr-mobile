# Build & Architecture

## Goals

- Personal-use Android client for IBKR, UI inspired by Longbridge.
- Scope: stocks + options + ETFs. Market data + trading.
- Single user. Single device. No multi-tenant.
- Paper account during all dev; switch to live by changing two env vars.

## Topology

```
Android (Kotlin + Compose)
   │  HTTPS + Bearer token
   ▼
FastAPI + ib_async  (native on Mac, port 8000)
   │  TWS binary socket
   ▼
IB Gateway in Docker  (gnzsnz image, port 4002 paper / 4001 live)
   │
   ▼
IBKR servers
```

The backend on the Mac is the **only** place holding IBKR credentials.
The phone holds only `(backend_url, bearer_token)`.

## Why this stack

| Decision | Why |
|---|---|
| **ib_async** (not ibapi) | Sync/async, pandas-friendly, modern repo (`ib-api-reloaded` org). Official `ibapi` is callback hell. |
| **Docker for Gateway only** | Auto-restart, IBC handles 2FA timeout & weekly Sunday token reset. Easier than babysitting a Java jar. |
| **FastAPI native (not in Docker)** | Faster dev iteration. We're a single-host app; container isolation buys nothing. |
| **Bearer token (not OAuth)** | Single user. Token in `.env`, served over HTTPS / Tailscale. OAuth would be theater. |
| **Kotlin + Compose** | Native Android only — no React Native / Flutter compromises. Best perf for high-density real-time UI. |
| **Ktor (not Retrofit)** | First-class coroutines, kotlinx.serialization, less boilerplate. |
| **Material 3 base + custom dark tokens** | M3 default is too airy. Override colorScheme + density to mirror Longbridge's compact dark look. |

## Network

- **Home Wi-Fi**: phone → `http://<mac-lan-ip>:8000`
- **Outside**: Tailscale on Mac + phone → `http://<mac>.<tailnet>.ts.net:8000`
- App settings page lets you switch between profiles.
- Future: TLS via Caddy reverse proxy in front of FastAPI (when we go live).

## Safety rails

1. `READ_ONLY_API=yes` in `.env` blocks all order placement at the Gateway level.
   Only flip to `no` when explicitly ready to place orders.
2. Paper account only until v3+ is tested end-to-end.
3. `clientId=10` reserved for backend; if you want to attach TWS GUI simultaneously, use a different clientId there.
4. Two-step confirmation (BiometricPrompt) required for any order placement in app.

## Gotchas to remember

- **Sunday ~01:00 ET**: IBKR resets login tokens. IBC will request 2FA — confirm on IBKR Mobile.
- **Daily 23:45 ET** (or your configured `AUTO_RESTART_TIME`): Gateway restarts itself. Backend should reconnect on next request.
- **Historical data pacing**: same contract+exchange+barType within 15s = violation. Throttle if you batch-fetch.
- **Decimal tickSize** (post 2026-02 ib_async): treat numeric ticker fields as `float | Decimal`, coerce explicitly.
- **Mac sleep**: Gateway and FastAPI both stop. Either `caffeinate -i uv run ...` or set "Prevent automatic sleeping on power adapter" in System Settings.

## Versioning roadmap

| Version | Scope |
|---|---|
| v0 | Account summary + positions over REST, Android skeleton |
| v1 | Watchlist + real-time quote stream via WebSocket |
| v2 | Stock detail page + K-line chart (TradingView Lightweight Charts in WebView) |
| v3 | Order placement (limit + market) + biometric confirm |
| v4 | Option chain + bracket orders |
| v5 | Push notifications (order fills, price alerts) via FCM |
| v6 | Home screen widget (Glance API) |
