package com.tis.ibkr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tis.ibkr.IbkrApp
import com.tis.ibkr.data.api.Quote
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Hard-coded reference universe for the market overview. */
object MarketUniverse {
    /** US indexes — Longbridge uses ".SYMBOL" convention. */
    val INDEXES = listOf(
        "S&P 500" to ".SPX",
        "纳斯达克" to ".IXIC",
        "道琼斯" to ".DJI",
        "罗素 2000" to ".RUT",
        "VIX" to ".VIX",
    )

    /** US blue chips + growth + popular ETFs. */
    val HOT = listOf(
        "AAPL", "MSFT", "NVDA", "GOOGL", "AMZN", "META", "TSLA",
        "AVGO", "ORCL", "JPM", "V", "MA", "WMT", "COST",
        "HD", "UNH", "JNJ", "LLY", "PG", "KO", "PEP",
        "NFLX", "AMD", "CRM", "ADBE", "INTC", "QQQ", "SPY", "DIA", "IWM",
    )
}

data class MarketUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val indexes: Map<String, Quote> = emptyMap(),
    val hot: Map<String, Quote> = emptyMap(),
) {
    val topGainers: List<Quote>
        get() = hot.values.filter { it.changePct != null }.sortedByDescending { it.changePct!! }.take(5)
    val topLosers: List<Quote>
        get() = hot.values.filter { it.changePct != null }.sortedBy { it.changePct!! }.take(5)
}

class MarketViewModel : ViewModel() {

    private val app get() = IbkrApp.instance
    private val _state = MutableStateFlow(MarketUiState())
    val state: StateFlow<MarketUiState> = _state.asStateFlow()

    private var pollActive = true

    init {
        viewModelScope.launch {
            while (pollActive) {
                refreshOnce()
                delay(5_000)
            }
        }
    }

    fun refresh() = viewModelScope.launch { refreshOnce() }

    private suspend fun refreshOnce() {
        _state.update { it.copy(loading = true) }
        val idxSymbols = MarketUniverse.INDEXES.map { it.second }
        val hotSymbols = MarketUniverse.HOT
        runCatching {
            val idxQuotes = runCatching { app.api.quotes(idxSymbols) }.getOrDefault(emptyList())
            val hotQuotes = runCatching { app.api.quotes(hotSymbols) }.getOrDefault(emptyList())
            idxQuotes.associateBy { it.symbol } to hotQuotes.associateBy { it.symbol }
        }.onSuccess { (idx, hot) ->
            _state.update { it.copy(loading = false, indexes = idx, hot = hot, error = null) }
        }.onFailure { e ->
            _state.update { it.copy(loading = false, error = e.message ?: e::class.simpleName) }
        }
    }

    override fun onCleared() {
        pollActive = false
        super.onCleared()
    }
}
