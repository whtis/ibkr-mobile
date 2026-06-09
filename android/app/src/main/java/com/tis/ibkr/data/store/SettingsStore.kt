package com.tis.ibkr.data.store

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ibkr_settings")

data class Settings(
    val backendUrl: String,
    val token: String,
    val deviceId: String,
) {
    fun isValid(): Boolean = backendUrl.isNotBlank() && token.isNotBlank()
    val isPaired: Boolean get() = deviceId.isNotBlank()
}

class SettingsStore(private val context: Context) {

    private val keyUrl = stringPreferencesKey("backend_url")
    private val keyToken = stringPreferencesKey("api_token")
    private val keyDeviceId = stringPreferencesKey("device_id")

    val flow: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun save(url: String, token: String) {
        context.dataStore.edit {
            it[keyUrl] = url.trim().trimEnd('/')
            it[keyToken] = token.trim()
        }
    }

    // --- Search history (most-recent-first, deduped, capped) ---
    private val keyHistory = stringPreferencesKey("search_history")

    val searchHistory: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[keyHistory]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun pushSearchHistory(q: String) {
        val query = q.trim()
        if (query.isEmpty()) return
        context.dataStore.edit { prefs ->
            val cur = prefs[keyHistory]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
            prefs[keyHistory] = (listOf(query) + cur.filterNot { it.equals(query, ignoreCase = true) })
                .take(12)
                .joinToString("\n")
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { it.remove(keyHistory) }
    }

    // --- Up/down color convention: true = red-up/green-down (default), false = green-up ---
    private val keyRedUp = booleanPreferencesKey("red_up")

    val redUp: Flow<Boolean> = context.dataStore.data.map { it[keyRedUp] ?: true }

    suspend fun setRedUp(value: Boolean) {
        context.dataStore.edit { it[keyRedUp] = value }
    }

    private fun Preferences.toSettings() = Settings(
        backendUrl = this[keyUrl]?.takeIf { it.isNotBlank() } ?: DEFAULT_URL,
        token = this[keyToken]?.takeIf { it.isNotBlank() } ?: DEFAULT_TOKEN,
        deviceId = this[keyDeviceId] ?: "",
    )

    suspend fun setDeviceId(deviceId: String) {
        context.dataStore.edit { it[keyDeviceId] = deviceId }
    }

    suspend fun clearPairing() {
        context.dataStore.edit { it.remove(keyDeviceId) }
    }

    companion object {
        const val DEFAULT_URL = ""
        const val DEFAULT_TOKEN = ""
    }
}
