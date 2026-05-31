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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tis.ibkr.data.api.SearchResult
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
                placeholder = { Text("代码或公司名，如 TSLA / alibaba / 700", color = LbColors.OnSurfaceMuted) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = LbColors.OnSurfaceMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
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
            state.query.isBlank() -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text(
                    "输入股票代码或公司名搜索
美股 TSLA / alibaba · 港股 700 · 关键字也行",
                    color = LbColors.OnSurfaceMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            state.results.isEmpty() -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text("没有匹配的结果", color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
            }
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                items(state.results, key = { it.symbol + "@" + (it.primaryExchange ?: "") + (it.currency ?: "") }) { r ->
                    SearchRow(
                        result = r,
                        onClick = {
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
private fun SearchRow(result: SearchResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        Text(
            listOfNotNull(result.primaryExchange, result.currency).joinToString(" · "),
            color = LbColors.OnSurfaceMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
