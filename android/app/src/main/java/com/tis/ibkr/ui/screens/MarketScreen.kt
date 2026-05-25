package com.tis.ibkr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tis.ibkr.data.api.Quote
import com.tis.ibkr.ui.components.ChangePctBlock
import com.tis.ibkr.ui.components.NumericText
import com.tis.ibkr.ui.components.changeColor
import com.tis.ibkr.ui.components.formatPrice3
import com.tis.ibkr.ui.components.formatSignedPct
import com.tis.ibkr.ui.theme.LbColors
import com.tis.ibkr.viewmodel.MarketUiState
import com.tis.ibkr.viewmodel.MarketUniverse
import com.tis.ibkr.viewmodel.MarketViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen(
    onOpenSymbol: (symbol: String, exchange: String, currency: String) -> Unit,
    vm: MarketViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val refreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = { vm.refresh() },
        state = refreshState,
    ) {
        LazyColumn(contentPadding = PaddingValues(vertical = 12.dp, horizontal = 0.dp)) {
            item("hdr") {
                Text(
                    "市场",
                    color = LbColors.OnSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item("idx") { IndexesRow(state, onOpenSymbol) }
            item("gainers") {
                MoversSection(
                    title = "涨幅榜",
                    quotes = state.topGainers,
                    onOpenSymbol = onOpenSymbol,
                )
            }
            item("losers") {
                MoversSection(
                    title = "跌幅榜",
                    quotes = state.topLosers,
                    onOpenSymbol = onOpenSymbol,
                )
            }
            item("hothdr") {
                Spacer(Modifier.height(8.dp))
                Text(
                    "美股热门",
                    color = LbColors.OnSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HotColumnHeaders()
            }
            items(MarketUniverse.HOT, key = { it }) { sym ->
                HotRow(sym = sym, quote = state.hot[sym], onClick = { onOpenSymbol(sym, "SMART", "USD") })
            }
        }
    }
}

@Composable
private fun IndexesRow(state: MarketUiState, onOpenSymbol: (String, String, String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(MarketUniverse.INDEXES, key = { it.second }) { (label, sym) ->
            val q = state.indexes[sym]
            val change = q?.change ?: 0.0
            val color = changeColor(change)
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(LbColors.Surface)
                    .clickable { onOpenSymbol(sym, "SMART", "USD") }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .width(140.dp),
            ) {
                Text(label, color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                NumericText(
                    text = formatPrice3(q?.last),
                    color = if (q?.last != null) color else LbColors.OnSurfaceMuted,
                    style = MaterialTheme.typography.titleMedium,
                )
                NumericText(
                    text = formatSignedPct(q?.changePct ?: 0.0),
                    color = color,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun MoversSection(title: String, quotes: List<Quote>, onOpenSymbol: (String, String, String) -> Unit) {
    if (quotes.isEmpty()) return
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            title,
            color = LbColors.OnSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(quotes, key = { it.symbol }) { q ->
                val color = changeColor(q.change ?: 0.0)
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(LbColors.Surface)
                        .clickable { onOpenSymbol(q.symbol, "SMART", "USD") }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .width(110.dp),
                ) {
                    Text(q.symbol, color = LbColors.OnSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    NumericText(text = formatPrice3(q.last), color = color, style = MaterialTheme.typography.bodyMedium)
                    NumericText(text = formatSignedPct(q.changePct ?: 0.0), color = color, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun HotColumnHeaders() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text("代码", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(2f))
        Text("最新价", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.2f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        Text("涨跌幅", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.2f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
    HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.5f), thickness = 0.5.dp)
}

@Composable
private fun HotRow(sym: String, quote: Quote?, onClick: () -> Unit) {
    val change = quote?.change ?: 0.0
    val color = changeColor(change)
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(sym, color = LbColors.OnSurface, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(2f))
            NumericText(
                text = formatPrice3(quote?.last),
                color = if (quote?.last != null) color else LbColors.OnSurfaceMuted,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1.2f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
            Box(modifier = Modifier.weight(1.2f), contentAlignment = Alignment.CenterEnd) {
                ChangePctBlock(pct = quote?.changePct ?: 0.0)
            }
        }
        HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.3f), thickness = 0.5.dp)
    }
}
