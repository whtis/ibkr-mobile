package com.tis.ibkr.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tis.ibkr.data.api.AccountSummary
import com.tis.ibkr.data.api.OrderResponse
import com.tis.ibkr.data.api.Position
import com.tis.ibkr.ui.components.NumericText
import com.tis.ibkr.ui.components.StackedPriceText
import com.tis.ibkr.ui.components.changeColor
import com.tis.ibkr.ui.components.formatPrice
import com.tis.ibkr.ui.components.formatPrice3
import com.tis.ibkr.ui.components.formatQty
import com.tis.ibkr.ui.components.formatSignedPct
import com.tis.ibkr.ui.components.formatSignedPrice
import com.tis.ibkr.ui.theme.LbColors
import com.tis.ibkr.viewmodel.PositionsUiState
import com.tis.ibkr.viewmodel.PositionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PositionsScreen(
    vm: PositionsViewModel = viewModel(),
    onPositionClick: (Position) -> Unit = {},
    onOrderClick: (Position, String) -> Unit = { _, _ -> },
) {
    val state by vm.state.collectAsState()
    val refreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = { vm.refresh() },
        state = refreshState,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.positions.isEmpty() && state.summaries.isEmpty() && state.error == null && state.loading -> {
                /* PullToRefreshBox shows its own spinner */
            }
            state.error != null && state.summaries.isEmpty() -> ErrorState(state.error!!) { vm.refresh() }
            else -> Content(state, onPositionClick, onOrderClick)
        }
    }
}

@Composable
private fun Content(
    state: PositionsUiState,
    onPositionClick: (Position) -> Unit,
    onOrderClick: (Position, String) -> Unit,
) {
    val vm: PositionsViewModel = viewModel()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item("header") {
            state.primarySummary?.let { HeaderCard(it, state) }
        }
        if (state.error != null) {
            item("err") {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    ErrorBanner(state.error)
                }
            }
        }
        if (state.activeOrders.isNotEmpty()) {
            item("orders_hdr") { OrdersHeader(state.activeOrders.size) }
            items(state.activeOrders, key = { it.orderId }) { ord ->
                OrderRow(ord, onCancel = { vm.cancelOrder(ord.orderId) })
            }
            item("orders_spacer") { Spacer(Modifier.height(8.dp)) }
        }
        if (state.positions.isEmpty()) {
            item("empty") { EmptyPositions() }
        } else {
            val byCurrencyOrdered = state.sortedPositions.groupBy { it.currency }
            byCurrencyOrdered.forEach { (currency, items) ->
                val totalMv = items.sumOf { it.marketValue ?: (it.position * it.avgCost) }
                val totalPnl = items.sumOf { it.unrealizedPnl ?: 0.0 }
                item("mkt_${currency}") {
                    MarketSection(currency, totalMv, totalPnl)
                }
                item("colhdr_${currency}") { ColumnHeaders(state.sort, onCycleSort = { vm.cycleSort() }) }
                items(
                    items,
                    key = { "${it.account}-${it.symbol}-${it.secType}-${it.position}" },
                ) {
                    PositionRow(
                        p = it,
                        onClick = { onPositionClick(it) },
                        onOrderClick = { side -> onOrderClick(it, side) },
                    )
                }
                item("spacer_${currency}") { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun HeaderCard(summary: AccountSummary, state: PositionsUiState) {
    val totalMarketValue = state.positions.sumOf { it.marketValue ?: (it.position * it.avgCost) }
    val totalPnl = state.totalUnrealizedPnl

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(
            text = "IBKR 综合账户(${summary.accountId})",
            color = LbColors.OnSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "总资产(${summary.currency})",
                    color = LbColors.OnSurfaceMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(4.dp))
                StackedPriceText(
                    value = summary.netLiquidation,
                    intStyle = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.SemiBold),
                    fracStyle = MaterialTheme.typography.titleLarge,
                    color = LbColors.OnSurface,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "当日盈亏",
                    color = LbColors.OnSurfaceMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(4.dp))
                val daily = summary.dailyPnl
                NumericText(
                    text = if (daily != 0.0) formatSignedPrice(daily) else "—",
                    color = if (daily != 0.0) changeColor(daily) else LbColors.Disabled,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            HeaderStat(
                label = "持仓总市值",
                value = formatPrice(totalMarketValue),
                color = LbColors.OnSurface,
                modifier = Modifier.weight(1f),
            )
            HeaderStat(
                label = "持仓总盈亏",
                value = formatSignedPrice(totalPnl),
                color = changeColor(totalPnl),
                modifier = Modifier.weight(1f),
            )
            HeaderStat(
                label = "现金",
                value = formatPrice(summary.totalCash),
                color = LbColors.OnSurface,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = LbColors.Outline, thickness = 0.5.dp)
    }
}

@Composable
private fun HeaderStat(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(2.dp))
        NumericText(text = value, color = color, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun MarketSection(currency: String, totalMv: Double, totalPnl: Double) {
    val (flag, name) = when (currency) {
        "USD" -> "🇺🇸" to "美股"
        "HKD" -> "🇭🇰" to "港股"
        "CNH", "CNY" -> "🇨🇳" to "沪深"
        "SGD" -> "🇸🇬" to "新加坡"
        else -> "🌐" to currency
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(flag, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(6.dp))
            Text(name, color = LbColors.OnSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            NumericText(
                text = formatPrice(totalMv),
                color = LbColors.OnSurface,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            HeaderStat(
                label = "持仓市值",
                value = formatPrice(totalMv),
                color = LbColors.OnSurface,
                modifier = Modifier.weight(1f),
            )
            HeaderStat(
                label = "持仓盈亏",
                value = formatSignedPrice(totalPnl),
                color = changeColor(totalPnl),
                modifier = Modifier.weight(1f),
            )
            HeaderStat(
                label = "浮动盈亏%",
                value = run {
                    val cost = totalMv - totalPnl
                    val pct = if (cost == 0.0) 0.0 else totalPnl / cost * 100.0
                    formatSignedPct(pct)
                },
                color = changeColor(totalPnl),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ColumnHeaders(sort: com.tis.ibkr.viewmodel.PositionsSort, onCycleSort: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCycleSort)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val sortLabel = when (sort) {
            com.tis.ibkr.viewmodel.PositionsSort.MarketValue -> "市值 ⇅"
            com.tis.ibkr.viewmodel.PositionsSort.UnrealizedPnl -> "盈亏 ⇅"
            com.tis.ibkr.viewmodel.PositionsSort.DailyPnl -> "当日 ⇅"
            com.tis.ibkr.viewmodel.PositionsSort.Symbol -> "代码 ⇅"
        }
        Text(
            "名称/代码",
            color = LbColors.OnSurfaceMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(2f),
        )
        Text(
            "市值/数量",
            color = if (sort == com.tis.ibkr.viewmodel.PositionsSort.MarketValue) LbColors.Accent else LbColors.OnSurfaceMuted,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.5f),
        )
        Text(
            "现价/成本",
            color = LbColors.OnSurfaceMuted,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.3f),
        )
        Text(
            sortLabel,
            color = LbColors.Accent,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.5f),
        )
    }
    HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.5f), thickness = 0.5.dp)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PositionRow(
    p: Position,
    onClick: () -> Unit = {},
    onOrderClick: (String) -> Unit = {},
) {
    val marketValue = p.marketValue ?: (p.position * p.avgCost)
    val pnl = p.unrealizedPnl ?: 0.0
    val cost = marketValue - pnl
    val pct = if (cost == 0.0) 0.0 else pnl / cost * 100.0
    val price = p.marketPrice ?: p.avgCost
    var showSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Column(modifier = Modifier.combinedClickable(
        onClick = onClick,
        onLongClick = { showSheet = true },
    )) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(2f)) {
                Text(
                    p.symbol,
                    color = LbColors.OnSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${p.secType}${if (p.exchange.isNotBlank()) " · ${p.exchange}" else ""}",
                    color = LbColors.OnSurfaceMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(
                modifier = Modifier.weight(1.5f),
                horizontalAlignment = Alignment.End,
            ) {
                NumericText(
                    text = formatPrice(marketValue),
                    color = LbColors.OnSurface,
                    style = MaterialTheme.typography.bodyLarge,
                )
                NumericText(
                    text = formatQty(p.position),
                    color = LbColors.OnSurfaceMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(
                modifier = Modifier.weight(1.3f),
                horizontalAlignment = Alignment.End,
            ) {
                NumericText(
                    text = formatPrice3(price),
                    color = LbColors.OnSurface,
                    style = MaterialTheme.typography.bodyLarge,
                )
                NumericText(
                    text = formatPrice3(p.avgCost),
                    color = LbColors.OnSurfaceMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(
                modifier = Modifier.weight(1.5f),
                horizontalAlignment = Alignment.End,
            ) {
                NumericText(
                    text = formatSignedPrice(pnl),
                    color = changeColor(pnl),
                    style = MaterialTheme.typography.bodyLarge,
                )
                NumericText(
                    text = formatSignedPct(pct),
                    color = changeColor(pnl),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.3f), thickness = 0.5.dp)
    }

    if (showSheet) {
        QuickActionSheet(
            symbol = p.symbol,
            onDismiss = { showSheet = false },
            onBuy = { showSheet = false; onOrderClick("BUY") },
            onSell = { showSheet = false; onOrderClick("SELL") },
            onDetail = { showSheet = false; onClick() },
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionSheet(
    symbol: String,
    onDismiss: () -> Unit,
    onBuy: () -> Unit,
    onSell: () -> Unit,
    onDetail: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState()
    val items = listOf(
        Triple("加仓", LbColors.Up, onBuy),
        Triple("减仓", LbColors.Down, onSell),
        Triple("查看详情", LbColors.OnSurface, onDetail),
        Triple("复制代码", LbColors.OnSurface) {
            clipboard.setText(androidx.compose.ui.text.AnnotatedString(symbol))
            android.widget.Toast.makeText(context, "已复制 $symbol", android.widget.Toast.LENGTH_SHORT).show()
            onDismiss()
        },
    )
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LbColors.Surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(symbol, color = LbColors.OnSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.5f), thickness = 0.5.dp)
            items.forEach { (label, color, action) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { action() }.padding(vertical = 14.dp),
                ) {
                    Text(label, color = color, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
                HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.3f), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun OrdersHeader(count: Int) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "当日订单 ($count)",
            color = LbColors.OnSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.5f), thickness = 0.5.dp)
    }
}

@Composable
private fun OrderRow(o: OrderResponse, onCancel: () -> Unit) {
    val sideColor = if (o.side == "BUY") LbColors.Up else LbColors.Down
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(2f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (o.side == "BUY") "买入" else "卖出",
                        color = sideColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                    Text(o.symbol, color = LbColors.OnSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "${o.type} · ${formatQty(o.quantity)}股${o.price?.let { " @ ${formatPrice(it)}" } ?: ""}",
                    color = LbColors.OnSurfaceMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(modifier = Modifier.weight(1.5f), horizontalAlignment = Alignment.End) {
                Text(o.status, color = LbColors.OnSurface, style = MaterialTheme.typography.bodyMedium)
                if (o.filled > 0) {
                    Text(
                        "已成交 ${formatQty(o.filled)}",
                        color = LbColors.OnSurfaceMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            androidx.compose.material3.TextButton(onClick = onCancel) {
                Text("撤单", color = LbColors.Error)
            }
        }
        HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.3f), thickness = 0.5.dp)
    }
}

@Composable
private fun EmptyPositions() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("暂无持仓", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LbColors.Error.copy(alpha = 0.15f)),
    ) {
        Text(
            "刷新失败: $message",
            modifier = Modifier.padding(12.dp),
            color = LbColors.Error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("加载失败", color = LbColors.Error, style = MaterialTheme.typography.titleMedium)
            Text(message, color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall)
            Text(
                "去「我的」Tab 配置后端 URL + Token，或下拉刷新重试",
                color = LbColors.Disabled,
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LbColors.Accent,
                    contentColor = LbColors.OnSurface,
                ),
            ) { Text("重试") }
        }
    }
}
