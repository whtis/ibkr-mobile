package com.tis.ibkr

import android.app.Application
import com.tis.ibkr.data.api.IbkrApi
import com.tis.ibkr.data.api.QuoteStream
import com.tis.ibkr.data.db.AppDatabase
import com.tis.ibkr.data.repo.TradingModeRepository
import com.tis.ibkr.data.repo.WatchlistRepository
import com.tis.ibkr.data.store.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class IbkrApp : Application() {

    lateinit var settingsStore: SettingsStore
        private set

    lateinit var api: IbkrApi
        private set

    lateinit var watchlist: WatchlistRepository
        private set

    lateinit var quoteStream: QuoteStream
        private set

    lateinit var tradingMode: TradingModeRepository
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsStore = SettingsStore(applicationContext)
        api = IbkrApi(settingsStore)
        // Keep the IbkrApi'''s cached device_id in sync with persisted Settings so
        // the request-signing interceptor can read it synchronously.
        appScope.launch {
            settingsStore.flow.collect { s ->
                api.setCachedDeviceId(s.deviceId)
            }
        }
        watchlist = WatchlistRepository(AppDatabase.get(applicationContext).watchlistDao())
        quoteStream = QuoteStream(settingsStore)
        tradingMode = TradingModeRepository(api)
        appScope.launch {
            while (isActive) {
                tradingMode.refresh()
                delay(30_000)
            }
        }
        // Check for an app update shortly after launch (needs a paired device for
        // the authed /app/latest call; failures are swallowed).
        appScope.launch {
            delay(3_000)
            runCatching { com.tis.ibkr.data.update.AppUpdater.check() }
        }
    }

    companion object {
        lateinit var instance: IbkrApp
            private set
    }
}
