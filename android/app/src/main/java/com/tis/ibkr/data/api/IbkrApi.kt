package com.tis.ibkr.data.api

import com.tis.ibkr.data.store.SettingsStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

class IbkrApi(private val settingsStore: SettingsStore) {

    private val client = HttpClient(OkHttp) {
        // Non-2xx responses throw (ClientRequestException for 4xx, ServerResponseException for 5xx),
        // letting callers map status codes to UX errors instead of failing kotlinx.serialization.
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 5_000
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 2)
            exponentialDelay()
        }
        install(Logging) { level = LogLevel.INFO }
        install(RequestSigning) {
            deviceIdProvider = {
                // settingsStore.flow is a Flow; for fast synchronous reads in the
                // interceptor we cache the last device_id whenever it changes.
                cachedDeviceId
            }
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }

    /** Updated by IbkrApp on every SettingsStore emission so the signing
     *  plugin can attach X-Device-ID without a suspending read. */
    @Volatile
    private var cachedDeviceId: String? = null

    fun setCachedDeviceId(deviceId: String?) {
        cachedDeviceId = deviceId?.takeIf { it.isNotBlank() }
    }

    private suspend fun base(): String = settingsStore.flow.first().backendUrl
    private suspend fun token(): String = settingsStore.flow.first().token

    private suspend fun authed(path: String) = client.get("${base()}$path") {
    }

    suspend fun health(): Health = client.get("${base()}/health").body()

    suspend fun accountSummary(): List<AccountSummary> = authed("/account/summary").body()

    suspend fun positions(): List<Position> = authed("/account/positions").body()

    suspend fun quote(symbol: String): Quote = authed("/quote/${symbol.uppercase()}").body()

    suspend fun bars(symbol: String, period: String = "1d"): List<Bar> = client.get(
        "${base()}/bars/${symbol.uppercase()}",
    ) {
        url { parameters.append("period", period) }
    }.body()

    suspend fun staticInfo(symbol: String, currency: String = "USD"): StaticInfo = client.get(
        "${base()}/static/${symbol.uppercase()}",
    ) {
        url { parameters.append("currency", currency) }
    }.body()

    suspend fun depth(symbol: String, currency: String = "USD"): Depth = client.get(
        "${base()}/depth/${symbol.uppercase()}",
    ) {
        url { parameters.append("currency", currency) }
    }.body()

    suspend fun intraday(symbol: String, currency: String = "USD"): List<IntradayPoint> = client.get(
        "${base()}/intraday/${symbol.uppercase()}",
    ) {
        url { parameters.append("currency", currency) }
    }.body()

    suspend fun trades(symbol: String, count: Int = 30, currency: String = "USD"): List<TradeTick> = client.get(
        "${base()}/trades/${symbol.uppercase()}",
    ) {
        url {
            parameters.append("count", count.toString())
            parameters.append("currency", currency)
        }
    }.body()

    suspend fun optionExpiries(symbol: String, currency: String = "USD"): List<OptionExpiry> = client.get(
        "${base()}/options/expiries/${symbol.uppercase()}",
    ) {
        url { parameters.append("currency", currency) }
    }.body()

    suspend fun optionChain(symbol: String, expiry: String, currency: String = "USD"): List<OptionContract> = client.get(
        "${base()}/options/chain/${symbol.uppercase()}",
    ) {
        url {
            parameters.append("expiry", expiry)
            parameters.append("currency", currency)
        }
    }.body()

    suspend fun quotes(symbols: List<String>, currency: String = "USD"): List<Quote> {
        if (symbols.isEmpty()) return emptyList()
        return client.get("${base()}/quotes") {
            url {
                parameters.append("symbols", symbols.joinToString(",") { it.uppercase() })
                parameters.append("currency", currency)
            }
        }.body()
    }

    suspend fun searchSymbols(q: String, limit: Int = 10): List<SearchResult> = client.get(
        "${base()}/search",
    ) {
        url {
            parameters.append("q", q)
            parameters.append("limit", limit.toString())
        }
    }.body()

    suspend fun placeOrder(req: PlaceOrderRequest): OrderResponse = client.post("${base()}/orders") {
        setBody(req)
    }.body()

    suspend fun activeOrders(): List<OrderResponse> = client.get("${base()}/orders/active") {
    }.body()

    suspend fun executions(symbol: String): List<ExecutionTick> = client.get(
        "${base()}/executions/${symbol.uppercase()}",
    ) {
    }.body()

    suspend fun cancelOrder(orderId: Int) {
        client.delete("${base()}/orders/$orderId") {
        }
    }
    /**
     * Pair this device with the backend. Uses Bearer token for one-time auth
     * (the only remaining bearer use after the signature migration). Returns
     * the device_id and HMAC key; caller must persist the key into Keystore.
     */
    suspend fun pairDevice(token: String, label: String?): PairResponse {
        return client.post("${base()}/devices/pair") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(PairRequest(label))
        }.body()
    }

}
