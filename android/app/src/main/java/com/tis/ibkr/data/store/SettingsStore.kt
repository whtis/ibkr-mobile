package com.tis.ibkr.data.store

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ibkr_settings")

data class Settings(
    val backendUrl: String,
    val token: String,
) {
    fun isValid(): Boolean = backendUrl.isNotBlank() && token.isNotBlank()
}

class SettingsStore(private val context: Context) {

    private val keyUrl = stringPreferencesKey("backend_url")
    private val keyToken = stringPreferencesKey("api_token")

    val flow: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun save(url: String, token: String) {
        context.dataStore.edit {
            it[keyUrl] = url.trim().trimEnd('/')
            it[keyToken] = token.trim()
        }
    }

    private fun Preferences.toSettings() = Settings(
        backendUrl = this[keyUrl]?.takeIf { it.isNotBlank() } ?: DEFAULT_URL,
        token = this[keyToken]?.takeIf { it.isNotBlank() } ?: DEFAULT_TOKEN,
    )

    companion object {
        const val DEFAULT_URL = ""
        const val DEFAULT_TOKEN = ""
    }
}
