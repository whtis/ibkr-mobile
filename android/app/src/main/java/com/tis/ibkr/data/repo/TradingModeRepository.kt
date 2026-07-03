package com.tis.ibkr.data.repo

import com.tis.ibkr.data.api.IbkrApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TradingMode { PAPER, LIVE, UNKNOWN }

/**
 * Tracks whether the connected IBKR account is paper or live, derived from /health.
 *
 * UNKNOWN means /health hasn't responded (or reported no connection). Since the
 * gateway may be connected to a live account, order submission treats UNKNOWN
 * like LIVE: it requires an explicit confirmation instead of submitting directly.
 * Only a confirmed PAPER mode skips the confirm dialog.
 */
class TradingModeRepository(private val api: IbkrApi) {

    private val _mode = MutableStateFlow(TradingMode.UNKNOWN)
    val mode: StateFlow<TradingMode> = _mode.asStateFlow()

    private val _accountId = MutableStateFlow<String?>(null)
    val accountId: StateFlow<String?> = _accountId.asStateFlow()

    suspend fun refresh() {
        runCatching { api.health() }
            .onSuccess { h ->
                _mode.value = when {
                    h.isLive -> TradingMode.LIVE
                    h.ibConnected || h.mockMode -> TradingMode.PAPER
                    else -> TradingMode.UNKNOWN
                }
                _accountId.value = h.accountId
            }
            .onFailure {
                _mode.value = TradingMode.UNKNOWN
                _accountId.value = null
            }
    }
}
