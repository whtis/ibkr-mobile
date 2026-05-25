# ibkr-mobile Onboarding

Operational manual for getting this back up and running, and avoiding the traps that already burned a day.

For *architecture* see [`BUILD.md`](BUILD.md). This doc is for *operations*: restart, diagnose, recover.

---

## TL;DR — daily restart (Mac reboot / sleep wake)

```bash
# 1. Make sure Docker Desktop is running (whale icon green in menu bar).
#    If not: open -a "Docker Desktop"  ← note: app name has a space, NOT "Docker.app"
open -a "Docker Desktop"

# 2. Start IB Gateway (auto-reconnects, 2FA push will hit IBKR Mobile)
cd /Users/tis/workspace/ibkr-mobile/backend
docker compose up -d

# 3. Approve the IB Gateway login push on IBKR Mobile App (within ~60s of start)
#    If you miss it, run `docker compose restart ibgateway` to retrigger.

# 4. Verify Gateway logged in
docker compose logs --tail=10 ibgateway   # look for: Configuration tasks completed

# 5. Start FastAPI (foreground; Ctrl-C to stop. Or use & + nohup for background)
uv run uvicorn app.main:app --host 0.0.0.0 --port 8000

# 6. Smoke test from another terminal
TOKEN=$(grep ^API_TOKEN /Users/tis/workspace/ibkr-mobile/backend/.env | cut -d= -f2)
curl -s http://localhost:8000/health | jq                                    # ib_connected: true
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8000/account/summary | jq
```

Open Android app → 持仓 tab → pull down to refresh → live data shows up.

---

## Configuration that you need to know lives where

| Thing | Where | How to rotate |
|---|---|---|
| Live IBKR user (`whtis5966`) | `backend/.env` `TWS_USERID` | IBKR Client Portal → Settings → Change Password |
| Live IBKR password | `backend/.env` `TWS_PASSWORD` (single-quoted) | same |
| FastAPI bearer token | `backend/.env` `API_TOKEN` | `openssl rand -hex 32` then paste into .env AND Android Settings tab |
| Mac LAN IP | `ipconfig getifaddr en0` | n/a, changes with Wi-Fi/router DHCP |
| Tailnet name | Tailscale Mac app shows it | n/a |

**`.env` rules**:
- Always single-quote the password: `TWS_PASSWORD='myP@ss$word'` — Docker Compose eats `$xxx` as variable substitution otherwise (we lost an hour to this).
- After editing, `docker compose up -d` will recreate the container (so the new env takes effect).

---

## First-time machine setup (one-time, already done on this Mac)

- macOS, Apple Silicon
- Docker Desktop (`brew install --cask docker-desktop` or download from docker.com)
- Java 17+ for Gradle (you have JDK 23, fine)
- Android Studio + Android SDK
- `uv` (`brew install uv`)
- Tailscale Mac app (only if you want to use the App outside home Wi-Fi)

Repo bootstrap:
```bash
cd backend
cp .env.example .env             # fill TWS_USERID/PASSWORD, generate API_TOKEN
docker compose pull              # pulls gnzsnz/ib-gateway:stable
uv sync                          # installs Python deps
```

Android side: open `android/` in Android Studio → File → Sync → Run on phone/emulator.

---

## Network access

### At home (same Wi-Fi as Mac)

App settings:
- Backend URL: `http://<Mac LAN IP>:8000` — get it with `ipconfig getifaddr en0`
- API Token: contents of `.env` `API_TOKEN`

### Outside (4G / different Wi-Fi)

Install Tailscale on Mac AND phone, sign in to same account. Then:
- Backend URL: `http://<mac-host>.<tailnet>.ts.net:8000`

Tailscale handles NAT and gives you a stable hostname. No router config needed.

---

## Pitfalls we hit (and you will again if you don't read this)

### Docker

| Symptom | Cause | Fix |
|---|---|---|
| `open -a Docker` does nothing | Docker rebranded the app to "Docker Desktop.app" | Use `open -a "Docker Desktop"` (quoted, with space) |
| `osascript` process hanging, daemon won't start | First-run requesting admin password for `vmnetd` | Look for the system dialog hidden behind windows, type your Mac password |
| Healthcheck `unhealthy` but Gateway working | gnzsnz container doesn't ship `nc` for healthcheck | Ignore. Test actual port with `nc -zv 127.0.0.1 4001` from Mac |
| `docker compose pull: no configuration file` | You're in the wrong directory | `cd backend/` first |

### Gateway / IBC / IBKR auth

| Symptom | Cause | Fix |
|---|---|---|
| "Invalid username or password" but browser login works | TWS/Gateway auth backend lags Client Portal by up to 24h after password reset | Wait, or switch to live mode (live doesn't have this lag) |
| "The specified user has multiple Paper Trading users associated" | Master account has multiple paper sub-users; you need to login as the specific paper user (e.g. `<yourname>-paper`), not the master | Use the paper-trading username from Client Portal → Settings → Paper Trading Account |
| Login progresses but stuck on dialog titled just "Gateway" with no obvious error | IBC didn't recognize the dialog. VNC in to read it. | `open vnc://localhost:5900` (password = `.env` `VNC_SERVER_PASSWORD`) |
| Gateway hangs at "Enter Mobile Authenticator app code" | Account uses TOTP code 2FA; IBC can't type the code | Switch IBKR Mobile to "IB Key" push 2FA + enable Permanent Session |
| Connection drops immediately after connecting (ib_async logs `Connected` then `Disconnected` in <10ms) | Gateway sees connection coming from a non-127.0.0.1 IP (Docker NAT) | Use socat port (host:4001 → container:4003, NOT container:4001). Already fixed in `docker-compose.yml` |
| Account locked for ~30 min after 5 failed login attempts | Normal IBKR security | Stop trying. Wait 30 min. |
| `TWS_PASSWORD=ab$cd` resolves to `ab` in container | Docker Compose substitutes `$cd` as an env var | Wrap password in single quotes: `'ab$cd'` |

### Android / Gradle

| Symptom | Cause | Fix |
|---|---|---|
| "Cannot select root node 'debugRuntimeClasspathCopy' as a variant" | AS bundled `gradle-9.0-milestone-1` (preview) which AGP 8.7.x doesn't support | `gradle/wrapper/gradle-wrapper.properties` pins `gradle-8.10.2`. After change: File → Sync Project |
| `Unresolved reference 'FontFeature'` etc. | Bogus imports that don't exist in Compose; `fontFeatureSettings = "tnum"` takes a string | Just delete the imports |
| `OutlinedTextField` + `TextFieldDefaults.colors(focusedBorderColor=)` fails | `focusedBorderColor` is on `OutlinedTextFieldDefaults.colors`, not `TextFieldDefaults` | Use the right defaults helper |

### Networking from phone

| Symptom | Cause | Fix |
|---|---|---|
| Test connection times out on home Wi-Fi | Mac firewall blocking uvicorn, or router AP isolation | Check System Settings → Network → Firewall; or use Tailscale to bypass |
| Works on Wi-Fi but not on 4G | LAN IP isn't reachable from cellular | Install Tailscale, change URL to tailnet name |

---

## Quick diagnostics (run when something's off)

```bash
# 1. Is Docker daemon up?
docker info | head -3

# 2. Is Gateway container running and listening on 4001?
docker compose ps
nc -zv 127.0.0.1 4001                  # should print "succeeded"

# 3. Did Gateway log in successfully (no error dialog)?
docker compose logs --tail=20 ibgateway | grep -iE "Configuration tasks completed|invalid|error"

# 4. Is FastAPI alive?
ps aux | grep -v grep | grep "uvicorn app.main"
curl -s http://localhost:8000/health | jq

# 5. Does it actually talk to IBKR?
TOKEN=$(grep ^API_TOKEN /Users/tis/workspace/ibkr-mobile/backend/.env | cut -d= -f2)
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8000/account/positions | jq 'length'

# 6. Is the phone reaching the Mac?
# On phone (Termux or similar), or simulate from Mac as if it were the phone:
curl -s -m 5 http://$(ipconfig getifaddr en0):8000/health | jq

# 7. VNC into Gateway to see GUI state (any unhandled dialogs)
open vnc://localhost:5900
# password = .env VNC_SERVER_PASSWORD
```

---

## Current production state

| Field | Value |
|---|---|
| Trading Mode | **LIVE** (real account U17337821, ~$25k) |
| API Mode | **READ-ONLY** (Gateway physically blocks order placement) |
| Gateway image | `ghcr.io/gnzsnz/ib-gateway:stable` |
| 2FA | IBKR Mobile push (must approve on phone on each Gateway start) |
| Auto restart | Daily 23:59 local time (set by `AUTO_RESTART_TIME` in docker-compose) |
| Weekly auth refresh | Sunday ~01:00 ET — manual 2FA approve required |

To switch to paper (after 24h+ since last password reset):
1. Edit `.env`: change `TWS_USERID` / `TWS_PASSWORD` to your paper credentials
2. Edit `docker-compose.yml`: `TRADING_MODE: paper`, port `127.0.0.1:4002:4003`, healthcheck port `4003`
3. Edit `.env`: `IB_PORT=4002`
4. `docker compose down && docker compose up -d`

To enable real order placement (DANGER — only do this once you trust the code):
1. Edit `docker-compose.yml`: `READ_ONLY_API: "no"`
2. `docker compose down && docker compose up -d`
3. Approve 2FA again

---

## What's left in v0 (and what's deferred)

Done:
- Backend: account summary + positions with market price/value/unrealized PnL
- Android: 5-tab skeleton, 持仓 + 我的 tabs functional
- Pull-to-refresh, colored PnL, sec-type badge, error states with retry

Deferred to v1+:
- 自选股 (watchlist) — placeholder tab
- 市场 (market overview) — placeholder tab
- 个股详情页 + K 线图
- WebSocket real-time quote stream
- Order placement (and you must flip READ_ONLY_API=no, dangerous)
- FCM push notifications on order fills / price alerts
- Home screen widget (Glance API)

---

## Sanity-check before every "did this break?" question

```bash
# Run these 3 commands. If all green, the backend is fine — problem is in the app or network.
docker compose ps                                         # ibkr-gateway should be Up
curl -s http://localhost:8000/health | jq .ib_connected   # should print "true"
TOKEN=$(grep ^API_TOKEN backend/.env | cut -d= -f2)
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8000/account/summary | jq 'length'   # should print 1 or 2
```
