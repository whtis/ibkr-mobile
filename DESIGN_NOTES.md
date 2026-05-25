# Design Notes — Longbridge Reference

User-shared real Longbridge app screenshots. Used as the source of truth for what each page should look like — when in doubt, refer here, **don't improvise**.

Screenshots live in `~/.claude/image-cache/046a2b9f-289a-46e8-8a16-08297c9f0e6b/` (16=持仓, 17=自选, 18=下单, 19=个股详情) for now. Move into the repo if needed for permanence.

---

## Global color convention

**红涨绿跌** (Chinese — red up, green down).

| Token | Hex | Used for |
|---|---|---|
| Up | `#FF4D4F` / 橙红 `#FF6D34` | gain (price up, positive PnL) |
| Down | `#00C087` / 青绿 | loss (price down, negative PnL) |
| Flat | `#9CA3AF` | unchanged |

In the watchlist, the % column has **solid color block backgrounds** (lighter shade of up/down), not just colored text.

In positions, PnL is colored **text only**, no block.

---

## 持仓 / 资产 page (screenshot 16) — v0 implemented

### Header
```
🏛  长桥综合账户(9763) ⇋                              [🔍] [🔔 10]

总资产(USD) ⓘ            [👁]                       当日盈亏 ↗
21,507.75                                            -204.13

持仓总市值        持仓总盈亏 (green)      现金
6,076.16          -5,936.32              15,431.59
─────────────────────────────────────────────────
```

Key details:
- Account name on top with **switch icon ⇋** (multi-account switcher — n/a for us, only one account)
- "总资产(USD) ⓘ" — currency in label, ⓘ tooltip
- **Cents in subscript size** (`.75` smaller than `21,507`)
- Eye icon to hide all numbers (defer to v1)
- 当日盈亏 in top right, just amount with arrow icon, no %
- 3-column stats row below — labels muted, values bold

### Quick actions row (skip)
```
存入资金   持仓日报   资金记录   交易大厅   全部功能
```
App-level features (deposit, daily report, transactions, trade hall, all). **Not in scope** for our project.

### Promo banner (skip)

### Sub-tabs
```
持仓分布   当日订单(0/6)   策略订单                  [⚙]
```
We only have `持仓分布`. Skip the sub-tabs UI until we add order management.

### Per-market section
```
🇭🇰 港股 45,803.49 ⌃
持仓市值       持仓盈亏       当日盈亏
27,549.00     -17,962.00     -1,475.00
─────────────────────────────────────────
名称/代码 │ 市值/数量 ⇅ │ 现价/成本 ⇅ │ 当日盈亏 ⇅
```
- Flag emoji + market name + total market value
- Collapsible (⌃)
- Sub-summary 3 columns: 持仓市值 / 持仓盈亏 / 当日盈亏
- Column headers with sort arrows (⇅)

### Position row layout
```
名称(truncated...)   市值          现价      当日盈亏 (colored)
代码 (gray)          数量 (gray)   成本 (gray)  当日盈亏% (colored)
```

Weights: roughly 2 : 1.5 : 1.3 : 1.5

Numbers:
- 市值 / 数量: 2 decimals; qty is integer or 4-decimal for fractional shares
- 现价 / 成本: **3 decimals** (e.g., 1.690, 81.140 — yes even when trailing zero)
- 当日盈亏: 2 decimals, signed
- 当日盈亏%: 2 decimals, signed, with `%` suffix

### Data we DON'T have yet
- **当日盈亏** (daily PnL per position & aggregate) — requires `ib.reqPnL()` subscription
- Currently substituted with **浮动盈亏 (unrealized PnL)** with relabeled column header

---

## 自选 page (screenshot 17) — v1 scope

### Top
```
自选  股单                                       [🔍] [🔔 10]
[港股] [沪深] [美股 SELECTED] [持仓] [ai相关股] [▼]
[⚙] [⎘] [⩃]                                       [✨ AI]
名称/代码         最新价 ⇅        涨跌幅 ⇅
```

- Tab pair `自选 / 股单` at top (watchlist / portfolio list)
- Filter chips horizontal: market filters + custom groups, last item is dropdown
- 3 small icons (settings / view-toggle / list-mode) on left, AI sparkle on right
- Column headers: name/code + sort, latest price + sort, change% + sort

### Row layout
```
名称(truncated...)   [mini sparkline]    最新价         [+涨跌幅%]   (colored block bg)
US TICKER (gray)                         盘前价 (gray)   盘前涨跌% 盘前 (small label)
```

Key details:
- Country prefix in code (e.g., "US AAOI", not just "AAOI")
- **Mini sparkline** chart in middle column — shows intraday line
- **Solid color block** behind 涨跌幅%, not just colored text. Down = green block, Up = red/orange block.
- "盘前" label as small chip next to pre-market change%
- 标普500波动率指数 (VIX) shown with **orange block** because +1.52% (up = orange in Chinese convention)

### Data we need for this
- Real-time quote stream (last, change, change%)
- **Intraday tick history** for sparkline (need historical 1-min K-line or accumulated streaming ticks)
- Pre-market quote (extended hours)

Backend additions needed (v1):
- `GET /watchlist` — list user-saved symbols
- `POST /watchlist/{symbol}`, `DELETE /watchlist/{symbol}`
- `GET /quote/{symbol}` (we have this, expand to include pre-market)
- WebSocket stream for live quotes

---

## 下单 page (screenshot 18) — v3 scope (DANGER: real orders)

### Top
```
<  CRCL Circle  EQ                                    [⚙]
🏛 长桥综合账户(H10099763)                            ⇋
111.390 -2.610 -2.29% (green)        🇺🇸 ⚡ 24H 👥 📊
盘前 110.990 -0.400 -0.36%
```

- Stock title with search shortcut next to it (`EQ` icon)
- Account row
- Real-time price + change + change% (green = down here)
- Pre-market line
- Right side icons: market flag, **⚡ 闪电单 (lightning order shortcut)**, 24H session toggle, depth icon, chart icon

### Order book bar
```
买盘 25.27% (orange) ════════│════════════ 卖盘 74.73% (green)
BBO 23  110.780                        111.050  68 BBO
[orange-tinted bg]                     [green-tinted bg]
```

- Visual ratio bar of bid/ask volume
- BBO (Best Bid/Offer) quantities and prices on each side
- Backgrounds tinted to match up/down colors

### Form fields
```
类型 ⓘ                    [限价单 ▼]

方向                      [买入 ORANGE SELECTED]    [卖出 GRAY]
价格                      [−]  110.99   [+]  [⊕ snap-to-market]
数量 ⇋                    [−]  最小买卖单位 0.0001  [+]  [≡ qty templates]
                          117 股 (orange)         融资最大可买  198 股 (orange)
                          现金可买
预估金额                                                              0.00 USD
                          预估成交后持仓成本 0.00 USD
有效期 ⓘ                  [当日有效 ▼]
时段 ⓘ                    [盘中 + 盘前盘后 ▼]
```

Order types: 限价单 / 市价单 / 止损市价 / 止损限价 / ELO / ALO / 拍卖盘 etc.
有效期: 当日有效 / 取消前有效(GTC) / 指定日期前有效(GTD)
时段: 仅盘中 / 盘中+盘前盘后

Quantity input has:
- −/+ steppers
- Placeholder shows **minimum trade unit** (`0.0001` for fractional CRCL)
- Right icon `≡` for quick-pick templates (1, 10, 100, 1k, max, half, etc.)
- Cash buyable + margin max-buyable shown below in orange

### Attached order section
```
附加订单 ⓘ
附加类型                                              [请选择 ▼]
```
For bracket orders (止盈 + 止损 子单).

### Bottom tabs
```
持仓   当日订单 (2/8)
```
Active orders + positions visible while ordering.

### Implementation notes for v3
- Order placement: `POST /orders` — must keep `READ_ONLY_API=no` toggle protected. Add biometric confirmation (Android BiometricPrompt).
- Real-time order book: WebSocket `/orderbook/{symbol}` streaming top of book
- Form: composable `OrderForm` with state for type/side/price/qty/tif/session/attached
- 闪电单 (lightning) = pre-configured one-tap order with default qty + market price
- Order confirmation dialog must show: side, qty, est cost, fees, **READ_ONLY warning if applicable**

---

## 个股详情 page (screenshot 19) — v2 scope

### Header
```
<  SATS 回声星通信                                    [♡ favorite] 9.7k
盘前交易中 05.19 美东
[行情 SELECTED] [全景] [财务]
```

- Symbol + Chinese name in title
- Trading status pill ("盘前交易中" / "交易中" / "已收盘")
- 3 sub-tabs: 行情 (quotes/chart) | 全景 (overview/business) | 财务 (financials)
- Heart icon to add to watchlist + watcher count

### Price section
```
136.450 -0.780 -0.57% (green)
创52周新高 (orange chip)
最高 147.252  今开 146.750  换手率 6.56%  市盈率TTM 亏损
最低 135.110  昨收 137.230  成交量 12.57亿  总市值(USD) 395.45亿
↓ 大笔卖出 1,000股  ↓                       15:40:49 美东
盘前 138.570 +2.120 (+1.55%)                 04:58:02 美东
```

Stats grid: 4 columns × 2 rows of key stats.
Special events ticker showing big block trades.
Pre-market quote at bottom.

### Chart section
```
[盘口 | 5日 | 日K | 周K | 月K | 年K | 1分 ▼ | ⋮]

[main candlestick chart with overlay indicators]    │ time-sales
                                                    │ list
─                                                   │ (right
[KDJ indicator subchart]                           │ panel)

[MACD | KDJ | RSI | VOL]              [逐笔明细 | 成交统计]
```

- Period selector: 盘口 (tick/depth view) / 多个 K 线周期
- Main chart can stack 1+ indicator subcharts
- Right panel: live tick-by-tick OR depth/order book
- Bottom indicator tabs

### 订单 section (your fills on this symbol)
```
订单                                                  查看历史交易 >
```
Shows your fills for this stock.

### 盘口 section (Level-1 depth)
```
盘口                                                  [⊟ depth+] ⓘ
买盘 62.50%  ═══════════│═══════ 卖盘 37.50%
BBO 5  138.000        138.880  3 BBO
```

### 资金流向 section
```
资金流向 │ 做空数据                                    [open in new]

成交统计 ⓘ                                            (单位: 万元)
[donut chart with bucket breakdown]
            净流入 4725.21
流入 20251.85  ███   流出 15526.64
  10475.83 大  ████  2383.80
   3489.90 中  ████  4771.69
   6286.11 小  ████  8371.16
```

Money flow grouped by trade size (大/中/小 = large/mid/small).
"实时资金流向" = intraday line chart of net flow.
"历史资金流向" = daily bars over weeks.

### 大家也在关注 (related stocks)
Cards with related tickers + change% + tags (热门交易 Top X, sector chips).

### Bottom tabs
```
资讯 │ 讨论 │ 公告                                    [+]
```
News / community / announcements. Floating action button for new post.

### Implementation notes for v2
- **K-line chart**: use **TradingView Lightweight Charts** in a WebView (zero deps, perfect for this use case). Pass OHLC data from REST endpoint.
- Backend `GET /bars/{symbol}?period=1d&duration=1y` returning OHLCV array
- Depth (Level 1): we have free L1; show top of book only
- Money flow: requires IBKR doesn't provide this; can compute basic version from trade ticks (large/mid/small by qty)
- Related stocks: defer or use a static "popular" list
- Tabs 全景/财务: defer; IBKR doesn't have built-in business overview, would need 3rd party data
- 资讯/讨论/公告: defer

### What we CAN build in v2 with current IBKR data
- Header + 行情 tab only
- Real-time quote (last, change, %, pre-market)
- 8 key stats (high/low/open/prev close/volume/turnover/PE/mkt cap)
- 日K / 周K / 月K chart via `reqHistoricalData`
- Tick-by-tick history via `reqTickByTickData`
- Bottom Level-1 depth

Skip for v2: 全景, 财务, 资金流向, 大家也在关注, 资讯/讨论/公告
