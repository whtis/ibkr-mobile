# android

Kotlin + Jetpack Compose. v0 = 5-tab skeleton + Settings + Positions wired to backend.

## Open in Android Studio

```
File → Open → /Users/tis/workspace/ibkr-mobile/android
```

First time Android Studio will:
1. Auto-generate the Gradle wrapper (`gradlew`, `gradle-wrapper.jar` etc.)
2. Sync dependencies (~5 min first time)
3. Index the project

When sync finishes, run on a phone or emulator.

## v0 scope

- BottomBar with 5 tabs (`自选 / 市场 / 持仓 / 圈子 / 我的`)
- **持仓** (PositionsScreen) — calls `/account/summary` + `/account/positions`, shows account net liquidation and position list
- **我的** (SettingsScreen) — configure backend URL + API token, "测试连接" button hits `/health`
- Other tabs = placeholders for future versions

Default landing tab = 持仓. Adjust in `RootScreen.kt` if you prefer Settings first run.

## First-run setup on the phone

1. Connect phone and Mac to same Wi-Fi
2. Find Mac LAN IP: `ipconfig getifaddr en0` on Mac
3. In app → "我的"
   - Backend URL: `http://192.168.x.x:8000`
   - API Token: same value as `backend/.env` `API_TOKEN`
   - 保存
   - 测试连接 → should show "OK · IB connected = true"
4. Switch to 持仓 tab → 应该看到 paper 账户净值

## Module layout

```
app/src/main/java/com/tis/ibkr/
├── MainActivity.kt              # Activity entry
├── IbkrApp.kt                   # Application + DI singletons (settings + api)
├── data/
│   ├── api/
│   │   ├── IbkrApi.kt           # Ktor client
│   │   └── Models.kt            # @Serializable DTOs
│   └── store/SettingsStore.kt   # DataStore for url + token
├── viewmodel/
│   ├── PositionsViewModel.kt
│   └── SettingsViewModel.kt
└── ui/
    ├── RootScreen.kt            # Scaffold + bottom nav
    ├── nav/Routes.kt            # Tab enum
    ├── theme/                   # 长桥风格深色 tokens
    │   ├── Color.kt
    │   ├── Theme.kt
    │   └── Type.kt
    ├── components/Numeric.kt    # tabular-nums text + price formatters
    └── screens/                 # 5 tabs (2 real, 3 placeholder)
```

## Design tokens (Longbridge-inspired)

| token | hex | usage |
|---|---|---|
| Background | `#0E1117` | scaffold background |
| Surface | `#161B22` | cards, nav bar |
| SurfaceElevated | `#1F2937` | active nav, dialogs |
| Outline | `#2D3748` | borders, dividers |
| Up | `#FF4D4F` | 涨（红） — Chinese convention default |
| Down | `#00C087` | 跌（绿） |
| Accent | `#3B82F6` | primary buttons, focus |

涨绿跌红 vs 涨红跌绿 配色切换将在 v1 加入 Settings 里。

All numeric text uses `fontFeatureSettings = "tnum"` to prevent digit width jumping during real-time refresh. See `ui/components/Numeric.kt`.

## Known v0 limitations

- No HTTPS — backend served over plain HTTP on LAN/Tailscale. Fine for personal use; do not expose to public internet.
- No biometric auth on app open (planned for v3 when orders ship).
- No automatic reconnect / refresh on resume — pull to refresh comes in v1.
- Quote endpoint is wired in `IbkrApi` but no UI yet — used in v2.
