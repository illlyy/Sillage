package com.termux.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import me.rerere.hugeicons.stroke.LanguageCircle
import me.rerere.hugeicons.stroke.Moon02
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
            val lang = if (language == "en") "en" else "zh"
            FcodeChatTheme(theme, lang, streamAnimations) {
                BackHandler { finish() }
                Scaffold { padding ->
                    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { finish() }) { Icon(HugeIcons.ArrowLeft01, nativeText(lang, "??", "Back")) }
                                Text(nativeText(lang, "??", "Settings"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                        item { SettingsSection(nativeText(lang, "??", "Appearance")) }
                        item { SettingsRow(HugeIcons.Moon02, nativeText(lang, "??", "Theme"), when (theme) { "light" -> nativeText(lang, "??", "Light"); "dark" -> nativeText(lang, "??", "Dark"); else -> nativeText(lang, "????", "System") }) { theme = when (theme) { "system" -> "light"; "light" -> "dark"; else -> "system" }; prefs.edit().putString("native_theme_mode_v1", theme).apply() } }
                        item { SettingsRow(HugeIcons.LanguageCircle, nativeText(lang, "??", "Language"), when (language) { "en" -> "English"; "zh" -> nativeText(lang, "????", "Simplified Chinese"); else -> nativeText(lang, "????", "System") }) { language = when (language) { "system" -> "zh"; "zh" -> "en"; else -> "system" }; prefs.edit().putString("native_language_v1", language).apply() } }
                        item { SettingsRow(HugeIcons.Text, nativeText(lang, "??? Markdown", "Typography & Markdown"), nativeText(lang, "???????????", "Math, code and list rendering")) {} }
                        item { SettingsSection(nativeText(lang, "??", "Conversation")) }
                        item { SettingsRow(HugeIcons.Moon02, nativeText(lang, "????", "Streaming animation"), if (streamAnimations) nativeText(lang, "???", "On") else nativeText(lang, "???", "Off")) { streamAnimations = !streamAnimations; prefs.edit().putBoolean("native_stream_animations_v1", streamAnimations).apply() } }
                        item { SettingsRow(HugeIcons.LanguageCircle, nativeText(lang, "????", "Reasoning"), nativeText(lang, "??????????", "Display, collapse and auto-follow")) {} }
                        item { SettingsSection(nativeText(lang, "??", "System")) }
                        item { SettingsRow(HugeIcons.Text, nativeText(lang, "????", "Diagnostics"), nativeText(lang, "???? UI ?????", "Inspect native UI and backend events")) {} }
                        item { SettingsRow(HugeIcons.Text, nativeText(lang, "?? Fcode", "About Fcode"), nativeText(lang, "?? Compose UI", "Native Compose UI")) {} }
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
