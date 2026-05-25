package com.tis.ibkr.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tis.ibkr.data.api.Quote
import com.tis.ibkr.data.db.WatchlistItem
import com.tis.ibkr.ui.components.ChangePctBlock
import com.tis.ibkr.ui.components.NumericText
import com.tis.ibkr.ui.components.PulsePriceText
import com.tis.ibkr.ui.components.changeColor
import com.tis.ibkr.ui.components.formatPrice3
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
                    item("hdr") { ColumnHeaders() }
                    items(state.items, key = { it.symbol }) { item ->
                        WatchlistRow(
                            item = item,
                            quote = state.quotes[item.symbol],
                            onClick = { onOpenSymbol(item.symbol, item.exchange, item.currency) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnHeaders() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text("名称 / 代码", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(2f))
        Text("最新价", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.2f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        Text("涨跌幅", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.2f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
    HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.5f), thickness = 0.5.dp)
}

@Composable
private fun WatchlistRow(item: WatchlistItem, quote: Quote?, onClick: () -> Unit) {
    val change = quote?.change ?: 0.0
    val color = changeColor(change)
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(2f)) {
                Text(item.name, color = LbColors.OnSurface, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("${item.symbol} · ${item.exchange.ifBlank { "—" }}", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall)
            }
            Column(modifier = Modifier.weight(1.2f), horizontalAlignment = Alignment.End) {
                PulsePriceText(
                    price = quote?.last,
                    color = if (quote?.last != null) color else LbColors.OnSurfaceMuted,
                    style = MaterialTheme.typography.bodyLarge,
                )
                NumericText(text = formatSignedPrice(change), color = color, style = MaterialTheme.typography.bodySmall)
            }
            androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1.2f), contentAlignment = Alignment.CenterEnd) {
                ChangePctBlock(pct = quote?.changePct ?: 0.0)
            }
        }
        HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.3f), thickness = 0.5.dp)
    }
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
