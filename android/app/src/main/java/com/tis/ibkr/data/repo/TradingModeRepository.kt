package com.tis.ibkr.data.repo

import com.tis.ibkr.data.api.IbkrApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TradingMode { PAPER, LIVE, UNKNOWN }

/**
 * Tracks whether the connected IBKR account is paper or live, derived from /health.
 *
 * UNKNOWN is the safer default for things like the top banner ("nothing shown"),
 * but for the order-confirm flow we treat UNKNOWN as PAPER — we already block
 * orders behind a token + qty/notional guards on the backend, and the user is
 * currently set up on a paper (DUQ*) account; forcing the live confirm dialog
 * when /health hasn't responded would just train muscle memory to dismiss it.
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
