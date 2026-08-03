@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.termux.app

import android.util.Base64
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityManager

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import java.util.Locale
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import io.noties.markwon.Markwon
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.core.MarkwonTheme
import org.commonmark.node.Code
import org.commonmark.node.BlockQuote
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.highlight.Highlight
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.ChartColumn
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.Clock02
import me.rerere.hugeicons.stroke.Download04
import me.rerere.hugeicons.stroke.Files02
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.InLove
import me.rerere.hugeicons.stroke.Idea01
import me.rerere.hugeicons.stroke.LanguageCircle
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Share08
import me.rerere.hugeicons.stroke.LookTop
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.TransactionHistory
import me.rerere.hugeicons.stroke.Tick02
import me.rerere.hugeicons.stroke.Upload02
import me.rerere.hugeicons.stroke.Voice
import me.rerere.hugeicons.stroke.Zap

@Composable
internal fun RikkaDrawerV2(
    currentThreadId: String,
    modelLabel: String,
    backend: NativeBackendType,
    onSwitchBackend: (NativeBackendType) -> Unit,
    conversations: List<NativeConversation>,
    onSearch: () -> Unit,
    onRenameConversation: (NativeConversation) -> Unit,
    onDeleteConversation: (NativeConversation) -> Unit,
    onToggleFavorite: (NativeConversation) -> Unit,
    onResumeConversation: (String) -> Unit,
    onClose: () -> Unit,
    onNewConversation: () -> Unit,
    onNewConversationAtProject: (String) -> Unit,
    onBackHome: () -> Unit,
    onOpenLegacyWebUi: () -> Unit,
) {
    val language = LocalNativeLanguage.current
    val context = LocalContext.current
    var mode by remember { mutableStateOf("tasks") }
    var projectRevision by remember { mutableIntStateOf(0) }
    var newMenuExpanded by remember { mutableStateOf(false) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var projectDialogError by remember { mutableStateOf("") }
    var renameProject by remember { mutableStateOf<NativeDrawerProject?>(null) }
    var renameProjectError by remember { mutableStateOf("") }
    var deleteProject by remember { mutableStateOf<NativeDrawerProject?>(null) }
    var expandedProjects by remember { mutableStateOf(emptySet<String>()) }
    // System folder picker (SAF). Keep the URI grant alongside the resolved path so a
    // restart does not silently lose access to a user-selected folder.
    val pickProjectFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        projectDialogError = ""
        // Some document providers expose a persistable read grant but reject the
        // optional write bit. Keep the usable read grant instead of turning a valid
        // folder selection into a silent failure.
        val persistError = persistDocumentTreePermission(context, uri)
        if (persistError != null) {
            projectDialogError = nativeText(
                language,
                "系统没有授予该文件夹持久权限，请选择设备本地文件夹后重试。",
                "Android did not grant a persistent folder permission. Choose a local folder and try again.",
            )
        } else {
            val path = documentTreeUriToPath(uri)
            if (path == null) {
                projectDialogError = nativeText(
                    language,
                    "无法解析该文件夹。请从设备本地存储中选择目录。",
                    "This folder provider is not supported. Choose a directory from local storage.",
                )
            } else {
                runCatching {
                    NativeDrawerProjectStore.register(
                        context,
                        path,
                        requestedName = newProjectName,
                        treeUri = uri.toString(),
                    )
                }
                    .onSuccess { project ->
                        context.getSharedPreferences("codex_mobile", android.content.Context.MODE_PRIVATE).edit()
                            .putBoolean("custom_project_root_enabled", true)
                            .putString("custom_project_root", project.path)
                            .putString("custom_project_root_uri", project.treeUri)
                            .commit()
                        projectRevision++
                        expandedProjects = expandedProjects + project.path
                        showNewProjectDialog = false
                        projectDialogError = ""
                        newProjectName = ""
                        onNewConversationAtProject(project.path)
                    }
                    .onFailure { error ->
                        projectDialogError = error.message ?: nativeText(language, "无法访问所选文件夹。", "Unable to access the selected folder.")
                    }
            }
        }
    }
    val conversationRevision = conversations.fold(1) { value, conversation ->
        var result = 31 * value + conversation.threadId.hashCode()
        result = 31 * result + conversation.title.hashCode()
        result = 31 * result + conversation.projectPath.hashCode()
        result = 31 * result + conversation.state.hashCode()
        result = 31 * result + conversation.favorite.hashCode()
        result = 31 * result + conversation.attention.hashCode()
        result
    }
    val conversationSnapshot = remember(conversationRevision) { conversations.toList() }
    val registeredProjects = remember(projectRevision) { NativeDrawerProjectStore.registered(context) }
    val hiddenProjects = remember(projectRevision) { NativeDrawerProjectStore.hiddenPaths(context) }
    val drawerClassification = remember(conversationRevision, registeredProjects, hiddenProjects, projectRevision) {
        classifyNativeDrawerTasks(
            conversations = conversationSnapshot,
            registeredProjectPaths = registeredProjects.map(NativeDrawerProject::path),
            hiddenProjectPaths = hiddenProjects,
        )
    }
    val projects = remember(drawerClassification.visibleProjects, registeredProjects) {
        drawerClassification.visibleProjects.mapNotNull { path ->
            registeredProjects.firstOrNull {
                nativeDrawerPathIdentity(it.path) == nativeDrawerPathIdentity(path)
            }
        }
    }
    val standaloneTasks = drawerClassification.standaloneTasks
    val tasksByProject = drawerClassification.tasksByProject
    val openTask: (NativeConversation) -> Unit = remember(context, onResumeConversation) {
        { conversation ->
            if (conversation.projectPath.isNotBlank()) {
                context.getSharedPreferences("codex_mobile", android.content.Context.MODE_PRIVATE).edit()
                    .putBoolean("custom_project_root_enabled", true)
                    .putString("custom_project_root", conversation.projectPath)
                    .commit()
            }
            onResumeConversation(conversation.threadId)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = androidx.compose.ui.graphics.RectangleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DrawerQuickAction(HugeIcons.Search01, nativeText(language, "\u641c\u7d22", "Search"), Modifier.weight(1f), onSearch)
                Box(Modifier.weight(1f)) {
                    DrawerQuickAction(
                        HugeIcons.MessageAdd01,
                        nativeText(language, "\u65b0\u5efa", "New"),
                        Modifier.fillMaxWidth(),
                    ) { newMenuExpanded = true }
                    DropdownMenu(expanded = newMenuExpanded, onDismissRequest = { newMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(nativeText(language, "\u65b0\u5efa\u4efb\u52a1", "New task")) },
                            leadingIcon = { Icon(HugeIcons.MessageAdd01, null) },
                            onClick = { newMenuExpanded = false; onNewConversation() },
                        )
                        DropdownMenuItem(
                            text = { Text(nativeText(language, "\u65b0\u5efa\u9879\u76ee\u6587\u4ef6\u5939", "New project folder")) },
                            leadingIcon = { Icon(HugeIcons.Folder01, null) },
                            onClick = {
                                newMenuExpanded = false
                                newProjectName = ""
                                projectDialogError = ""
                                showNewProjectDialog = true
                            },
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("tasks" to nativeText(language, "\u4efb\u52a1", "Tasks"), "projects" to nativeText(language, "\u9879\u76ee", "Projects")).forEach { (value, label) ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fcodePressClickable(
                                onClickLabel = nativeText(language, "\u5207\u6362\u5230$label", "Show $label"),
                            ) { mode = value },
                        shape = RoundedCornerShape(13.dp),
                        color = if (mode == value) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (mode == value) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (mode == "tasks") {
                    if (standaloneTasks.isEmpty()) {
                        item { DrawerEmptyState(nativeText(language, "\u8fd8\u6ca1\u6709\u4efb\u52a1", "No tasks yet")) }
                    } else {
                        items(standaloneTasks, key = { "task:${it.threadId}" }) { conversation ->
                            DrawerConversationTaskRow(
                                conversation = conversation,
                                selected = conversation.threadId == currentThreadId,
                                indented = false,
                                onOpen = { openTask(conversation) },
                                onFavorite = { onToggleFavorite(conversation) },
                                onRename = { onRenameConversation(conversation) },
                                onDelete = { onDeleteConversation(conversation) },
                            )
                        }
                    }
                } else {
                    if (projects.isEmpty()) {
                        item { DrawerEmptyState(nativeText(language, "\u65b0\u5efa\u9879\u76ee\u540e\uff0cAI \u5c06\u9ed8\u8ba4\u8bfb\u53d6\u8be5\u6587\u4ef6\u5939", "Create a project to give AI a default readable folder")) }
                    }
                    projects.forEach { project ->
                        val projectTasks = tasksByProject[project.path].orEmpty()
                        item(key = "project:${project.path}") {
                            DrawerProjectRow(
                                project = project,
                                taskCount = projectTasks.size,
                                expanded = project.path in expandedProjects,
                                onToggle = {
                                    expandedProjects = if (project.path in expandedProjects) expandedProjects - project.path else expandedProjects + project.path
                                },
                                onNewTask = { onNewConversationAtProject(project.path) },
                                onRename = { renameProjectError = ""; renameProject = project },
                                onDelete = { deleteProject = project },
                            )
                        }
                        if (project.path in expandedProjects) {
                            if (projectTasks.isEmpty()) {
                                item(key = "empty:${project.path}") {
                                    Text(
                                        nativeText(language, "\u8be5\u9879\u76ee\u8fd8\u6ca1\u6709\u4efb\u52a1", "No tasks in this project"),
                                        modifier = Modifier.padding(start = 46.dp, top = 6.dp, bottom = 8.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                }
                            } else {
                                items(projectTasks, key = { "project-task:${it.threadId}" }) { conversation ->
                                    DrawerConversationTaskRow(
                                        conversation = conversation,
                                        selected = conversation.threadId == currentThreadId,
                                        indented = true,
                                        onOpen = { openTask(conversation) },
                                        onFavorite = { onToggleFavorite(conversation) },
                                        onRename = { onRenameConversation(conversation) },
                                        onDelete = { onDeleteConversation(conversation) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            DrawerQuickAction(
                HugeIcons.Settings03,
                nativeText(language, "\u8bbe\u7f6e", "Settings"),
                Modifier.fillMaxWidth(),
                onBackHome,
            )
        }
    }

    if (showNewProjectDialog) {
        val name = newProjectName
        FlClashAnimatedDialog(
            onDismissRequest = { showNewProjectDialog = false; newProjectName = ""; projectDialogError = "" },
            title = { Text(nativeText(language, "\u65b0\u5efa\u9879\u76ee", "New project")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(nativeText(language, "项目会组织任务，并将该文件夹设为 AI 的默认工作目录。", "Projects organize tasks and set a default folder the AI can work in."))
                    OutlinedTextField(newProjectName, { newProjectName = it; projectDialogError = "" }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(nativeText(language, "项目名称", "Project name")) })
                    TextButton(onClick = { projectDialogError = ""; pickProjectFolder.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(HugeIcons.Folder01, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(nativeText(language, "或选择已有的本地文件夹", "Or pick an existing local folder"))
                    }
                    if (projectDialogError.isNotBlank()) Text(projectDialogError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { NativeDrawerProjectStore.create(context, name) }
                        .onSuccess { project ->
                            context.getSharedPreferences("codex_mobile", android.content.Context.MODE_PRIVATE).edit()
                                .putBoolean("custom_project_root_enabled", true)
                                .putString("custom_project_root", project.path)
                                .commit()
                            projectRevision++
                            expandedProjects = expandedProjects + project.path
                            showNewProjectDialog = false
                            projectDialogError = ""
                            newProjectName = ""
                            onNewConversationAtProject(project.path)
                        }
                        .onFailure { error -> projectDialogError = error.message.orEmpty() }
                }) { Text(nativeText(language, "\u521b\u5efa", "Create")) }
            },
            dismissButton = { TextButton(onClick = { showNewProjectDialog = false; newProjectName = ""; projectDialogError = "" }) { Text(nativeText(language, "\u53d6\u6d88", "Cancel")) } },
        )
    }

    renameProject?.let { project ->
        var name by remember(project.path) { mutableStateOf(project.name) }
        FlClashAnimatedDialog(
            onDismissRequest = { renameProject = null; renameProjectError = "" },
            title = { Text(nativeText(language, "\u91cd\u547d\u540d\u9879\u76ee", "Rename project")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it; renameProjectError = "" }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (renameProjectError.isNotBlank()) Text(renameProjectError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.trim().isNotEmpty(),
                    onClick = {
                        runCatching { NativeDrawerProjectStore.rename(context, project.path, name) }
                            .onSuccess { projectRevision++; renameProject = null; renameProjectError = "" }
                            .onFailure { error ->
                                renameProjectError = error.message ?: nativeText(language, "无法重命名项目。", "Unable to rename project.")
                            }
                    },
                ) { Text(nativeText(language, "\u4fdd\u5b58", "Save")) }
            },
            dismissButton = { TextButton(onClick = { renameProject = null; renameProjectError = "" }) { Text(nativeText(language, "\u53d6\u6d88", "Cancel")) } },
        )
    }

    deleteProject?.let { project ->
        FlClashAnimatedDialog(
            onDismissRequest = { deleteProject = null },
            title = { Text(nativeText(language, "\u5220\u9664\u9879\u76ee\uff1f", "Delete project?")) },
            text = { Text(nativeText(language, "\u5c06\u4ece\u9879\u76ee\u5217\u8868\u79fb\u9664“${project.name}”\u3002\u6e90\u6587\u4ef6\u5939\u548c\u4efb\u52a1\u4e0d\u4f1a\u88ab\u5220\u9664\u3002", "Remove \u201c${project.name}\u201d from Projects. Its source folder and tasks will not be deleted.")) },
            confirmButton = { TextButton(onClick = { NativeDrawerProjectStore.remove(context, project.path); expandedProjects = expandedProjects - project.path; projectRevision++; deleteProject = null }) { Text(nativeText(language, "\u5220\u9664", "Delete"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteProject = null }) { Text(nativeText(language, "\u53d6\u6d88", "Cancel")) } },
        )
    }
}

/**
 * Persist the strongest grant the provider accepts. A few OEM document providers advertise
 * persistable access but reject FLAG_GRANT_WRITE_URI_PERMISSION; read-only access is still useful
 * for resolving and reopening the selected workspace, and is preferable to losing the selection.
 */
internal fun persistDocumentTreePermission(context: Context, uri: android.net.Uri): Throwable? {
    val resolver = context.contentResolver
    val readWriteFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    val readWriteError = runCatching {
        resolver.takePersistableUriPermission(uri, readWriteFlags)
    }.exceptionOrNull()
    if (readWriteError == null) return null
    return runCatching {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.exceptionOrNull()
}

/** Resolve a SAF document-tree URI to a concrete filesystem path (primary/SD volumes). */
internal fun documentTreeUriToPath(uri: android.net.Uri): String? {
    if (uri.scheme != "content") return null
    val treeId = runCatching { android.provider.DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
    val separator = treeId.indexOf(':')
    if (separator < 0) return null
    val volume = treeId.substring(0, separator).takeIf { it.isNotBlank() } ?: return null
    val rest = android.net.Uri.decode(treeId.substring(separator + 1))
    val base = java.io.File(if (volume == "primary") "/storage/emulated/0" else "/storage/$volume").canonicalFile
    val selected = if (rest.isBlank()) base else java.io.File(base, rest).canonicalFile
    val basePath = base.path.trimEnd(java.io.File.separatorChar) + java.io.File.separator
    return if (selected == base || selected.path.startsWith(basePath)) selected.path else null
}

@Composable
internal fun DrawerEmptyState(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 28.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun ConversationAttentionBadge(attention: String) {
    val language = LocalNativeLanguage.current
    val label = when (attention) {
        "answer" -> nativeText(language, "\u5f85\u7b54", "Answer")
        "approval" -> nativeText(language, "\u5f85\u5ba1\u6279", "Approve")
        "resume" -> nativeText(language, "\u5f85\u7ee7\u7eed", "Resume")
        else -> nativeText(language, "\u5f85\u6267\u884c", "Plan")
    }
    val container = when (attention) {
        "answer" -> MaterialTheme.colorScheme.tertiaryContainer
        "approval" -> MaterialTheme.colorScheme.errorContainer
        "resume" -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val content = when (attention) {
        "answer" -> MaterialTheme.colorScheme.onTertiaryContainer
        "approval" -> MaterialTheme.colorScheme.onErrorContainer
        "resume" -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        shape = CircleShape,
        color = container,
        contentColor = content,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun DrawerProjectRow(
    project: NativeDrawerProject,
    taskCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onNewTask: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val language = LocalNativeLanguage.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var menuExpanded by remember(project.path) { mutableStateOf(false) }
    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fcodePressCombinedClickable(
                    onClickLabel = nativeText(
                        language,
                        if (expanded) "\u6536\u8d77\u9879\u76ee" else "\u5c55\u5f00\u9879\u76ee",
                        if (expanded) "Collapse project" else "Expand project",
                    ),
                    onLongClickLabel = nativeText(language, "\u9879\u76ee\u83dc\u5355", "Project menu"),
                    onClick = onToggle,
                    onLongClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); menuExpanded = true },
                ),
            shape = RoundedCornerShape(15.dp),
            color = if (expanded) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent,
        ) {
            Row(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(HugeIcons.ArrowRight01, null, Modifier.size(15.dp).graphicsLayer { rotationZ = if (expanded) 90f else 0f })
                Spacer(Modifier.width(9.dp))
                Icon(HugeIcons.Folder01, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(project.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(taskCount.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f))
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .fcodePressClickable(
                            onClickLabel = nativeText(language, "\u5728\u6b64\u9879\u76ee\u4e2d\u65b0\u5efa\u4efb\u52a1", "New task in this project"),
                            onClick = onNewTask,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(HugeIcons.MessageAdd01, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(text = { Text(nativeText(language, "\u91cd\u547d\u540d", "Rename")) }, leadingIcon = { Icon(HugeIcons.PencilEdit01, null) }, onClick = { menuExpanded = false; onRename() })
            DropdownMenuItem(text = { Text(nativeText(language, "\u5220\u9664", "Delete")) }, leadingIcon = { Icon(HugeIcons.Delete01, null) }, onClick = { menuExpanded = false; onDelete() })
        }
    }
}

@Composable
internal fun DrawerConversationTaskRow(
    conversation: NativeConversation,
    selected: Boolean,
    indented: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val language = LocalNativeLanguage.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var menuExpanded by remember(conversation.threadId) { mutableStateOf(false) }
    // Real-time activity (from the process-wide store) wins over the persisted task state so a
    // just-started or cross-host turn shows as running immediately.
    val liveActivity = NativeSessionActivityStore.activityFor(conversation.threadId)
    val liveRunning = liveActivity != null
    val running = liveRunning || conversation.state == CodexTaskStore.RUNNING
    Box(Modifier.padding(start = if (indented) 28.dp else 0.dp)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fcodePressCombinedClickable(
                    onClickLabel = nativeText(language, "\u6253\u5f00\u4efb\u52a1", "Open task"),
                    onLongClickLabel = nativeText(language, "\u4efb\u52a1\u83dc\u5355", "Task menu"),
                    onClick = onOpen,
                    onLongClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); menuExpanded = true },
                ),
            shape = RoundedCornerShape(15.dp),
            color = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
        ) {
            Row(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (running) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                else Icon(HugeIcons.Sparkles, null, Modifier.size(18.dp), tint = if (conversation.state == CodexTaskStore.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Text(conversation.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                if (conversation.attention.isNotBlank()) {
                    Spacer(Modifier.width(7.dp))
                    ConversationAttentionBadge(conversation.attention)
                }
                if (conversation.favorite) Icon(HugeIcons.InLove, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.76f))
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(text = { Text(if (conversation.favorite) nativeText(language, "\u53d6\u6d88\u6536\u85cf", "Unfavorite") else nativeText(language, "\u6536\u85cf", "Favorite")) }, leadingIcon = { Icon(HugeIcons.InLove, null) }, onClick = { menuExpanded = false; onFavorite() })
            DropdownMenuItem(text = { Text(nativeText(language, "\u91cd\u547d\u540d", "Rename")) }, leadingIcon = { Icon(HugeIcons.PencilEdit01, null) }, onClick = { menuExpanded = false; onRename() })
            DropdownMenuItem(text = { Text(nativeText(language, "\u5220\u9664", "Delete")) }, leadingIcon = { Icon(HugeIcons.Delete01, null) }, onClick = { menuExpanded = false; onDelete() })
        }
    }
}

@Composable
internal fun RikkaDrawer(
    currentThreadId: String,
    modelLabel: String,
    conversations: List<NativeConversation>,
    onSearch: () -> Unit,
    onRenameConversation: (NativeConversation) -> Unit,
    onDeleteConversation: (NativeConversation) -> Unit,
    onToggleFavorite: (NativeConversation) -> Unit,
    onResumeConversation: (String) -> Unit,
    onClose: () -> Unit,
    onNewConversation: () -> Unit,
    onBackHome: () -> Unit,
    onOpenLegacyWebUi: () -> Unit,
) {
    val language = LocalNativeLanguage.current
    var selectedCategory by remember { mutableStateOf("all") }
    val conversationListState = rememberLazyListState()
    var conversationRevision = 1
    conversations.forEach { conversation ->
        conversationRevision = 31 * conversationRevision + conversation.threadId.hashCode()
        conversationRevision = 31 * conversationRevision + conversation.title.hashCode()
        conversationRevision = 31 * conversationRevision + conversation.projectPath.hashCode()
        conversationRevision = 31 * conversationRevision + conversation.state.hashCode()
        conversationRevision = 31 * conversationRevision + conversation.favorite.hashCode()
        conversationRevision = 31 * conversationRevision + conversation.attention.hashCode()
    }
    val conversationSnapshot = remember(conversationRevision) { conversations.toList() }
    val projectPaths = remember(conversationRevision) {
        conversationSnapshot.asSequence().map { it.projectPath }.filter { it.isNotBlank() }.distinct().toList()
    }
    val visibleConversations = remember(conversationRevision, selectedCategory) {
        conversationSnapshot.filter { conversation ->
            when (selectedCategory) {
                "all" -> true
                "favorite" -> conversation.favorite
                else -> conversation.projectPath == selectedCategory
            }
        }
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(modifier = Modifier.size(50.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Icon(HugeIcons.Sparkles, null, modifier = Modifier.size(24.dp)) }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sillage 用户", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Icon(HugeIcons.PencilEdit01, "编辑昵称", modifier = Modifier.padding(start = 6.dp).size(16.dp))
                    }
                    Text("今天想聊点什么？", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DrawerQuickAction(HugeIcons.Search01, "搜索", Modifier.weight(1f), onSearch)
                DrawerQuickAction(HugeIcons.TransactionHistory, "历史", Modifier.weight(1f))
            }

            val categories = remember(projectPaths) { listOf("all", "favorite") + projectPaths }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                items(categories, key = { it }, contentType = { "drawer-category" }) { category ->
                    val label = when (category) { "all" -> nativeText(language, "\u5168\u90e8", "All"); "favorite" -> nativeText(language, "\u6536\u85cf", "Favorites"); else -> category.trimEnd('/').substringAfterLast('/').ifBlank { nativeText(language, "\u65e0\u9879\u76ee", "No project") } }
                    Surface(onClick = { selectedCategory = category }, shape = RoundedCornerShape(50), color = if (category == selectedCategory) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (category == "favorite") HugeIcons.InLove else HugeIcons.Folder01, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            LazyColumn(
                state = conversationListState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(
                    visibleConversations,
                    key = { it.threadId },
                    contentType = { "drawer-conversation" },
                ) { conversation ->
                    val isCurrent = conversation.threadId == currentThreadId
                    var actionsExpanded by remember(conversation.threadId) { mutableStateOf(false) }
                    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                    Box {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onResumeConversation(conversation.threadId) },
                                    onLongClick = {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        actionsExpanded = true
                                    },
                                ),
                            shape = RoundedCornerShape(28.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f) else Color.Transparent,
                            contentColor = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                            if (conversation.state == CodexTaskStore.RUNNING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                val tint = if (conversation.state == CodexTaskStore.FAILED) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                Icon(HugeIcons.Sparkles, conversation.state, modifier = Modifier.size(19.dp), tint = tint)
                            }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    conversation.title,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                if (conversation.attention.isNotBlank()) {
                                    Spacer(Modifier.width(7.dp))
                                    ConversationAttentionBadge(conversation.attention)
                                }
                                if (conversation.favorite) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(HugeIcons.InLove, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.76f))
                                }
                            }
                        }
                        DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(if (conversation.favorite) nativeText(language, "\u53d6\u6d88\u6536\u85cf", "Unfavorite") else nativeText(language, "\u6536\u85cf", "Favorite")) },
                                leadingIcon = { Icon(HugeIcons.InLove, null) },
                                onClick = { actionsExpanded = false; onToggleFavorite(conversation) },
                            )
                            DropdownMenuItem(
                                text = { Text(nativeText(language, "\u5220\u9664", "Delete")) },
                                leadingIcon = { Icon(HugeIcons.Delete01, null) },
                                onClick = { actionsExpanded = false; onDeleteConversation(conversation) },
                            )
                        }
                    }
                }
            }

            NavigationDrawerItem(
                label = { Text(modelLabel.ifBlank { nativeText(language, "\u9009\u62e9\u52a9\u624b", "Select assistant") }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                selected = false,
                onClick = onNewConversation,
                icon = { Icon(HugeIcons.LookTop, "助手") },
                colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {}) { Icon(HugeIcons.LookTop, "助手") }
                IconButton(onClick = {}) { Icon(HugeIcons.Sparkles, "功能") }
                IconButton(onClick = {}) { Icon(HugeIcons.InLove, "收藏") }
                IconButton(onClick = {}) { Icon(HugeIcons.ChartColumn, "统计") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onBackHome) { Icon(HugeIcons.Settings03, "设置") }
            }
        }
    }
}
@Composable
internal fun ConversationMenu(
    conversation: NativeConversation,
    onRename: (NativeConversation) -> Unit,
    onDelete: (NativeConversation) -> Unit,
    onToggleFavorite: (NativeConversation) -> Unit,
) {
    val language = LocalNativeLanguage.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(32.dp)) { Icon(HugeIcons.MoreVertical, nativeText(language, "\u66f4\u591a", "More"), modifier = Modifier.size(18.dp)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(if (conversation.favorite) nativeText(language, "\u53d6\u6d88\u6536\u85cf", "Unfavorite") else nativeText(language, "\u6536\u85cf", "Favorite")) }, leadingIcon = { Icon(HugeIcons.InLove, null) }, onClick = { expanded = false; onToggleFavorite(conversation) })
            DropdownMenuItem(text = { Text(nativeText(language, "\u91cd\u547d\u540d", "Rename")) }, leadingIcon = { Icon(HugeIcons.PencilEdit01, null) }, onClick = { expanded = false; onRename(conversation) })
            DropdownMenuItem(text = { Text(nativeText(language, "\u5220\u9664", "Delete")) }, leadingIcon = { Icon(HugeIcons.Delete01, null) }, onClick = { expanded = false; onDelete(conversation) })
        }
    }
}

@Composable
internal fun ConversationSearchDialog(
    conversations: List<NativeConversation>,
    sessionsRoot: java.io.File?,
    onDismiss: () -> Unit,
    onSelect: (NativeConversation, NativeSearchHit?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("title") }
    var fullTextHits by remember { mutableStateOf<List<NativeSearchHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchProgress by remember { mutableStateOf(0 to 0) }
    val language = LocalNativeLanguage.current
    val titleResults = conversations.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
    val engine = remember(sessionsRoot) {
        NativeConversationSearchEngine(sessionsRoot = { sessionsRoot })
    }
    // Debounced, cancellation-safe search: every keystroke restarts this effect and cancels the
    // previous scan (engine.search is cooperative), so stale results can never win.
    LaunchedEffect(query, mode) {
        if (mode != "fulltext") {
            fullTextHits = emptyList()
            searching = false
            return@LaunchedEffect
        }
        val normalized = query.trim()
        if (normalized.length < 2) {
            fullTextHits = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        kotlinx.coroutines.delay(250)
        var lastProgress = 0 to 0
        val hits = engine.search(normalized, progress = { scanned, total -> lastProgress = scanned to total })
        searchProgress = lastProgress
        fullTextHits = hits
        searching = false
    }
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(nativeText(language, "\u641c\u7d22\u5bf9\u8bdd", "Search conversations")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = if (mode == "title") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fcodePressClickable(onClickLabel = nativeText(language, "\u6309\u6807\u9898\u641c\u7d22", "Search by title")) { mode = "title" },
                    ) { Text(nativeText(language, "\u6807\u9898", "Title"), Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium) }
                    Surface(
                        shape = CircleShape,
                        color = if (mode == "fulltext") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fcodePressClickable(onClickLabel = nativeText(language, "\u5168\u6587\u641c\u7d22", "Search full text")) { mode = "fulltext" },
                    ) { Text(nativeText(language, "\u5168\u6587", "Full text"), Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium) }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(HugeIcons.Search01, null) },
                    placeholder = {
                        Text(if (mode == "fulltext")
                            nativeText(language, "\u641c\u7d22\u6240\u6709\u5bf9\u8bdd\u7684\u5185\u5bb9\u2026", "Search message content across conversations\u2026")
                        else nativeText(language, "\u641c\u7d22\u5bf9\u8bdd", "Search conversations"))
                    },
                )
                if (mode == "fulltext" && searching) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.8.dp)
                        Spacer(Modifier.width(8.dp))
                        val (scanned, total) = searchProgress
                        Text(
                            nativeText(language, "\u6b63\u5728\u641c\u7d22\u2026\uff08$scanned/$total\uff09", "Searching\u2026 ($scanned/$total)"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                when {
                    mode == "fulltext" && query.trim().length < 2 -> Text(
                        nativeText(language, "\u8f93\u5165\u81f3\u5c11 2 \u4e2a\u5b57\u7b26\u5f00\u59cb\u5168\u6587\u641c\u7d22", "Type at least 2 characters to search full text"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    mode == "fulltext" && !searching && fullTextHits.isEmpty() && query.trim().length >= 2 -> Text(
                        nativeText(language, "\u6ca1\u6709\u627e\u5230\u5339\u914d\u5185\u5bb9", "No matching messages found"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Unit
                }
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    if (mode == "title") {
                        items(titleResults, key = { it.threadId }) { conversation ->
                            NavigationDrawerItem(label = { Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, selected = false, onClick = { onSelect(conversation, null) })
                        }
                    } else {
                        items(fullTextHits.distinctBy { it.threadId }, key = { it.threadId + it.lineNumber }) { hit ->
                            val conversation = conversations.firstOrNull { it.threadId == hit.threadId }
                            NavigationDrawerItem(
                                label = {
                                    Column {
                                        Text(
                                            conversation?.title?.takeIf { it.isNotBlank() } ?: hit.threadId.take(12),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            hit.snippet,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                selected = false,
                                onClick = { conversation?.let { onSelect(it, hit) } },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(nativeText(language, "\u5173\u95ed", "Close")) } },
    )
}

@Composable
internal fun RenameConversationDialog(conversation: NativeConversation, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var title by remember(conversation.threadId) { mutableStateOf(conversation.title) }
    val language = LocalNativeLanguage.current
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(nativeText(language, "\u91cd\u547d\u540d\u5bf9\u8bdd", "Rename conversation")) },
        text = { OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), singleLine = true) },
        confirmButton = { TextButton(onClick = { if (title.isNotBlank()) onConfirm(title.trim()) }) { Text(nativeText(language, "\u4fdd\u5b58", "Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(nativeText(language, "\u53d6\u6d88", "Cancel")) } },
    )
}

@Composable
internal fun DrawerQuickAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Surface(modifier = modifier.liquidPress(onClick = onClick), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun DrawerMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = false,
        onClick = onClick,
        icon = { Icon(icon, null) },
        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
    )
}

