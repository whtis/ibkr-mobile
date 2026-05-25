package com.tis.ibkr.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Health(
    val ok: Boolean,
    @SerialName("ib_connected") val ibConnected: Boolean,
)

@Serializable
data class AccountSummary(
    @SerialName("account_id") val accountId: String,
    val currency: String = "USD",
    @SerialName("net_liquidation") val netLiquidation: Double = 0.0,
    @SerialName("total_cash") val totalCash: Double = 0.0,
    @SerialName("buying_power") val buyingPower: Double = 0.0,
    @SerialName("realized_pnl") val realizedPnl: Double = 0.0,
    @SerialName("unrealized_pnl") val unrealizedPnl: Double = 0.0,
    @SerialName("daily_pnl") val dailyPnl: Double = 0.0,
)

@Serializable
data class Position(
    val account: String,
    val symbol: String,
    @SerialName("sec_type") val secType: String,
    val exchange: String,
    val currency: String,
    val position: Double,
    @SerialName("avg_cost") val avgCost: Double,
    @SerialName("market_price") val marketPrice: Double? = null,
    @SerialName("market_value") val marketValue: Double? = null,
    @SerialName("unrealized_pnl") val unrealizedPnl: Double? = null,
    @SerialName("realized_pnl") val realizedPnl: Double? = null,
    @SerialName("daily_pnl") val dailyPnl: Double? = null,
)

@Serializable
data class ExtendedQuote(
    val last: Double? = null,
    @SerialName("prev_close") val prevClose: Double? = null,
    val change: Double? = null,
    @SerialName("change_pct") val changePct: Double? = null,
    val volume: Double? = null,
    val timestamp: String? = null,
)

@Serializable
data class Quote(
    val symbol: String,
    val last: Double? = null,
    val bid: Double? = null,
    val ask: Double? = null,
    val open: Double? = null,
    val high: Double? = null,
    val low: Double? = null,
    val close: Double? = null,
    @SerialName("prev_close") val prevClose: Double? = null,
    val volume: Double? = null,
    val turnover: Double? = null,
    val change: Double? = null,
    @SerialName("change_pct") val changePct: Double? = null,
    @SerialName("pre_market") val preMarket: ExtendedQuote? = null,
    @SerialName("post_market") val postMarket: ExtendedQuote? = null,
    val timestamp: String,
)

@Serializable
data class Bar(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
)

@Serializable
data class ExecutionTick(
    val time: Long,
    val symbol: String,
    @SerialName("sec_type") val secType: String,
    val side: String,      // "BUY" or "SELL"
    val price: Double,
    val shares: Double,
    val exchange: String? = null,
)

@Serializable
data class IntradayPoint(
    val time: Long,
    val price: Double,
    @SerialName("avg_price") val avgPrice: Double? = null,
    val volume: Double? = null,
    @SerialName("line_type") val lineType: Int = 0,  // 0=Normal,1=Pre,2=Post,3=Overnight
)

@Serializable
data class TradeTick(
    val time: Long,
    val price: Double,
    val volume: Double,
    val direction: String = "",
)

@Serializable
data class OptionExpiry(val expiry: String)

@Serializable
data class OptionContract(
    val symbol: String,
    val underlying: String,
    val expiry: String,
    val strike: Double,
    val right: String,        // "C" or "P"
    val last: Double? = null,
    @SerialName("change_pct") val changePct: Double? = null,
    val iv: Double? = null,
    val delta: Double? = null,
    val gamma: Double? = null,
    val theta: Double? = null,
    val vega: Double? = null,
    @SerialName("open_interest") val openInterest: Int? = null,
    val volume: Int? = null,
)

@Serializable
data class PlaceOrderRequest(
    val symbol: String,
    val exchange: String = "SMART",
    val currency: String = "USD",
    @SerialName("sec_type") val secType: String = "STK",
    val expiry: String? = null,   // YYYY-MM-DD for options
    val strike: Double? = null,
    val right: String? = null,    // "C" or "P"
    val side: String,
    @SerialName("order_type") val orderType: String,
    val quantity: Double,
    val price: Double? = null,
    val tif: String = "DAY",
    @SerialName("outside_rth") val outsideRth: Boolean = false,
)

@Serializable
data class OrderResponse(
    @SerialName("order_id") val orderId: Int,
    @SerialName("perm_id") val permId: Int? = null,
    val status: String,
    val symbol: String,
    val side: String,
    val quantity: Double,
    val filled: Double = 0.0,
    @SerialName("avg_fill_price") val avgFillPrice: Double? = null,
    val type: String,
    val price: Double? = null,
    val tif: String = "DAY",
    @SerialName("outside_rth") val outsideRth: Boolean = false,
)

@Serializable
data class DepthLevel(
    val position: Int,
    val price: Double,
    val volume: Long,
    @SerialName("order_num") val orderNum: Int,
)

@Serializable
data class Depth(
    val symbol: String,
    val bids: List<DepthLevel> = emptyList(),
    val asks: List<DepthLevel> = emptyList(),
)

@Serializable
data class StaticInfo(
    val symbol: String,
    @SerialName("name_cn") val nameCn: String? = null,
    @SerialName("name_en") val nameEn: String? = null,
    @SerialName("name_hk") val nameHk: String? = null,
    val exchange: String? = null,
    val currency: String? = null,
    @SerialName("lot_size") val lotSize: Int? = null,
    @SerialName("total_shares") val totalShares: Long? = null,
    @SerialName("circulating_shares") val circulatingShares: Long? = null,
    val eps: Double? = null,
    @SerialName("eps_ttm") val epsTtm: Double? = null,
    val bps: Double? = null,
    @SerialName("dividend_yield") val dividendYield: Double? = null,
) {
    /** Best-effort display name. Prefer Chinese, fall back to English. */
    val displayName: String
        get() = nameCn?.takeIf { it.isNotBlank() }
            ?: nameEn?.takeIf { it.isNotBlank() }
            ?: nameHk?.takeIf { it.isNotBlank() }
            ?: symbol
}
