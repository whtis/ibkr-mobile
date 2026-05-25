# Contributing to ibkr-mobile

Thanks for your interest. This is a personal project and will stay that way — the roadmap is driven by what the owner needs, not by a backlog of community requests. That said, well-scoped pull requests are genuinely welcome, and a good contributor experience matters.

Read this document before opening an issue or PR.

---

## Welcome — and a few honest words

ibkr-mobile is a personal Android trading client, not a community platform. The owner ([@whtis](https://github.com/whtis)) uses it daily against a real brokerage account, which means:

- Bugs are taken seriously. Please report them.
- Code quality and safety matter more than feature count.
- PRs that touch the trading path go through a higher bar of review.
- **For any non-trivial change, open an issue first.** Write code after the direction is agreed on, not before. This saves everyone time.

Contributions aligned with [`ROADMAP.md`](ROADMAP.md) are most likely to land. The roadmap is shaped by personal need; it is not request-driven.

---

## Dev setup

### Backend (Python + FastAPI)

Full setup is in [`backend/README.md`](backend/README.md). Short version:

```bash
cd backend
cp .env.example .env
# Edit .env — see backend/README.md for required fields
uv sync
uv run uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

**Don't have an IBKR or LongPort account?** Set `MOCK_MODE=yes` in your `.env`. Mock mode returns synthetic positions, quotes, and K-line data so you can run the entire stack locally without any brokerage credentials. This is the recommended way to get started.

```bash
# .env (minimum for mock mode)
MOCK_MODE=yes
API_TOKEN=any-token-you-choose
```

With mock mode running:

```bash
TOKEN=any-token-you-choose
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8000/health | jq
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8000/account/positions | jq
```

### Android app

```bash
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open Settings → fill in `http://<your-mac-ip>:8000` as the backend URL and the `API_TOKEN` value. With mock mode the connection test should go green immediately.

See [`ONBOARDING.md`](ONBOARDING.md) for Android / Gradle troubleshooting and the full list of backend environment variables.

---

## What kind of PRs we love

- **Bug fixes** — with a clear reproduction case.
- **UI polish** that matches [`DESIGN_NOTES.md`](DESIGN_NOTES.md) — spacing, typography, Longbridge-inspired conventions.
- **Accessibility** — TalkBack support, content descriptions, larger font scaling, minimum touch-target sizes.
- **Internationalization** — extracting hardcoded Chinese string literals into `res/values/strings.xml` (zh-rCN default) and `res/values-en/strings.xml`, so the app is usable for non-Chinese speakers.
- **Tests** — unit tests for ViewModel logic, backend route smoke tests.
- **Performance** — chart rendering, list scroll jank, coroutine/flow hot path improvements.
- **Documentation** — clarifications, typo fixes, missing setup steps.

---

## What you'll probably get pushback on

- **Broad refactors without prior discussion.** "This could be cleaner" is not a PR description.
- **New external dependencies without clear justification.** Every new library is a maintenance burden and a supply-chain risk. If you're adding one, explain why no existing dependency covers it.
- **Backwards-compat scaffolding.** This project explicitly avoids compatibility shims, versioned interfaces, and migration adapters for internal code paths. The app targets a single owner's device; compatibility layers add complexity without payoff.
- **Features that don't fit the roadmap.** Check [`ROADMAP.md`](ROADMAP.md) before building.
- **Paid third-party services.** The stack uses free tiers (LongPort L1 developer account, IBKR paper). PRs that introduce paid APIs will not merge.
- **Architecture decisions.** The stack choices in [`BUILD.md`](BUILD.md) are intentional. Proposing Retrofit over Ktor or React Native over Compose will be closed without extended debate.

---

## Code style

### Kotlin

- Follow `kotlin.code.style=official` (enforced by the root `gradle.properties`).
- Use coroutines and `StateFlow` / `SharedFlow` for async. No callbacks.
- Comment the **why** of non-obvious decisions, not the what. `// increment counter` is noise; `// IBKR drops the connection after 15 min idle; we ping 1s before` is useful.
- Color convention: **红涨绿跌** (red = up / gain, green = down / loss). This is not a bug. Do not invert it.

### Python

- Code must be compatible with `ruff` formatting and `black` line length (88).
- Type hints on all public function signatures.
- Same comment philosophy as Kotlin: why, not what.
- `uv` for dependency management; add to `pyproject.toml`, not a bare `requirements.txt`.

---

## Commit and PR conventions

- **Small, focused commits.** One logical change per commit.
- **Imperative present tense** with a reason: `Fix positions sort crashing on empty list` not `Fixed bug`.
- **Keep PR descriptions short.** Summary + linked issue + what changed. Skip the autobiography.
- PRs are **squash-merged**. Your individual commits don't persist in main, so polish them for your own review clarity, not for posterity.

---

## How to test your changes

**Backend:** Run the smoke tests in [`backend/README.md`](backend/README.md) with `MOCK_MODE=yes`. The test set covers health, positions, quotes, and WebSocket connection.

**Android:** Build with `./gradlew assembleDebug` and install via `adb`. Verify on a real device or emulator (Pixel-equivalent API 26+ emulator is fine). If you changed chart rendering, test with both landscape and portrait.

**Neither requires real brokerage credentials** when mock mode is on.

---

## Reporting bugs

Use the [bug report issue template](.github/ISSUE_TEMPLATE/bug_report.yml).

Include: app version + build, backend git SHA, device + Android version, and the relevant logcat/backend log snippet. The clearer the reproduction case, the faster the fix.

**Security issues that could affect users' money or credentials** — do not open a public issue. Contact the maintainer directly via the contact details on the [GitHub profile](https://github.com/whtis).
