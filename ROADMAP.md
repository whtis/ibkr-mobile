# Roadmap

## Status

**v1** — Current shipped state. Single-user. Backend `.env` holds one IBKR paper credential and one shared LongPort developer credential. Bearer-token "auth" between app and backend is a transport ACL, not a user identity.

**v2** — Multi-user redesign. Design complete, implementation pending. See [`openspec/changes/multi-user-v2/`](openspec/changes/multi-user-v2/) for the full spec.

---

## v2 — multi-user redesign

### Problem

v1 is single-tenant by construction:
- One IBKR account, one Longbridge credential, both in backend `.env`.
- The bearer token is a network-level door lock; it doesn't separate users.
- A second user can only be supported by handing them the backend's `.env` — i.e., by giving them everyone's credentials.

### Goal

Make the app safe to install on multiple devices, with each end user supplying their own IBKR credentials and their own Longbridge developer tokens — without the backend ever persisting either.

### Design summary

```
┌─────────────────────────────────────────────────────────┐
│ Android App                                             │
│                                                         │
│  ┌───────────────────────────────────┐                 │
│  │ Longbridge Java SDK (in-process)  │ ── 长桥 cloud   │
│  │   per active Longbridge account   │                 │
│  └───────────────────────────────────┘                 │
│                                                         │
│  ┌───────────────────────────────────┐                 │
│  │ Account Manager (Keystore)        │                 │
│  │   IBKR accounts: [(user, pwd), …] │                 │
│  │   LB accounts:   [(k, s, t), …]   │                 │
│  │   activeIbkrId, activeLbId        │                 │
│  └───────────────────────────────────┘                 │
└──────────────┬──────────────────────────────────────────┘
               │ HTTPS + Bearer token  (transport ACL)
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

### Major changes

| Area | v1 | v2 |
|---|---|---|
| **IBKR creds** | Static, in backend `.env` | Posted to backend per-login, never persisted |
| **Longbridge creds** | One shared developer key in backend `.env` | Per-user, stored encrypted on device, SDK runs in-app |
| **Gateway lifecycle** | `docker-compose up` at backend start | Started by `POST /ibkr/login`, destroyed by `POST /ibkr/logout`, managed via `docker-py` |
| **Quote / bars endpoints** | `/quote/{symbol}`, `/bars/{symbol}` on backend | Removed. Replaced by in-app Longbridge SDK calls. |
| **Account model in app** | None — backend is "the account" | Two independent lists: IBKR accounts and Longbridge accounts. Either can be empty. Active pointers stored separately. |
| **Sensitive storage on device** | `DataStore` (plaintext-on-device) | `EncryptedSharedPreferences` + Android Keystore |
| **Auth between app and backend** | Single `API_TOKEN` bearer | Unchanged. Bearer remains as transport ACL. |

### Architecture decisions

- **ADR-1**: Keep the backend; do not go phone-direct for IBKR. The retail IBKR API still requires a Java Gateway; there is no documented username/password endpoint we can call from a phone.
- **ADR-2**: Longbridge moves to the client. The Java SDK is Android-friendly (assuming the spike confirms); per-user credentials end the shared-developer-key anti-pattern.
- **ADR-3**: IBKR is one-at-a-time, not concurrent. Switching IBKR accounts restarts the Gateway container; ~30 second cost is acceptable for ≤3 users. Concurrent mode deferred.
- **ADR-4**: Backend never persists IBKR credentials. Used to start the container, dropped from memory immediately.
- **ADR-5**: Account types are independent. A user can have one IBKR account and zero Longbridge accounts (positions only, no charts), or vice versa.
- **ADR-6**: Existing bearer token stays as transport auth. JWT / Tailscale enforcement deferred.

### In scope

- Two independent account managers (IBKR + Longbridge) with add / edit / delete / set-active.
- Per-user IBKR login via app, with biometric unlock gate.
- Per-user Longbridge auth (paste 3 tokens: App Key, App Secret, Access Token).
- One IBKR session active at a time on the backend; switching restarts the Gateway with new creds.
- Either account type can run alone.
- Backend keeps single `API_TOKEN` bearer for transport auth.
- `/quote` and `/bars` removed from backend; charts served by in-app SDK.

### Out of scope (deferred)

- Concurrent multi-IBKR sessions (multiple Gateway containers).
- Longbridge OAuth flow (one public app + per-user authorize). Currently users paste their own developer tokens.
- Longbridge token auto-renewal (90-day expiry); user re-pastes when it expires.
- Cross-device sync (Google Drive / Android Backup).
- iOS client.
- Trading endpoints. v2 remains read-only.

### Implementation order

Eight phases, gated by the Longbridge SDK Android spike.

1. **Spike** — Confirm the Longbridge Java SDK ships an Android-compatible AAR with arm64-v8a native libs. **Hard gate**: if it fails, v2 collapses back to "multi-IBKR-account only" with Longbridge staying server-side.
2. **Backend refactor** — Credential-free Gateway management via `docker-py`. New `/ibkr/{login,logout,status}` endpoints. Remove `/quote`, `/bars`, `app/longbridge.py`, `docker-compose.yml`.
3. **Account storage layer (Android)** — `AccountStore` using `EncryptedSharedPreferences`, separate active pointers, v1→v2 migration that seeds a "default" IBKR account from the old `SettingsStore` if present.
4. **Longbridge SDK wrapper (Android)** — `LbSdk` singleton, swappable on account change, coroutine-friendly `quote()` and `bars()` wrappers.
5. **Account management UI** — Restructured 我的 tab: two sections, list rows, login screens, 2FA progress screen.
6. **App-wide plumbing** — Wire `AccountStore` and `LbSdk` through `IbkrApp`; auto-login on app start if a saved active account exists.
7. **Documentation** — Update `BUILD.md`, `ONBOARDING.md`, write a v1 → v2 migration note.
8. **Validation** — Manual test matrix: fresh install, IBKR-only, LB-only, switching, deletion, offline, backend unreachable.

Detailed task breakdown: [`openspec/changes/multi-user-v2/tasks.md`](openspec/changes/multi-user-v2/tasks.md).

### Risks

| Risk | Mitigation |
|---|---|
| Longbridge Java SDK lacks Android arm64-v8a native libs | Spike before any other work; fall back to keeping LB on backend if it fails. |
| `docker-py` missing features used by `docker-compose.yml` (healthchecks, restart policy) | Reimplement in Python — health = TCP probe to `127.0.0.1:4001`; restart = signal handler re-creating the container. |
| IBKR 2FA blocks backend if user dawdles | `POST /ibkr/login` returns immediately with a `login_id`; app polls `/ibkr/status`. 5-minute timeout, then retry. |
| Encrypted account store corrupt on Keystore reset (factory reset, biometric template change) | On decrypt failure, wipe and force re-add. Don't try to recover. |
| User uninstall → all accounts lost | Documented. Cross-device sync is explicitly out of scope. |

---

## Beyond v2

Nothing committed. Likely candidates for v3, in rough order of usefulness:

- **Concurrent IBKR sessions** — multiple Gateway containers fronted by a session router. Required for family-of-three with overlapping market hours.
- **Longbridge OAuth** — one publicly-registered app, users authorize via web; we manage refresh.
- **iOS client** — shared backend, fresh Swift / SwiftUI app.
- **Write trading endpoints in v2** — currently read-only by choice; needs an explicit order-confirmation UX before unlocking.
- **Cross-device sync** — encrypted backup of `AccountStore` to Google Drive or Android cloud backup.

---

## Contributing

The roadmap is shaped by personal need, not requests. Bug reports welcome; feature PRs welcome if they're in line with what's already here. For anything bigger, open an issue first to discuss before writing code.
