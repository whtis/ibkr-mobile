package com.tis.ibkr.data.api

import android.util.Log
import com.tis.ibkr.data.store.SettingsStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

private const val TAG = "QuoteStream"

@Serializable
data class QuoteTick(
    val symbol: String,
    val last: Double? = null,
    val open: Double? = null,
    val high: Double? = null,
    val low: Double? = null,
    val volume: Long = 0,
    val turnover: Double = 0.0,
    val timestamp: Double? = null,
)

/**
 * Single shared WebSocket stream for realtime quote pushes.
 * - Auto-reconnects on disconnect.
 * - Re-subscribes outstanding symbols after reconnect.
 * - Multiple ViewModels can subscribe to the same symbol; ref-counted server-side.
 */
class QuoteStream(private val settingsStore: SettingsStore) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = HttpClient(OkHttp) {
        install(WebSockets) {
            pingIntervalMillis = 30_000
        }
        install(HttpTimeout) { connectTimeoutMillis = 5_000 }
    }
    private val json = Json { ignoreUnknownKeys = true }

    private val _ticks = MutableSharedFlow<QuoteTick>(extraBufferCapacity = 64)
    val ticks: SharedFlow<QuoteTick> = _ticks

    private val outbox = Channel<String>(Channel.BUFFERED)
    private val refCounts = mutableMapOf<String, Int>()
    private val lock = Any()
    private var running: Job? = null

    fun start() {
        if (running?.isActive == true) return
        running = scope.launch { runLoop() }
    }

    /** Add references for the given symbols. Sends a subscribe message immediately. */
    fun subscribe(symbols: List<String>, currency: String = "USD") {
        if (symbols.isEmpty()) return
        val toSend: MutableList<String> = mutableListOf()
        synchronized(lock) {
            for (s in symbols) {
                val cnt = refCounts.getOrDefault(s, 0)
                refCounts[s] = cnt + 1
                if (cnt == 0) toSend.add(s)
            }
        }
        if (toSend.isNotEmpty()) sendSubscribe(toSend, currency)
        start()
    }

    fun unsubscribe(symbols: List<String>, currency: String = "USD") {
        if (symbols.isEmpty()) return
        val toRemove: MutableList<String> = mutableListOf()
        synchronized(lock) {
            for (s in symbols) {
                val cnt = refCounts.getOrDefault(s, 0)
                if (cnt > 0) {
                    refCounts[s] = cnt - 1
                    if (cnt - 1 == 0) {
                        refCounts.remove(s)
                        toRemove.add(s)
                    }
                }
            }
        }
        if (toRemove.isNotEmpty()) sendUnsubscribe(toRemove, currency)
    }

    private fun sendSubscribe(symbols: List<String>, currency: String) {
        outbox.trySend(makeMsg("subscribe", symbols, currency))
    }

    private fun sendUnsubscribe(symbols: List<String>, currency: String) {
        outbox.trySend(makeMsg("unsubscribe", symbols, currency))
    }

    private fun makeMsg(action: String, symbols: List<String>, currency: String): String {
        val obj: JsonObject = buildJsonObject {
            put("action", action)
            put("currency", currency)
            put("symbols", buildJsonArray { symbols.forEach { add(it) } })
        }
        return obj.toString()
    }

    private suspend fun runLoop() {
        var backoff = 1_000L
        while (scope.isActive) {
            try {
                val s = settingsStore.flow.first()
                if (s.token.isBlank() || s.backendUrl.isBlank()) {
                    delay(2_000); continue
                }
                val httpUrl = Url(s.backendUrl)
                val wsProto = if (httpUrl.protocol == URLProtocol.HTTPS) "wss" else "ws"
                val portPart = if (httpUrl.specifiedPort > 0) ":${httpUrl.specifiedPort}" else ""
                val url = "$wsProto://${httpUrl.host}$portPart/ws/quotes?token=${s.token}"
                Log.i(TAG, "connecting $url")
                client.webSocket(url) {
                    backoff = 1_000L
                    // Re-subscribe all outstanding symbols after reconnect.
                    val resub: List<String> = synchronized(lock) { refCounts.keys.toList() }
                    if (resub.isNotEmpty()) {
                        send(Frame.Text(makeMsg("subscribe", resub, "USD")))
                    }
                    // Pump outbox -> server
                    val sender = launch {
                        outbox.consumeEach { msg ->
                            send(Frame.Text(msg))
                        }
                    }
                    // Receiver
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val txt = frame.readText()
                                val obj = runCatching { json.parseToJsonElement(txt).jsonObject }
                                    .getOrNull() ?: continue
                                val sym = obj["symbol"]?.jsonPrimitive?.contentOrNull ?: continue
                                val tick = QuoteTick(
                                    symbol = sym,
                                    last = obj["last"]?.jsonPrimitive?.doubleOrNull,
                                    open = obj["open"]?.jsonPrimitive?.doubleOrNull,
                                    high = obj["high"]?.jsonPrimitive?.doubleOrNull,
                                    low = obj["low"]?.jsonPrimitive?.doubleOrNull,
                                    volume = obj["volume"]?.jsonPrimitive?.longOrNull ?: 0L,
                                    turnover = obj["turnover"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                                    timestamp = obj["timestamp"]?.jsonPrimitive?.doubleOrNull,
                                )
                                _ticks.tryEmit(tick)
                            }
                        }
                    } finally {
                        sender.cancelAndJoin()
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "ws closed: ${t.message}")
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(30_000)
        }
    }
}
