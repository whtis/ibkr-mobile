package com.tis.ibkr

import android.app.Application
import com.tis.ibkr.data.api.IbkrApi
import com.tis.ibkr.data.api.QuoteStream
import com.tis.ibkr.data.db.AppDatabase
import com.tis.ibkr.data.repo.WatchlistRepository
import com.tis.ibkr.data.store.SettingsStore

class IbkrApp : Application() {

    lateinit var settingsStore: SettingsStore
        private set

    lateinit var api: IbkrApi
        private set

    lateinit var watchlist: WatchlistRepository
        private set

    lateinit var quoteStream: QuoteStream
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsStore = SettingsStore(applicationContext)
        api = IbkrApi(settingsStore)
        watchlist = WatchlistRepository(AppDatabase.get(applicationContext).watchlistDao())
        quoteStream = QuoteStream(settingsStore)
    }

    companion object {
        lateinit var instance: IbkrApp
            private set
    }
}
