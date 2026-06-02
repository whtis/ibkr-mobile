package com.tis.ibkr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tis.ibkr.IbkrApp
import com.tis.ibkr.data.api.SearchResult
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
    val results: List<SearchResult> = emptyList(),
    val history: List<String> = emptyList(),
    val hot: List<String> = emptyList(),
)

class SearchViewModel : ViewModel() {

    private val app get() = IbkrApp.instance
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var debounceJob: Job? = null

    init {
        _state.update { it.copy(hot = MarketUniverse.HOT.take(10)) }
        viewModelScope.launch {
            app.settingsStore.searchHistory.collect { h -> _state.update { it.copy(history = h) } }
        }
    }

    /** Record an intentional pick (typed query, or the symbol when opened from a chip). */
    fun onResultOpened(symbol: String) {
        viewModelScope.launch {
            app.settingsStore.pushSearchHistory(_state.value.query.ifBlank { symbol })
        }
    }

    fun clearHistory() {
        viewModelScope.launch { app.settingsStore.clearSearchHistory() }
    }

    fun updateQuery(q: String) {
        _state.update { it.copy(query = q) }
        debounceJob?.cancel()
        val trimmed = q.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(results = emptyList(), error = null, loading = false) }
            return
        }
        debounceJob = viewModelScope.launch {
            delay(400)
            search(trimmed)
        }
    }

    private suspend fun search(q: String) {
        _state.update { it.copy(loading = true, error = null) }
        runCatching { app.api.searchSymbols(q) }
            .onSuccess { items ->
                _state.update { it.copy(loading = false, results = items, error = null) }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, results = emptyList(), error = friendlyError(e)) }
            }
    }

    private fun friendlyError(e: Throwable): String = when {
        e.message?.contains("401") == true -> "Token 错误，去「我的」检查"
        e.message?.contains("502") == true -> "IBKR 暂时连不上，稍后重试"
        else -> e.message ?: e::class.simpleName ?: "未知错误"
    }
}
