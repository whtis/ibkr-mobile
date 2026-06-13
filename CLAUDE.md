# CLAUDE.md — working rules for this repo

This is a **public, open-source** repository. Anything committed (file contents,
commit messages, and tags) is world-readable and stays in git history.

## Security red line — never commit personal / sensitive info

Do **not** put any of the following into code, docs, comments, commit messages,
or tags. Use placeholders instead (`<account>`, `<your-ibkr-username>`,
`your-domain.com`, `<repo>`, `<RELAY_SECRET>`):

- **Real IBKR account numbers** (e.g. `U########`) or balances
- **Real IBKR usernames / passwords**, API tokens, HMAC keys
- **Private domains / hostnames / IPs** (the user's own domains, Tailscale IPs, NAS hostnames)
- **Local absolute paths** that leak a username (`/Users/<name>/…`, `/vol1/<uid>/…`) — use relative paths or `<repo>`
- **Credential files**: `.env`, `google-services.json`, `firebase-sa.json`, keystores, service-account JSON, private keys

If you need a concrete value to illustrate something, invent an obviously-fake
placeholder. When in doubt, leave it out and ask.

Secrets live only in gitignored files (`.env`, `android/app/google-services.json`,
`backend/firebase-sa.json`) and in deploy-time secret stores (systemd env,
Cloudflare Worker secrets) — never in the repo. Before committing, scan the diff
for the patterns above.

## Conventions

- **Backend** (`backend/`): FastAPI + ib_async; config via `backend/.env`
  (see `.env.example`). Device auth is HMAC-signed per request (`app/auth.py`).
- **`.env` passwords with `$`**: single-quote them and escape `$` as `$$` —
  Docker Compose interpolates `${...}` otherwise (this silently truncates the
  password). See `ONBOARDING.md`.
- **Android** (`android/`): Kotlin + Compose, version catalog
  (`gradle/libs.versions.toml`). FCM needs `android/app/google-services.json`
  (gitignored) to build.
- **Versioning / releases**: a pushed `v*` tag drives the signed release build
  (`.github/workflows/android-release.yml`); `versionName` / `versionCode` are
  parsed from the tag. Only bump `X.Y.Z` when explicitly asked.
- **Push notifications**: the China-hosted backend can't reach Google, so FCM is
  sent through a Cloudflare Worker relay (`fcm-relay-worker/`).
