# Proposal: multi-user-v2

## Intent

The v1 system is single-tenant: one IBKR account and one Longbridge developer credential live in the backend `.env`. The bearer-token "auth" between phone and backend is theater — it doesn't separate users, it just locks the door.

For v2, the app needs to support **multiple end users**, each with their own IBKR account and (independently) their own Longbridge OpenAPI credentials. Trading and asset data continues to route through an IBKR Gateway (architectural constraint of IBKR retail API). Market data moves out of the backend entirely: the Longbridge Java SDK is integrated directly inside the Android app, with each user pasting their own three tokens.

## Scope

### In scope

- Two **independent** account managers in the app: IBKR account list and Longbridge account list. A user can have 0–N of each.
- Per-user IBKR login via app: username + password, encrypted at rest on the device, transmitted to the backend only at session-establishment time.
- Per-user Longbridge auth via app: user pastes their own App Key + App Secret + Access Token. No OAuth.
- One IBKR session active at a time on the backend. Switching IBKR accounts restarts the Gateway container with the new credentials.
- Either account type can run alone (IBKR-only ⇒ no chart, Longbridge-only ⇒ no positions).
- Backend keeps the existing `API_TOKEN` bearer mechanism for transport auth (no JWT, no Tailscale enforcement). No multi-user state on backend.
- `/quote` and `/bars` endpoints are removed from the backend. Charts and quotes are served by the Longbridge SDK in-app.

### Out of scope

- Concurrent multi-IBKR sessions (multiple Gateway containers). Deferred.
- Longbridge OAuth flow (we register one public app, users authorize via web). Deferred.
- Token expiry auto-renewal for Longbridge (90-day expiry). User is responsible for updating tokens.
- Cross-device account sync (Google Drive / Android Backup). Deferred.
- iOS client.
- Trading endpoints. Still read-only in v2.

## Approach

1. **Longbridge spike first.** The hard prerequisite is that the Longbridge Java SDK (`io.github.longportapp:openapi`) ships an Android-compatible binary (arm64-v8a native libs). A 1-hour spike — add the gradle dep, instantiate `QuoteContext` on a real device, fetch one quote — gates the rest of v2. If the SDK is not Android-compatible, we fall back to keeping Longbridge in the backend (and v2 collapses to "multi-IBKR-account only").
2. **Backend refactor.** Remove `TWS_USERID` / `TWS_PASSWORD` / `LONGPORT_*` from `.env`. Add `POST /ibkr/login`, `POST /ibkr/logout`, `GET /ibkr/status`. The backend uses `docker-py` to manage the Gateway container lifecycle: credentials are passed as environment variables at `container.create()` time, never persisted to a file. Remove `/quote/*` and `/bars/*` route handlers.
3. **Android account management UI.** New "我的" tab structure: independent IBKR account list and Longbridge account list. Each row supports add, edit, delete, set-active. Active accounts persist in `EncryptedSharedPreferences`. Switching IBKR account triggers `POST /ibkr/login` and a 2FA wait flow.
4. **Android Longbridge SDK integration.** Configure `Config(app_key, app_secret, access_token)` from the active Longbridge account. `QuoteContext` is recreated when the active account changes. Replace all calls to backend `/quote/{symbol}` and `/bars/{symbol}` with direct SDK calls.
5. **Empty-state handling.** Each tab knows which account type it needs and surfaces a clear "no account configured" CTA when missing.
