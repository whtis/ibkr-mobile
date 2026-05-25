# Tasks: multi-user-v2

## 1. Spike — Longbridge Java SDK on Android

- [ ] 1.1 Find the official Maven groupId/artifactId for Longbridge OpenAPI Java SDK (check open.longportapp.com docs)
- [ ] 1.2 Add the dependency to `android/app/build.gradle.kts`
- [ ] 1.3 Inspect the AAR's `jniLibs/` to verify it ships `arm64-v8a/libxxx.so`
- [ ] 1.4 Write a one-shot test in `MainActivity.onCreate` that constructs `QuoteContext` with hard-coded test creds and calls `quote(["AAPL.US"])`
- [ ] 1.5 Run on a real Android device, log the result
- [ ] 1.6 **Gate**: if the SDK does NOT work on Android, stop here and re-plan. If it works, remove the test code and continue.

## 2. Backend — credential-free Gateway management

- [ ] 2.1 Add `docker>=7` to `pyproject.toml`; `uv sync`
- [ ] 2.2 Create `app/docker_gateway.py` with `start(username, password, mode="live", read_only=True) -> ContainerHandle` and `stop()`
- [ ] 2.3 The container start MUST inject `TWS_USERID`, `TWS_PASSWORD`, `TRADING_MODE`, `READ_ONLY_API`, `EXISTING_SESSION_DETECTED_ACTION=primary`, and the other env vars currently in `docker-compose.yml`
- [ ] 2.4 Refactor `app/ibkr.py` to accept a host:port and connect/disconnect on demand (remove the auto-connect-on-startup logic)
- [ ] 2.5 Create `app/routes/ibkr_auth.py` with three endpoints:
  - [ ] 2.5.1 `POST /ibkr/login {username, password}` → spawns container, returns `{login_id, status: "awaiting_2fa"}`
  - [ ] 2.5.2 `POST /ibkr/logout` → stops container, drops in-memory session
  - [ ] 2.5.3 `GET /ibkr/status` → `{connected: bool, account_id: str | null, login_state: str}`
- [ ] 2.6 Remove `app/routes/quote.py` and unregister it in `main.py`
- [ ] 2.7 Remove `app/longbridge.py` and the `longport` dependency
- [ ] 2.8 Remove `TWS_USERID`, `TWS_PASSWORD`, `LONGPORT_*` from `config.py` and `.env.example`
- [ ] 2.9 Delete `docker-compose.yml` (Gateway lifecycle is now Python-managed)
- [ ] 2.10 Update `backend/README.md` with the new "FastAPI is the only thing you start manually; Gateway is started by app login" flow
- [ ] 2.11 Curl test: `POST /ibkr/login` with real creds, watch `/ibkr/status` go to `connected: true`, hit `/account/positions`, then `POST /ibkr/logout`, confirm container removed

## 3. Android — account storage layer

- [ ] 3.1 Add deps: `androidx.security:security-crypto`, `androidx.biometric:biometric`
- [ ] 3.2 Define data classes: `IbkrAccount(id, label, username, encryptedPassword)`, `LbAccount(id, label, appKey, encryptedSecret, encryptedToken)`
- [ ] 3.3 Implement `AccountStore` using `EncryptedSharedPreferences` (one for each account type, list serialized as JSON)
- [ ] 3.4 Track `activeIbkrId` and `activeLbId` separately in plain DataStore
- [ ] 3.5 Add migration: on first launch after v2, read the v1 `SettingsStore` (if any IBKR creds were stored there) and seed `AccountStore` with one IBKR account labeled "default"
- [ ] 3.6 Unit-test encrypt → store → reload → decrypt

## 4. Android — Longbridge SDK wrapper

- [ ] 4.1 Create `data/longbridge/LbSdk.kt` as a singleton holding the active `QuoteContext`
- [ ] 4.2 `LbSdk.configure(account: LbAccount)` recreates `Config` and `QuoteContext` on a background thread; emits a `StateFlow<Boolean>` for "ready"
- [ ] 4.3 `LbSdk.quote(symbol: String): Quote` and `LbSdk.bars(symbol: String, period: String): List<Bar>` — coroutine-friendly suspending wrappers around the SDK's sync calls
- [ ] 4.4 Map IBKR-style `(symbol, currency)` to Longbridge `TSLA.US` form (same logic as backend `to_lb_symbol`)
- [ ] 4.5 Wire `StockDetailViewModel` to use `LbSdk.bars` instead of `IbkrApi.bars`
- [ ] 4.6 Wire 自选 / 市场 placeholder screens (next milestone, but lay the data-layer call site)

## 5. Android — account management UI

- [ ] 5.1 Restructure `SettingsScreen.kt` into a vertically sectioned list with: "IBKR 账户" section, "长桥账户" section, "设置" section
- [ ] 5.2 `IbkrAccountListScreen` — list rows show label + masked username + active checkmark; tap row to set active, swipe to delete
- [ ] 5.3 `LongbridgeAccountListScreen` — similar, shows label + masked App Key prefix + active checkmark
- [ ] 5.4 `IbkrLoginScreen` (used both for add-new and edit) — username + password fields, "保存并登录" button
- [ ] 5.5 `LongbridgeLoginScreen` — App Key + App Secret + Access Token paste-three fields, "保存" button (Longbridge has no live-login step, just stash creds and let `LbSdk` use them)
- [ ] 5.6 `IbkrLoginProgressScreen` — shown after the user picks/saves an IBKR account; polls `GET /ibkr/status` every 2 seconds; shows "请在 IBKR Mobile 上 Approve 推送", times out at 5 minutes with retry button
- [ ] 5.7 Biometric gate: when the user first launches the app after a saved account exists, prompt biometric to unlock; on success, proceed to the default tab
- [ ] 5.8 Empty states:
  - [ ] 5.8.1 持仓 tab when no active IBKR account → "添加 IBKR 账户" CTA → IbkrLoginScreen
  - [ ] 5.8.2 自选 / 市场 / stock detail when no active Longbridge account → "添加长桥账户" CTA → LongbridgeLoginScreen
  - [ ] 5.8.3 持仓 tab when IBKR account exists but Gateway is not connected → "正在连接 IBKR…" banner with status detail

## 6. Android — app-wide plumbing

- [ ] 6.1 Update `IbkrApp.kt` to instantiate `AccountStore`, `LbSdk`, and provide them via App-scoped DI (keep manual DI; no Hilt)
- [ ] 6.2 Replace any direct reads of "the IBKR account" with `AccountStore.activeIbkrAccount()`
- [ ] 6.3 At app start: if there's a saved active IBKR account, automatically POST `/ibkr/login` to wake the Gateway and show the progress screen
- [ ] 6.4 At app start: if there's a saved active Longbridge account, call `LbSdk.configure(account)` to make quote/bars APIs ready

## 7. Documentation

- [ ] 7.1 Update `BUILD.md` architecture diagram + tradeoff table
- [ ] 7.2 Update `ONBOARDING.md` daily restart section — Gateway is started by app, not by the user
- [ ] 7.3 Add a "Migrating from v1" subsection to `BUILD.md`
- [ ] 7.4 Move v2-relevant sections of `DESIGN_NOTES.md` into the shipped spec, leave only the genuinely deferred items
- [ ] 7.5 Add an entry to `openspec/changes/archive/YYYY-MM-DD-multi-user-v2/` after merge

## 8. Validation

- [ ] 8.1 Manual: install fresh APK on a phone with no accounts → 自选 / 持仓 both show empty state CTAs
- [ ] 8.2 Manual: add an IBKR account → 2FA flow → 持仓 loads
- [ ] 8.3 Manual: switch to a second IBKR account → first session terminates, second comes online, 持仓 reloads with new numbers
- [ ] 8.4 Manual: add a Longbridge account → 自选 placeholder dataset loads → click into a stock → K-line draws using in-app SDK
- [ ] 8.5 Manual: delete the active IBKR account → 持仓 falls back to empty state
- [ ] 8.6 Manual: turn off Wi-Fi and re-open app → both tabs show offline banner gracefully
- [ ] 8.7 Manual: kill Mac backend → 持仓 tab shows "backend unreachable", but 自选 / K-line continue working (Longbridge is in-app)
