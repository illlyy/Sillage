package com.termux.app

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.OpenableColumns
import android.webkit.WebView
import android.widget.Toast
import com.termux.BuildConfig
import com.termux.R
import com.termux.app.update.AppUpdateManager
import com.termux.shared.termux.TermuxConstants
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.LanguageCircle
import me.rerere.hugeicons.stroke.LookTop
import me.rerere.hugeicons.stroke.MagicWand01
import me.rerere.hugeicons.stroke.Moon02
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Text
import me.rerere.hugeicons.stroke.Tick02


private data class SkillSettingsSnapshot(val loaded: Boolean = false, val official: List<NativeOfficialSkill> = emptyList(), val installed: List<NativeInstalledSkill> = emptyList(), val error: String = "")


@Composable
internal fun SkillsSettingsPage(lang: String, prefs: SharedPreferences, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var busySkill by remember { mutableStateOf<String?>(null) }
    val snapshot by produceState(SkillSettingsSnapshot(), revision) {
        value = withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { SkillSettingsSnapshot(true, NativeSkillManager.official(context), NativeSkillManager.installed()) }
                .getOrElse { SkillSettingsSnapshot(true, error = it.message.orEmpty()) }
        }
    }
    val installedIds = remember(snapshot.installed) { snapshot.installed.map { File(it.path).parentFile?.name.orEmpty() }.toSet() }
    val official = remember(snapshot.official, query) { snapshot.official.filter { query.isBlank() || it.name.contains(query, true) || it.description.contains(query, true) } }
    fun markChanged() { prefs.edit().putLong("native_skills_revision_v1", System.currentTimeMillis()).apply(); revision++ }
    SettingsScaffold("Skills", tr(lang, "\u4e0e WebUI \u5171\u7528\u5b98\u65b9\u76ee\u5f55\u548c\u672c\u5730 Skill \u76ee\u5f55", "Share the official catalog and local skill directories with WebUI"), onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
            if (!snapshot.loaded) item { EmptySettingsState(HugeIcons.Sparkles, tr(lang, "\u6b63\u5728\u8bfb\u53d6 Skills", "Loading skills"), "") }
            if (snapshot.error.isNotBlank()) item { Text(snapshot.error, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error) }
            if (snapshot.installed.isNotEmpty()) {
                item { SettingsSection(tr(lang, "\u5df2\u5b89\u88c5", "Installed")) }
                items(snapshot.installed, key = { it.path }) { skill ->
                    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            SettingsIcon(HugeIcons.Sparkles); Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) { Text(skill.name, fontWeight = FontWeight.SemiBold); Text(skill.description.ifBlank { skill.path }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            if (skill.managed) IconButton(onClick = { scope.launch { busySkill = skill.name; runCatching { withContext(kotlinx.coroutines.Dispatchers.IO) { NativeSkillManager.uninstall(skill.path) } }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }; busySkill = null; markChanged() } }, enabled = busySkill == null) { Icon(HugeIcons.Delete01, tr(lang, "\u5378\u8f7d", "Uninstall"), tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            item { SettingsSection(tr(lang, "\u5b98\u65b9 Skills", "Official skills")) }
            item { SettingsTextField(query, { query = it }, tr(lang, "\u641c\u7d22", "Search"), tr(lang, "\u540d\u79f0\u6216\u63cf\u8ff0", "Name or description")) }
            items(official, key = { it.id }) { skill ->
                val installed = skill.id in installedIds
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(skill.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); if (installed) Icon(HugeIcons.Tick02, null, tint = MaterialTheme.colorScheme.primary) }
                        Text(skill.description, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        FilledTonalButton(
                            onClick = { scope.launch { busySkill = skill.id; runCatching { withContext(kotlinx.coroutines.Dispatchers.IO) { NativeSkillManager.installOfficial(context, skill.id) } }.onSuccess { Toast.makeText(context, tr(lang, "\u5df2\u5b89\u88c5 ${skill.name}", "Installed ${skill.name}"), Toast.LENGTH_SHORT).show() }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }; busySkill = null; markChanged() } },
                            modifier = Modifier.align(Alignment.End).padding(top = 10.dp), enabled = !installed && busySkill == null,
                        ) { Text(when { installed -> tr(lang, "\u5df2\u5b89\u88c5", "Installed"); busySkill == skill.id -> tr(lang, "\u5b89\u88c5\u4e2d\u2026", "Installing…"); else -> tr(lang, "\u5b89\u88c5", "Install") }) }
                    }
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}


