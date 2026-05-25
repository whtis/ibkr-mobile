package com.tis.ibkr.data.repo

import com.tis.ibkr.data.db.WatchlistDao
import com.tis.ibkr.data.db.WatchlistItem
import kotlinx.coroutines.flow.Flow

class WatchlistRepository(private val dao: WatchlistDao) {

    fun observeAll(): Flow<List<WatchlistItem>> = dao.observeAll()

    fun observeContains(symbol: String): Flow<Boolean> = dao.observeContains(symbol.uppercase())

    suspend fun add(symbol: String, exchange: String, currency: String, name: String) {
        dao.upsert(
            WatchlistItem(
                symbol = symbol.uppercase(),
                exchange = exchange,
                currency = currency,
                name = name,
                addedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun remove(symbol: String) = dao.deleteBySymbol(symbol.uppercase())

    suspend fun allSymbols(): List<String> = dao.allSymbols()
}
