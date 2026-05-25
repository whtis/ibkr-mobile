package com.tis.ibkr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tis.ibkr.IbkrApp
import com.tis.ibkr.data.api.AccountSummary
import com.tis.ibkr.data.api.OrderResponse
import com.tis.ibkr.data.api.Position
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class PositionsSort { MarketValue, UnrealizedPnl, DailyPnl, Symbol }

data class PositionsUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val summaries: List<AccountSummary> = emptyList(),
    val positions: List<Position> = emptyList(),
    val activeOrders: List<OrderResponse> = emptyList(),
    val sort: PositionsSort = PositionsSort.MarketValue,
) {
    val sortedPositions: List<Position>
        get() = when (sort) {
            PositionsSort.MarketValue -> positions.sortedByDescending { abs(it.marketValue ?: (it.position * it.avgCost)) }
            PositionsSort.UnrealizedPnl -> positions.sortedByDescending { it.unrealizedPnl ?: 0.0 }
            PositionsSort.DailyPnl -> positions.sortedByDescending { it.dailyPnl ?: 0.0 }
            PositionsSort.Symbol -> positions.sortedBy { it.symbol }
        }

    /** Aggregate unrealized PnL summed from positions (more accurate than IBKR's per-account summary tag). */
    val totalUnrealizedPnl: Double
        get() = positions.sumOf { it.unrealizedPnl ?: 0.0 }

    /** First non-"All" summary, for headline display. */
    val primarySummary: AccountSummary?
        get() = summaries.firstOrNull { it.accountId != "All" } ?: summaries.firstOrNull()
}

class PositionsViewModel : ViewModel() {

    private val app get() = IbkrApp.instance
    private val _state = MutableStateFlow(PositionsUiState())
    val state: StateFlow<PositionsUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val summaries = app.api.accountSummary()
                val positions = app.api.positions()
                val orders = runCatching { app.api.activeOrders() }.getOrDefault(emptyList())
                Triple(summaries, positions, orders)
            }.onSuccess { (s, p, o) ->
                _state.update {
                    it.copy(loading = false, summaries = s, positions = p, activeOrders = o, error = null)
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(loading = false, error = e.message ?: e::class.simpleName)
                }
            }
        }
    }

    fun cancelOrder(orderId: Int) {
        viewModelScope.launch {
            runCatching { app.api.cancelOrder(orderId) }
            refresh()
        }
    }

    fun cycleSort() {
        val next = when (_state.value.sort) {
            PositionsSort.MarketValue -> PositionsSort.UnrealizedPnl
            PositionsSort.UnrealizedPnl -> PositionsSort.DailyPnl
            PositionsSort.DailyPnl -> PositionsSort.Symbol
            PositionsSort.Symbol -> PositionsSort.MarketValue
        }
        _state.update { it.copy(sort = next) }
    }
}
