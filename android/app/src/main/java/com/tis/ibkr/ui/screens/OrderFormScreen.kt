package com.tis.ibkr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tis.ibkr.ui.components.NumericText
import com.tis.ibkr.ui.components.formatSignedPct
import com.tis.ibkr.ui.theme.LbColors
import com.tis.ibkr.viewmodel.OrderFormUiState
import com.tis.ibkr.viewmodel.OrderFormViewModel

private val ORDER_TYPE_LABELS = mapOf("LMT" to "限价单", "MKT" to "市价单")
private val TIF_LABELS = mapOf("DAY" to "当日有效", "GTC" to "至取消")
private fun sessionLabel(outsideRth: Boolean) = if (outsideRth) "盘中 + 盘前盘后" else "仅盘中"

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OrderFormScreen(
    symbol: String,
    side: String,
    exchange: String = "SMART",
    currency: String = "USD",
    secType: String = "STK",
    expiry: String? = null,
    strike: Double? = null,
    right: String? = null,
    onBack: () -> Unit,
) {
    val vmKey = "order-$symbol-$side-$secType-${expiry.orEmpty()}-${strike ?: 0}-${right.orEmpty()}"
    val vm: OrderFormViewModel = viewModel(
        key = vmKey,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                OrderFormViewModel(symbol, side, exchange, currency, secType, expiry, strike, right) as T
        },
    )
    val state by vm.state.collectAsState()

    var showPreview by remember { mutableStateOf(false) }
    val previewSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LbColors.Background)
            .verticalScroll(rememberScrollState()),
    ) {
        TopBarRow(state, onBack)
        AccountBar()
        SymbolPriceRow(state)
        DepthSplitBar(state)
        BboRow(state)
        HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.35f))
        OrderTypeRow(state, vm::setOrderType)
        HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.35f))
        DirectionSegmented(state.side, vm::setSide)
        Spacer(Modifier.height(6.dp))
        PriceRow(state, vm)
        QtyRow(state, vm)
        AffordRow(state)
        EstimateRow(state)
        DropdownRow<String>(
            label = "有效期",
            valueLabel = TIF_LABELS[state.tif] ?: state.tif,
            options = TIF_LABELS.map { it.value to it.key },
            onPick = { (_, k) -> vm.setTif(k) },
        )
        DropdownRow<Boolean>(
            label = "时段",
            valueLabel = sessionLabel(state.outsideRth),
            options = listOf("仅盘中" to false, "盘中 + 盘前盘后" to true),
            onPick = { (_, v) -> vm.setOutsideRth(v) },
        )
        Spacer(Modifier.height(12.dp))
        SubmitBar(
            state = state,
            onSubmit = { vm.submit() },
            onPreview = { showPreview = true },
        )
    }

    // -- Preview sheet
    if (showPreview) {
        ModalBottomSheet(
            onDismissRequest = { showPreview = false },
            sheetState = previewSheetState,
            containerColor = LbColors.Surface,
        ) {
            OrderPreviewSheetContent(state) { showPreview = false }
        }
    }

    // -- Success / Error dialogs
    state.success?.let { resp ->
        AlertDialog(
            onDismissRequest = vm::dismissSuccess,
            confirmButton = {
                TextButton(onClick = {
                    vm.dismissSuccess()
                    onBack()
                }) { Text("完成", color = LbColors.Accent) }
            },
            title = { Text("订单已提交", color = LbColors.OnSurface) },
            text = {
                Column {
                    Text("订单 ID: ${resp.orderId}", color = LbColors.OnSurface)
                    Text("状态: ${resp.status}", color = LbColors.OnSurfaceMuted)
                }
            },
            containerColor = LbColors.Surface,
        )
    }
    state.error?.let { err ->
        AlertDialog(
            onDismissRequest = vm::dismissError,
            confirmButton = { TextButton(onClick = vm::dismissError) { Text("好", color = LbColors.Accent) } },
            title = { Text("下单失败", color = LbColors.Error) },
            text = { Text(err, color = LbColors.OnSurface) },
            containerColor = LbColors.Surface,
        )
    }
}

// ---------------- Header ----------------

@Composable
private fun TopBarRow(state: OrderFormUiState, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = LbColors.OnSurface)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (state.secType == "OPT") "${state.symbol} 单腿期权" else state.symbol,
                color = LbColors.OnSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.secType == "OPT") {
                val rightLabel = if (state.right == "C") "Call" else "Put"
                val strikeLabel = state.strike?.let { "%.0f".format(it) } ?: "--"
                val expiryCompact = state.expiry?.replace("-", "")?.takeLast(6) ?: "--"
                Text(
                    "${state.symbol.replace(".", "")} $expiryCompact $strikeLabel $rightLabel",
                    color = LbColors.OnSurfaceMuted,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.width(40.dp)) // visual balance for the back button
    }
}

@Composable
private fun AccountBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(LbColors.Accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "IBKR 模拟账户 (DUQ096796)",
            color = LbColors.OnSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SymbolPriceRow(state: OrderFormUiState) {
    val q = state.quote
    val last = q?.last
    val change = q?.change ?: 0.0
    val color = if (change >= 0) LbColors.Up else LbColors.Down
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            NumericText(
                text = if (last != null) "%.3f".format(last) else "--",
                color = color,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(end = 8.dp),
            )
            if (last != null && q.changePct != null) {
                NumericText(
                    text = "%+.3f".format(change),
                    color = color,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 6.dp),
                )
                NumericText(
                    text = formatSignedPct(q.changePct ?: 0.0),
                    color = color,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        val post = q?.postMarket?.last
        if (post != null) {
            val postChange = q.postMarket?.change ?: 0.0
            val postPct = q.postMarket?.changePct ?: 0.0
            val postColor = if (postChange >= 0) LbColors.Up else LbColors.Down
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("盘后", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 6.dp))
                NumericText(text = "%.3f".format(post), color = postColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 6.dp))
                NumericText(text = "%+.3f".format(postChange), color = postColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 6.dp))
                NumericText(text = formatSignedPct(postPct), color = postColor, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ---------------- Depth split bar + BBO ----------------

@Composable
private fun DepthSplitBar(state: OrderFormUiState) {
    val buyShare = state.buyShare
    val sellShare = 1f - buyShare
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("买盘", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(4.dp))
            NumericText(
                text = "%.2f%%".format(buyShare * 100),
                color = LbColors.Up,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            NumericText(
                text = "%.2f%%".format(sellShare * 100),
                color = LbColors.Down,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End,
            )
            Spacer(Modifier.width(4.dp))
            Text("卖盘", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
            Box(modifier = Modifier.weight(buyShare.coerceAtLeast(0.01f)).fillMaxSize().background(LbColors.Up))
            Spacer(Modifier.width(2.dp))
            Box(modifier = Modifier.weight(sellShare.coerceAtLeast(0.01f)).fillMaxSize().background(LbColors.Down))
        }
    }
}

@Composable
private fun BboRow(state: OrderFormUiState) {
    val bestBid = state.bestBid
    val bestAsk = state.bestAsk
    val bidVol = state.depth?.bids?.firstOrNull()?.volume ?: 0
    val askVol = state.depth?.asks?.firstOrNull()?.volume ?: 0
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("BBO", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(8.dp))
        NumericText(text = bidVol.toString(), color = LbColors.OnSurface, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.weight(1f))
        NumericText(
            text = bestBid?.let { "%.2f".format(it) } ?: "--",
            color = LbColors.Up,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(end = 6.dp),
        )
        Text("/", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall)
        NumericText(
            text = bestAsk?.let { "%.2f".format(it) } ?: "--",
            color = LbColors.Down,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp),
        )
        Spacer(Modifier.weight(1f))
        NumericText(text = askVol.toString(), color = LbColors.OnSurface, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(8.dp))
        Text("BBO", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.labelSmall)
    }
}

// ---------------- Order type dropdown ----------------

@Composable
private fun OrderTypeRow(state: OrderFormUiState, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("类型", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(LbColors.SurfaceElevated)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ORDER_TYPE_LABELS[state.orderType] ?: state.orderType,
                    color = LbColors.OnSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(4.dp))
                Text("▼", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.labelSmall)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(LbColors.SurfaceElevated),
            ) {
                ORDER_TYPE_LABELS.forEach { (k, label) ->
                    DropdownMenuItem(
                        text = { Text(label, color = LbColors.OnSurface) },
                        onClick = { onPick(k); expanded = false },
                    )
                }
            }
        }
    }
}

// ---------------- Direction segmented ----------------

@Composable
private fun DirectionSegmented(side: String, onPick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("方向", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(60.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(LbColors.SurfaceElevated),
        ) {
            SegmentedTab("买入", side == "BUY", LbColors.Up) { onPick("BUY") }
            SegmentedTab("卖出", side == "SELL", LbColors.Down) { onPick("SELL") }
        }
    }
}

@Composable
private fun RowScope.SegmentedTab(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) color else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color.White else LbColors.OnSurfaceMuted,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// Required for RowScope receiver above
private typealias RowScope = androidx.compose.foundation.layout.RowScope

// ---------------- Price + Qty steppers ----------------

@Composable
private fun PriceRow(state: OrderFormUiState, vm: OrderFormViewModel) {
    StepperRow(
        label = "价格",
        valueText = state.priceText,
        onValueChange = vm::setPrice,
        onMinus = { vm.bumpPrice(-0.01) },
        onPlus = { vm.bumpPrice(0.01) },
        keyboardType = KeyboardType.Decimal,
        placeholder = state.quote?.last?.let { "%.2f".format(it) } ?: "--",
        trailingIcon = {
            IconButton(onClick = { vm.snapPriceToCurrent() }) {
                Icon(Icons.Outlined.GpsFixed, "对齐当前价", tint = LbColors.OnSurfaceMuted, modifier = Modifier.size(20.dp))
            }
        },
    )
}

@Composable
private fun QtyRow(state: OrderFormUiState, vm: OrderFormViewModel) {
    var menuExpanded by remember { mutableStateOf(false) }
    StepperRow(
        label = "数量",
        valueText = state.quantityText,
        onValueChange = vm::setQuantity,
        onMinus = { vm.bumpQuantity(-1) },
        onPlus = { vm.bumpQuantity(1) },
        keyboardType = KeyboardType.Number,
        placeholder = if (state.secType == "OPT") "张" else "股",
        trailingIcon = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    // Stacked-layers icon
                    Text("☰", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.titleMedium)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(LbColors.SurfaceElevated),
                ) {
                    listOf(0.25, 0.50, 0.75, 1.00).forEach { f ->
                        DropdownMenuItem(
                            text = { Text("%.0f%%".format(f * 100) + " 现金可买", color = LbColors.OnSurface) },
                            onClick = {
                                vm.setQuantityFraction(f)
                                menuExpanded = false
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun StepperRow(
    label: String,
    valueText: String,
    onValueChange: (String) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    keyboardType: KeyboardType,
    placeholder: String,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(60.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(LbColors.SurfaceElevated),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onMinus) { Text("－", color = LbColors.OnSurface, style = MaterialTheme.typography.titleMedium) }
            BasicTextField(
                value = valueText,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = LbColors.OnSurface, fontSize = 18.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(LbColors.Accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                        if (valueText.isBlank()) {
                            Text(placeholder, color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
                        }
                        inner()
                    }
                },
            )
            IconButton(onClick = onPlus) { Text("＋", color = LbColors.OnSurface, style = MaterialTheme.typography.titleMedium) }
        }
        if (trailingIcon != null) {
            Spacer(Modifier.width(4.dp))
            trailingIcon()
        }
    }
}

// ---------------- Affordability + estimate ----------------

@Composable
private fun AffordRow(state: OrderFormUiState) {
    val unit = if (state.secType == "OPT") "张" else "股"
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
        Text("现金可买 ", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall)
        NumericText(text = "${state.maxCashAffordable} $unit", color = LbColors.Up, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        if (state.secType == "OPT") {
            Text("1张对应100股", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EstimateRow(state: OrderFormUiState) {
    val amt = state.estimatedAmount
    val newCost = computeNewCostBasis(state)
    val title = if (state.secType == "OPT") "预估支付权利金" else "预估金额"
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            NumericText(
                text = if (amt != null) "%.2f USD".format(amt) else "0.00 USD",
                color = LbColors.OnSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row {
            Spacer(Modifier.weight(1f))
            Text(
                "预估成交后持仓成本 ${newCost?.let { "%.2f".format(it) } ?: "--"} USD",
                color = LbColors.OnSurfaceMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun computeNewCostBasis(s: OrderFormUiState): Double? {
    val p = s.price ?: s.quote?.last ?: return null
    val q = s.quantity ?: return null
    if (q <= 0) return null
    val existingQty = s.position?.position ?: 0.0
    val existingCost = s.position?.avgCost ?: 0.0
    val newQty = if (s.side == "BUY") existingQty + q else existingQty - q
    if (newQty <= 0) return null
    val newTotalCost = if (s.side == "BUY")
        existingQty * existingCost + q * p
    else
        existingQty * existingCost - q * p  // approximate; sell reduces basis proportionally
    return newTotalCost / newQty
}

// ---------------- Dropdown row (TIF / session) ----------------

@Composable
private fun <T> DropdownRow(
    label: String,
    valueLabel: String,
    options: List<Pair<String, T>>,
    onPick: (Pair<String, T>) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(LbColors.SurfaceElevated)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(valueLabel, color = LbColors.OnSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(4.dp))
                Text("▼", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.labelSmall)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(LbColors.SurfaceElevated),
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt.first, color = LbColors.OnSurface) },
                        onClick = { onPick(opt); expanded = false },
                    )
                }
            }
        }
    }
}

// ---------------- Submit bar ----------------

@Composable
private fun SubmitBar(state: OrderFormUiState, onSubmit: () -> Unit, onPreview: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (state.side == "BUY") LbColors.Up else LbColors.Down
        val label = if (state.side == "BUY") "买入下单" else "卖出下单"
        Button(
            onClick = onSubmit,
            enabled = state.canSubmit,
            modifier = Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)),
            colors = ButtonDefaults.buttonColors(
                containerColor = color,
                contentColor = Color.White,
                disabledContainerColor = color.copy(alpha = 0.35f),
                disabledContentColor = Color.White.copy(alpha = 0.7f),
            ),
        ) {
            Text(if (state.submitting) "提交中..." else label, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onPreview)) {
            Text("📋", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.titleMedium)
            Text("预览", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ---------------- Preview sheet ----------------

@Composable
private fun OrderPreviewSheetContent(state: OrderFormUiState, onDismiss: () -> Unit) {
    val amt = state.estimatedAmount ?: 0.0
    val newCost = computeNewCostBasis(state)
    val cash = state.accountSummary?.totalCash ?: 0.0
    val sign = if (state.side == "BUY") -1 else 1
    val cashAfter = cash + sign * amt
    val buyingPower = state.accountSummary?.buyingPower ?: 0.0
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("订单预览", color = LbColors.OnSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("✕", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clickable(onClick = onDismiss))
        }
        Spacer(Modifier.height(16.dp))
        PreviewRow("预估订单金额", "%.2f".format(amt), subtitle = "${state.quantity?.toLong() ?: 0}@${state.priceText.ifBlank { "--" }}")
        HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 12.dp))
        PreviewRow("预估费用", "0.00")
        PreviewRowSub("· 佣金", "≈ 1.00")    // IBKR US stock ~$1 per trade for our paper account
        PreviewRowSub("· 平台费", "--")
        PreviewRowSub("· 第三方收费", "--")
        HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 12.dp))
        PreviewRow("预估成交后持仓成本", "${newCost?.let { "%.2f".format(it) } ?: "--"} USD")
        PreviewRow("预估订单总额", "USD %.2f".format(amt))
        Spacer(Modifier.height(8.dp))
        Text(
            "说明: 当前计算的金额为预估值，仅供参考。最终收费情况，请以实际日结单的数据为准。",
            color = LbColors.OnSurfaceMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))
        Text("账户变动预览（USD）", color = LbColors.OnSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        AccountDelta("现金", cash, cashAfter)
        AccountDelta("购买力", buyingPower, buyingPower + sign * amt)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PreviewRow(label: String, value: String, subtitle: String? = null) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = LbColors.OnSurface, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            NumericText(text = value, color = LbColors.OnSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PreviewRowSub(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        NumericText(text = value, color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AccountDelta(label: String, before: Double, after: Double) {
    val delta = after - before
    val deltaColor = if (delta == 0.0) LbColors.OnSurfaceMuted else if (delta > 0) LbColors.Up else LbColors.Down
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = LbColors.OnSurface, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        NumericText(text = "%.2f".format(before), color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
            NumericText(text = "%.2f".format(after), color = LbColors.OnSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            NumericText(text = "%+.2f".format(delta), color = deltaColor, style = MaterialTheme.typography.bodySmall)
        }
    }
}
