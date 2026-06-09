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
    val redUp: Boolean = true,
    val connectionOk: Boolean = false,
    val connectionMessage: String? = null,
    val deviceId: String = "",
    val pairing: Boolean = false,
    val pairMessage: String? = null,
)

class SettingsViewModel : ViewModel() {

    private val app get() = IbkrApp.instance
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            app.settingsStore.flow.collect { s ->
                _state.update { it.copy(backendUrl = s.backendUrl, token = s.token, deviceId = s.deviceId) }
            }
        }
        viewModelScope.launch {
            app.settingsStore.redUp.collect { v -> _state.update { it.copy(redUp = v) } }
        }
    }

    fun setRedUp(value: Boolean) {
        viewModelScope.launch { app.settingsStore.setRedUp(value) }
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
    fun pairDevice() {
        viewModelScope.launch {
            _state.update { it.copy(pairing = true, pairMessage = null) }
            val curToken = _state.value.token
            if (curToken.isBlank()) {
                _state.update { it.copy(pairing = false, pairMessage = "请先填写并保存 API Token") }
                return@launch
            }
            runCatching {
                val resp = app.api.pairDevice(curToken, label = android.os.Build.MODEL)
                com.tis.ibkr.data.security.KeystoreHmac.importKey(resp.hmacKeyHex)
                app.settingsStore.setDeviceId(resp.deviceId)
                resp
            }.onSuccess { resp ->
                _state.update {
                    it.copy(
                        pairing = false,
                        pairMessage = "配对成功 (${resp.deviceId})\n建议立即在后端轮换 API Token",
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        pairing = false,
                        pairMessage = "配对失败: ${e.message ?: e::class.simpleName}",
                    )
                }
            }
        }
    }

    fun clearPairing() {
        viewModelScope.launch {
            com.tis.ibkr.data.security.KeystoreHmac.clearKey()
            app.settingsStore.clearPairing()
            _state.update { it.copy(pairMessage = "已清除本机配对（后端 devices 表中的记录请单独删除）") }
        }
    }

}
