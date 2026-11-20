package com.termux.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class DeviceShellApprovalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        val command = DeviceShellApprovalBroker.current(requestId)
        if (command == null) {
            finish()
            return
        }
        val prefs = getSharedPreferences(DeviceControlStore.PREFS_NAME, MODE_PRIVATE)
        val lang = resolveLanguage(prefs.getString("native_language_v1", "system").orEmpty())
        setContent {
            MaterialTheme {
                BackHandler { deny(requestId) }
                ApprovalScreen(command, lang, onDeny = { deny(requestId) }, onApprove = {
                    DeviceShellApprovalBroker.resolve(requestId, true)
                    finish()
                })
            }
        }
    }

    private fun deny(requestId: String) {
        DeviceShellApprovalBroker.resolve(requestId, false)
        finish()
    }

    companion object {
        const val EXTRA_REQUEST_ID = "device_shell_request_id"
    }
}

@Composable
private fun ApprovalScreen(command: String, lang: String, onDeny: () -> Unit, onApprove: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
            Text(tr(lang, "允许设备命令？", "Allow device command?"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text(
                tr(lang, "该命令将以 Shizuku 的 shell 身份执行，可修改系统状态。请逐字核对。", "This command runs as Shizuku's shell identity and can change system state. Review it carefully."),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(18.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    command,
                    Modifier.fillMaxWidth().padding(16.dp).horizontalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onDeny, Modifier.weight(1f)) { Text(tr(lang, "拒绝", "Deny")) }
                Button(onApprove, Modifier.weight(1f)) { Text(tr(lang, "执行一次", "Run once")) }
            }
        }
    }
}
