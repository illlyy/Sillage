package com.termux.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.Moon02
import me.rerere.hugeicons.stroke.LanguageCircle
import me.rerere.hugeicons.stroke.Text

class NativeSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        setContent {
            var theme by remember { mutableStateOf(prefs.getString("native_theme_mode_v1", "system").orEmpty()) }
            var language by remember { mutableStateOf(prefs.getString("native_language_v1", "system").orEmpty()) }
            var streamAnimations by remember { mutableStateOf(prefs.getBoolean("native_stream_animations_v1", true)) }
            FcodeChatTheme(theme, if (language == "en") "en" else "zh", streamAnimations) {
                BackHandler { finish() }
                Scaffold { padding ->
                    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { finish() }) { Icon(HugeIcons.ArrowLeft01, "返回") }
                                Text("设置", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                        item { SettingsSection("外观") }
                        item { SettingsRow(HugeIcons.Moon02, "主题", when(theme) { "light" -> "浅色"; "dark" -> "深色"; else -> "跟随系统" }) { theme = when(theme) { "system" -> "light"; "light" -> "dark"; else -> "system" }; prefs.edit().putString("native_theme_mode_v1", theme).apply() } }
                        item { SettingsRow(HugeIcons.LanguageCircle, "语言", when(language) { "en" -> "English"; "zh" -> "简体中文"; else -> "跟随系统" }) { language = when(language) { "system" -> "zh"; "zh" -> "en"; else -> "system" }; prefs.edit().putString("native_language_v1", language).apply() } }
                        item { SettingsRow(HugeIcons.Text, "字体与 Markdown", "公式、代码块、列表渲染") { } }
                        item { SettingsSection("对话") }
                        item { SettingsRow(HugeIcons.Moon02, nativeText(if (language == "en") "en" else "zh", "????", "Streaming animation"), if (streamAnimations) nativeText(if (language == "en") "en" else "zh", "???", "On") else nativeText(if (language == "en") "en" else "zh", "???", "Off")) { streamAnimations = !streamAnimations; prefs.edit().putBoolean("native_stream_animations_v1", streamAnimations).apply() } }
                        item { SettingsRow(HugeIcons.LanguageCircle, "思考过程", "显示、折叠和自动跟随") { } }
                        item { SettingsSection("系统") }
                        item { SettingsRow(HugeIcons.Text, "诊断日志", "查看原生 UI 和后端事件") { } }
                        item { SettingsRow(HugeIcons.Text, "关于 Fcode", "原生 Compose UI") { } }
                    }
                }
            }
        }
    }
}

@Composable private fun SettingsSection(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 8.dp))
}

@Composable private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(15.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
