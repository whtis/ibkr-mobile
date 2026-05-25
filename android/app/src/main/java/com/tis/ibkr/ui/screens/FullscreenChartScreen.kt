package com.tis.ibkr.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tis.ibkr.ui.chart.IntradayChart
import com.tis.ibkr.ui.chart.KlineChart
import com.tis.ibkr.ui.theme.LbColors
import com.tis.ibkr.viewmodel.StockDetailViewModel

@Composable
fun FullscreenChartScreen(
    symbol: String,
    exchange: String,
    currency: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(Unit) {
        // Force landscape + immersive while screen is shown
        val prevOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.requestedOrientation =
                prevOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val vm: StockDetailViewModel = viewModel(
        key = "stock-$symbol-$exchange-$currency",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                StockDetailViewModel(symbol, exchange, currency) as T
        },
    )
    val state by vm.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(LbColors.Background)) {
        val execs = state.executions.map {
            com.tis.ibkr.ui.chart.ChartExecution(it.time, it.price, it.side, it.shares)
        }
        if (state.period == "intraday") {
            IntradayChart(
                points = state.filteredIntraday,
                prevClose = state.quote?.prevClose,
                costBasis = state.position?.avgCost,
                costShares = state.position?.position,
                executions = execs,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            KlineChart(
                bars = state.bars,
                period = state.period,
                costBasis = state.position?.avgCost,
                costShares = state.position?.position,
                executions = execs,
                modifier = Modifier.fillMaxSize(),
            )
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
        ) {
            Icon(Icons.Outlined.Close, "退出全屏", tint = LbColors.OnSurface)
        }
    }
}
