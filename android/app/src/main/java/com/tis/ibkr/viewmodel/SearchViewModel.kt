package com.tis.ibkr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tis.ibkr.IbkrApp
import com.tis.ibkr.data.api.Quote
import com.tis.ibkr.data.api.StaticInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val info: StaticInfo? = null,
    val quote: Quote? = null,
    val inWatchlist: Boolean = false,
    val justAdded: Boolean = false,
)

class SearchViewModel : ViewModel() {

    private val app get() = IbkrApp.instance
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var debounceJob: Job? = null

    fun updateQuery(q: String) {
        _state.update { it.copy(query = q, justAdded = false) }
        debounceJob?.cancel()
        if (q.isBlank() || q.length < 1) {
            _state.update { it.copy(info = null, quote = null, error = null, loading = false) }
            return
        }
        debounceJob = viewModelScope.launch {
            delay(400)
            lookup(q.trim().uppercase())
        }
    }

    private suspend fun lookup(symbol: String) {
        _state.update { it.copy(loading = true, error = null) }
        runCatching {
            val info = app.api.staticInfo(symbol)
            val quote = runCatching { app.api.quotes(listOf(symbol)).firstOrNull() }.getOrNull()
            val contains = app.watchlist.allSymbols().contains(symbol)
            Triple(info, quote, contains)
        }.onSuccess { (info, quote, contains) ->
            _state.update {
                it.copy(loading = false, info = info, quote = quote, inWatchlist = contains, error = null)
            }
        }.onFailure { e ->
            _state.update {
                it.copy(loading = false, info = null, quote = null, error = friendlyError(e))
            }
        }
    }

    fun addToWatchlist() {
        val info = _state.value.info ?: return
        viewModelScope.launch {
            app.watchlist.add(
                symbol = info.symbol,
                exchange = info.exchange ?: "SMART",
                currency = info.currency ?: "USD",
                name = info.displayName,
            )
            _state.update { it.copy(inWatchlist = true, justAdded = true) }
        }
    }

    fun removeFromWatchlist() {
        val info = _state.value.info ?: return
        viewModelScope.launch {
            app.watchlist.remove(info.symbol)
            _state.update { it.copy(inWatchlist = false, justAdded = false) }
        }
    }

    private fun friendlyError(e: Throwable): String = when {
        e.message?.contains("404") == true -> "找不到这个 symbol"
        e.message?.contains("503") == true -> "长桥未配置"
        e.message?.contains("401") == true -> "Token 错误，去「我的」检查"
        else -> e.message ?: e::class.simpleName ?: "未知错误"
    }
}
