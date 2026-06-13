package com.tis.ibkr.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tis.ibkr.data.api.LatestRelease
import com.tis.ibkr.data.update.AppUpdater
import kotlinx.coroutines.launch

/**
 * Shown by RootScreen whenever [AppUpdater.available] holds a newer release.
 * "立即更新" downloads + installs the APK in place; if the OS hasn't granted
 * install-from-this-app yet, it first sends the user to that settings page.
 */
@Composable
fun UpdateDialog(release: LatestRelease, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val progress by AppUpdater.downloadProgress.collectAsState()
    val downloading = progress != null

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("发现新版本 ${release.versionName}") },
        text = {
            Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "当前版本 ${AppUpdater.currentVersionName(ctx)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (release.notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(release.notes, style = MaterialTheme.typography.bodySmall)
                }
                if (downloading) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { (progress ?: 0) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("下载中 ${progress}%", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !downloading && release.apkUrl != null,
                onClick = {
                    if (!AppUpdater.canInstall(ctx)) {
                        ctx.startActivity(AppUpdater.installPermissionIntent(ctx))
                    } else {
                        val url = release.apkUrl ?: return@TextButton
                        scope.launch { runCatching { AppUpdater.downloadAndInstall(ctx, url) } }
                    }
                },
            ) {
                Text(if (AppUpdater.canInstall(ctx)) "立即更新" else "允许安装")
            }
        },
        dismissButton = {
            if (!downloading) TextButton(onClick = onDismiss) { Text("稍后") }
        },
    )
}
