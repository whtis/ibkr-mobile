package com.tis.ibkr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tis.ibkr.IbkrApp
import com.tis.ibkr.data.api.AccountSummary
import com.tis.ibkr.data.api.Depth
import com.tis.ibkr.data.api.OrderResponse
import com.tis.ibkr.data.api.PlaceOrderRequest
import com.tis.ibkr.data.api.Position
import com.tis.ibkr.data.api.Quote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrderFormUiState(
    val symbol: String,
    val side: String,                     // "BUY" or "SELL"
    val orderType: String = "LMT",        // "LMT" or "MKT"
    val priceText: String = "",
    val quantityText: String = "",
    val tif: String = "DAY",               // "DAY" or "GTC"
    // 仅盘中(RTH only) vs 盘中+盘前盘后(extended)
    val outsideRth: Boolean = true,
    val submitting: Boolean = false,
    val error: String? = null,
    val success: OrderResponse? = null,
    val quote: Quote? = null,
    val depth: Depth? = null,
    val accountSummary: AccountSummary? = null,
    val position: Position? = null,
    // Option-specific
    val secType: String = "STK",
    val expiry: String? = null,
    val strike: Double? = null,
    val right: String? = null,
) {
    val price: Double? get() = priceText.trim().toDoubleOrNull()
    val quantity: Double? get() = quantityText.trim().toDoubleOrNull()

    /** Multiplier per contract: 100 shares per option contract, 1 for stock. */
    val multiplier: Int get() = if (secType == "OPT") 100 else 1

    /** Notional dollar amount the order represents. */
    val estimatedAmount: Double?
        get() {
            val q = quantity ?: return null
            val p = price ?: quote?.last ?: return null
            return q * p * multiplier
        }

    /** Bid-volume / total ratio (0..1). Used for the 买盘/卖盘 split bar. */
    val buyShare: Float
        get() {
            val d = depth ?: return 0.5f
            val bidVol = d.bids.sumOf { it.volume.toLong() }
            val askVol = d.asks.sumOf { it.volume.toLong() }
            val total = bidVol + askVol
            return if (total == 0L) 0.5f else (bidVol.toFloat() / total)
        }

    val bestBid: Double? get() = depth?.bids?.firstOrNull()?.price?.takeIf { it > 0 }
    val bestAsk: Double? get() = depth?.asks?.firstOrNull()?.price?.takeIf { it > 0 }

    /**
     * Max shares/contracts the user can afford with current cash, given the working price.
     * For STK: buying_power / price. For OPT: buying_power / (price * 100).
     */
    val maxCashAffordable: Long
        get() {
            val bp = accountSummary?.buyingPower ?: return 0
            val p = price ?: quote?.last ?: return 0
            if (p <= 0) return 0
            return (bp / (p * multiplier)).toLong().coerceAtLeast(0)
        }

    val canSubmit: Boolean
        get() {
            val q = quantity ?: return false
            if (q <= 0.0) return false
            if (orderType == "LMT" && (price == null || price!! <= 0.0)) return false
            return !submitting
        }
}

class OrderFormViewModel(
    symbol: String,
    side: String,
    private val exchange: String,
    private val currency: String,
    secType: String = "STK",
    expiry: String? = null,
    strike: Double? = null,
    right: String? = null,
) : ViewModel() {

    private val app get() = IbkrApp.instance
    private val _state = MutableStateFlow(
        OrderFormUiState(
            symbol = symbol.uppercase(),
            side = side.uppercase(),
            secType = secType,
            expiry = expiry,
            strike = strike,
            right = right,
        )
    )
    val state: StateFlow<OrderFormUiState> = _state.asStateFlow()

    init {
        // Stock orders: price defaults to current last; fetch depth + account summary + position.
        // Option orders: same supporting fetches, but skip the underlying quote (option price ≠ underlying).
        if (secType == "STK") fetchQuote()
        fetchDepth()
        fetchAccount()
        fetchPosition()
    }

    private fun fetchQuote() = viewModelScope.launch {
        runCatching { app.api.quote(_state.value.symbol) }
            .onSuccess { q ->
                _state.update {
                    it.copy(
                        quote = q,
                        priceText = it.priceText.ifBlank { q.last?.let { p -> "%.2f".format(p) } ?: "" },
                    )
                }
            }
    }

    private fun fetchDepth() = viewModelScope.launch {
        runCatching { app.api.depth(_state.value.symbol) }
            .onSuccess { d -> _state.update { it.copy(depth = d) } }
    }

    private fun fetchAccount() = viewModelScope.launch {
        runCatching { app.api.accountSummary() }
            .onSuccess { list ->
                // Pick the per-account summary (skip the "All" aggregator) — single user, single account.
                val summary = list.firstOrNull { it.accountId != "All" } ?: list.firstOrNull()
                _state.update { it.copy(accountSummary = summary) }
            }
    }

    private fun fetchPosition() = viewModelScope.launch {
        runCatching { app.api.positions().firstOrNull { it.symbol == _state.value.symbol } }
            .onSuccess { p -> _state.update { it.copy(position = p) } }
    }

    /** Snap the price text to the current quote.last (matches Longbridge's crosshair-icon button). */
    fun snapPriceToCurrent() {
        val last = _state.value.quote?.last ?: _state.value.bestBid ?: return
        _state.update { it.copy(priceText = "%.2f".format(last)) }
    }

    /** Set quantity as a percentage (25/50/75/100) of max-cash-affordable. */
    fun setQuantityFraction(fraction: Double) {
        val max = _state.value.maxCashAffordable
        val q = (max * fraction).toLong().coerceAtLeast(0)
        _state.update { it.copy(quantityText = q.toString()) }
    }

    fun dismissSuccess() = _state.update { it.copy(success = null) }
    fun dismissError() = _state.update { it.copy(error = null) }

    fun setSide(side: String) = _state.update { it.copy(side = side, success = null, error = null) }
    fun setOrderType(t: String) = _state.update { it.copy(orderType = t, success = null, error = null) }
    fun setPrice(p: String) = _state.update { it.copy(priceText = p, success = null, error = null) }
    fun setQuantity(q: String) = _state.update { it.copy(quantityText = q, success = null, error = null) }
    fun setTif(t: String) = _state.update { it.copy(tif = t, success = null, error = null) }
    fun setOutsideRth(b: Boolean) = _state.update { it.copy(outsideRth = b, success = null, error = null) }

    fun bumpPrice(delta: Double) {
        val current = _state.value.price ?: _state.value.quote?.last ?: 0.0
        val next = ((current + delta) * 100).toLong() / 100.0
        _state.update { it.copy(priceText = "%.2f".format(next)) }
    }

    fun bumpQuantity(delta: Long) {
        val current = (_state.value.quantity ?: 0.0).toLong()
        val next = (current + delta).coerceAtLeast(0L)
        _state.update { it.copy(quantityText = next.toString()) }
    }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            runCatching {
                app.api.placeOrder(
                    PlaceOrderRequest(
                        symbol = s.symbol,
                        exchange = exchange,
                        currency = currency,
                        secType = s.secType,
                        expiry = s.expiry,
                        strike = s.strike,
                        right = s.right,
                        side = s.side,
                        orderType = s.orderType,
                        quantity = s.quantity!!,
                        price = if (s.orderType == "LMT") s.price else null,
                        tif = s.tif,
                        outsideRth = s.outsideRth,
                    ),
                )
            }.onSuccess { resp ->
                _state.update { it.copy(submitting = false, success = resp, error = null) }
            }.onFailure { e ->
                _state.update { it.copy(submitting = false, error = e.message ?: e::class.simpleName) }
            }
        }
    }
}
