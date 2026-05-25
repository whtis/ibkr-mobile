package com.tis.ibkr.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watchlist")
data class WatchlistItem(
    @PrimaryKey val symbol: String,
    val exchange: String,
    val currency: String,
    val name: String,
    val addedAt: Long,
    val sortIndex: Long = 0,
)
