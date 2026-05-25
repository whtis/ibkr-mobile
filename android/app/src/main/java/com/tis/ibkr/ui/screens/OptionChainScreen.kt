package com.tis.ibkr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tis.ibkr.data.api.OptionContract
import com.tis.ibkr.ui.components.NumericText
import com.tis.ibkr.ui.components.changeColor
import com.tis.ibkr.ui.components.formatPrice
import com.tis.ibkr.ui.components.formatPrice3
import com.tis.ibkr.ui.components.formatSignedPct
import com.tis.ibkr.ui.theme.LbColors
import com.tis.ibkr.viewmodel.OptionChainViewModel

@Composable
fun OptionChainScreen(
    symbol: String,
    onBack: () -> Unit,
    onSelectContract: (OptionContract) -> Unit,
) {
    val vm: OptionChainViewModel = viewModel(
        key = "options-$symbol",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                OptionChainViewModel(symbol) as T
        },
    )
    val state by vm.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(LbColors.Background)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = LbColors.OnSurface)
            }
            Text(
                "${symbol.uppercase()} 期权链",
                color = LbColors.OnSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.5f), thickness = 0.5.dp)

        // Expiry chips
        if (state.expiries.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(state.expiries, key = { it.expiry }) { e ->
                    val selected = e.expiry == state.activeExpiry
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) LbColors.Accent.copy(alpha = 0.25f) else LbColors.Surface)
                            .clickable { vm.selectExpiry(e.expiry) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            e.expiry,
                            color = if (selected) LbColors.Accent else LbColors.OnSurfaceMuted,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        // Table header
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text("CALL 现价", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text("Δ", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
            Text("行权价", color = LbColors.OnSurface, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("Δ", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.6f))
            Text("现价 PUT", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.5f), thickness = 0.5.dp)

        when {
            state.loading && state.contracts.isEmpty() ->
                Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LbColors.Accent)
                }
            state.error != null && state.contracts.isEmpty() ->
                Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("加载失败：${state.error}", color = LbColors.Error, style = MaterialTheme.typography.bodySmall)
                }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(state.strikes, key = { it }) { strike ->
                    val call = state.calls[strike]
                    val put = state.puts[strike]
                    ChainRow(strike = strike, call = call, put = put, onSelect = onSelectContract)
                }
            }
        }
    }
}

@Composable
private fun ChainRow(
    strike: Double,
    call: OptionContract?,
    put: OptionContract?,
    onSelect: (OptionContract) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Call price
        Box(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = call != null) { call?.let(onSelect) },
        ) {
            Column {
                NumericText(text = formatPrice3(call?.last), color = if ((call?.changePct ?: 0.0) >= 0) LbColors.Up else LbColors.Down, style = MaterialTheme.typography.bodyMedium)
                NumericText(text = formatSignedPct(call?.changePct ?: 0.0), color = if ((call?.changePct ?: 0.0) >= 0) LbColors.Up else LbColors.Down, style = MaterialTheme.typography.labelSmall)
            }
        }
        // Call delta
        NumericText(text = call?.delta?.let { "%.3f".format(it) } ?: "--", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
        // Strike
        NumericText(text = formatPrice(strike), color = LbColors.OnSurface, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        // Put delta
        NumericText(text = put?.delta?.let { "%.3f".format(it) } ?: "--", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.6f))
        // Put price
        Box(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = put != null) { put?.let(onSelect) },
        ) {
            Column(horizontalAlignment = Alignment.End) {
                NumericText(text = formatPrice3(put?.last), color = if ((put?.changePct ?: 0.0) >= 0) LbColors.Up else LbColors.Down, style = MaterialTheme.typography.bodyMedium)
                NumericText(text = formatSignedPct(put?.changePct ?: 0.0), color = if ((put?.changePct ?: 0.0) >= 0) LbColors.Up else LbColors.Down, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.3f), thickness = 0.5.dp)
}
