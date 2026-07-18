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
            var animations by remember { mutableStateOf(prefs.getBoolean("native_stream_animations_v1", true)) }
            var reasoning by remember { mutableStateOf(prefs.getBoolean("native_show_reasoning_v1", true)) }
            var follow by remember { mutableStateOf(prefs.getBoolean("native_auto_follow_v1", true)) }
            val lang = if (language == "en") "en" else "zh"
            FcodeChatTheme(theme, lang, animations, reasoning, follow) {
                BackHandler { finish() }
                Scaffold { pad -> LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 28.dp)) {
                    item { Row(Modifier.fillMaxWidth().padding(8.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ finish() }) { Icon(HugeIcons.ArrowLeft01, null) }
                        Text(if (lang == "zh") "??" else "Settings", style = MaterialTheme.typography.headlineSmall)
                    }}
                    item { SettingsSection(if (lang == "zh") "??" else "Appearance") }
                    item { SettingsRow(HugeIcons.Moon02, if (lang == "zh") "??" else "Theme", when(theme){"light"->if(lang=="zh")"??" else "Light";"dark"->if(lang=="zh")"??" else "Dark";else->if(lang=="zh")"????" else "System"}) { theme=when(theme){"system"->"light";"light"->"dark";else->"system"}; prefs.edit().putString("native_theme_mode_v1",theme).apply() } }
                    item { SettingsRow(HugeIcons.LanguageCircle, if (lang == "zh") "??" else "Language", if(lang=="zh") "????" else "English") { language=if(language=="en")"zh" else "en"; prefs.edit().putString("native_language_v1",language).apply() } }
                    item { SettingsRow(HugeIcons.Text, if(lang=="zh") "??? Markdown" else "Typography & Markdown", if(lang=="zh") "????????" else "Math, code and lists") {} }
                    item { SettingsSection(if(lang=="zh") "??" else "Conversation") }
                    item { SettingsRow(HugeIcons.LanguageCircle, if(lang=="zh") "????" else "Streaming animation", if(animations) if(lang=="zh")"??" else "On" else if(lang=="zh")"??" else "Off") { animations=!animations; prefs.edit().putBoolean("native_stream_animations_v1",animations).apply() } }
                    item { SettingsRow(HugeIcons.LanguageCircle, if(lang=="zh") "????" else "Reasoning", if(reasoning) if(lang=="zh")"??" else "Shown" else if(lang=="zh")"??" else "Hidden") { reasoning=!reasoning; prefs.edit().putBoolean("native_show_reasoning_v1",reasoning).apply() } }
                    item { SettingsRow(HugeIcons.LanguageCircle, if(lang=="zh") "??????" else "Auto-follow output", if(follow) if(lang=="zh")"??" else "On" else if(lang=="zh")"??" else "Off") { follow=!follow; prefs.edit().putBoolean("native_auto_follow_v1",follow).apply() } }
                    item { SettingsSection(if(lang=="zh") "??" else "System") }
                    item { SettingsRow(HugeIcons.Text, if(lang=="zh") "?? Fcode" else "About Fcode", "Compose UI") {} }
                }}
            }
        }
    }
}
@Composable private fun SettingsSection(title:String) { Text(title, style=MaterialTheme.typography.labelLarge, color=MaterialTheme.colorScheme.primary, modifier=Modifier.padding(start=24.dp,top=18.dp,bottom=8.dp)) }
@Composable private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector,title:String,value:String,onClick:()->Unit) { Surface(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp).clickable(onClick=onClick), shape=MaterialTheme.shapes.large, color=MaterialTheme.colorScheme.surfaceContainerLow) { Row(Modifier.padding(horizontal=16.dp,vertical=15.dp),verticalAlignment=Alignment.CenterVertically) { Icon(icon,null,Modifier.size(21.dp),tint=MaterialTheme.colorScheme.primary); Spacer(Modifier.width(15.dp)); Text(title,Modifier.weight(1f)); Text(value,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) } } }
