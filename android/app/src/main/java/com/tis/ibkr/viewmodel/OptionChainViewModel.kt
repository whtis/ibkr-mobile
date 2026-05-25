package com.tis.ibkr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tis.ibkr.IbkrApp
import com.tis.ibkr.data.api.OptionContract
import com.tis.ibkr.data.api.OptionExpiry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OptionChainUiState(
    val symbol: String,
    val loading: Boolean = false,
    val error: String? = null,
    val expiries: List<OptionExpiry> = emptyList(),
    val activeExpiry: String? = null,
    val contracts: List<OptionContract> = emptyList(),
) {
    val strikes: List<Double>
        get() = contracts.map { it.strike }.distinct().sorted()
    val calls: Map<Double, OptionContract>
        get() = contracts.filter { it.right == "C" }.associateBy { it.strike }
    val puts: Map<Double, OptionContract>
        get() = contracts.filter { it.right == "P" }.associateBy { it.strike }
}

class OptionChainViewModel(private val symbol: String) : ViewModel() {

    private val app get() = IbkrApp.instance
    private val _state = MutableStateFlow(OptionChainUiState(symbol = symbol.uppercase()))
    val state: StateFlow<OptionChainUiState> = _state.asStateFlow()

    init { loadExpiries() }

    private fun loadExpiries() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { app.api.optionExpiries(symbol) }
                .onSuccess { list ->
                    _state.update { it.copy(loading = false, expiries = list) }
                    list.firstOrNull()?.let { selectExpiry(it.expiry) }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: e::class.simpleName) } }
        }
    }

    fun selectExpiry(expiry: String) {
        if (expiry == _state.value.activeExpiry) return
        _state.update { it.copy(activeExpiry = expiry, contracts = emptyList(), loading = true, error = null) }
        viewModelScope.launch {
            runCatching { app.api.optionChain(symbol, expiry) }
                .onSuccess { list -> _state.update { it.copy(loading = false, contracts = list) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: e::class.simpleName) } }
        }
    }
}
