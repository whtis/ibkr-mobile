# FCM relay (Cloudflare Worker)

The backend runs on a NAS in mainland China and can't reach Google. This Worker
sits on Cloudflare's edge, holds the Firebase service-account credentials, and
forwards pushes to FCM. The backend POSTs `{ token, title, body, data }` here
with a shared secret.

## Deploy

```bash
cd fcm-relay-worker
npm i -g wrangler        # if you don't have it
wrangler login

# Secrets (values come from backend/firebase-sa.json):
wrangler secret put FCM_CLIENT_EMAIL   # paste the "client_email"
wrangler secret put FCM_PRIVATE_KEY    # paste the whole "private_key" PEM (with -----BEGIN/END----- lines)
wrangler secret put RELAY_SECRET       # pick a long random string; the backend uses the same value

wrangler deploy
```

`FCM_PROJECT_ID` is set in `wrangler.toml` (`[vars]`) — change it if your Firebase
project id isn't `bulltap`.

### Make it reachable from China

`*.workers.dev` is often blocked in China. Put the Worker on your own Cloudflare
domain by uncommenting the `routes` line in `wrangler.toml`, e.g.:

```toml
routes = [{ pattern = "fcm.your-domain.com/*", zone_name = "your-domain.com" }]
```

(Add a proxied DNS record for `fcm` if there isn't one.)

## Wire the backend

Put the Worker URL + the same `RELAY_SECRET` in `backend/.env`:

```
FCM_RELAY_URL=https://fcm.your-domain.com
FCM_RELAY_SECRET=<the RELAY_SECRET you chose>
```

Then restart the backend. `notify.send_to_all()` will POST through the Worker
instead of calling Google directly.

## Test

```bash
curl -X POST https://fcm.your-domain.com \
  -H "Authorization: Bearer <RELAY_SECRET>" \
  -H "Content-Type: application/json" \
  -d '{"token":"fake","title":"t","body":"b"}'
# -> {"ok":false,"unregistered":true,...}  (auth + FCM reachable; token just invalid)
```

A real device token returns `{"ok":true}` and the phone shows the notification.
