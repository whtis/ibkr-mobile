package com.tis.ibkr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tis.ibkr.IbkrApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val backendUrl: String = "",
    val token: String = "",
    val connectionOk: Boolean = false,
    val connectionMessage: String? = null,
)

class SettingsViewModel : ViewModel() {

    private val app get() = IbkrApp.instance
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            app.settingsStore.flow.collect { s ->
                _state.update { it.copy(backendUrl = s.backendUrl, token = s.token) }
            }
        }
    }

    suspend fun save(url: String, token: String) {
        app.settingsStore.save(url, token)
        _state.update { it.copy(connectionMessage = "已保存", connectionOk = true) }
    }

    suspend fun testConnection() {
        runCatching { app.api.health() }
            .onSuccess { h ->
                _state.update {
                    it.copy(
                        connectionOk = h.ok && h.ibConnected,
                        connectionMessage = "OK · IB connected = ${h.ibConnected}",
                    )
                }
            }
            .onFailure { e ->
                _state.update {
                    it.copy(connectionOk = false, connectionMessage = "失败: ${e.message ?: e::class.simpleName}")
                }
            }
    }
}
