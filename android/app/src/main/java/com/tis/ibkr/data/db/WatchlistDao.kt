package com.tis.ibkr.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist ORDER BY sortIndex ASC, addedAt DESC")
    fun observeAll(): Flow<List<WatchlistItem>>

    @Query("SELECT * FROM watchlist WHERE symbol = :symbol LIMIT 1")
    suspend fun findBySymbol(symbol: String): WatchlistItem?

    @Query("SELECT COUNT(*) > 0 FROM watchlist WHERE symbol = :symbol")
    fun observeContains(symbol: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WatchlistItem)

    @Query("DELETE FROM watchlist WHERE symbol = :symbol")
    suspend fun deleteBySymbol(symbol: String)

    @Query("SELECT symbol FROM watchlist")
    suspend fun allSymbols(): List<String>
}
