package com.tis.ibkr.data.security

import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import java.security.KeyStore
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA256 key storage backed by AndroidKeyStore. The key is hardware-backed
 * (TEE / StrongBox) on any modern device, so even a rooted attacker cannot extract
 * the raw bytes after [importKey] is called. We can only ask the OS to sign a buffer
 * with it via [sign].
 *
 * Used by [com.tis.ibkr.data.api.RequestSigning] to sign every outgoing request to
 * the backend.
 */
object KeystoreHmac {

    private const val ALIAS = "bulltap-hmac-v1"
    private const val PROVIDER = "AndroidKeyStore"
    private const val ALGORITHM = "HmacSHA256"

    private fun ks(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    fun hasKey(): Boolean = ks().containsAlias(ALIAS)

    /**
     * Persist [hexKey] (64 hex chars = 32 bytes) into the Keystore under [ALIAS].
     * Overwrites any existing entry. Once stored, the key is no longer extractable.
     */
    fun importKey(hexKey: String) {
        val raw = hexKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        require(raw.size == 32) { "expected 32-byte HMAC key, got ${raw.size}" }
        val secret = SecretKeySpec(raw, ALGORITHM)
        val protection = KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        val store = ks()
        if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS)
        store.setEntry(ALIAS, KeyStore.SecretKeyEntry(secret), protection)
    }

    fun clearKey() {
        val store = ks()
        if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS)
    }

    fun sign(data: ByteArray): String {
        val key = ks().getKey(ALIAS, null) as SecretKey
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(key)
        return mac.doFinal(data).joinToString("") { "%02x".format(it) }
    }
}
