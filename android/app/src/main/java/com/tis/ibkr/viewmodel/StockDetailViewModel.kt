package com.tis.ibkr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tis.ibkr.IbkrApp
import com.tis.ibkr.data.api.Bar
import com.tis.ibkr.data.api.Position
import com.tis.ibkr.data.api.Depth
import com.tis.ibkr.data.api.IntradayPoint
import com.tis.ibkr.data.api.Quote
import com.tis.ibkr.data.api.StaticInfo
import com.tis.ibkr.data.api.TradeTick
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class IntradaySession(val code: Int?) { All(null), PreMarket(1), Normal(0), PostMarket(2), Overnight(3) }

data class StockDetailUiState(
    val symbol: String,
    // Default to intraday with the current ET session selected — that's almost always what
    // the user wants when opening a chart during market hours / extended hours / overnight.
    val period: String = "intraday",
    val loading: Boolean = false,
    val error: String? = null,
    val bars: List<Bar> = emptyList(),
    val intraday: List<IntradayPoint> = emptyList(),
    val position: Position? = null,
    val info: StaticInfo? = null,
    val quote: Quote? = null,
    val depth: Depth? = null,
    val trades: List<TradeTick> = emptyList(),
    val executions: List<com.tis.ibkr.data.api.ExecutionTick> = emptyList(),
    val intradaySession: IntradaySession = currentEtSession(),
) {
    /**
     * Filters intraday points by the selected session.
     *
     * For 盘前/盘后/夜盘: also enforce a "trading-day rollover" — once we're past the most
     * recent post-market close (20:00 ET), we no longer surface that day's pre/post/overnight
     * data. The user wants the *upcoming* session, not yesterday's stale one; if the new
     * session hasn't begun, the chart shows empty and the UI prompts "等待 X 开始".
     *
     * For 盘中 / 全部: keep all data (the main session is still the most useful "recent
     * trading action" view when sitting in the gap between trading days).
     */
    val filteredIntraday: List<IntradayPoint>
        get() {
            val code = intradaySession.code ?: return intraday
            val byType = intraday.filter { it.lineType == code }
            return when (intradaySession) {
                IntradaySession.PreMarket,
                IntradaySession.PostMarket,
                IntradaySession.Overnight -> byType.filter { it.time >= postCloseRolloverEpoch() }
                else -> byType
            }
        }
}

/**
 * The trading session that's "currently happening" in US Eastern Time. Used as the default
 * session selection so opening a chart during overnight hours surfaces the overnight view
 * instead of leaving the user staring at yesterday's pre-market.
 */
fun currentEtSession(): IntradaySession {
    val now = java.time.ZonedDateTime.now(java.time.ZoneId.of("America/New_York"))
    val hm = now.hour + now.minute / 60.0
    return when {
        hm >= 4.0 && hm < 9.5 -> IntradaySession.PreMarket
        hm >= 9.5 && hm < 16.0 -> IntradaySession.Normal
        hm >= 16.0 && hm < 20.0 -> IntradaySession.PostMarket
        else -> IntradaySession.Overnight  // 20:00–04:00 ET
    }
}

/**
 * Epoch seconds of the most recent 20:00 ET (post-market end). Once `now` crosses this
 * boundary the "trading day" has rolled over — any data with timestamp < this is from
 * the previous trading day and should be hidden from edge-session filters.
 */
private fun postCloseRolloverEpoch(): Long {
    val et = java.time.ZoneId.of("America/New_York")
    val now = java.time.ZonedDateTime.now(et)
    val rollover = if (now.hour >= 20) {
        now.toLocalDate().atTime(20, 0).atZone(et)
    } else {
        now.toLocalDate().minusDays(1).atTime(20, 0).atZone(et)
    }
    return rollover.toEpochSecond()
}

class StockDetailViewModel(
    private val symbol: String,
    private val exchange: String,
    private val currency: String,
) : ViewModel() {

    private val app get() = IbkrApp.instance
    private val _state = MutableStateFlow(StockDetailUiState(symbol = symbol))
    val state: StateFlow<StockDetailUiState> = _state.asStateFlow()

    val isFavorite: StateFlow<Boolean> = app.watchlist.observeContains(symbol)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        refresh()
        startRealtime()
    }

    private fun startRealtime() {
        val stream = app.quoteStream
        stream.subscribe(listOf(symbol), currency)
        viewModelScope.launch {
            stream.ticks
                .filter { it.symbol.equals(symbol, ignoreCase = true) }
                .collect { tick ->
                    _state.update { s ->
                        val prev = s.quote ?: return@update s
                        val newLast = tick.last ?: prev.last
                        val newChange = if (newLast != null && prev.prevClose != null)
                            newLast - prev.prevClose else prev.change
                        val newChangePct = if (newLast != null && prev.prevClose != null && prev.prevClose != 0.0)
                            (newLast - prev.prevClose) / prev.prevClose * 100.0 else prev.changePct
                        s.copy(
                            quote = prev.copy(
                                last = newLast,
                                open = tick.open ?: prev.open,
                                high = tick.high ?: prev.high,
                                low = tick.low ?: prev.low,
                                volume = if (tick.volume > 0) tick.volume.toDouble() else prev.volume,
                                change = newChange,
                                changePct = newChangePct,
                            ),
                        )
                    }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { app.quoteStream.unsubscribe(listOf(symbol), currency) }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val current = isFavorite.value
            if (current) {
                app.watchlist.remove(symbol)
            } else {
                val info = _state.value.info
                app.watchlist.add(
                    symbol = symbol,
                    exchange = info?.exchange ?: exchange,
                    currency = info?.currency ?: currency,
                    name = info?.displayName ?: symbol,
                )
            }
        }
    }

    fun changePeriod(period: String) {
        if (period == _state.value.period) return
        _state.update { it.copy(period = period) }
        refresh()
    }

    fun setIntradaySession(s: IntradaySession) {
        _state.update { it.copy(intradaySession = s) }
    }

    fun refresh() {
        val period = _state.value.period
        // 1) Seed chart instantly from in-memory cache (if any). Stale data still paints
        // immediately; the live fetch below corrects it within ~hundreds of ms.
        val cachedBars = if (period != "intraday") com.tis.ibkr.data.cache.ChartCache.getBars(symbol, period) else null
        val cachedIntra = if (period == "intraday") com.tis.ibkr.data.cache.ChartCache.getIntraday(symbol) else null
        _state.update {
            it.copy(
                error = null,
                bars = cachedBars ?: it.bars,
                intraday = cachedIntra ?: it.intraday,
                loading = cachedBars == null && cachedIntra == null,
            )
        }
        // 2) Fire each fetch independently so the chart, quote, depth, trades, etc. all
        // populate as soon as their own network call returns. No single call blocks the chart.
        viewModelScope.launch {
            if (period == "intraday") {
                runCatching { app.api.intraday(symbol) }
                    .onSuccess { data ->
                        com.tis.ibkr.data.cache.ChartCache.putIntraday(symbol, data)
                        _state.update { it.copy(intraday = data, loading = false) }
                    }
                    .onFailure { e -> _state.update { s -> s.copy(loading = false, error = s.error ?: e.message) } }
            } else {
                runCatching { app.api.bars(symbol, period) }
                    .onSuccess { data ->
                        com.tis.ibkr.data.cache.ChartCache.putBars(symbol, period, data)
                        _state.update { it.copy(bars = data, loading = false) }
                    }
                    .onFailure { e -> _state.update { s -> s.copy(loading = false, error = s.error ?: e.message) } }
            }
        }
        viewModelScope.launch {
            runCatching { app.api.positions().firstOrNull { it.symbol == symbol } }
                .onSuccess { d -> _state.update { it.copy(position = d) } }
        }
        viewModelScope.launch {
            runCatching { app.api.staticInfo(symbol) }
                .onSuccess { d -> _state.update { it.copy(info = d) } }
        }
        viewModelScope.launch {
            runCatching { app.api.quote(symbol) }
                .onSuccess { d -> _state.update { it.copy(quote = d) } }
        }
        viewModelScope.launch {
            runCatching { app.api.depth(symbol) }
                .onSuccess { d -> _state.update { it.copy(depth = d) } }
        }
        viewModelScope.launch {
            runCatching { app.api.trades(symbol, 30) }
                .onSuccess { d -> _state.update { it.copy(trades = d) } }
        }
        viewModelScope.launch {
            runCatching { app.api.executions(symbol) }
                .onSuccess { d -> _state.update { it.copy(executions = d) } }
        }
    }
}
