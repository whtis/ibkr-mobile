package com.tis.ibkr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tis.ibkr.IbkrApp
import com.tis.ibkr.data.api.SearchResult
import kotlinx.coroutines.CancellationException
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
        // Backend caps `q` at 32 chars (returns 422 beyond that); cap here so an
        // over-long query never round-trips into a raw error.
        val capped = q.take(32)
        _state.update { it.copy(query = capped) }
        debounceJob?.cancel()
        val trimmed = capped.trim()
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
        try {
            val items = app.api.searchSymbols(q)
            _state.update { it.copy(loading = false, results = items, error = null) }
        } catch (e: CancellationException) {
            // Superseded by a newer keystroke — honor cancellation, don't surface
            // it as a red "x0 was cancelled" error. (runCatching used to swallow this.)
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, results = emptyList(), error = friendlyError(e)) }
        }
    }

    private fun friendlyError(e: Throwable): String {
        val m = e.message ?: ""
        return when {
            m.contains("401") -> "Token 错误，去「我的」检查"
            m.contains("422") -> "搜索词无效，换个关键词试试"
            m.contains("502") || m.contains("503") -> "行情服务暂时连不上，稍后重试"
            m.contains("Failed to connect", ignoreCase = true) ||
                m.contains("ConnectException") ||
                m.contains("UnknownHost") -> "连不上后端，检查「我的」里的地址"
            else -> "搜索失败，稍后重试"  // never surface the raw client/JSON dump
        }
    }
}
