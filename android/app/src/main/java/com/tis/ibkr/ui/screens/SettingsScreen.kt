package com.tis.ibkr.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tis.ibkr.ui.theme.LbColors
import com.tis.ibkr.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()

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

        Text(
            "本地后端示例: http://<LAN IP>:8000\n云端部署: https://<your-domain>",
            color = LbColors.OnSurfaceMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
