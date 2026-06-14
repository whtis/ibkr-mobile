package com.tis.ibkr.data.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Human-readable FCM registration status, surfaced in Settings so push issues
 *  can be diagnosed without logcat (the token fetch can fail silently). */
object PushStatus {
    private val _status = MutableStateFlow("未注册")
    val status: StateFlow<String> = _status.asStateFlow()

    fun set(s: String) {
        _status.value = s
    }
}
