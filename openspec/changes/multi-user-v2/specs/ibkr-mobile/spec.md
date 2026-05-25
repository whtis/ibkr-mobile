# ibkr-mobile Spec Delta — multi-user-v2

## ADDED Requirements

### Requirement: Multiple IBKR accounts on device

The Android app SHALL allow the user to save zero, one, or many IBKR accounts. Each account consists of a user-supplied label, an IBKR username, and a password. Account records SHALL be persisted in `EncryptedSharedPreferences` and never transmitted in plaintext outside of one specific endpoint: `POST /ibkr/login`.

#### Scenario: Add an IBKR account
- GIVEN the 我的 tab is open
- WHEN the user taps "添加 IBKR 账户", fills in the form, and saves
- THEN the account is appended to the local IBKR account list, encrypted at rest

#### Scenario: Switch active IBKR account
- GIVEN two IBKR accounts are saved and account A is active
- WHEN the user taps account B
- THEN the app sends `POST /ibkr/logout`, then `POST /ibkr/login` with B's credentials, polls `/ibkr/status` until `connected: true`, and marks B as active

#### Scenario: Delete an IBKR account
- GIVEN account A is saved (active or not)
- WHEN the user swipes A and confirms delete
- THEN A is removed from the encrypted store; if A was active, the active pointer becomes null

### Requirement: Multiple Longbridge accounts on device

The Android app SHALL allow the user to save zero, one, or many Longbridge accounts. Each account consists of a user-supplied label, App Key, App Secret, and Access Token. App Secret and Access Token SHALL be stored encrypted; App Key MAY be stored plaintext (it's a public identifier).

#### Scenario: Add a Longbridge account
- WHEN the user pastes the three tokens and saves
- THEN the account is appended, the in-process `QuoteContext` is reconfigured if this is the first active account, and quote/bars APIs become ready

#### Scenario: Switch active Longbridge account
- GIVEN two Longbridge accounts are saved
- WHEN the user taps the inactive one
- THEN the app updates `activeLbId`, reconfigures `QuoteContext` on a background thread, and invalidates any cached quote/bars state

### Requirement: Independent account types

IBKR and Longbridge accounts SHALL be managed as two **independent** lists. There is no concept of pairing an IBKR account with a specific Longbridge account.

#### Scenario: Only IBKR configured
- GIVEN one IBKR account is saved and active, zero Longbridge accounts
- WHEN the user opens the 持仓 tab
- THEN positions load normally

#### Scenario: Only IBKR configured, opens 自选 tab
- GIVEN the same configuration
- WHEN the user opens 自选 or 市场
- THEN the screen shows "添加长桥账户" empty-state CTA

#### Scenario: Only Longbridge configured, opens 持仓 tab
- GIVEN one Longbridge account is saved and active, zero IBKR accounts
- WHEN the user opens 持仓
- THEN the screen shows "添加 IBKR 账户" empty-state CTA

### Requirement: Longbridge SDK runs in-process

Quote and historical bar data SHALL be fetched by the Longbridge Java SDK running inside the Android app process. The SDK SHALL be reconfigured when the active Longbridge account changes.

#### Scenario: In-process quote fetch
- GIVEN active Longbridge account is configured
- WHEN the app needs the current quote for TSLA
- THEN it calls `LbSdk.quote("TSLA")` directly without contacting the backend

#### Scenario: SDK reconfiguration on account switch
- GIVEN active Longbridge account is A
- WHEN the user switches active account to B
- THEN `LbSdk.configure(B)` is called and produces a new `QuoteContext` bound to B's credentials before any further data is fetched

### Requirement: Dynamic IBKR Gateway lifecycle on backend

The backend SHALL manage the IB Gateway Docker container as an on-demand resource. The container SHALL be created when the app sends `POST /ibkr/login` and destroyed when the app sends `POST /ibkr/logout` or when a new login replaces the previous account.

#### Scenario: Login starts the container
- GIVEN no Gateway container is running
- WHEN the backend receives `POST /ibkr/login {username, password}`
- THEN it creates a Gateway container via `docker-py` with the credentials injected as environment variables, returns `{login_id, status: "awaiting_2fa"}` within 1 second, and the actual login proceeds asynchronously

#### Scenario: Logout stops the container
- GIVEN a Gateway container is running
- WHEN the backend receives `POST /ibkr/logout`
- THEN it stops and removes the container, drops its in-memory session reference, and any subsequent `/account/*` request returns 409

### Requirement: Backend never persists IBKR credentials

The backend MUST NOT write IBKR credentials to disk, log files, or any other persistent store. Credentials are accepted via the `POST /ibkr/login` request body, used to start the Gateway container, and discarded from process memory immediately after the container is created.

#### Scenario: Credentials transit only
- WHEN `POST /ibkr/login` is received
- THEN the credentials appear only in: the request body, the `docker-py` env arg, and the resulting container's environment. They are NOT written to any backend file.

#### Scenario: Backend restart wipes session
- GIVEN a Gateway container is running with credentials passed earlier
- WHEN the FastAPI process is restarted
- THEN the in-memory session is lost; the app must re-issue `POST /ibkr/login` to resume

### Requirement: IBKR connectivity is required only for /account/*

Only `/account/summary` and `/account/positions` depend on an active IBKR session. All other endpoints (`/health`, `/ibkr/login`, `/ibkr/logout`, `/ibkr/status`) SHALL succeed regardless of IBKR state.

#### Scenario: No active IBKR session, app calls /account
- GIVEN `GET /ibkr/status` returns `{connected: false}`
- WHEN the app calls `GET /account/positions`
- THEN the backend returns 409 with `{error: "no_active_ibkr_session"}`

## MODIFIED Requirements

### Requirement: Single-user assumption (RENAMED → Single network identity)

Previously: "The system SHALL operate as a single-tenant system. There is exactly one IBKR account in `.env`, one Longbridge developer credential in `.env`, and one bearer token shared between the phone and the backend."

Now: The system SHALL support multiple end-user account identities on the device. Transport-level authentication between the app and the backend remains a single shared `API_TOKEN` bearer value (the bearer is a network ACL, not a user identity). There is no longer any IBKR or Longbridge credential stored in backend `.env`.

#### Scenario: Bearer still required
- WHEN any backend endpoint other than `/health` is called without the bearer header
- THEN the backend returns 401

#### Scenario: No backend-side user records
- WHEN the app queries the backend
- THEN the backend treats every authenticated request as anonymous-but-trusted; it has no concept of "which user"

### Requirement: Longbridge for quotes and historical bars (MOVED to client)

Previously: "When `LONGPORT_APP_KEY`, `LONGPORT_APP_SECRET`, and `LONGPORT_ACCESS_TOKEN` are all non-empty in backend `.env`, the backend SHALL route `/quote/{symbol}` and `/bars/{symbol}` to Longbridge."

Now: The Longbridge integration MOVES from the backend to the Android app. The app SHALL use the Longbridge Java SDK directly, with credentials sourced from the active Longbridge account in `AccountStore`. The backend SHALL NOT have any Longbridge code paths.

#### Scenario: Direct SDK quote
- GIVEN active Longbridge account is configured in the app
- WHEN the app needs a quote
- THEN it calls the in-process SDK, not the backend

## REMOVED Requirements

### Requirement: `/quote/{symbol}` backend endpoint
**Reason**: Replaced by in-app Longbridge SDK call.

### Requirement: `/bars/{symbol}` backend endpoint
**Reason**: Replaced by in-app Longbridge SDK call.

### Requirement: Backend `.env` IBKR credentials (`TWS_USERID`, `TWS_PASSWORD`)
**Reason**: Credentials now flow per-login from the app to the backend, in-memory only.

### Requirement: Backend `.env` Longbridge credentials (`LONGPORT_APP_KEY`, `LONGPORT_APP_SECRET`, `LONGPORT_ACCESS_TOKEN`)
**Reason**: Longbridge is now per-user, in-app, not on the backend.

### Requirement: `docker-compose.yml` Gateway service
**Reason**: Gateway is now managed by Python via `docker-py`, started on `POST /ibkr/login` rather than at compose-up time.
