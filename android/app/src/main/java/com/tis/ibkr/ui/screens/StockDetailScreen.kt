package com.tis.ibkr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tis.ibkr.data.api.Depth
import com.tis.ibkr.data.api.ExtendedQuote
import com.tis.ibkr.data.api.Position
import com.tis.ibkr.data.api.Quote
import com.tis.ibkr.data.api.StaticInfo
import com.tis.ibkr.ui.chart.IntradayChart
import com.tis.ibkr.ui.chart.KlineChart
import com.tis.ibkr.ui.chart.MacdSubChart
import com.tis.ibkr.ui.chart.rememberChartLink
import com.tis.ibkr.ui.components.NumericText
import com.tis.ibkr.ui.components.StackedPriceText
import com.tis.ibkr.ui.components.changeColor
import com.tis.ibkr.ui.components.formatBigNumber
import com.tis.ibkr.ui.components.formatPrice
import com.tis.ibkr.ui.components.formatPrice3
import com.tis.ibkr.ui.components.formatSignedPct
import com.tis.ibkr.ui.components.formatSignedPrice
import com.tis.ibkr.ui.theme.LbColors
import com.tis.ibkr.viewmodel.IntradaySession
import com.tis.ibkr.viewmodel.StockDetailViewModel

private val FIXED_PERIODS = listOf(
    "5d" to "5日",
    "1d" to "日K",
    "1w" to "周K",
    "1mo" to "月K",
    "1y" to "年K",
)

private val MINUTE_PERIODS = listOf(
    "1min" to "1分",
    "2min" to "2分",
    "3min" to "3分",
    "5min" to "5分",
    "15min" to "15分",
    "30min" to "30分",
    "60min" to "60分",
)

private val MINUTE_PERIOD_KEYS = MINUTE_PERIODS.map { it.first }.toSet()

@Composable
fun StockDetailScreen(
    symbol: String,
    exchange: String = "SMART",
    currency: String = "USD",
    onBack: () -> Unit,
    onOrder: (side: String) -> Unit = {},
    onOptions: () -> Unit = {},
    onFullscreen: () -> Unit = {},
) {
    val vm: StockDetailViewModel = viewModel(
        key = "stock-$symbol-$exchange-$currency",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                StockDetailViewModel(symbol, exchange, currency) as T
        },
    )
    val state by vm.state.collectAsState()
    val isFavorite by vm.isFavorite.collectAsState()

    var activeTab by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("行情") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(LbColors.Background),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item("top") {
            TopBar(
                symbol = state.symbol,
                displayName = state.info?.displayName,
                position = state.position,
                isFavorite = isFavorite,
                onBack = onBack,
                onToggleFavorite = vm::toggleFavorite,
            )
        }
        item("subtabs") { SubTabsBar(active = activeTab, onSelect = { activeTab = it }) }
        if (activeTab == "全景") {
            item("panorama") { PanoramaTab(state.quote, state.info, exchange, currency) }
            return@LazyColumn
        }
        if (activeTab == "财务") {
            item("financials") { FinancialsTab(state.info, state.quote) }
            return@LazyColumn
        }
        item("price") { PriceSection(state.quote, state.position, exchange, currency) }
        item("events") { EventChips(state.quote, state.bars) }
        item("preposed") { PrePostSection(state.quote) }
        item("stats") { StatsGrid(state.quote, state.info) }
        item("period") {
            UnifiedPeriodSelector(
                period = state.period,
                session = state.intradaySession,
                onSelectPeriod = vm::changePeriod,
                onSelectSession = { s ->
                    vm.setIntradaySession(s)
                    if (state.period != "intraday") vm.changePeriod("intraday")
                },
            )
        }
        if (state.period == "intraday") {
            item("trading_day") {
                IntradayTradingDayBadge(
                    points = state.filteredIntraday,
                    session = state.intradaySession,
                    quote = state.quote,
                )
            }
        }
        item("chart") {
            if (state.period == "intraday") {
                Row(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                    Box(modifier = Modifier.weight(1.8f).fillMaxHeight()) {
                        IntradayChart(
                            points = state.filteredIntraday,
                            prevClose = state.quote?.prevClose,
                            costBasis = state.position?.avgCost,
                            costShares = state.position?.position,
                            executions = state.executions.map {
                                com.tis.ibkr.ui.chart.ChartExecution(it.time, it.price, it.side, it.shares)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (state.loading && state.filteredIntraday.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = LbColors.Accent)
                            }
                        }
                        IconButton(
                            onClick = onFullscreen,
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            Icon(Icons.Outlined.Fullscreen, "全屏", tint = LbColors.OnSurfaceMuted)
                        }
                    }
                    TradesSidebar(state.trades, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                val link = rememberChartLink()
                Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                    KlineChart(
                        bars = state.bars,
                        period = state.period,
                        link = link,
                        costBasis = state.position?.avgCost,
                        costShares = state.position?.position,
                        executions = state.executions.map {
                            com.tis.ibkr.ui.chart.ChartExecution(it.time, it.price, it.side, it.shares)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (state.loading && state.bars.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = LbColors.Accent)
                        }
                    }
                    if (state.error != null && state.bars.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "加载失败: ${state.error}",
                                color = LbColors.Error,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    IconButton(
                        onClick = onFullscreen,
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Icon(Icons.Outlined.Fullscreen, "全屏", tint = LbColors.OnSurfaceMuted)
                    }
                }
                if (state.bars.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(110.dp)) {
                        MacdSubChart(bars = state.bars, link = link, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
        item("depth") { DepthSection(state.depth) }
        // In intraday mode, trades are shown in the sidebar above; hide the below-chart section.
        if (state.period != "intraday") {
            item("trades") { TradesSection(state.trades) }
        }
        item("actions") {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onOptions,
                    modifier = Modifier.height(48.dp),
                ) { Text("期权") }
                androidx.compose.material3.Button(
                    onClick = { onOrder("BUY") },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = LbColors.Up, contentColor = LbColors.OnSurface),
                ) { Text("买入", fontWeight = FontWeight.SemiBold) }
                androidx.compose.material3.Button(
                    onClick = { onOrder("SELL") },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = LbColors.Down, contentColor = LbColors.OnSurface),
                ) { Text("卖出", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun TopBar(
    symbol: String,
    displayName: String?,
    position: Position?,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = LbColors.OnSurface)
        }
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
            Text(symbol, color = LbColors.OnSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (!displayName.isNullOrBlank() && displayName != symbol) {
                androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                Text(
                    displayName,
                    color = LbColors.OnSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        IconButton(onClick = onToggleFavorite) {
            if (isFavorite) Icon(Icons.Filled.Favorite, "从自选移除", tint = LbColors.Up)
            else Icon(Icons.Outlined.FavoriteBorder, "加入自选", tint = LbColors.OnSurfaceMuted)
        }
    }
    val sessionText = sessionStatusLine()
    val secText = position?.let { "${it.secType} · ${it.exchange.ifBlank { "—" }}" }
    val combined = listOfNotNull(sessionText, secText).joinToString(" · ")
    if (combined.isNotBlank()) {
        Text(
            combined,
            color = LbColors.OnSurfaceMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
        )
    }
}

/**
 * Live "夜盘交易中 MM.DD 美东" style status line based on current ET time. Matches
 * Longbridge's subtitle right under the symbol name.
 */
private fun sessionStatusLine(): String {
    val et = java.time.ZoneId.of("America/New_York")
    val now = java.time.ZonedDateTime.now(et)
    val date = "%02d.%02d".format(now.monthValue, now.dayOfMonth)
    val hm = now.hour + now.minute / 60.0
    val label = when {
        hm >= 4.0 && hm < 9.5 -> "盘前交易中"
        hm >= 9.5 && hm < 16.0 -> "盘中交易中"
        hm >= 16.0 && hm < 20.0 -> "盘后交易中"
        else -> "夜盘交易中"
    }
    return "$label $date 美东"
}

@Composable
private fun EventChips(quote: com.tis.ibkr.data.api.Quote?, bars: List<com.tis.ibkr.data.api.Bar>) {
    val last = quote?.last ?: return
    if (bars.size < 50) return
    val window = bars.takeLast(252) // 52 weeks of daily bars
    val yearHigh = window.maxOf { it.high }
    val yearLow = window.minOf { it.low }
    val isNearHigh = last >= yearHigh * 0.998
    val isNearLow = last <= yearLow * 1.002 && yearLow > 0
    if (!isNearHigh && !isNearLow) return
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (isNearHigh) Chip(label = "创 52 周新高", color = LbColors.Warning)
        if (isNearLow) Chip(label = "创 52 周新低", color = LbColors.Down)
    }
}

@Composable
private fun Chip(label: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SubTabsBar(active: String, onSelect: (String) -> Unit) {
    val items = listOf("行情", "全景", "财务")
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        items.forEach { label ->
            val selected = label == active
            Column(
                modifier = Modifier
                    .clickable { onSelect(label) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    color = if (selected) LbColors.OnSurface else LbColors.OnSurfaceMuted,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
                Spacer(Modifier.size(4.dp))
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(2.dp)
                        .background(if (selected) LbColors.Accent else androidx.compose.ui.graphics.Color.Transparent),
                )
            }
        }
    }
    HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.5f), thickness = 0.5.dp)
}

// ---------- 全景 tab ----------

@Composable
private fun PanoramaTab(quote: Quote?, info: StaticInfo?, exchange: String, currency: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        // 公司信息卡
        Text("公司信息", color = LbColors.OnSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        InfoRow("中文名", info?.nameCn ?: "—")
        InfoRow("英文名", info?.nameEn ?: "—")
        info?.nameHk?.takeIf { it.isNotBlank() }?.let { InfoRow("港股名", it) }
        InfoRow("交易所", info?.exchange ?: exchange)
        InfoRow("货币", info?.currency ?: currency)
        InfoRow("每手股数", info?.lotSize?.toString() ?: "—")

        Spacer(Modifier.height(20.dp))
        Text("关键指标", color = LbColors.OnSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        InfoRow("最新价", quote?.last?.let { formatPrice3(it) } ?: "—")
        InfoRow("成交额", quote?.turnover?.let { formatBigNumber(it) } ?: "—")
        InfoRow("成交量", quote?.volume?.toLong()?.toString() ?: "—")
        val mc = info?.totalShares?.let { (quote?.last ?: 0.0) * it }
        InfoRow("总市值", mc?.takeIf { it > 0 }?.let { formatBigNumber(it) } ?: "—")
        InfoRow("总股本", info?.totalShares?.let { formatBigNumber(it.toDouble()) } ?: "—")
        InfoRow("流通股", info?.circulatingShares?.let { formatBigNumber(it.toDouble()) } ?: "—")
    }
}

// ---------- 财务 tab ----------

@Composable
private fun FinancialsTab(info: StaticInfo?, quote: Quote?) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("盈利能力", color = LbColors.OnSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        InfoRow("EPS", info?.eps?.let { "%.3f".format(java.util.Locale.US, it) } ?: "—")
        InfoRow("EPS (TTM)", info?.epsTtm?.let { "%.3f".format(java.util.Locale.US, it) } ?: "—")
        val pe = if (info?.epsTtm != null && info.epsTtm > 0 && quote?.last != null) quote.last!! / info.epsTtm else null
        InfoRow("市盈率 (TTM)", pe?.let { "%.2f".format(java.util.Locale.US, it) } ?: "—")
        val pb = if (info?.bps != null && info.bps > 0 && quote?.last != null) quote.last!! / info.bps else null
        InfoRow("市净率", pb?.let { "%.2f".format(java.util.Locale.US, it) } ?: "—")
        InfoRow("每股净资产 (BPS)", info?.bps?.let { "%.3f".format(java.util.Locale.US, it) } ?: "—")

        Spacer(Modifier.height(20.dp))
        Text("分红", color = LbColors.OnSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        InfoRow("股息率", info?.dividendYield?.let { "%.2f%%".format(java.util.Locale.US, it * 100) } ?: "—")

        Spacer(Modifier.height(20.dp))
        Text("说明", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall)
        Text(
            "财务数据来自 Longbridge 静态信息接口, 更详细的财报 (营收/净利润/现金流) 暂无接口支持.",
            color = LbColors.OnSurfaceMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        NumericText(text = value, color = LbColors.OnSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PriceSection(quote: Quote?, position: Position?, exchange: String, currency: String) {
    val price = quote?.last ?: position?.marketPrice
    val change = quote?.change ?: 0.0
    val pct = quote?.changePct ?: 0.0
    val color = changeColor(change)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        StackedPriceText(
            value = price,
            intStyle = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.SemiBold),
            fracStyle = MaterialTheme.typography.titleLarge,
            color = color,
            fractionDigits = 3,
        )
        if (quote != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumericText(text = formatSignedPrice(change), color = color, style = MaterialTheme.typography.titleMedium)
                NumericText(text = formatSignedPct(pct), color = color, style = MaterialTheme.typography.titleMedium)
            }
        } else if (position != null) {
            val pnl = position.unrealizedPnl ?: 0.0
            val cost = (position.marketValue ?: 0.0) - pnl
            val pctP = if (cost == 0.0) 0.0 else pnl / cost * 100.0
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumericText(text = formatSignedPrice(pnl), color = changeColor(pnl), style = MaterialTheme.typography.titleMedium)
                NumericText(text = formatSignedPct(pctP), color = changeColor(pnl), style = MaterialTheme.typography.titleMedium)
                Text("浮动盈亏", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        } else {
            Text("$exchange · $currency", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PrePostSection(quote: Quote?) {
    val pre = quote?.preMarket
    val post = quote?.postMarket
    if (pre == null && post == null) return
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        pre?.let { ExtRow("盘前", it) }
        post?.let { ExtRow("盘后", it) }
    }
}

@Composable
private fun ExtRow(label: String, q: ExtendedQuote) {
    val color = changeColor(q.change ?: 0.0)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 6.dp))
        NumericText(text = formatPrice3(q.last), color = color, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.padding(horizontal = 4.dp))
        NumericText(text = formatSignedPrice(q.change ?: 0.0), color = color, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.padding(horizontal = 4.dp))
        NumericText(text = formatSignedPct(q.changePct ?: 0.0), color = color, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatsGrid(quote: Quote?, info: StaticInfo?) {
    if (quote == null && info == null) return
    val price = quote?.last
    val mc = if (price != null && info?.totalShares != null) price * info.totalShares else null
    val pe = if (price != null && info?.epsTtm != null && info.epsTtm != 0.0) price / info.epsTtm else null
    val turnoverRate = if (quote?.volume != null && info?.circulatingShares != null && info.circulatingShares > 0L) {
        quote.volume / info.circulatingShares.toDouble() * 100.0
    } else null

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Stat("最高", formatPrice3(quote?.high), Modifier.weight(1f))
            Stat("今开", formatPrice3(quote?.open), Modifier.weight(1f))
            Stat("换手率", turnoverRate?.let { String.format(java.util.Locale.US, "%.2f%%", it) } ?: "--", Modifier.weight(1f))
            Stat("市盈率TTM", pe?.let { if (it < 0) "亏损" else String.format(java.util.Locale.US, "%.2f", it) } ?: "--", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Stat("最低", formatPrice3(quote?.low), Modifier.weight(1f))
            Stat("昨收", formatPrice3(quote?.prevClose), Modifier.weight(1f))
            Stat("成交量", formatBigNumber(quote?.volume), Modifier.weight(1f))
            Stat("总市值", formatBigNumber(mc), Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(2.dp))
        NumericText(text = value, color = LbColors.OnSurface, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun UnifiedPeriodSelector(
    period: String,
    session: IntradaySession,
    onSelectPeriod: (String) -> Unit,
    onSelectSession: (IntradaySession) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Slot 1: session dropdown (active when period == "intraday")
        item("session") {
            DropdownChip(
                label = if (period == "intraday") SESSION_ITEMS.first { it.first == session }.second else "盘中",
                selected = period == "intraday",
                options = SESSION_ITEMS.map { it.second },
                onPick = { idx -> onSelectSession(SESSION_ITEMS[idx].first) },
            )
        }
        // Slot 2-6: fixed K periods
        items(FIXED_PERIODS, key = { it.first }) { (key, label) ->
            StaticChip(
                label = label,
                selected = period == key,
                onClick = { onSelectPeriod(key) },
            )
        }
        // Slot 7: minute dropdown
        item("minute") {
            val activeMinuteLabel = MINUTE_PERIODS.firstOrNull { it.first == period }?.second ?: "1分"
            DropdownChip(
                label = activeMinuteLabel,
                selected = period in MINUTE_PERIOD_KEYS,
                options = MINUTE_PERIODS.map { it.second },
                onPick = { idx -> onSelectPeriod(MINUTE_PERIODS[idx].first) },
            )
        }
    }
}

@Composable
private fun StaticChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) LbColors.SurfaceElevated else LbColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) LbColors.OnSurface else LbColors.OnSurfaceMuted,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun DropdownChip(
    label: String,
    selected: Boolean,
    options: List<String>,
    onPick: (index: Int) -> Unit,
) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) LbColors.SurfaceElevated else LbColors.Surface)
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                color = if (selected) LbColors.OnSurface else LbColors.OnSurfaceMuted,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(3.dp))
            Text(
                "▼",
                color = if (selected) LbColors.OnSurface else LbColors.OnSurfaceMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(LbColors.Surface),
        ) {
            options.forEachIndexed { idx, opt ->
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Text(
                            opt,
                            color = if (opt == label) LbColors.Accent else LbColors.OnSurface,
                            fontWeight = if (opt == label) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onPick(idx)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DepthSection(depth: Depth?) {
    if (depth == null) return
    val topBid = depth.bids.firstOrNull()
    val topAsk = depth.asks.firstOrNull()
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("盘口", color = LbColors.OnSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.5f), thickness = 0.5.dp)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            DepthCell(
                "买盘",
                price = topBid?.price,
                volume = topBid?.volume,
                color = LbColors.Down,
                modifier = Modifier.weight(1f),
            )
            DepthCell(
                "卖盘",
                price = topAsk?.price,
                volume = topAsk?.volume,
                color = LbColors.Up,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val SESSION_ITEMS = listOf(
    IntradaySession.All to "全部",
    IntradaySession.PreMarket to "盘前",
    IntradaySession.Normal to "盘中",
    IntradaySession.PostMarket to "盘后",
    IntradaySession.Overnight to "夜盘",
)

@Composable
private fun TradesSidebar(trades: List<com.tis.ibkr.data.api.TradeTick>, modifier: Modifier = Modifier) {
    val sorted = trades.sortedByDescending { it.time }.take(40)
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier.background(LbColors.Surface),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    ) {
        itemsIndexed(sorted) { _, t ->
            val color = when (t.direction.uppercase()) {
                "BUY", "B" -> LbColors.Up
                "SELL", "S" -> LbColors.Down
                else -> LbColors.OnSurface
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                Text(
                    formatHmmss(t.time).take(5),
                    color = LbColors.OnSurfaceMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                NumericText(
                    text = formatPrice3(t.price),
                    color = color,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
                NumericText(
                    text = t.volume.toLong().toString(),
                    color = LbColors.OnSurfaceMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(0.8f),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun TradesSection(trades: List<com.tis.ibkr.data.api.TradeTick>) {
    if (trades.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("逐笔成交", color = LbColors.OnSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.5f), thickness = 0.5.dp)
        // Header
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("时间", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text("价格", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            Text("数量", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        // Rows — show most recent first
        val sorted = trades.sortedByDescending { it.time }.take(20)
        sorted.forEach { t ->
            val color = when (t.direction.uppercase()) {
                "BUY", "B" -> LbColors.Up
                "SELL", "S" -> LbColors.Down
                else -> LbColors.OnSurface
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    text = formatHmmss(t.time),
                    color = LbColors.OnSurfaceMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                NumericText(
                    text = formatPrice3(t.price),
                    color = color,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
                NumericText(
                    text = t.volume.toLong().toString(),
                    color = LbColors.OnSurface,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

private fun formatHmmss(epochSeconds: Long): String {
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getDefault())
    cal.timeInMillis = epochSeconds * 1000
    return String.format(
        java.util.Locale.US,
        "%02d:%02d:%02d",
        cal.get(java.util.Calendar.HOUR_OF_DAY),
        cal.get(java.util.Calendar.MINUTE),
        cal.get(java.util.Calendar.SECOND),
    )
}

@Composable
private fun DepthCell(label: String, price: Double?, volume: Long?, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(2.dp))
        NumericText(text = formatPrice3(price), color = color, style = MaterialTheme.typography.titleMedium)
        NumericText(text = if (volume != null) "$volume 股" else "--", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Empty-state hint above the intraday chart for edge sessions (盘前/盘后/夜盘):
 *  - If we're already inside the session's time window → "夜盘进行中 · 暂无成交"
 *  - If we're outside → "等待 X 开始 HH:MM"
 */
@Composable
private fun IntradayTradingDayBadge(
    points: List<com.tis.ibkr.data.api.IntradayPoint>,
    session: com.tis.ibkr.viewmodel.IntradaySession,
    quote: com.tis.ibkr.data.api.Quote?,
) {
    if (points.isNotEmpty()) return
    val showHint = session == com.tis.ibkr.viewmodel.IntradaySession.PreMarket ||
        session == com.tis.ibkr.viewmodel.IntradaySession.PostMarket ||
        session == com.tis.ibkr.viewmodel.IntradaySession.Overnight
    if (!showHint) return
    val nowEt = java.time.ZonedDateTime.now(java.time.ZoneId.of("America/New_York"))
    val inWindow = com.tis.ibkr.viewmodel.currentEtSession() == session
    val message = if (inWindow) {
        val live = quote?.last?.let { last ->
            val pct = quote.changePct?.let { " (${if (it >= 0) "+" else ""}%.2f%%)".format(it) } ?: ""
            "${sessionLabel(session)}进行中 · 实时价 ${"%.3f".format(last)}$pct · 图表数据暂未提供"
        } ?: "${sessionLabel(session)}进行中 · 暂无成交"
        live
    } else {
        "等待 ${nextSessionOpen(session, nowEt)}"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message,
            color = LbColors.OnSurfaceMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun sessionLabel(s: com.tis.ibkr.viewmodel.IntradaySession): String = when (s) {
    com.tis.ibkr.viewmodel.IntradaySession.PreMarket -> "盘前"
    com.tis.ibkr.viewmodel.IntradaySession.Normal -> "盘中"
    com.tis.ibkr.viewmodel.IntradaySession.PostMarket -> "盘后"
    com.tis.ibkr.viewmodel.IntradaySession.Overnight -> "夜盘"
    com.tis.ibkr.viewmodel.IntradaySession.All -> "全部"
}

/** Returns a human-readable "next session start" string in Beijing time. */
private fun nextSessionOpen(
    session: com.tis.ibkr.viewmodel.IntradaySession,
    nowEt: java.time.ZonedDateTime,
): String {
    val cn = java.time.ZoneId.of("Asia/Shanghai")
    val (etHour, etMinute, label) = when (session) {
        com.tis.ibkr.viewmodel.IntradaySession.PreMarket -> Triple(4, 0, "盘前")
        com.tis.ibkr.viewmodel.IntradaySession.Normal -> Triple(9, 30, "盘中")
        com.tis.ibkr.viewmodel.IntradaySession.PostMarket -> Triple(16, 0, "盘后")
        com.tis.ibkr.viewmodel.IntradaySession.Overnight -> Triple(20, 0, "夜盘")
        com.tis.ibkr.viewmodel.IntradaySession.All -> Triple(4, 0, "盘前")
    }
    var target = nowEt.toLocalDate().atTime(etHour, etMinute).atZone(nowEt.zone)
    if (!target.isAfter(nowEt)) target = target.plusDays(1)
    val targetCn = target.withZoneSameInstant(cn)
    val today = java.time.LocalDate.now(cn)
    val datePrefix = if (targetCn.toLocalDate() == today) "今日" else "${targetCn.monthValue}/${targetCn.dayOfMonth}"
    return "$label $datePrefix ${String.format(java.util.Locale.US, "%02d:%02d", targetCn.hour, targetCn.minute)}"
}
