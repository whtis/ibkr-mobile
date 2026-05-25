package com.tis.ibkr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tis.ibkr.IbkrApp
import com.tis.ibkr.data.api.Quote
import com.tis.ibkr.data.db.WatchlistItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WatchlistUiState(
    val items: List<WatchlistItem> = emptyList(),
    val quotes: Map<String, Quote> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null,
)

class WatchlistViewModel : ViewModel() {

    private val app get() = IbkrApp.instance
    private val _quotes = MutableStateFlow<Map<String, Quote>>(emptyMap())
    private val _loading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val state: StateFlow<WatchlistUiState> = combine(
        app.watchlist.observeAll(),
        _quotes,
        _loading,
        _error,
    ) { items, quotes, loading, error ->
        WatchlistUiState(items = items, quotes = quotes, loading = loading, error = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchlistUiState())

    private var pollingActive = true

    init { startPolling() }

    private fun startPolling() {
        viewModelScope.launch {
            while (pollingActive) {
                refreshOnce()
                delay(5_000)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { refreshOnce() }
    }

    private suspend fun refreshOnce() {
        val symbols = app.watchlist.allSymbols()
        if (symbols.isEmpty()) {
            _quotes.value = emptyMap()
            _error.update { null }
            return
        }
        _loading.update { true }
        runCatching { app.api.quotes(symbols) }
            .onSuccess { list ->
                _quotes.value = list.associateBy { it.symbol }
                _error.update { null }
            }
            .onFailure { e -> _error.update { e.message ?: e::class.simpleName } }
        _loading.update { false }
    }

    fun remove(symbol: String) {
        viewModelScope.launch { app.watchlist.remove(symbol) }
    }

    override fun onCleared() {
        pollingActive = false
        super.onCleared()
    }
}
