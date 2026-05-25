# ibkr-mobile Specification (v1)

## Purpose

Personal Android client for viewing IBKR positions and market data. Single-user, single-IBKR-account, single-Longbridge-developer-credential. Backend runs on the user's Mac and proxies both IBKR (via Docker'd Gateway) and Longbridge OpenAPI.

## Requirements

### Requirement: Backend bearer token authentication

The backend MUST require an `Authorization: Bearer <API_TOKEN>` header on every endpoint except `/health`. The token value SHALL be the literal `API_TOKEN` value from backend `.env`.

#### Scenario: Authorized request
- GIVEN the app has stored the matching token in DataStore
- WHEN the app sends `GET /account/positions` with the header set
- THEN the backend returns 200 with the JSON position list

#### Scenario: Missing token
- GIVEN no `Authorization` header
- WHEN any non-health endpoint is called
- THEN the backend returns 401 Unauthorized

### Requirement: IBKR positions and account summary

The backend MUST expose `/account/summary` and `/account/positions` for the IBKR account whose credentials live in `.env` (`TWS_USERID`, `TWS_PASSWORD`). Data is fetched through `ib_async` from a Gateway container running on the same host.

#### Scenario: Live positions
- GIVEN IB Gateway is logged in and `ib.portfolio()` is populated
- WHEN the app calls `GET /account/positions`
- THEN the backend returns each position with `market_price`, `market_value`, `unrealized_pnl`, `realized_pnl`, plus the static fields (symbol, secType, exchange, currency, position, avg_cost)

#### Scenario: Gateway disconnected
- GIVEN `Connectivity between IBKR and Trader Workstation has been lost`
- WHEN the app calls `GET /account/positions`
- THEN the backend MAY return a stale snapshot or 503 within `requestTimeoutMillis`

### Requirement: Longbridge for quotes and historical bars

When `LONGPORT_APP_KEY`, `LONGPORT_APP_SECRET`, and `LONGPORT_ACCESS_TOKEN` are all non-empty in backend `.env`, the backend SHALL route `/quote/{symbol}` and `/bars/{symbol}` to Longbridge via the `longport` Python SDK. Otherwise it SHALL fall back to IBKR `reqMktData` / `reqHistoricalData`.

#### Scenario: Longbridge configured
- GIVEN all three `LONGPORT_*` env vars are set
- WHEN the app calls `GET /bars/TSLA?period=1d`
- THEN the backend converts `TSLA` to `TSLA.US`, calls `QuoteContext.candlesticks`, and returns up to 500 daily bars as `{time, open, high, low, close, volume}[]`

#### Scenario: Longbridge missing
- GIVEN `LONGPORT_*` env vars are empty
- WHEN the app calls `GET /quote/TSLA`
- THEN the backend invokes `ib_async.reqMktData` against IBKR and returns whatever fields it can populate (likely many nulls without a market-data subscription)

### Requirement: Single-user assumption

The system SHALL operate as a single-tenant system. There is exactly one IBKR account in `.env`, one Longbridge developer credential in `.env`, and one bearer token shared between the phone and the backend.

#### Scenario: Multi-user not supported
- WHEN a second user attempts to add credentials
- THEN there is no UI or backend endpoint to do so

### Requirement: Android system theme following

The Android app SHALL render in the dark color palette when `isSystemInDarkTheme()` returns true, and the light palette otherwise. Token coverage SHALL be complete: no hard-coded colors in screens, all colors flow through `LbColors`.

#### Scenario: System switches to light
- GIVEN the app is in dark mode
- WHEN the user toggles the OS to light mode
- THEN the next composition uses `LightIbkrColors` for all surfaces, text, dividers, and bars

### Requirement: Position tap navigates to stock detail with K-line

Tapping any row in the 持仓 tab SHALL navigate to a detail screen that renders OHLC candlesticks via TradingView Lightweight Charts inside a WebView. The chart JS MUST be bundled in `app/src/main/assets/lightweight-charts.js` (no CDN dependency).

#### Scenario: Tap a position
- GIVEN the user is on the 持仓 tab with positions visible
- WHEN they tap a row for symbol TSLA
- THEN the app pushes route `stock/TSLA?exchange=NASDAQ&currency=USD`, hides the bottom nav, and shows the detail screen with day-K bars fetched from `/bars/TSLA?period=1d`

### Requirement: Bottom navigation tabs

The app SHALL expose exactly four bottom tabs: 自选 (Watchlist, placeholder), 市场 (Market, placeholder), 持仓 (Positions, functional), 我的 (Settings, functional). The 圈子 tab present in earlier drafts is removed.

#### Scenario: Bottom nav visibility
- GIVEN the user is on any top-level tab route
- WHEN the screen renders
- THEN the bottom nav is visible with these four entries

#### Scenario: Detail screens hide the bar
- GIVEN the user is on `stock/{symbol}`
- WHEN the screen renders
- THEN the bottom nav is hidden, only the detail content with a back arrow is shown

### Requirement: Red-up green-down semantics

Numeric color tokens follow the Chinese convention: `Up = #FF4D4F` (red) for positive deltas, `Down = #00C087` (green) for negative deltas, `Flat = #9CA3AF` for zero/unchanged.

#### Scenario: Negative PnL
- GIVEN a position has `unrealized_pnl = -281.22`
- WHEN the row renders
- THEN the PnL text uses `LbColors.Down` (green)

### Requirement: Number formatting

All financial numbers SHALL use `font-feature-settings: "tnum"` (tabular figures) so digits don't shift width during streaming updates. Prices use 2 decimals (`#,##0.00`) for most contexts and 3 decimals (`#,##0.000`) for current/cost price columns in 持仓.

#### Scenario: Header total assets
- GIVEN net liquidation is 24956.85
- WHEN the 持仓 header renders
- THEN the integer part `24,956` displays in `displayLarge` and `.85` displays in `titleLarge`, both with tabular figures

### Requirement: Pull-to-refresh

The 持仓 screen SHALL implement Material 3 `PullToRefreshBox`. Pulling the list down SHALL re-fetch `/account/summary` and `/account/positions` in parallel.

#### Scenario: Pull
- GIVEN positions are visible
- WHEN the user drags the top of the list down past the threshold
- THEN the refresh indicator shows, both endpoints are called, and the UI updates when both succeed
