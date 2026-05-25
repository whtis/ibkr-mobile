# backend

FastAPI + ib_async, talks to IB Gateway running in Docker.

## First-time setup

```bash
cd backend

# 1. Create your .env
cp .env.example .env
# then edit:
#   TWS_USERID=<your paper username>
#   TWS_PASSWORD=<your paper password>
#   API_TOKEN=$(openssl rand -hex 32)

# 2. Pull and start Gateway
docker compose pull
docker compose up -d

# 3. Wait ~30s, then check the Gateway logs to confirm login
docker compose logs -f ibgateway
# look for: "API server listening on port 4002"

# 4. Install Python deps (only first time)
uv sync

# 5. Run FastAPI
uv run uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

## Smoke test

```bash
TOKEN=$(grep ^API_TOKEN .env | cut -d= -f2)

# unauthenticated
curl -s http://localhost:8000/health | jq

# authenticated
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8000/account/summary | jq
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8000/account/positions | jq
curl -s -H "Authorization: Bearer $TOKEN" 'http://localhost:8000/quote/AAPL' | jq
```

`/health` should show `ib_connected: true` once Gateway is up.

## Allowing the phone in (LAN)

The FastAPI server binds `0.0.0.0:8000`, so any device on your home Wi-Fi can reach it.
Find the Mac's LAN IP:

```bash
ipconfig getifaddr en0
# e.g. 192.168.1.42
```

Then on the phone, point the app to `http://192.168.1.42:8000` with the same `API_TOKEN`.

For access outside home Wi-Fi, install Tailscale on both Mac and phone, then use the Mac's tailnet name (e.g. `http://<host>.tailnet-xxxx.ts.net:8000`).

## Gateway debugging via VNC

If something is wrong with the login (2FA prompt, security question, etc.), VNC into the container:

```
vnc://localhost:5900
# password = VNC_SERVER_PASSWORD from .env
```

You'll see the Gateway GUI and can resolve the prompt manually.

## Stopping everything

```bash
# stop FastAPI: Ctrl-C
docker compose down       # stops Gateway
docker compose down -v    # stops + wipes Gateway state (rare)
```

## Common issues

| Symptom | Likely cause | Fix |
|---|---|---|
| `/health` shows `ib_connected: false` | Gateway not ready or login failed | `docker compose logs -f ibgateway`; check `.env` credentials |
| `accountSummaryAsync()` returns empty | Connected with wrong clientId, or paper account just created | Restart backend; check `IB_CLIENT_ID=10` not used elsewhere |
| Quote returns all `null` | Outside US market hours + no delayed data | Add `ib.reqMarketDataType(3)` for delayed-frozen, or query during market hours |
| Cannot connect from phone | Mac firewall | System Settings → Network → Firewall → allow incoming for uvicorn |
| Connection drops Sunday morning | IBKR weekly token reset (expected) | Open IBKR Mobile app, approve 2FA push, Gateway auto-resumes |
