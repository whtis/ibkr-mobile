package com.tis.ibkr.data.cache

import com.tis.ibkr.data.api.Bar
import com.tis.ibkr.data.api.IntradayPoint

/**
 * In-memory cache for chart data so revisits to the same symbol show instantly while a
 * fresh fetch is kicked off in the background. Bars are stable for minutes; intraday is
 * fresher so it uses a shorter ceiling.
 */
object ChartCache {
    private data class Entry<T>(val data: T, val ts: Long)

    private val bars = mutableMapOf<String, Entry<List<Bar>>>()
    private val intraday = mutableMapOf<String, Entry<List<IntradayPoint>>>()

    private const val BARS_MAX_AGE_MS = 5 * 60_000L     // 5 min — daily/weekly bars rarely change intraday
    private const val INTRADAY_MAX_AGE_MS = 30_000L     // 30 s — refresh frequently

    @Synchronized
    fun getBars(symbol: String, period: String): List<Bar>? {
        val e = bars["$symbol|$period"] ?: return null
        return if (System.currentTimeMillis() - e.ts < BARS_MAX_AGE_MS) e.data else null
    }

    @Synchronized
    fun putBars(symbol: String, period: String, data: List<Bar>) {
        bars["$symbol|$period"] = Entry(data, System.currentTimeMillis())
    }

    @Synchronized
    fun getIntraday(symbol: String): List<IntradayPoint>? {
        val e = intraday[symbol] ?: return null
        return if (System.currentTimeMillis() - e.ts < INTRADAY_MAX_AGE_MS) e.data else null
    }

    @Synchronized
    fun putIntraday(symbol: String, data: List<IntradayPoint>) {
        intraday[symbol] = Entry(data, System.currentTimeMillis())
    }
}
