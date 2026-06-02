package com.tis.ibkr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tis.ibkr.data.api.Quote
import com.tis.ibkr.data.db.WatchlistItem
import com.tis.ibkr.ui.chart.Sparkline
import com.tis.ibkr.ui.components.ChangePctBlock
import com.tis.ibkr.ui.components.NumericText
import com.tis.ibkr.ui.components.PulsePriceText
import com.tis.ibkr.ui.components.changeColor
import com.tis.ibkr.ui.components.formatPrice3
import com.tis.ibkr.ui.components.formatSignedPct
import com.tis.ibkr.ui.components.formatSignedPrice
import com.tis.ibkr.ui.theme.LbColors
import com.tis.ibkr.viewmodel.WatchlistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    onSearch: () -> Unit,
    onOpenSymbol: (symbol: String, exchange: String, currency: String) -> Unit,
    vm: WatchlistViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val refreshState = rememberPullToRefreshState()
    var sortKey by remember { mutableStateOf(SortKey.NONE) }
    var sortAsc by remember { mutableStateOf(false) }
    var marketTab by remember { mutableStateOf(ALL_MARKETS) }
    // Market tabs are derived from the items' currency — no schema/group storage.
    // Only shown when the watchlist actually spans more than one market.
    val markets = remember(state.items) {
        val present = state.items.map { marketOf(it) }.distinct()
        if (present.size <= 1) emptyList() else listOf(ALL_MARKETS) + present
    }
    val rows = remember(state.items, state.quotes, sortKey, sortAsc, marketTab) {
        val filtered = if (marketTab == ALL_MARKETS) state.items else state.items.filter { marketOf(it) == marketTab }
        when (sortKey) {
            SortKey.NONE -> filtered
            SortKey.PRICE -> filtered.sortedBy { state.quotes[it.symbol]?.last ?: Double.NEGATIVE_INFINITY }
            SortKey.CHANGE -> filtered.sortedBy { state.quotes[it.symbol]?.changePct ?: Double.NEGATIVE_INFINITY }
        }.let { if (sortKey == SortKey.NONE || sortAsc) it else it.reversed() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("自选", style = MaterialTheme.typography.titleLarge, color = LbColors.OnSurface, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onSearch) {
                Icon(Icons.Outlined.Search, "搜索", tint = LbColors.OnSurface)
            }
        }
        HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.5f), thickness = 0.5.dp)

        if (markets.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                markets.forEach { m -> MarketTab(label = m, selected = m == marketTab, onClick = { marketTab = m }) }
            }
            HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.5f), thickness = 0.5.dp)
        }

        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = { vm.refresh() },
            state = refreshState,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.items.isEmpty()) {
                EmptyWatchlist(onSearch)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item("hdr") {
                        ColumnHeaders(
                            sortKey = sortKey,
                            sortAsc = sortAsc,
                            onSort = { k -> if (sortKey == k) sortAsc = !sortAsc else { sortKey = k; sortAsc = false } },
                        )
                    }
                    items(rows, key = { it.symbol }) { item ->
                        WatchlistRow(
                            item = item,
                            quote = state.quotes[item.symbol],
                            sparkline = state.sparklines[item.symbol].orEmpty(),
                            onClick = { onOpenSymbol(item.symbol, item.exchange, item.currency) },
                        )
                    }
                }
            }
        }
    }
}

private const val ALL_MARKETS = "全部"

/** Derive a market bucket from the item's currency — drives the filter tabs without any schema. */
private fun marketOf(item: WatchlistItem): String = when {
    item.currency.equals("HKD", ignoreCase = true) -> "港股"
    item.currency.equals("CNY", ignoreCase = true) || item.currency.equals("CNH", ignoreCase = true) -> "沪深"
    item.currency.equals("USD", ignoreCase = true) -> "美股"
    else -> item.currency.uppercase().ifBlank { "其他" }
}

@Composable
private fun MarketTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) LbColors.Accent else LbColors.SurfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (selected) Color.White else LbColors.OnSurfaceMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private enum class SortKey { NONE, PRICE, CHANGE }

@Composable
private fun ColumnHeaders(sortKey: SortKey, sortAsc: Boolean, onSort: (SortKey) -> Unit) {
    fun arrow(k: SortKey) = if (sortKey != k) "" else if (sortAsc) " ↑" else " ↓"
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("名称 / 代码", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.5f))
        Spacer(Modifier.width(54.dp))
        Text(
            "最新价${arrow(SortKey.PRICE)}",
            color = if (sortKey == SortKey.PRICE) LbColors.OnSurface else LbColors.OnSurfaceMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1.2f).clickable { onSort(SortKey.PRICE) },
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
        Text(
            "涨跌幅${arrow(SortKey.CHANGE)}",
            color = if (sortKey == SortKey.CHANGE) LbColors.OnSurface else LbColors.OnSurfaceMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1.3f).clickable { onSort(SortKey.CHANGE) },
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
    HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.5f), thickness = 0.5.dp)
}

@Composable
private fun WatchlistRow(item: WatchlistItem, quote: Quote?, sparkline: List<Double>, onClick: () -> Unit) {
    val change = quote?.change ?: 0.0
    val color = changeColor(change)
    val ext = extendedHours(quote)
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1.5f)) {
                // Ticker first (never truncated), company name secondary.
                Text(item.symbol, color = LbColors.OnSurface, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    item.name.ifBlank { item.exchange.ifBlank { "—" } },
                    color = LbColors.OnSurfaceMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Sparkline(
                points = sparkline,
                color = if (sparkline.size >= 2) changeColor(sparkline.last() - sparkline.first()) else LbColors.Flat,
                baseline = quote?.prevClose,
                modifier = Modifier.width(54.dp).height(28.dp).padding(horizontal = 6.dp),
            )
            Column(modifier = Modifier.weight(1.2f), horizontalAlignment = Alignment.End) {
                PulsePriceText(
                    price = quote?.last,
                    color = if (quote?.last != null) color else LbColors.OnSurfaceMuted,
                    style = MaterialTheme.typography.bodyLarge,
                )
                NumericText(text = formatSignedPrice(change), color = color, style = MaterialTheme.typography.bodySmall)
            }
            Column(modifier = Modifier.weight(1.3f), horizontalAlignment = Alignment.End) {
                ChangePctBlock(pct = quote?.changePct ?: 0.0)
                ext?.let { (pct, label) ->
                    NumericText(
                        text = "${formatSignedPct(pct)} $label",
                        color = LbColors.OnSurfaceMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.3f), thickness = 0.5.dp)
    }
}

/** Extended-hours change to surface under the % block: post-market wins over pre-market. */
private fun extendedHours(q: Quote?): Pair<Double, String>? {
    q?.postMarket?.changePct?.let { return it to "盘后" }
    q?.preMarket?.changePct?.let { return it to "盘前" }
    return null
}

@Composable
private fun EmptyWatchlist(onSearch: () -> Unit) {
    com.tis.ibkr.ui.components.EmptyState(
        icon = androidx.compose.material.icons.Icons.Outlined.StarBorder,
        title = "还没有自选股",
        subtitle = "搜索 symbol 添加，5 秒自动刷新",
        actionLabel = "+ 添加自选",
        onAction = onSearch,
    )
}
