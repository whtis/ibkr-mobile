package com.tis.ibkr.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tis.ibkr.data.update.AppUpdater
import com.tis.ibkr.ui.theme.LbColors
import com.tis.ibkr.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    var url by remember(state.backendUrl) { mutableStateOf(state.backendUrl) }
    var token by remember(state.token) { mutableStateOf(state.token) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = LbColors.Surface,
        unfocusedContainerColor = LbColors.Surface,
        focusedTextColor = LbColors.OnSurface,
        unfocusedTextColor = LbColors.OnSurface,
        focusedBorderColor = LbColors.Accent,
        unfocusedBorderColor = LbColors.Outline,
        focusedLabelColor = LbColors.OnSurfaceMuted,
        unfocusedLabelColor = LbColors.OnSurfaceMuted,
        cursorColor = LbColors.Accent,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("后端连接", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Backend URL") },
            placeholder = { Text("http://<host>:8000  or  https://your.domain") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("API Token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )

        Button(
            onClick = { scope.launch { vm.save(url, token) } },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = LbColors.Accent,
                contentColor = LbColors.OnSurface,
            ),
        ) { Text("保存") }

        Button(
            onClick = { scope.launch { vm.testConnection() } },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = LbColors.SurfaceElevated,
                contentColor = LbColors.OnSurface,
            ),
        ) { Text("测试连接") }

        state.connectionMessage?.let { msg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = LbColors.SurfaceElevated),
            ) {
                Text(
                    msg,
                    modifier = Modifier.padding(12.dp),
                    color = if (state.connectionOk) LbColors.Down else LbColors.Error,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text("设备配对", style = MaterialTheme.typography.titleMedium)
        if (state.deviceId.isNotBlank()) {
            Text(
                "已配对 · device_id = ${state.deviceId}",
                color = LbColors.Down,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text(
                "未配对 — 请先保存 URL + Token，然后点击下方按钮",
                color = LbColors.OnSurfaceMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            onClick = { vm.pairDevice() },
            enabled = !state.pairing && state.token.isNotBlank() && state.backendUrl.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = LbColors.Accent,
                contentColor = LbColors.OnSurface,
            ),
        ) {
            Text(
                if (state.pairing) "配对中..."
                else if (state.deviceId.isNotBlank()) "重新配对（覆盖现有 device_id）"
                else "配对此设备"
            )
        }
        if (state.deviceId.isNotBlank()) {
            Button(
                onClick = { vm.clearPairing() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LbColors.SurfaceElevated,
                    contentColor = LbColors.OnSurface,
                ),
            ) { Text("清除本机配对") }
        }
        state.pairMessage?.let { msg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = LbColors.SurfaceElevated),
            ) {
                Text(msg, modifier = Modifier.padding(12.dp), color = LbColors.OnSurface)
            }
        }

        Spacer(Modifier.height(8.dp))

        Text("显示", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("涨跌颜色", color = LbColors.OnSurface, style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (state.redUp) "红涨绿跌（A股 / 港股习惯）" else "绿涨红跌（美股习惯）",
                    color = LbColors.OnSurfaceMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = state.redUp,
                onCheckedChange = { vm.setRedUp(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = LbColors.Accent,
                ),
            )
        }

        Spacer(Modifier.height(8.dp))

        Text("关于", style = MaterialTheme.typography.titleMedium)
        Text(
            "当前版本 ${AppUpdater.currentVersionName(ctx)}",
            color = LbColors.OnSurfaceMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        var checking by remember { mutableStateOf(false) }
        var checkMsg by remember { mutableStateOf<String?>(null) }
        Button(
            onClick = {
                checking = true
                checkMsg = null
                scope.launch {
                    val r = AppUpdater.check(manual = true)
                    checking = false
                    if (r == null) checkMsg = "已是最新版本"
                }
            },
            enabled = !checking,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = LbColors.SurfaceElevated,
                contentColor = LbColors.OnSurface,
            ),
        ) { Text(if (checking) "检查中..." else "检查更新") }
        checkMsg?.let {
            Text(it, color = LbColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "本地后端示例: http://<LAN IP>:8000\n云端部署: https://<your-domain>",
            color = LbColors.OnSurfaceMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
