package com.tis.ibkr.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tis.ibkr.data.api.Quote
import com.tis.ibkr.data.api.StaticInfo
import com.tis.ibkr.ui.components.NumericText
import com.tis.ibkr.ui.components.changeColor
import com.tis.ibkr.ui.components.formatPrice3
import com.tis.ibkr.ui.components.formatSignedPct
import com.tis.ibkr.ui.theme.LbColors
import com.tis.ibkr.viewmodel.SearchViewModel

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenSymbol: (symbol: String, exchange: String, currency: String) -> Unit,
    vm: SearchViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = LbColors.OnSurface)
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::updateQuery,
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                placeholder = { Text("输入 symbol，如 TSLA / AAPL / 0700", color = LbColors.OnSurfaceMuted) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = LbColors.OnSurfaceMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Search,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = LbColors.Surface,
                    unfocusedContainerColor = LbColors.Surface,
                    focusedTextColor = LbColors.OnSurface,
                    unfocusedTextColor = LbColors.OnSurface,
                    focusedBorderColor = LbColors.Accent,
                    unfocusedBorderColor = LbColors.Outline,
                    cursorColor = LbColors.Accent,
                ),
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.5f), thickness = 0.5.dp)

        when {
            state.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LbColors.Accent)
            }
            state.error != null && state.query.isNotBlank() -> Text(
                state.error!!,
                modifier = Modifier.padding(24.dp),
                color = LbColors.Error,
                style = MaterialTheme.typography.bodyMedium,
            )
            state.info != null -> ResultCard(
                info = state.info!!,
                quote = state.quote,
                inWatchlist = state.inWatchlist,
                justAdded = state.justAdded,
                onAdd = vm::addToWatchlist,
                onRemove = vm::removeFromWatchlist,
                onOpen = { onOpenSymbol(state.info!!.symbol, state.info!!.exchange ?: "SMART", state.info!!.currency ?: "USD") },
            )
            state.query.isBlank() -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text(
                    "输入股票代码搜索\n美股 TSLA / 港股 700 / 沪深 600519",
                    color = LbColors.OnSurfaceMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ResultCard(
    info: StaticInfo,
    quote: Quote?,
    inWatchlist: Boolean,
    justAdded: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = LbColors.Surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(info.displayName, color = LbColors.OnSurface, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${info.symbol}${info.exchange?.let { " · $it" } ?: ""}${info.currency?.let { " · $it" } ?: ""}",
                        color = LbColors.OnSurfaceMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (quote?.last != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        NumericText(
                            text = formatPrice3(quote.last),
                            color = changeColor(quote.change ?: 0.0),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        quote.changePct?.let { pct ->
                            NumericText(
                                text = formatSignedPct(pct),
                                color = changeColor(quote.change ?: 0.0),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.5f), thickness = 0.5.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (inWatchlist) {
                    OutlinedButton(onClick = onRemove, modifier = Modifier.weight(1f)) {
                        Text("从自选移除")
                    }
                } else {
                    Button(
                        onClick = onAdd,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = LbColors.Accent, contentColor = LbColors.OnSurface),
                    ) { Text(if (justAdded) "✓ 已加入自选" else "加入自选") }
                }
                OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Text("查看 K 线")
                }
            }
        }
    }
}
