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


private enum class ProxySection { OVERVIEW, NODES, SUBSCRIPTIONS, SETTINGS }

@Composable
internal fun ProxySettingsPage(
    lang: String,
    prefs: SharedPreferences,
    onBack: () -> Unit,
    onOpenDashboard: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val manager = remember { MihomoManager.get(context) }
    val controller = remember { MihomoControllerClient(manager) }
    val scope = rememberCoroutineScope()
    var section by remember { mutableStateOf(ProxySection.OVERVIEW) }
    var runtimeRevision by remember { mutableIntStateOf(0) }
    var groupReloadKey by remember { mutableIntStateOf(0) }
    var subscriptions by remember { mutableStateOf<List<MihomoManager.Subscription>>(emptyList()) }
    var groups by remember { mutableStateOf<List<MihomoControllerClient.ProxyGroup>>(emptyList()) }
    var groupsLoading by remember { mutableStateOf(false) }
    var selectedGroupName by remember { mutableStateOf(prefs.getString("mihomo_proxy_group", "").orEmpty()) }
    var proxySort by remember { mutableStateOf(prefs.getString("mihomo_proxy_sort", "default").orEmpty()) }
    var routeApi by remember { mutableStateOf(prefs.getBoolean("mihomo_route_api", false)) }
    var autoStart by remember { mutableStateOf(prefs.getBoolean("mihomo_auto_start", false)) }
    var proxyNotice by remember { mutableStateOf(prefs.getString("proxy_notice_text", "").orEmpty()) }
    var busyLabel by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showAddSubscription by remember { mutableStateOf(false) }
    var pendingDeleteSubscription by remember { mutableStateOf<MihomoManager.Subscription?>(null) }

    // Observe the polling tick so external process exits/starts refresh the status card.
    @Suppress("UNUSED_VARIABLE") val runtimeTick = runtimeRevision
    val installed = manager.isInstalled
    val running = manager.isRunning
    val supported = manager.isSupported

    fun runAction(
        loading: String,
        success: String,
        refreshSubs: Boolean = false,
        refreshProxyGroups: Boolean = false,
        action: () -> Unit,
    ) {
        if (busyLabel != null) return
        scope.launch {
            busyLabel = loading
            val result = withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching(action) }
            busyLabel = null
            if (result.isSuccess) {
                runtimeRevision++
                if (refreshSubs) subscriptions = withContext(kotlinx.coroutines.Dispatchers.IO) { manager.subscriptions() }
                if (refreshProxyGroups) groupReloadKey++
                Toast.makeText(context, success, Toast.LENGTH_SHORT).show()
            } else {
                errorMessage = result.exceptionOrNull()?.let { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" }
            }
        }
    }
    fun installBundle() {
        if (!supported) {
            errorMessage = tr(lang, "当前内置内核仅支持 ARM64（arm64-v8a）", "The bundled core currently supports ARM64 (arm64-v8a) only")
            return
        }
        if (busyLabel != null) return
        scope.launch {
            busyLabel = tr(lang, "正在准备组件…", "Preparing components…")
            val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    manager.install { progress -> scope.launch { busyLabel = progress } }
                }
            }
            busyLabel = null
            if (result.isSuccess) {
                runtimeRevision++
                subscriptions = withContext(kotlinx.coroutines.Dispatchers.IO) { manager.subscriptions() }
                Toast.makeText(context, tr(lang, "Mihomo 与 MetaCubeXD 已就绪", "Mihomo and MetaCubeXD are ready"), Toast.LENGTH_SHORT).show()
            } else {
                errorMessage = result.exceptionOrNull()?.let { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            if (!manager.isInstalled) {
                errorMessage = tr(lang, "请先安装 Mihomo 组件", "Install the Mihomo components first")
            } else {
                val displayName = queryDisplayName(context, uri).substringBeforeLast('.').ifBlank { tr(lang, "本地配置", "Local profile") }
                runAction(
                    tr(lang, "正在导入配置…", "Importing configuration…"),
                    tr(lang, "配置已导入并启用", "Configuration imported and activated"),
                    refreshSubs = true,
                    refreshProxyGroups = true,
                ) {
                    context.contentResolver.openInputStream(uri)?.use { manager.importSubscription(displayName, it) }
                        ?: throw java.io.IOException("Unable to open selected file")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        subscriptions = withContext(kotlinx.coroutines.Dispatchers.IO) { manager.subscriptions() }
        while (isActive) {
            delay(2_000)
            runtimeRevision++
        }
    }
    LaunchedEffect(section, groupReloadKey, running) {
        if (section == ProxySection.NODES && running) {
            groupsLoading = true
            val result = withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { controller.groups() } }
            groupsLoading = false
            if (result.isSuccess) {
                groups = result.getOrDefault(emptyList())
                val selected = groups.firstOrNull { it.name == selectedGroupName } ?: groups.firstOrNull()
                if (selected != null && selected.name != selectedGroupName) {
                    selectedGroupName = selected.name
                    prefs.edit().putString("mihomo_proxy_group", selected.name).apply()
                }
            } else {
                errorMessage = result.exceptionOrNull()?.message
            }
        } else if (!running) {
            groups = emptyList()
        }
    }

    SettingsScaffold(
        tr(lang, "网络与代理", "Network & proxy"),
        tr(lang, "Mihomo 内核、订阅与节点管理", "Mihomo core, subscriptions and node management"),
        onBack,
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            ProxySectionBar(lang, section) { section = it }
            if (busyLabel != null) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(busyLabel.orEmpty(), Modifier.padding(top = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            when (section) {
                ProxySection.OVERVIEW -> ProxyOverviewContent(
                    lang, manager, installed, running, supported, routeApi, subscriptions.firstOrNull { it.active }, busyLabel != null,
                    onPrimaryAction = {
                        when {
                            !installed -> installBundle()
                            running -> runAction(tr(lang, "正在停止…", "Stopping…"), tr(lang, "Mihomo 已停止", "Mihomo stopped"), refreshProxyGroups = true) { manager.stop() }
                            else -> runAction(tr(lang, "正在启动…", "Starting…"), tr(lang, "Mihomo 已启动", "Mihomo started"), refreshProxyGroups = true) { manager.start() }
                        }
                    },
                    onRouteChange = {
                        routeApi = it; prefs.edit().putBoolean("mihomo_route_api", it).apply()
                        if (it && installed && !running) runAction(tr(lang, "正在启动…", "Starting…"), tr(lang, "Mihomo 已启动", "Mihomo started"), refreshProxyGroups = true) { manager.start() }
                        else if (it && !installed) errorMessage = tr(lang, "路由已开启，请先安装 Mihomo 组件", "Routing is enabled; install Mihomo first")
                    },
                    onSubscriptions = { section = ProxySection.SUBSCRIPTIONS },
                    onDashboard = { if (running) onOpenDashboard() else errorMessage = tr(lang, "请先启动 Mihomo", "Start Mihomo first") },
                )
                ProxySection.NODES -> ProxyNodesContent(
                    lang, installed, running, groups, groupsLoading, selectedGroupName, proxySort,
                    onStart = { if (!installed) installBundle() else runAction(tr(lang, "正在启动…", "Starting…"), tr(lang, "Mihomo 已启动", "Mihomo started"), refreshProxyGroups = true) { manager.start() } },
                    onRefresh = { groupReloadKey++ },
                    onGroup = { selectedGroupName = it; prefs.edit().putString("mihomo_proxy_group", it).apply() },
                    onSort = { proxySort = it; prefs.edit().putString("mihomo_proxy_sort", it).apply() },
                    onTest = { group -> runAction(tr(lang, "正在测试延迟…", "Testing latency…"), tr(lang, "测速完成", "Latency test complete"), refreshProxyGroups = true) { controller.testGroup(group.name) } },
                    onSelect = { group, node -> runAction(tr(lang, "正在切换节点…", "Switching node…"), tr(lang, "已切换到 ${node.name}", "Switched to ${node.name}"), refreshProxyGroups = true) { controller.select(group.name, node.name) } },
                )
                ProxySection.SUBSCRIPTIONS -> ProxySubscriptionsContent(
                    lang, installed, subscriptions,
                    onAdd = { if (installed) showAddSubscription = true else errorMessage = tr(lang, "请先安装 Mihomo 组件", "Install Mihomo first") },
                    onImport = { importLauncher.launch(arrayOf("application/yaml", "text/yaml", "text/x-yaml", "text/plain", "application/octet-stream")) },
                    onActivate = { item -> runAction(tr(lang, "正在切换订阅…", "Switching subscription…"), tr(lang, "已切换到 ${item.name}", "Switched to ${item.name}"), refreshSubs = true, refreshProxyGroups = true) { manager.activateSubscription(item.id) } },
                    onUpdate = { item -> runAction(tr(lang, "正在更新 ${item.name}…", "Updating ${item.name}…"), tr(lang, "订阅已更新", "Subscription updated"), refreshSubs = true, refreshProxyGroups = true) { manager.updateSubscription(item.id) } },
                    onDelete = { pendingDeleteSubscription = it },
                )
                ProxySection.SETTINGS -> ProxyRuntimeSettingsContent(
                    lang, manager, installed, routeApi, autoStart, proxyNotice,
                    onInstall = ::installBundle,
                    onRoute = { routeApi = it; prefs.edit().putBoolean("mihomo_route_api", it).apply() },
                    onAutoStart = { autoStart = it; prefs.edit().putBoolean("mihomo_auto_start", it).apply() },
                    onNotice = { proxyNotice = it.take(40); prefs.edit().putString("proxy_notice_text", proxyNotice).apply() },
                    onDashboard = { if (running) onOpenDashboard() else errorMessage = tr(lang, "请先启动 Mihomo", "Start Mihomo first") },
                )
            }
        }
    }

    if (showAddSubscription) AddSubscriptionDialog(
        lang = lang,
        onDismiss = { showAddSubscription = false },
        onAdd = { name, url ->
            showAddSubscription = false
            runAction(
                tr(lang, "正在下载订阅…", "Downloading subscription…"),
                tr(lang, "订阅已添加并启用", "Subscription added and activated"),
                refreshSubs = true,
                refreshProxyGroups = true,
            ) { manager.addSubscription(name, url) }
        },
    )
    pendingDeleteSubscription?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDeleteSubscription = null },
            title = { Text(tr(lang, "删除这个订阅？", "Delete this subscription?")) },
            text = { Text(item.name) },
            dismissButton = { TextButton({ pendingDeleteSubscription = null }) { Text(tr(lang, "取消", "Cancel")) } },
            confirmButton = {
                TextButton({
                    pendingDeleteSubscription = null
                    runAction(tr(lang, "正在删除订阅…", "Deleting subscription…"), tr(lang, "订阅已删除", "Subscription deleted"), refreshSubs = true, refreshProxyGroups = true) { manager.deleteSubscription(item.id) }
                }) { Text(tr(lang, "删除", "Delete"), color = MaterialTheme.colorScheme.error) }
            },
        )
    }
    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text(tr(lang, "代理操作未完成", "Proxy action not completed")) },
            text = { Text(message) },
            confirmButton = { TextButton({ errorMessage = null }) { Text(tr(lang, "关闭", "Close")) } },
        )
    }
}

@Composable
private fun ProxySectionBar(lang: String, selected: ProxySection, onSelected: (ProxySection) -> Unit) {
    val items = listOf(
        ProxySection.OVERVIEW to tr(lang, "概览", "Overview"),
        ProxySection.NODES to tr(lang, "节点", "Nodes"),
        ProxySection.SUBSCRIPTIONS to tr(lang, "订阅", "Profiles"),
        ProxySection.SETTINGS to tr(lang, "设置", "Settings"),
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (section, label) ->
            Surface(
                onClick = { onSelected(section) },
                modifier = Modifier,
                shape = RoundedCornerShape(14.dp),
                color = if (section == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Text(
                    label,
                    Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (section == selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProxyOverviewContent(
    lang: String,
    manager: MihomoManager,
    installed: Boolean,
    running: Boolean,
    supported: Boolean,
    routeApi: Boolean,
    activeSubscription: MihomoManager.Subscription?,
    busy: Boolean,
    onPrimaryAction: () -> Unit,
    onRouteChange: (Boolean) -> Unit,
    onSubscriptions: () -> Unit,
    onDashboard: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = if (running) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(44.dp), shape = CircleShape, color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest) {
                            Box(contentAlignment = Alignment.Center) { Icon(HugeIcons.Code, null, Modifier.size(22.dp), tint = if (running) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Mihomo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                when { !supported -> tr(lang, "设备不支持", "Unsupported device"); !installed -> tr(lang, "组件未安装", "Components not installed"); running -> tr(lang, "本地内核运行中", "Local core is running"); else -> tr(lang, "已安装，当前停止", "Installed and stopped") },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Surface(Modifier.size(12.dp), shape = CircleShape, color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant) {}
                    }
                    if (installed) Text(
                        "Mixed  127.0.0.1:${manager.mixedPort()}\nController  127.0.0.1:${manager.controllerPort()}",
                        Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onPrimaryAction, enabled = supported && !busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(50.dp), shape = RoundedCornerShape(15.dp),
                    ) {
                        Text(when { !installed -> tr(lang, "安装离线组件", "Install bundled components"); running -> tr(lang, "停止 Mihomo", "Stop Mihomo"); else -> tr(lang, "启动 Mihomo", "Start Mihomo") })
                    }
                }
            }
        }
        item { SettingsSection(tr(lang, "当前订阅", "Active profile")) }
        item {
            NavigationSettingsRow(
                HugeIcons.Folder01,
                activeSubscription?.name ?: tr(lang, "暂无当前订阅", "No active profile"),
                activeSubscription?.let { if (it.isRemote) it.url else tr(lang, "本地 YAML 配置", "Local YAML configuration") } ?: tr(lang, "添加 URL 或导入配置文件", "Add a URL or import a configuration file"),
                onSubscriptions,
            )
        }
        item { SettingsSection(tr(lang, "应用内代理", "In-app routing")) }
        item { ToggleSettingsRow(HugeIcons.Code, tr(lang, "Sillage 请求使用 Mihomo", "Route Sillage through Mihomo"), tr(lang, "不创建 Android VPN，也不会影响其他应用", "Does not create an Android VPN or affect other apps"), routeApi, onRouteChange) }
        item { NavigationSettingsRow(HugeIcons.Code, "MetaCubeXD", tr(lang, "流量、规则、连接和高级控制台", "Traffic, rules, connections and advanced console"), onDashboard) }
    }
}

@Composable
private fun ProxyNodesContent(
    lang: String,
    installed: Boolean,
    running: Boolean,
    groups: List<MihomoControllerClient.ProxyGroup>,
    loading: Boolean,
    selectedGroupName: String,
    sort: String,
    onStart: () -> Unit,
    onRefresh: () -> Unit,
    onGroup: (String) -> Unit,
    onSort: (String) -> Unit,
    onTest: (MihomoControllerClient.ProxyGroup) -> Unit,
    onSelect: (MihomoControllerClient.ProxyGroup, MihomoControllerClient.ProxyNode) -> Unit,
) {
    if (!installed || !running) {
        LazyColumn(Modifier.fillMaxSize()) {
            item { EmptySettingsState(HugeIcons.Code, if (!installed) tr(lang, "尚未安装代理组件", "Proxy components are not installed") else tr(lang, "Mihomo 当前已停止", "Mihomo is stopped"), tr(lang, "启动内核后即可读取代理组、选择节点并测试延迟。", "Start the core to load proxy groups, select nodes and test latency.")) }
            item { Button(onStart, Modifier.fillMaxWidth().padding(horizontal = 28.dp).height(50.dp), shape = RoundedCornerShape(15.dp)) { Text(if (!installed) tr(lang, "安装组件", "Install components") else tr(lang, "启动 Mihomo", "Start Mihomo")) } }
        }
        return
    }
    val selectedGroup = groups.firstOrNull { it.name == selectedGroupName } ?: groups.firstOrNull()
    val sortedNodes = remember(selectedGroup, sort) {
        selectedGroup?.nodes?.toList()?.let { nodes ->
            when (sort) {
                "name" -> nodes.sortedBy { it.name.lowercase() }
                "delay" -> nodes.sortedWith(compareBy<MihomoControllerClient.ProxyNode> { if (it.delay > 0) it.delay else Int.MAX_VALUE }.thenBy { it.name.lowercase() })
                else -> nodes
            }
        }.orEmpty()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr(lang, "代理节点", "Proxy nodes"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(tr(lang, "按代理组选择并查看最近延迟", "Select by proxy group and inspect recent latency"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onRefresh) { Icon(HugeIcons.Refresh03, tr(lang, "刷新", "Refresh")) }
            }
        }
        if (loading && groups.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) }
        if (groups.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { group -> ProxyChoiceChip(group.name, group.name == selectedGroup?.name) { onGroup(group.name) } }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("default" to tr(lang, "默认", "Default"), "delay" to tr(lang, "延迟", "Latency"), "name" to tr(lang, "名称", "Name")).forEach { (value, label) ->
                        ProxyChoiceChip(label, sort == value) { onSort(value) }
                    }
                }
            }
            selectedGroup?.let { group ->
                item {
                    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(group.name, fontWeight = FontWeight.SemiBold)
                                Text(tr(lang, "当前：${group.selected.ifBlank { "—" }}", "Selected: ${group.selected.ifBlank { "—" }}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FilledTonalButton({ onTest(group) }) { Text(tr(lang, "测速", "Test")) }
                        }
                    }
                }
                items(sortedNodes, key = { "${group.name}-${it.name}" }) { node ->
                    ProxyNodeCard(lang, node, node.name == group.selected) { onSelect(group, node) }
                }
            }
        } else if (!loading) {
            item { EmptySettingsState(HugeIcons.Code, tr(lang, "没有可选择的代理组", "No selectable proxy groups"), tr(lang, "检查当前订阅是否包含 select、url-test 等代理组。", "Check whether the active profile contains select or url-test proxy groups.")) }
        }
    }
}

@Composable
private fun ProxyChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = Modifier, shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(label, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun ProxyNodeCard(lang: String, node: MihomoControllerClient.ProxyNode, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(17.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(horizontal = 15.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(10.dp), shape = CircleShape, color = when { selected -> MaterialTheme.colorScheme.primary; !node.alive -> MaterialTheme.colorScheme.error; node.delay > 0 -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.outlineVariant }) {}
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(node.name, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(node.type.ifBlank { tr(lang, "代理节点", "Proxy node") }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (node.delay > 0) "${node.delay} ms" else if (!node.alive) tr(lang, "不可用", "Offline") else "—", style = MaterialTheme.typography.labelLarge, color = when { !node.alive -> MaterialTheme.colorScheme.error; node.delay in 1..250 -> MaterialTheme.colorScheme.primary; node.delay > 0 -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.onSurfaceVariant })
            if (selected) { Spacer(Modifier.width(8.dp)); Icon(HugeIcons.Tick02, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun ProxySubscriptionsContent(
    lang: String,
    installed: Boolean,
    subscriptions: List<MihomoManager.Subscription>,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onActivate: (MihomoManager.Subscription) -> Unit,
    onUpdate: (MihomoManager.Subscription) -> Unit,
    onDelete: (MihomoManager.Subscription) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(tr(lang, "订阅配置", "Subscription profiles"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(tr(lang, "添加 URL、导入 YAML，并在多个配置间切换", "Add URLs, import YAML and switch between profiles"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onAdd, Modifier.weight(1f), enabled = installed, shape = RoundedCornerShape(14.dp)) { Icon(HugeIcons.Add01, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(tr(lang, "添加订阅", "Add URL")) }
                FilledTonalButton(onImport, Modifier.weight(1f), enabled = installed, shape = RoundedCornerShape(14.dp)) { Icon(HugeIcons.Folder01, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(tr(lang, "导入文件", "Import file")) }
            }
        }
        if (!installed) {
            item { EmptySettingsState(HugeIcons.Code, tr(lang, "请先安装代理组件", "Install proxy components first"), tr(lang, "组件安装完成后才能校验和保存订阅配置。", "Profiles can be validated and saved after the components are installed.")) }
        } else if (subscriptions.isEmpty()) {
            item { EmptySettingsState(HugeIcons.Folder01, tr(lang, "暂无订阅", "No profiles"), tr(lang, "添加订阅 URL，或从设备导入 YAML 配置。", "Add a subscription URL or import a YAML configuration from the device.")) }
        } else {
            item { SettingsSection(tr(lang, "订阅列表", "Profiles")) }
            items(subscriptions, key = { it.id }) { item ->
                SubscriptionCard(lang, item, { onActivate(item) }, { onUpdate(item) }, { onDelete(item) })
            }
        }
    }
}

@Composable
private fun SubscriptionCard(
    lang: String,
    item: MihomoManager.Subscription,
    onActivate: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = if (item.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (item.isRemote) item.url else tr(lang, "本地 YAML 文件", "Local YAML file"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (item.active) Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary) { Text(tr(lang, "当前使用", "Active"), Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary) }
            }
            if (item.updatedAt > 0) Text(formatSubscriptionTime(lang, item.updatedAt), Modifier.padding(top = 7.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.End) {
                if (!item.active) TextButton(onActivate) { Text(tr(lang, "使用", "Use")) }
                if (item.isRemote) TextButton(onUpdate) { Text(tr(lang, "更新", "Update")) }
                TextButton(onDelete) { Text(tr(lang, "删除", "Delete"), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun AddSubscriptionDialog(lang: String, onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(lang, "添加订阅", "Add subscription")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(tr(lang, "名称（可选）", "Name (optional)")) }, placeholder = { Text(tr(lang, "例如：机场订阅", "e.g. Main profile")) }, singleLine = true)
                OutlinedTextField(url, { url = it; error = false }, label = { Text(tr(lang, "订阅 URL", "Subscription URL")) }, placeholder = { Text("https://example.com/config.yaml") }, singleLine = true, isError = error, supportingText = if (error) ({ Text(tr(lang, "请输入有效的 HTTP(S) 地址", "Enter a valid HTTP(S) URL")) }) else null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(tr(lang, "取消", "Cancel")) } },
        confirmButton = { TextButton({ if (isValidHttpUrl(url.trim())) onAdd(name.trim(), url.trim()) else error = true }) { Text(tr(lang, "添加", "Add")) } },
    )
}

@Composable
private fun ProxyRuntimeSettingsContent(
    lang: String,
    manager: MihomoManager,
    installed: Boolean,
    routeApi: Boolean,
    autoStart: Boolean,
    proxyNotice: String,
    onInstall: () -> Unit,
    onRoute: (Boolean) -> Unit,
    onAutoStart: (Boolean) -> Unit,
    onNotice: (String) -> Unit,
    onDashboard: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { SettingsSection(tr(lang, "组件", "Components")) }
        item { NavigationSettingsRow(HugeIcons.Refresh03, tr(lang, "Mihomo 内核与网页面板", "Mihomo core & dashboard"), "Mihomo v${MihomoManager.CORE_VERSION} · MetaCubeXD v${MihomoManager.DASHBOARD_VERSION} · ${if (installed) tr(lang, "已安装", "Installed") else tr(lang, "未安装", "Not installed")}", onInstall) }
        item { SettingsSection(tr(lang, "运行行为", "Runtime behavior")) }
        item { ToggleSettingsRow(HugeIcons.Code, tr(lang, "应用内 URL 使用 Mihomo", "Route in-app URLs through Mihomo"), tr(lang, "不会影响浏览器或其他 Android 应用", "Does not affect the browser or other Android apps"), routeApi, onRoute) }
        item { ToggleSettingsRow(HugeIcons.Refresh03, tr(lang, "随应用自动启动", "Start with the app"), tr(lang, "只启动本地内核，不创建 Android VPN", "Starts the local core without creating an Android VPN"), autoStart, onAutoStart) }
        item { SettingsSection(tr(lang, "顶部提示", "Proxy-ready notice")) }
        item {
            OutlinedTextField(
                value = proxyNotice, onValueChange = onNotice,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                label = { Text(tr(lang, "提示文字", "Notice text")) },
                placeholder = { Text(tr(lang, "代理已开启", "Proxy enabled")) },
                supportingText = { Text("${proxyNotice.length}/40") },
                singleLine = true, shape = RoundedCornerShape(14.dp),
            )
        }
        item {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(9.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {}
                    Spacer(Modifier.width(10.dp))
                    Text(proxyNotice.ifBlank { tr(lang, "代理已开启", "Proxy enabled") }, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(tr(lang, "预览", "Preview"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { SettingsSection(tr(lang, "高级", "Advanced")) }
        item { NavigationSettingsRow(HugeIcons.Code, "MetaCubeXD", tr(lang, "查看流量、规则、连接和节点详情", "Inspect traffic, rules, connections and nodes"), onDashboard) }
        item {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(16.dp)) {
                    Text(tr(lang, "本地端点", "Local endpoints"), fontWeight = FontWeight.SemiBold)
                    Text("Mixed Port   127.0.0.1:${manager.mixedPort()}\nController   127.0.0.1:${manager.controllerPort()}", Modifier.padding(top = 7.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Text(tr(lang, "只代理 Fcode 流量。控制器仅监听本机，并使用应用私有随机密钥保护；不会创建系统 VPN。", "Only Fcode traffic is routed. The controller listens on loopback, uses an app-private random secret, and does not create a system VPN."), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull().orEmpty().ifBlank { "config.yaml" }
}

private fun formatSubscriptionTime(lang: String, timestamp: Long): String {
    val formatted = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(timestamp))
    return tr(lang, "更新于 $formatted", "Updated $formatted")
}


