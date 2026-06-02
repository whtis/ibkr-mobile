package com.tis.ibkr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tis.ibkr.data.api.SearchResult
import com.tis.ibkr.ui.theme.LbColors
import com.tis.ibkr.viewmodel.SearchViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenSymbol: (symbol: String, exchange: String, currency: String) -> Unit,
    vm: SearchViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val focusRequester = remember { FocusRequester() }
    // Auto-focus the field and pop the keyboard the moment search opens.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(modifier = Modifier.fillMaxSize().padding(top = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        ) {
            // Filled, fully-rounded search pill — no border, light-gray fill.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(LbColors.SurfaceElevated)
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = LbColors.OnSurfaceMuted,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (state.query.isEmpty()) {
                        Text(
                            "代码或公司名，如 TSLA / alibaba / 700",
                            color = LbColors.OnSurfaceMuted,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                    }
                    BasicTextField(
                        value = state.query,
                        onValueChange = vm::updateQuery,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = LbColors.OnSurface),
                        cursorBrush = SolidColor(LbColors.Accent),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Search,
                        ),
                    )
                }
                if (state.query.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "清除",
                        tint = LbColors.OnSurfaceMuted,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable { vm.updateQuery("") },
                    )
                }
            }
            Text(
                "取消",
                color = LbColors.OnSurface,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

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
            state.query.isBlank() -> Column(Modifier.fillMaxWidth().padding(16.dp)) {
                if (state.history.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("历史记录", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall)
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "清空历史",
                            tint = LbColors.OnSurfaceMuted,
                            modifier = Modifier.size(18.dp).clip(CircleShape).clickable { vm.clearHistory() },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.history.forEach { h -> SearchChip(h) { vm.updateQuery(h) } }
                    }
                    Spacer(Modifier.height(20.dp))
                }
                Text("热门股票", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.hot.forEach { sym ->
                        SearchChip(sym) {
                            vm.onResultOpened(sym)
                            onOpenSymbol(sym, "SMART", "USD")
                        }
                    }
                }
            }
            state.results.isEmpty() -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text("没有匹配的结果", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
            }
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                items(state.results, key = { it.symbol + "@" + (it.primaryExchange ?: "") + (it.currency ?: "") }) { r ->
                    SearchRow(
                        result = r,
                        onClick = {
                            vm.onResultOpened(r.symbol)
                            onOpenSymbol(
                                r.symbol,
                                r.primaryExchange ?: "SMART",
                                r.currency ?: "USD",
                            )
                        },
                    )
                    HorizontalDivider(color = LbColors.Outline.copy(alpha = 0.3f), thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun SearchChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(LbColors.SurfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, color = LbColors.OnSurface, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

@Composable
private fun SearchRow(result: SearchResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    result.symbol,
                    color = LbColors.OnSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(LbColors.SurfaceElevated)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(result.secType, color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
            result.name?.takeIf { it.isNotBlank() }?.let { n ->
                Spacer(Modifier.height(2.dp))
                Text(
                    n,
                    color = LbColors.OnSurfaceMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text(
            listOfNotNull(result.primaryExchange, result.currency).joinToString(" · "),
            color = LbColors.OnSurfaceMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
