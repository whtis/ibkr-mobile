package com.tis.ibkr.data.api

import com.tis.ibkr.data.security.KeystoreHmac
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import java.security.MessageDigest

/**
 * Ktor client plugin: sign every outgoing request with HMAC-SHA256 using the
 * Keystore-backed key, and attach X-Timestamp + X-Signature (+ optional
 * X-Device-ID) headers.
 *
 * Skipped if:
 *   - no key paired yet (pre-first-pair, or after [KeystoreHmac.clearKey]); or
 *   - the request already carries an Authorization header (only /devices/pair
 *     uses Bearer auth post-migration).
 *
 * Canonical string MUST match backend (app/auth.py require_signature):
 *   METHOD + "\n" + PATH_WITH_QUERY + "\n" + TIMESTAMP_MS + "\n" + sha256_hex(body)
 */
class RequestSigningConfig {
    /** Producer of the current device_id to attach as X-Device-ID. */
    var deviceIdProvider: () -> String? = { null }
}

val RequestSigning = createClientPlugin("RequestSigning", ::RequestSigningConfig) {
    val deviceIdProvider = pluginConfig.deviceIdProvider
    onRequest { request, _ ->
        if (!KeystoreHmac.hasKey()) return@onRequest
        if (request.headers.contains(HttpHeaders.Authorization)) return@onRequest

        val ts = System.currentTimeMillis().toString()
        val method = request.method.value
        val encodedQuery = request.url.encodedQuery
        val pathAndQuery =
            request.url.encodedPath + if (encodedQuery.isNotEmpty()) "?$encodedQuery" else ""

        val bodyBytes = when (val c = request.body) {
            is OutgoingContent.ByteArrayContent -> c.bytes()
            else -> ByteArray(0)
        }
        val bodyHash = MessageDigest.getInstance("SHA-256").digest(bodyBytes)
            .joinToString("") { "%02x".format(it) }

        val canonical = "$method\n$pathAndQuery\n$ts\n$bodyHash".toByteArray()
        val sig = KeystoreHmac.sign(canonical)

        request.header("X-Timestamp", ts)
        request.header("X-Signature", sig)
        deviceIdProvider()?.takeIf { it.isNotBlank() }?.let {
            request.header("X-Device-ID", it)
        }
    }
}
