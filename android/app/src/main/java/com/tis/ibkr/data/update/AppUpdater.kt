package com.tis.ibkr.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.tis.ibkr.IbkrApp
import com.tis.ibkr.data.api.LatestRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app update: ask the backend for the newest GitHub release, compare against
 * the installed versionName, and (on the user's confirmation) download + install
 * the APK. Holds the "update available" + download-progress state so RootScreen
 * can show a single dialog whether the check was automatic (app start) or manual
 * (Settings → 检查更新).
 */
object AppUpdater {

    private val _available = MutableStateFlow<LatestRelease?>(null)
    /** Set when a newer release is available and not yet dismissed this session. */
    val available: StateFlow<LatestRelease?> = _available.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Int?>(null)
    /** 0..100 while downloading, null when idle. */
    val downloadProgress: StateFlow<Int?> = _downloadProgress.asStateFlow()

    @Volatile
    private var dismissedVersion: String? = null

    fun currentVersionName(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" }
            .getOrDefault("")

    /**
     * Query the backend for the latest release. Publishes it to [available] when
     * it's newer than the installed build (the most recent release is by
     * definition the newest, so name inequality = update). Returns the release if
     * an update is available, else null. [manual] ignores a prior dismissal.
     */
    suspend fun check(manual: Boolean = false): LatestRelease? {
        val beta = runCatching { IbkrApp.instance.settingsStore.betaUpdates.first() }.getOrDefault(false)
        val channel = if (beta) "beta" else "stable"
        val latest = runCatching { IbkrApp.instance.api.appLatest(channel) }.getOrNull() ?: return null
        val cur = currentVersionName(IbkrApp.instance)
        val isNewer = latest.versionName.isNotBlank() && latest.versionName != cur
        if (isNewer && (manual || latest.versionName != dismissedVersion)) {
            _available.value = latest
        } else if (!isNewer && manual) {
            _available.value = null
        }
        return if (isNewer) latest else null
    }

    fun dismiss() {
        dismissedVersion = _available.value?.versionName
        _available.value = null
    }

    /** Whether the OS will let us install APKs (Android 8+ per-app "unknown sources"). */
    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Download the APK (reporting progress) and launch the system installer. */
    suspend fun downloadAndInstall(ctx: Context, url: String) {
        _downloadProgress.value = 0
        try {
            val apk = withContext(Dispatchers.IO) { download(ctx, url) }
            installApk(ctx, apk)
        } finally {
            _downloadProgress.value = null
        }
    }

    private fun download(ctx: Context, url: String): File {
        val out = File(ctx.cacheDir, "update.apk")
        if (out.exists()) out.delete()
        val conn = openFollowingRedirects(url)
        try {
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buf)
                        if (read == -1) break
                        output.write(buf, 0, read)
                        done += read
                        if (total > 0) _downloadProgress.value = ((done * 100) / total).toInt()
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        return out
    }

    /** HttpURLConnection won't cross http<->https on redirect; GitHub asset URLs
     * redirect to a CDN, so follow them by hand. */
    private fun openFollowingRedirects(start: String): HttpURLConnection {
        var url = URL(start)
        var redirects = 0
        while (true) {
            val c = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            val code = c.responseCode
            if (code in 300..399 && redirects < 5) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                url = URL(url, loc)
                redirects++
                continue
            }
            return c
        }
    }

    private fun installApk(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }
}
