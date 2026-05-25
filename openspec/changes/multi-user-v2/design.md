# Design: multi-user-v2

## Technical Approach

### High-level data flow

```
┌─────────────────────────────────────────────────────────┐
│ Android App                                             │
│                                                         │
│  ┌───────────────────────────────────┐                 │
│  │ Longbridge Java SDK (in-process)  │ ── 长桥 cloud   │
│  │   per active Longbridge account   │                 │
│  │   → /quote /bars (now in-app)     │                 │
│  └───────────────────────────────────┘                 │
│                                                         │
│  ┌───────────────────────────────────┐                 │
│  │ Account Manager (Keystore)        │                 │
│  │   IBKR accounts: [(user, pwd), …] │                 │
│  │   LB accounts:   [(k, s, t), …]   │                 │
│  │   activeIbkrId, activeLbId        │                 │
│  └───────────────────────────────────┘                 │
└──────────────┬──────────────────────────────────────────┘
               │ HTTPS + Bearer token (API_TOKEN)
               │ (Tailscale or LAN)
               ▼
┌─────────────────────────────────────────────────────────┐
│ Backend (FastAPI, stateless w.r.t. users)               │
│                                                         │
│  POST /ibkr/login   → recreate Gateway container        │
│                       with passed creds as env vars     │
│  POST /ibkr/logout  → stop Gateway container            │
│  GET  /ibkr/status  → connected? account_id?            │
│  GET  /account/*    → proxy to active Gateway           │
└──────────────┬──────────────────────────────────────────┘
               │ TWS socket :4001
               ▼
         IB Gateway (recreated per login)
```

### What the backend stops doing

- Storing IBKR credentials in `.env`.
- Routing `/quote` and `/bars` (deleted).
- Holding Longbridge developer credentials (deleted; per-user creds are now in app).
- Pretending to authenticate "users" (still only one bearer token; no per-user backend state).

### What the backend starts doing

- Managing the Gateway container lifecycle via `docker-py`:
  - `client.containers.run(image, environment={TWS_USERID: …, TWS_PASSWORD: …, TRADING_MODE: …}, ports=…, network=…, detach=True)`
  - `container.stop(); container.remove()`
- Tracking exactly one in-memory "current IBKR session" (account id, login time, healthy flag).
- Returning 409 from `/account/*` when no IBKR session is active.

## Architecture Decisions

### ADR-1: Keep the backend; do not go phone-direct for IBKR

IBKR's retail API authentication flow runs through a Java Gateway. There is no documented username/password endpoint we can hit from a phone. Even OAuth 2.0 requires per-user IBKR-side enablement that is not self-service for retail. A Mac/NAS-side Gateway is the simplest path.

### ADR-2: Longbridge moves to the client

The Longbridge Java SDK is Android-friendly (assuming spike confirms). Putting it in the app:
- Eliminates an entire endpoint class from the backend (`/quote`, `/bars`).
- Lets each user use their own Longbridge entitlements (free L1, paid L2, etc.).
- Removes the shared-developer-credential anti-pattern.

### ADR-3: IBKR is one-at-a-time, not concurrent

Concurrent IBKR sessions require one Gateway container per user. Personal/family scale (≤3 users) does not need concurrent access — switching with a ~30 second cost is acceptable. Re-evaluating concurrent mode is deferred.

### ADR-4: Credentials never persist on backend disk

IBKR credentials sent in `POST /ibkr/login` are used to start the Gateway container and then dropped. They are NOT written to `.env`, NOT logged, NOT cached. If the backend restarts, no IBKR session resumes automatically — the app must re-login.

### ADR-5: Account types are independent, not paired

A user can have one IBKR account and zero Longbridge accounts (positions visible, no charts), or two Longbridge accounts and zero IBKR accounts (charts visible, no positions). The two lists are independent; there is no "account" wrapper object that bundles them.

### ADR-6: Existing bearer token stays as transport auth

Despite the multi-user UI, the network-level auth between app and backend remains the single `API_TOKEN`. The bearer is a network ACL, not a user identity. (User explicitly chose to defer JWT/Tailscale work.)

## Data Flow

### Switching IBKR accounts

1. User taps a different IBKR account in 我的 tab.
2. App reads encrypted credentials from `EncryptedSharedPreferences`.
3. App calls `POST /ibkr/logout` (idempotent).
4. App calls `POST /ibkr/login {username, password}` over HTTPS+bearer.
5. Backend uses `docker-py` to remove the existing Gateway container and start a new one with the new env vars.
6. Backend returns `{status: "awaiting_2fa", login_id}`.
7. App shows a "请在 IBKR Mobile 上批准登录" screen with a poll on `GET /ibkr/status`.
8. User approves push notification on phone.
9. Gateway login completes. `GET /ibkr/status` returns `{connected: true, account_id: U…}`.
10. App marks the new IBKR account as active and reloads the 持仓 tab.

### Switching Longbridge accounts

1. User taps a different Longbridge account in 我的 tab.
2. App reads encrypted (app_key, app_secret, access_token).
3. App calls `LongbridgeSdkHolder.replace(newConfig)` — synchronous, in-process.
4. The new `QuoteContext` is created on a background thread.
5. Active screens (自选 / 个股详情) invalidate their state and re-query.

### Fetching a stock's K-line

Was: `App → backend /bars/{symbol} → longport SDK on backend`
Now: `App → in-process longport SDK → Longbridge cloud`

### Fetching positions

Unchanged: `App → backend /account/positions → ib_async → Gateway → IBKR`

## File Changes

### Backend

| File | Change |
|---|---|
| `app/routes/quote.py` | DELETED |
| `app/longbridge.py` | DELETED |
| `app/ibkr.py` | Refactored: no longer reads creds from settings; accepts creds via `connect(username, password)`. Removes the connect-on-startup pattern. |
| `app/routes/ibkr_auth.py` | NEW: `POST /ibkr/login`, `POST /ibkr/logout`, `GET /ibkr/status` |
| `app/docker_gateway.py` | NEW: thin wrapper over `docker-py` that creates/destroys the Gateway container with passed env vars |
| `app/main.py` | Remove `quote.router` registration; add `ibkr_auth.router`; remove auto-connect lifespan code |
| `app/config.py` | Remove `LONGPORT_*` and IBKR cred fields |
| `pyproject.toml` | Add `docker>=7`; remove `longport` |
| `.env.example` | Remove `TWS_USERID`, `TWS_PASSWORD`, `LONGPORT_*`. Keep `API_TOKEN`, `IB_HOST`, `IB_PORT`, `IB_CLIENT_ID` |
| `docker-compose.yml` | DELETED (Gateway is now managed by FastAPI via docker-py, not docker-compose) |

### Android

| File | Change |
|---|---|
| `data/api/IbkrApi.kt` | Remove `quote()` and `bars()` methods. Add `ibkrLogin()`, `ibkrLogout()`, `ibkrStatus()` |
| `data/longbridge/LbSdk.kt` | NEW: singleton wrapping `QuoteContext` lifecycle, swappable on account change |
| `data/store/AccountStore.kt` | NEW: encrypted storage for IBKR + Longbridge account lists; uses `EncryptedSharedPreferences` or `androidx.security.crypto` |
| `viewmodel/AccountsViewModel.kt` | NEW: list/add/edit/delete/setActive flows for both account types |
| `viewmodel/IbkrLoginViewModel.kt` | NEW: handles the login→2FA-poll flow |
| `viewmodel/StockDetailViewModel.kt` | Switch `bars()` source from backend to `LbSdk` |
| `viewmodel/PositionsViewModel.kt` | Surface "no IBKR account active" empty state |
| `ui/screens/IbkrAccountListScreen.kt` | NEW |
| `ui/screens/LongbridgeAccountListScreen.kt` | NEW |
| `ui/screens/IbkrLoginScreen.kt` | NEW (form + biometric unlock) |
| `ui/screens/LongbridgeLoginScreen.kt` | NEW (paste-three-tokens form) |
| `ui/screens/IbkrLoginProgressScreen.kt` | NEW (2FA waiting) |
| `ui/screens/SettingsScreen.kt` | Restructured to show two account sub-lists + backend address + theme prefs |
| `app/build.gradle.kts` | Add `io.github.longportapp:openapi` (assume groupId after spike), `androidx.security:security-crypto`, `androidx.biometric:biometric` |
| `data/store/SettingsStore.kt` | Keep backend URL + API token; deprecate "single ibkr account" assumption |

### Project root

| File | Change |
|---|---|
| `BUILD.md` | Update architecture section to reflect v2 |
| `ONBOARDING.md` | Update restart flow (no Gateway daemon at startup — it's launched on app login) |
| `DESIGN_NOTES.md` | Mark v2 sections as "shipped" once landed |

## Risks

| Risk | Mitigation |
|---|---|
| Longbridge Java SDK doesn't ship arm64-v8a Android-compatible native libs | Spike before any other work. If it fails, fall back to keeping `/quote` and `/bars` in backend with backend-side Longbridge creds (i.e., abandon the LB-in-app part of v2). |
| `docker-py` doesn't have an equivalent for some docker-compose features we use (healthchecks, restart policy) | Reimplement those features explicitly in Python (health = TCP probe to `127.0.0.1:4001`, restart = `signal` handler to re-create container on exit) |
| IBKR 2FA flow blocks the entire backend Gateway management if the user dawdles | `POST /ibkr/login` returns immediately with a login_id; the app polls `/ibkr/status`. Login times out after 5 min; user can retry |
| Encrypted account storage corrupt on Android Keystore reset (factory reset, biometric template change) | On decrypt failure, wipe and force re-add. Don't try to recover. |
| User uninstalls and reinstalls app → all accounts lost | Document; cross-device sync is explicitly out of scope for v2 |
