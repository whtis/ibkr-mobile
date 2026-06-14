package com.tis.ibkr

import android.app.Application
import com.google.firebase.messaging.FirebaseMessaging
import com.tis.ibkr.data.api.IbkrApi
import com.tis.ibkr.data.push.PushNotifications
import com.tis.ibkr.data.push.PushStatus
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
        PushNotifications.ensureChannel(applicationContext)
        appScope.launch {
            var lastRegistered: String? = null
            settingsStore.flow.collect { s ->
                api.setCachedDeviceId(s.deviceId)
                // Register the FCM push token once we have a paired device (the
                // /devices/fcm-token call is authed, so it needs the device_id).
                if (s.deviceId.isNotBlank() && s.deviceId != lastRegistered) {
                    lastRegistered = s.deviceId
                    registerPushToken()
                }
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

    fun registerPushToken() {
        PushStatus.set("注册中…")
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                appScope.launch {
                    runCatching { api.registerFcmToken(token) }
                        .onSuccess { PushStatus.set("已注册 ✓ (${token.take(10)}…${token.takeLast(4)})") }
                        .onFailure { PushStatus.set("注册POST失败: ${it.message ?: it::class.simpleName}") }
                }
            }
            .addOnFailureListener { e ->
                PushStatus.set("取FCM token失败: ${e.message ?: e::class.simpleName}")
            }
    }

    companion object {
        lateinit var instance: IbkrApp
            private set
    }
}
