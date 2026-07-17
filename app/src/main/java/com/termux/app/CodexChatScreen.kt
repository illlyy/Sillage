@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.termux.app

import android.util.Base64

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
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
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.math.tanh
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.ChartColumn
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.Bug01
import me.rerere.hugeicons.stroke.MagicWand01
import me.rerere.hugeicons.stroke.Files02
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.InLove
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
import me.rerere.hugeicons.stroke.Voice
import me.rerere.hugeicons.stroke.Zap

private val FcodeLightColors = lightColorScheme(
    primary = Color(0xFF73515A),
    onPrimary = Color(0xFFFDFBFB),
    primaryContainer = Color(0xFFE6CDD2),
    onPrimaryContainer = Color(0xFF553840),
    secondary = Color(0xFF67565A),
    onSecondary = Color(0xFFFDFBFB),
    secondaryContainer = Color(0xFFE6CDD2),
    onSecondaryContainer = Color(0xFF493C40),
    tertiary = Color(0xFF6E5A45),
    onTertiary = Color(0xFFFDFBFB),
    tertiaryContainer = Color(0xFFE6D6C1),
    onTertiaryContainer = Color(0xFF4F412F),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFDFBFB),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF5F1F1),
    onBackground = Color(0xFF241F20),
    surface = Color(0xFFF5F1F1),
    onSurface = Color(0xFF241F20),
    surfaceVariant = Color(0xFFE5DCDE),
    onSurfaceVariant = Color(0xFF504749),
    outline = Color(0xFF776C6E),
    outlineVariant = Color(0xFFCFC4C6),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF393334),
    inverseOnSurface = Color(0xFFF2E9EA),
    inversePrimary = Color(0xFFD9AEB7),
    surfaceDim = Color(0xFFDDD6D7),
    surfaceBright = Color(0xFFF5F1F1),
    surfaceContainerLowest = Color(0xFFFDFBFB),
    surfaceContainerLow = Color(0xFFF1EBEC),
    surfaceContainer = Color(0xFFEDE7E8),
    surfaceContainerHigh = Color(0xFFE8E1E2),
    surfaceContainerHighest = Color(0xFFE2DADB),
)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FcodeChatTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = FcodeLightColors,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeChatScreen(
    state: NativeChatState,
    onSend: (String) -> Unit,
    onInputChange: (String) -> Unit,
    onRetry: (String) -> Unit,
    onEditMessage: (String, String) -> Unit,
    onStop: () -> Unit,
    onNewConversation: () -> Unit,
    onResumeConversation: (String) -> Unit,
    onPickImages: () -> Unit,
    onPickFiles: () -> Unit,
    onRemoveAttachment: (NativeAttachment) -> Unit,
    onRenameConversation: (NativeConversation, String) -> Unit,
    onDeleteConversation: (NativeConversation) -> Unit,
    onToggleFavorite: (NativeConversation) -> Unit,
    onBackHome: () -> Unit,
    onOpenLegacyWebUi: () -> Unit,
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val listDragged by listState.interactionSource.collectIsDraggedAsState()
    var followOutput by remember { mutableStateOf(true) }
    var historyLimit by remember { mutableIntStateOf(40) }
    var inputHeightPx by remember { mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val inputBottomPadding = with(density) { inputHeightPx.toDp() } + 8.dp
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val floatingInsetModifier = if (imeVisible) Modifier.imePadding() else Modifier
    val showScrollToBottom by remember { derivedStateOf { state.messages.isNotEmpty() && listState.canScrollForward } }
    var showModelPicker by remember { mutableStateOf(false) }
    var showFilesSheet by remember { mutableStateOf(false) }
    var showConversationSearch by remember { mutableStateOf(false) }
    var showMessageSearch by remember { mutableStateOf(false) }
    var renameConversation by remember { mutableStateOf<NativeConversation?>(null) }
    var deleteConversation by remember { mutableStateOf<NativeConversation?>(null) }
    var elapsedSeconds by remember { mutableStateOf(0L) }
    var editMessage by remember { mutableStateOf<NativeChatMessage?>(null) }
    var previewAttachment by remember { mutableStateOf<NativeAttachment?>(null) }

    LaunchedEffect(state.busy, state.turnStartedAt) {
        while (state.busy) {
            elapsedSeconds = ((System.currentTimeMillis() - state.turnStartedAt).coerceAtLeast(0L) / 1000L)
            delay(1000L)
        }
        elapsedSeconds = 0L
    }

    LaunchedEffect(listDragged) {
        if (!listDragged) followOutput = !listState.canScrollForward
    }

    LaunchedEffect(state.conversationAnimationKey) {
        historyLimit = 40
        delay(16L)
        if (state.messages.isNotEmpty()) {
            val visibleCount = minOf(historyLimit, state.messages.size)
            val loaderOffset = if (state.messages.size > visibleCount) 1 else 0
            listState.scrollToItem((visibleCount - 1 + loaderOffset + if (state.busy) 1 else 0).coerceAtLeast(0), Int.MAX_VALUE)
        }
    }

    LaunchedEffect(state.messages.size) {
        if (followOutput && state.messages.isNotEmpty()) {
            val visibleCount = minOf(historyLimit, state.messages.size)
            val loaderOffset = if (state.messages.size > visibleCount) 1 else 0
            listState.animateScrollToItem((visibleCount - 1 + loaderOffset + if (state.busy) 1 else 0).coerceAtLeast(0), Int.MAX_VALUE)
        }
    }

    // Streaming deltas can arrive many times per second. Follow them at a stable frame rate
    // instead of launching a new scroll animation for every token.
    LaunchedEffect(state.busy, followOutput) {
        while (state.busy && followOutput) {
            if (state.messages.isNotEmpty() && !listDragged) {
                val visibleCount = minOf(historyLimit, state.messages.size)
                val loaderOffset = if (state.messages.size > visibleCount) 1 else 0
                listState.scrollToItem((visibleCount - 1 + loaderOffset + if (state.busy) 1 else 0).coerceAtLeast(0), Int.MAX_VALUE)
            }
            delay(96L)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            RikkaDrawer(
                modelLabel = state.modelLabel,
                conversations = state.conversations,
                onSearch = { showConversationSearch = true },
                onRenameConversation = { renameConversation = it },
                onDeleteConversation = { deleteConversation = it },
                onToggleFavorite = onToggleFavorite,
                onResumeConversation = { threadId ->
                    onResumeConversation(threadId)
                    scope.launch { drawerState.close() }
                },
                onClose = { scope.launch { drawerState.close() } },
                onNewConversation = {
                    onNewConversation()
                    scope.launch { drawerState.close() }
                },
                onBackHome = onBackHome,
                onOpenLegacyWebUi = onOpenLegacyWebUi,
            )
        },
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    RikkaTopBar(
                        title = state.conversationTitle,
                        modelLabel = state.modelLabel,
                        ready = state.ready,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSearch = { showMessageSearch = true },
                        onExport = { shareConversation(context, state.conversationTitle, state.messages) },
                        canExport = state.messages.any { it.role == NativeChatRole.USER || it.role == NativeChatRole.ASSISTANT },
                        onNewConversation = onNewConversation,
                    )
                },
                bottomBar = {},
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    AssistantBackdrop()
                    if (state.messages.isEmpty()) {
                        EmptyChatState(
                            ready = state.ready,
                            onPrompt = onInputChange,
                        )
                    } else {
                        val visibleMessages = remember(state.messages.size, state.conversationAnimationKey, historyLimit) {
                            state.messages.takeLast(historyLimit)
                        }
                        val hiddenMessageCount = state.messages.size - visibleMessages.size
                        val retryPrompts = remember(visibleMessages, state.conversationAnimationKey) {
                            buildMap<String, String> {
                                var lastUser: String? = null
                                visibleMessages.forEach { message ->
                                    if (message.role == NativeChatRole.USER) lastUser = message.content
                                    else if (message.role == NativeChatRole.ASSISTANT || message.role == NativeChatRole.ERROR) lastUser?.let { put(message.id, it) }
                                }
                            }
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().imePadding(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = inputBottomPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (hiddenMessageCount > 0) {
                                item(key = "history-loader", contentType = "history-loader") {
                                    TextButton(
                                        onClick = { historyLimit = (historyLimit + 40).coerceAtMost(state.messages.size) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text("\u52a0\u8f7d\u66f4\u65e9\u6d88\u606f\uff08\u8fd8\u6709 $hiddenMessageCount \u6761\uff09") }
                                }
                            }
                            itemsIndexed(
                                items = visibleMessages,
                                key = { _, message -> message.id },
                                contentType = { _, message -> message.role },
                            ) { _, message ->
                                val previousUser = retryPrompts[message.id]
                                RikkaMessageItem(
                                    message = message,
                                    onEdit = { editMessage = message },
                                    onRetry = previousUser?.let { prompt -> { onRetry(prompt) } },
                                    onQuote = { quoted ->
                                        val block = quoted.lineSequence().joinToString("\n") { "> $it" }
                                        onInputChange(listOf(state.input.trimEnd(), block, "").filter { it.isNotEmpty() }.joinToString("\n\n"))
                                    },
                                )
                            }
                            if (state.busy) {
                                item("processing") { ProcessingPanel(state, elapsedSeconds) }
                            }
                        }
                        AnimatedVisibility(
                            visible = showScrollToBottom,
                            modifier = Modifier.align(Alignment.BottomEnd).then(floatingInsetModifier).padding(end = 16.dp, bottom = inputBottomPadding + 16.dp),
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                        ) {
                            SmallFloatingActionButton(
                                onClick = {
                                    followOutput = true
                                    scope.launch { listState.animateScrollToItem(state.messages.lastIndex, Int.MAX_VALUE) }
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape = CircleShape,
                            ) {
                                Icon(HugeIcons.ArrowDown01, "回到底部", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    RikkaChatInput(
                        value = state.input,
                        enabled = state.ready && !state.busy,
                        loading = state.busy,
                        modelLabel = state.modelLabel,
                        onModelClick = { showModelPicker = true },
                        effortOptions = state.modelOptions.firstOrNull { it.id == state.selectedModel }?.efforts.orEmpty().ifEmpty { listOf("none", "low", "medium", "high", "xhigh") },
                        selectedEffort = state.selectedEffort,
                        onEffortSelected = { state.selectedEffort = it },
                        attachments = state.attachments,
                        onMoreClick = { showFilesSheet = true },
                        onRemoveAttachment = onRemoveAttachment,
                        onPreviewAttachment = { previewAttachment = it },
                        onValueChange = onInputChange,
                        onSend = { if (state.busy) onStop() else onSend(state.input) },
                        onHeightChanged = { inputHeightPx = it },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
    previewAttachment?.let { attachment ->
        AttachmentPreviewDialog(attachment = attachment, onDismiss = { previewAttachment = null })
    }
    if (showMessageSearch) {
        MessageSearchDialog(
            messages = state.messages,
            onDismiss = { showMessageSearch = false },
            onSelect = { index ->
                showMessageSearch = false
                scope.launch { listState.animateScrollToItem(index) }
            },
        )
    }
    editMessage?.let { message ->
        EditMessageDialog(
            initialText = message.content,
            onDismiss = { editMessage = null },
            onConfirm = { value -> editMessage = null; onEditMessage(message.id, value) },
        )
    }
    if (showConversationSearch) {
        ConversationSearchDialog(
            conversations = state.conversations,
            onDismiss = { showConversationSearch = false },
            onSelect = { onResumeConversation(it.threadId); showConversationSearch = false },
        )
    }
    renameConversation?.let { conversation ->
        RenameConversationDialog(
            conversation = conversation,
            onDismiss = { renameConversation = null },
            onConfirm = { title -> onRenameConversation(conversation, title); renameConversation = null },
        )
    }
    deleteConversation?.let { conversation ->
        FlClashAnimatedDialog(
            onDismissRequest = { deleteConversation = null },
            title = { Text("搜索对话") },
            text = { Text("确定从列表中删除“${conversation.title}”吗？") },
            confirmButton = { TextButton(onClick = { onDeleteConversation(conversation); deleteConversation = null }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteConversation = null }) { Text("取消") } },
        )
    }
    if (showFilesSheet) {
        ModalBottomSheet(onDismissRequest = { showFilesSheet = false }) {
            RikkaFilesPicker(
                onPickImage = { showFilesSheet = false; onPickImages() },
                onPickFile = { showFilesSheet = false; onPickFiles() },
            )
        }
    }
    if (showModelPicker) {
        ChoiceDialog(
            title = "选择模型",
            options = state.modelOptions.map { it.id to it.name },
            selected = state.selectedModel,
            onDismiss = { showModelPicker = false },
            onSelect = { id ->
                state.selectedModel = id
                val option = state.modelOptions.firstOrNull { it.id == id }
                state.modelLabel = option?.name ?: id
                if (option != null && state.selectedEffort !in option.efforts) {
                    state.selectedEffort = option.defaultEffort
                }
                showModelPicker = false
            },
        )
    }

}

@Composable
private fun FlClashAnimatedDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    var windowVisible by remember { mutableStateOf(true) }
    val visibility = remember { MutableTransitionState(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        delay(18)
        visibility.targetState = true
    }
    fun dismissAnimated() {
        visibility.targetState = false
        scope.launch {
            delay(210)
            windowVisible = false
            onDismissRequest()
        }
    }
    if (windowVisible) {
        Dialog(onDismissRequest = { dismissAnimated() }, properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true, usePlatformDefaultWidth = false)) {
            AnimatedVisibility(
                visibleState = visibility,
                enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.86f, animationSpec = tween(250, easing = CubicBezierEasing(0.175f, 0.885f, 0.32f, 1f))),
                exit = fadeOut(tween(145)) + scaleOut(targetScale = 0.90f, animationSpec = tween(190, easing = FastOutSlowInEasing)),
            ) {
                Surface(
                    modifier = modifier.fillMaxWidth(0.88f).widthIn(min = 280.dp, max = 560.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                    shadowElevation = 12.dp,
                ) {
                    Column(modifier = Modifier.padding(top = 24.dp, bottom = 10.dp)) {
                        if (title != null) Box(Modifier.padding(horizontal = 24.dp)) { title() }
                        if (title != null && text != null) Spacer(Modifier.height(16.dp))
                        if (text != null) Box(Modifier.padding(horizontal = 24.dp)) { text() }
                        Spacer(Modifier.height(14.dp))
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            if (dismissButton != null) dismissButton()
                            confirmButton()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageSearchDialog(
    messages: List<NativeChatMessage>,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, messages.size) {
        if (query.isBlank()) emptyList()
        else messages.mapIndexedNotNull { index, message ->
            if (message.role != NativeChatRole.ACTIVITY && message.content.contains(query.trim(), ignoreCase = true)) index to message else null
        }
    }
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text("搜索当前对话") },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(HugeIcons.Search01, null) },
                    placeholder = { Text("输入关键词") },
                )
                if (query.isNotBlank()) {
                    Text("找到 ${results.size} 条结果", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(results, key = { it.first }) { (index, message) ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { onSelect(index) },
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(if (message.role == NativeChatRole.USER) "你" else "助手", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(message.content.replace('\n', ' '), maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun EditMessageDialog(initialText: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember(initialText) { mutableStateOf(initialText) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑消息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("保存后将从这条消息重新生成后续内容。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = value, onValueChange = { value = it }, modifier = Modifier.fillMaxWidth().focusRequester(focusRequester), minLines = 3, maxLines = 10)
            }
        },
        confirmButton = { TextButton(onClick = { if (value.isNotBlank()) onConfirm(value.trim()) }) { Text("保存并重新生成") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(options, key = { it.first }) { option ->
                    NavigationDrawerItem(
                        label = { Text(option.second) },
                        selected = option.first == selected,
                        onClick = { onSelect(option.first) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Modifier.liquidPress(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    var pressed by remember { mutableStateOf(false) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var measuredSize by remember { mutableStateOf(IntSize.Zero) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.035f else 1f,
        animationSpec = spring(dampingRatio = 0.48f, stiffness = 360f),
        label = "liquidCardScale",
    )
    val targetX = if (pressed && measuredSize.width > 0) {
        val normalized = (pointer.x - measuredSize.width / 2f) / measuredSize.width
        9f * tanh(normalized * 1.8f)
    } else 0f
    val targetY = if (pressed && measuredSize.height > 0) {
        val normalized = (pointer.y - measuredSize.height / 2f) / measuredSize.height
        7f * tanh(normalized * 1.8f)
    } else 0f
    val translationX by animateFloatAsState(targetX, spring(dampingRatio = 0.56f, stiffness = 430f), label = "liquidCardX")
    val translationY by animateFloatAsState(targetY, spring(dampingRatio = 0.56f, stiffness = 430f), label = "liquidCardY")
    return this
        .onSizeChanged { measuredSize = it }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.translationX = translationX
            this.translationY = translationY
        }
        .semantics {
            role = Role.Button
            onClick { if (enabled) onClick(); enabled }
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                pressed = true
                pointer = down.position
                val start = down.position
                var moved = false
                var canceled = false
                var active = true
                while (active) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    pointer = change.position
                    if ((change.position - start).getDistance() > viewConfiguration.touchSlop) moved = true
                    if (change.isConsumed && moved) canceled = true
                    active = change.pressed
                }
                pressed = false
                if (!moved && !canceled) onClick()
            }
        }
}

private fun shareConversation(context: android.content.Context, title: String, messages: List<NativeChatMessage>) {
    val body = buildString {
        append("# Fcode Codex 对话\n\n")
        append("导出时间：").append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())).append("\n\n")
        messages.forEach { message ->
            when (message.role) {
                NativeChatRole.USER -> append("## 你\n\n").append(message.content).append("\n\n")
                NativeChatRole.ASSISTANT -> append("## 助手\n\n").append(message.content).append("\n\n")
                NativeChatRole.ERROR -> append("## 错误\n\n").append(message.content).append("\n\n")
                NativeChatRole.ACTIVITY -> Unit
            }
        }
    }.trim()
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
        .setType("text/markdown")
        .putExtra(android.content.Intent.EXTRA_SUBJECT, "Fcode Codex 对话")
        .putExtra(android.content.Intent.EXTRA_TEXT, body)
    context.startActivity(android.content.Intent.createChooser(intent, "导出对话"))
}

private data class StarterPrompt(val icon: ImageVector, val title: String, val description: String, val prompt: String)

@Composable
private fun EmptyChatState(ready: Boolean, onPrompt: (String) -> Unit) {
    val prompts = remember {
        listOf(
            StarterPrompt(HugeIcons.Code, "理解项目", "分析当前工作区结构和核心逻辑", "请分析当前项目的目录结构，说明核心模块、启动流程和值得优先改进的地方。"),
            StarterPrompt(HugeIcons.Bug01, "检查问题", "查找潜在 bug、崩溃和异常边界", "请检查当前项目中可能的 bug、崩溃点和异常边界，优先修复高风险问题并运行验证。"),
            StarterPrompt(HugeIcons.MagicWand01, "改进体验", "优化 UI、性能和用户交互", "请审查当前项目的用户体验，直接实现一项最有价值的 UI 或交互优化，并完成测试。"),
            StarterPrompt(HugeIcons.Files02, "继续开发", "读取变更并建议下一步", "请检查当前 Git 变更和项目状态，总结已完成的工作，然后直接继续最合理的下一步。"),
        )
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Icon(HugeIcons.Sparkles, null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("今天想做什么？", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (ready) "Codex 已准备好，可以与你一起处理当前项目" else "正在连接 Codex…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Column(modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            prompts.forEach { item ->
                Surface(
                    modifier = Modifier.fillMaxWidth().liquidPress(enabled = ready) { onPrompt(item.prompt) },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                ) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) { Icon(item.icon, null, modifier = Modifier.size(19.dp)) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(HugeIcons.ArrowRight01, null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun RikkaTopBar(
    title: String,
    modelLabel: String,
    ready: Boolean,
    onOpenDrawer: () -> Unit,
    onSearch: () -> Unit,
    onExport: () -> Unit,
    canExport: Boolean,
    onNewConversation: () -> Unit,
) {
    TopAppBar(
        modifier = Modifier.statusBarsPadding(),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(HugeIcons.Menu03, contentDescription = "对话列表")
            }
        },
        title = {
            Column {
                AnimatedContent(
                    targetState = title,
                    transitionSpec = {
                        (fadeIn(tween(150)) + scaleIn(initialScale = 0.88f, animationSpec = spring(dampingRatio = 0.55f, stiffness = 360f)) + slideInHorizontally { it / 8 })
                            .togetherWith(fadeOut(tween(110)) + scaleOut(targetScale = 1.06f, animationSpec = tween(120)) + slideOutHorizontally { -it / 10 })
                    },
                    label = "conversationTitleTransition",
                ) { value ->
                    Text(value, maxLines = 1, style = MaterialTheme.typography.bodyMedium, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    text = "默认助手 / ${modelLabel.ifBlank { "默认模型" }} (Fcode)",
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (ready) Color(0xFF43A047) else MaterialTheme.colorScheme.outline, CircleShape),
            )
            IconButton(onClick = {}) {
                Icon(HugeIcons.LeftToRightListBullet, contentDescription = "对话选项")
            }
            IconButton(onClick = onNewConversation) {
                Icon(HugeIcons.MessageAdd01, contentDescription = "新对话")
            }
        },
    )
}

@Composable
private fun AssistantBackdrop() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f),
                        Color.Transparent,
                        Color.Transparent,
                    ),
                ),
            ),
    )
}

@Composable
private fun RikkaEmptyState(
    status: String,
    ready: Boolean,
    onSuggestion: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        HugeIcons.Sparkles,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("有什么可以帮你？", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(5.dp))
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(listOf("检查当前项目", "运行测试", "解释这段代码", "修复一个问题")) { text ->
                Surface(
                    modifier = Modifier.clickable(enabled = ready) { onSuggestion(text) },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun RikkaMessageItem(message: NativeChatMessage, onEdit: () -> Unit, onRetry: (() -> Unit)?, onQuote: (String) -> Unit) {
    when (message.role) {
        NativeChatRole.USER -> RikkaUserMessage(message.content, onEdit)
        NativeChatRole.ASSISTANT -> RikkaAssistantMessage(message.content, message.streaming, message.revealStartedAt, onRetry, onQuote)
        NativeChatRole.ACTIVITY -> RikkaActivityMessage(message.content)
        NativeChatRole.ERROR -> RikkaErrorMessage(message.content, onRetry)
    }
}

@Composable
private fun RikkaUserMessage(text: String, onEdit: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var menuExpanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Box {
            Surface(
                modifier = Modifier.widthIn(max = 360.dp).combinedClickable(onClick = {}, onLongClick = { menuExpanded = true }),
                shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                SelectionContainer {
                    Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp, letterSpacing = 0.1.sp)
                }
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("复制") },
                    leadingIcon = { Icon(HugeIcons.Copy01, null, modifier = Modifier.size(18.dp)) },
                    onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(text)); menuExpanded = false },
                )
                DropdownMenuItem(
                    text = { Text("编辑并重新生成") },
                    leadingIcon = { Icon(HugeIcons.PencilEdit01, null, modifier = Modifier.size(18.dp)) },
                    onClick = { menuExpanded = false; onEdit() },
                )
            }
        }
        MessageActions(text = text, onEdit = onEdit)
    }
}


@Composable
private fun StreamingResponseText(text: String, streaming: Boolean, revealStartedAt: Long) {
    val latestText = rememberUpdatedState(text)
    val animateReveal = streaming || (revealStartedAt > 0L && System.currentTimeMillis() - revealStartedAt < 30_000L)
    var displayedText by remember { mutableStateOf(if (animateReveal) "" else text) }
    var revealedTailLength by remember { mutableIntStateOf(0) }
    var showRichText by remember { mutableStateOf(!animateReveal) }
    val tailAlpha = remember { androidx.compose.animation.core.Animatable(1f) }

    LaunchedEffect(streaming, animateReveal) {
        if (!animateReveal) {
            displayedText = latestText.value
            showRichText = true
            return@LaunchedEffect
        }
        showRichText = false
        if (!latestText.value.startsWith(displayedText)) displayedText = ""
        while (streaming || displayedText.length < latestText.value.length) {
            val target = latestText.value
            if (!target.startsWith(displayedText)) {
                displayedText = ""
            } else if (displayedText.length < target.length) {
                val pending = target.length - displayedText.length
                val step = when {
                    pending > 400 -> 14
                    pending > 160 -> 9
                    pending > 64 -> 6
                    pending > 20 -> 4
                    else -> 2
                }
                val nextLength = (displayedText.length + step).coerceAtMost(target.length)
                revealedTailLength = nextLength - displayedText.length
                displayedText = target.take(nextLength)
            }
            delay(if (streaming) 48L else 36L)
        }
        displayedText = latestText.value
        delay(24L)
        showRichText = true
    }

    LaunchedEffect(displayedText) {
        if (displayedText.isNotEmpty() && !showRichText) {
            tailAlpha.snapTo(0.42f)
            tailAlpha.animateTo(1f, tween(150, easing = LinearOutSlowInEasing))
        }
    }

    if (!showRichText) {
        val stableEnd = (displayedText.length - revealedTailLength).coerceAtLeast(0)
        val animatedText = androidx.compose.ui.text.buildAnnotatedString {
            append(displayedText.substring(0, stableEnd))
            withStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.onSurface.copy(alpha = tailAlpha.value))) {
                append(displayedText.substring(stableEnd))
            }
        }
        Text(
            text = animatedText,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 24.sp,
            letterSpacing = 0.1.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    } else {
        RichResponseText(text)
    }
}

@Composable
private fun RikkaAssistantMessage(text: String, streaming: Boolean, revealStartedAt: Long, onRetry: (() -> Unit)?, onQuote: (String) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).combinedClickable(onClick = {}, onLongClick = { menuExpanded = true }),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("\u9ed8\u8ba4\u52a9\u624b", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                StreamingResponseText(text, streaming, revealStartedAt)
                if (streaming) Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(7.dp))
                    Text("\u6b63\u5728\u751f\u6210", style = MaterialTheme.typography.labelSmall)
                }
            }
            MessageActions(text = text, onRetry = onRetry, allowShare = true)
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("复制回答") },
                leadingIcon = { Icon(HugeIcons.Copy01, null, modifier = Modifier.size(18.dp)) },
                onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(text)); menuExpanded = false },
            )
            DropdownMenuItem(
                text = { Text("引用回答") },
                leadingIcon = { Icon(HugeIcons.LeftToRightListBullet, null, modifier = Modifier.size(18.dp)) },
                onClick = { menuExpanded = false; onQuote(text) },
            )
            if (onRetry != null) DropdownMenuItem(
                text = { Text("重新生成") },
                leadingIcon = { Icon(HugeIcons.Refresh03, null, modifier = Modifier.size(18.dp)) },
                onClick = { menuExpanded = false; onRetry() },
            )
            DropdownMenuItem(
                text = { Text("分享回答") },
                leadingIcon = { Icon(HugeIcons.Share08, null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    menuExpanded = false
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
                    context.startActivity(android.content.Intent.createChooser(intent, "分享回答"))
                },
            )
        }
    }
}

@Composable
private fun MessageActions(text: String, onEdit: (() -> Unit)? = null, onRetry: (() -> Unit)? = null, allowShare: Boolean = false) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        IconButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(text)) }, modifier = Modifier.size(32.dp)) { Icon(HugeIcons.Copy01, "复制", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (onEdit != null) IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) { Icon(HugeIcons.PencilEdit01, "编辑", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (onRetry != null) IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) { Icon(HugeIcons.Refresh03, "重新生成", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (allowShare) IconButton(onClick = {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
            context.startActivity(android.content.Intent.createChooser(intent, "分享回答"))
        }, modifier = Modifier.size(32.dp)) { Icon(HugeIcons.Share08, "分享", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun QElasticExpand(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(expandFrom = Alignment.Top, animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f), clip = true),
        exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = spring(dampingRatio = 0.82f, stiffness = 440f), clip = true),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(75, easing = LinearEasing)) + slideInVertically(initialOffsetY = { -it / 8 }, animationSpec = tween(130, easing = LinearEasing)),
            exit = fadeOut(tween(65, easing = LinearEasing)) + slideOutVertically(targetOffsetY = { -it / 10 }, animationSpec = tween(105, easing = LinearEasing)),
        ) { content() }
    }
}

@Composable
private fun ProcessingPanel(state: NativeChatState, elapsedSeconds: Long) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("${state.processingLabel.ifBlank { "处理中" }} ${elapsedSeconds}s", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        QElasticExpand(expanded) {
            Column {
                if (state.reasoningText.isNotBlank()) Box(modifier = Modifier.padding(top = 10.dp)) { RichResponseText(state.reasoningText) }
                if (state.commandText.isNotBlank()) Surface(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) { SelectionContainer { Text(state.commandText, modifier = Modifier.padding(10.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) } }
            }
        }
    }
}

private data class MarkdownBlock(val code: Boolean, val text: String, val language: String = "")

private fun markdownBlocks(source: String): List<MarkdownBlock> {
    if (!source.contains("```")) return listOf(MarkdownBlock(false, source))
    return source.split("```").mapIndexedNotNull { index, part ->
        if (part.isEmpty()) null
        else if (index % 2 == 1) {
            val firstLine = part.substringBefore('\n').trim()
            val hasLanguage = part.contains('\n') && firstLine.matches(Regex("[A-Za-z0-9_+.#-]{1,24}"))
            MarkdownBlock(true, if (hasLanguage) part.substringAfter('\n').trimEnd() else part.trimEnd(), if (hasLanguage) firstLine else "")
        } else MarkdownBlock(false, part)
    }
}

@Composable
private fun RichResponseText(text: String) {
    val blocks = remember(text) { markdownBlocks(text) }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block -> if (block.code) RikkaCodeBlock(block.language, block.text) else if (block.text.isNotBlank()) RichMarkdownText(block.text) }
    }
}

@Composable
private fun RikkaCodeBlock(language: String, code: String) {
    val clipboard = LocalClipboardManager.current
    val lines = remember(code) { code.lines() }
    val collapsible = lines.size > 18 || code.length > 1800
    var expanded by remember(code) { mutableStateOf(!collapsible) }
    val visibleCode = remember(code, expanded) {
        if (expanded || !collapsible) code else lines.take(16).joinToString("\n")
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 5.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    language.ifBlank { "代码" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("${lines.size} 行", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                IconButton(
                    onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(code)) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(HugeIcons.Copy01, "复制代码", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = if (collapsible) 6.dp else 14.dp),
            ) {
                SelectionContainer {
                    Text(visibleCode, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
            if (collapsible) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(if (expanded) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (expanded) "收起代码" else "展开全部 ${lines.size} 行")
                }
            }
        }
    }
}

private object NativeMarkdownRenderer {
    @Volatile private var renderer: Markwon? = null
    fun get(context: android.content.Context): Markwon = renderer ?: synchronized(this) {
        renderer ?: Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(JLatexMathPlugin.create(42f) { builder -> builder.inlinesEnabled(true) })
            .build()
            .also { renderer = it }
    }
}

@Composable
private fun RichMarkdownText(text: String) {
    val context = LocalContext.current
    val markwon = remember(context.applicationContext) { NativeMarkdownRenderer.get(context.applicationContext) }
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { android.widget.TextView(it).apply {
            setTextIsSelectable(true)
            textSize = 16f
            includeFontPadding = false
            letterSpacing = 0.01f
            setLineSpacing(resources.displayMetrics.density * 4f, 1f)
            breakStrategy = android.text.Layout.BREAK_STRATEGY_SIMPLE
            hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
            linksClickable = true
            setTextColor(android.graphics.Color.rgb(45, 40, 42))
        } },
        update = { view ->
            if (view.tag != text) {
                view.tag = text
                markwon.setMarkdown(view, text)
            }
        },
    )
}

@Composable
private fun MarkdownLikeText(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        markdownBlocks(text).forEach { block ->
            if (block.code) {
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color(0xFF1F1B24)) {
                    Text(
                        block.text,
                        modifier = Modifier.padding(14.dp),
                        color = Color(0xFFF4EFF7),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            } else {
                Text(block.text, style = MaterialTheme.typography.bodyLarge, lineHeight = 22.sp)
            }
        }
    }
}

@Composable
private fun RikkaActivityMessage(text: String) {
    if (text.startsWith("NOTICE|")) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(HugeIcons.Refresh03, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(6.dp))
                    Text(text.substringAfter('|'), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        return
    }
    val payload = if (text.startsWith("PROCESS2|")) runCatching {
        JSONObject(String(Base64.decode(text.substringAfter('|'), Base64.DEFAULT), Charsets.UTF_8))
    }.getOrNull() else null
    val duration = payload?.optLong("duration")?.toString() ?: text.substringAfter("PROCESS|", "0").substringBefore('|')
    val reasoning = payload?.optString("reasoning").orEmpty()
    val command = payload?.optString("command").orEmpty()
    val tools = payload?.optJSONArray("tools")
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
        Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(HugeIcons.Zap, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text("思考了 ${duration}s", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        QElasticExpand(expanded) {
            Column {
        if (reasoning.isNotBlank()) Box(modifier = Modifier.padding(top = 10.dp)) { RichResponseText(reasoning) }
        if (command.isNotBlank()) ToolTextCard("命令执行", command, false)
        if (tools != null) for (index in 0 until tools.length()) {
            val item = runCatching { JSONObject(tools.optString(index)) }.getOrNull() ?: continue
            val type = item.optString("type")
            val title = when (type) { "fileChange" -> "文件修改"; "mcpToolCall" -> "MCP 工具"; "webSearch" -> "网页搜索"; "collabAgentToolCall" -> "子代理"; else -> type }
            val detail = when (type) {
                "commandExecution" -> buildString {
                    val commandValue = item.optString("command", "")
                    val outputValue = item.optString("aggregatedOutput", item.optString("output", ""))
                    if (commandValue.isNotBlank()) append("$ ").append(commandValue).append('\n')
                    if (outputValue.isNotBlank()) append(outputValue.trimEnd()).append('\n')
                    val code = item.opt("exitCode")
                    if (code != null && code != JSONObject.NULL) append("exit ").append(code)
                }
                "fileChange" -> item.optString("changes", item.toString(2))
                "mcpToolCall" -> buildString {
                    append(item.optString("tool", item.optString("name", "MCP")))
                    val arguments = item.opt("arguments")
                    if (arguments != null && arguments != JSONObject.NULL) append("\n\n参数\n").append(arguments.toString())
                    val output = item.optString("output", "")
                    if (output.isNotBlank()) append("\n\n结果\n").append(output)
                }
                "webSearch" -> buildString {
                    append(item.optString("query", item.toString(2)))
                    val output = item.optString("output", "")
                    if (output.isNotBlank()) append("\n\n").append(output)
                }
                "collabAgentToolCall" -> item.optString("detail", item.toString(2))
                else -> item.toString(2)
            }
            if (type == "commandExecution") CommandExecutionCard(item)
            else ToolTextCard(title, detail, type == "fileChange")
        }
            }
        }
    }
}

@Composable
private fun CommandExecutionCard(item: JSONObject) {
    val command = item.optString("command", "")
    val stdout = item.optString("stdout", "")
    val stderr = item.optString("stderr", "")
    val aggregated = item.optString("aggregatedOutput", item.optString("output", ""))
    val output = if (stdout.isNotBlank() || stderr.isNotBlank()) stdout else aggregated
    val cwd = item.optString("cwd", "")
    val exitCode = item.opt("exitCode")?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty()
    val durationMs = item.optLong("durationMs", item.optLong("duration_ms", 0L))
    val failed = item.optString("status").equals("failed", true) || (exitCode.toIntOrNull()?.let { it != 0 } == true)
    val normalized = buildString {
        append("$ ").append(command).append('\n')
        if (output.isNotBlank()) append(output.trimEnd()).append('\n')
        if (exitCode.isNotBlank()) append("exit ").append(exitCode)
    }
    if (commandViewType(normalized) != CommandViewType.TERMINAL && stderr.isBlank()) {
        SmartCommandCard(normalized)
        return
    }
    var expanded by remember { mutableStateOf(false) }
    val accent = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(HugeIcons.Sparkles, null, modifier = Modifier.size(17.dp), tint = accent)
                Spacer(Modifier.width(8.dp)); Column(modifier = Modifier.weight(1f)) {
                    Text(command.ifBlank { "命令执行" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                    if (cwd.isNotBlank()) Text(cwd, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val meta = buildList { if (durationMs > 0) add("%.1fs".format(durationMs / 1000.0)); if (exitCode.isNotBlank()) add("exit $exitCode") }.joinToString(" · ")
                Text(meta.ifBlank { if (failed) "失败" else "完成" }, style = MaterialTheme.typography.labelSmall, color = accent)
            }
            QElasticExpand(expanded) {
                Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            if (stdout.isNotBlank() || (stdout.isBlank() && aggregated.isNotBlank())) CommandStreamSection("输出", if (stdout.isNotBlank()) stdout else aggregated, false)
            if (stderr.isNotBlank()) CommandStreamSection("错误输出", stderr, true)
        
                }
            }
        }
    }
}

@Composable
private fun CommandStreamSection(title: String, value: String, error: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().background(if (error) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f) else Color.Transparent).padding(10.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp)); SelectionContainer { Text(value.trimEnd(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
    }
}

private enum class CommandViewType { DIRECTORY, FILE, SEARCH, TERMINAL }

private fun commandViewType(content: String): CommandViewType {
    val command = content.lineSequence().firstOrNull().orEmpty().removePrefix("$ ").trim()
    return when {
        Regex("(^|\\s|[/\"'`])(ls|tree|find|fd)(\\s|$)").containsMatchIn(command) || command.contains("Get-ChildItem") -> CommandViewType.DIRECTORY
        Regex("(^|\\s|[/\"'`])(cat|head|tail|sed)(\\s|$)").containsMatchIn(command) || command.contains("Get-Content") -> CommandViewType.FILE
        Regex("(^|\\s|[/\"'`])(rg|grep|findstr)(\\s|$)").containsMatchIn(command) || command.contains("Select-String") -> CommandViewType.SEARCH
        else -> CommandViewType.TERMINAL
    }
}

@Composable
private fun SmartCommandCard(content: String) {
    when (commandViewType(content)) {
        CommandViewType.DIRECTORY -> DirectoryOutputCard(content)
        CommandViewType.FILE -> FileOutputCard(content)
        CommandViewType.SEARCH -> SearchOutputCard(content)
        CommandViewType.TERMINAL -> ToolTextCard("命令执行", content, false)
    }
}

@Composable
private fun DirectoryOutputCard(content: String) {
    val rawLines = content.lines().drop(1).filter { it.isNotBlank() && !it.startsWith("exit ") }
    val lines = rawLines.sortedWith(compareBy<String> { line ->
        val name = line.trim().substringAfterLast(' ')
        !(name.endsWith("/") || line.startsWith("d"))
    }.thenBy { it.lowercase() })
    var expanded by remember { mutableStateOf(false) }
    val visible = if (expanded) lines else lines.take(12)
    Surface(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(HugeIcons.Folder01, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp)); Text("目录内容", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text("${lines.size} 项", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            visible.forEach { line ->
                val name = line.trim().substringAfterLast(' ')
                val folder = name.endsWith("/") || line.startsWith("d")
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    val image = name.substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "gif", "webp", "svg")
                    Icon(if (folder) HugeIcons.Folder01 else if (image) HugeIcons.Image02 else HugeIcons.Files02, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp)); SelectionContainer { Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (lines.size > 12) TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) { Text(if (expanded) "收起" else "显示剩余 ${lines.size - 12} 项") }
        }
    }
}

@Composable
private fun FileOutputCard(content: String) {
    val command = content.lineSequence().firstOrNull().orEmpty().removePrefix("$ ")
    val name = command.trim().split(Regex("\\s+")).lastOrNull().orEmpty().substringAfterLast('/')
    ToolTextCard(name.ifBlank { "文件内容" }, content.lines().drop(1).filterNot { it.startsWith("exit ") }.joinToString("\n"), false)
}

@Composable
private fun SearchOutputCard(content: String) {
    val results = content.lines().drop(1).filter { it.isNotBlank() && !it.startsWith("exit ") }
    val grouped = results.groupBy { line ->
        val match = Regex("^(.+?):(\\d+):(.*)$").find(line)
        match?.groupValues?.get(1) ?: "搜索输出"
    }
    var expanded by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(HugeIcons.Search01, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp)); Text("搜索结果", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text("${results.size} 条 · ${grouped.size} 个文件", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                grouped.forEach { (file, lines) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(HugeIcons.Files02, null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp)); Text(file, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    lines.forEach { line ->
                        val match = Regex("^(.+?):(\\d+):(.*)$").find(line)
                        val shown = if (match != null) "${match.groupValues[2].padStart(4)}  ${match.groupValues[3]}" else line
                        SelectionContainer { Text(shown, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}
@Composable
private fun ToolTextCard(title: String, detail: String, diff: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val failed = detail.contains("failed", true) || detail.contains("error", true) || Regex("exit [1-9]").containsMatchIn(detail)
    val accent = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (diff) HugeIcons.Folder01 else HugeIcons.Sparkles, null, modifier = Modifier.size(17.dp), tint = accent)
                Spacer(Modifier.width(8.dp))
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(if (failed) "失败" else "完成", style = MaterialTheme.typography.labelSmall, color = accent)
            }
            QElasticExpand(expanded) {
                Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            if (diff) DiffText(detail) else SelectionContainer { Text(detail, modifier = Modifier.padding(10.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        
                }
            }
        }
    }
}
@Composable
private fun DiffText(diff: String) {
    SelectionContainer {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            diff.lineSequence().forEach { line ->
                val background = when {
                    line.startsWith("+++") || line.startsWith("---") || line.startsWith("@@") -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    line.startsWith("+") -> Color(0xFFD8F3DC)
                    line.startsWith("-") -> Color(0xFFFFDAD6)
                    else -> Color.Transparent
                }
                val foreground = when {
                    line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF175C2C)
                    line.startsWith("-") && !line.startsWith("---") -> Color(0xFF8C1D18)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = line.ifEmpty { " " },
                    modifier = Modifier.fillMaxWidth().background(background).padding(horizontal = 10.dp, vertical = 1.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = foreground,
                )
            }
        }
    }
}

@Composable
private fun RikkaErrorMessage(text: String, onRetry: (() -> Unit)?) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("发生错误", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp)); SelectionContainer { Text(text, style = MaterialTheme.typography.bodySmall) }
            if (onRetry != null) TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) {
                Icon(HugeIcons.Refresh03, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("重试")
            }
        }
    }
}
@Composable
private fun RikkaChatInput(
    value: String,
    enabled: Boolean,
    loading: Boolean,
    modelLabel: String,
    onModelClick: () -> Unit,
    effortOptions: List<String>,
    selectedEffort: String,
    onEffortSelected: (String) -> Unit,
    attachments: List<NativeAttachment>,
    onMoreClick: () -> Unit,
    onRemoveAttachment: (NativeAttachment) -> Unit,
    onPreviewAttachment: (NativeAttachment) -> Unit,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val bottomCorner by animateDpAsState(if (imeVisible) 0.dp else 28.dp, tween(210, easing = FastOutSlowInEasing), label = "inputBottomCorner")
    val sidePadding by animateDpAsState(if (imeVisible) 0.dp else 8.dp, tween(210, easing = FastOutSlowInEasing), label = "inputSidePadding")
    val bottomPadding by animateDpAsState(if (imeVisible) 0.dp else 8.dp, tween(210, easing = FastOutSlowInEasing), label = "inputBottomPadding")
    val keyboardOverlap by animateDpAsState(if (imeVisible) 3.dp else 0.dp, tween(180, easing = FastOutSlowInEasing), label = "inputKeyboardOverlap")
    val inputBorderColor by animateColorAsState(
        if (imeVisible) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        tween(160),
        label = "inputBorderColor",
    )
    val inputShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = bottomCorner, bottomStart = bottomCorner)
    val insetModifier = if (imeVisible) Modifier.imePadding() else Modifier
    Surface(modifier = modifier, color = Color.Transparent) {
        Column(
            modifier = insetModifier.padding(start = sidePadding, end = sidePadding, top = 8.dp, bottom = bottomPadding).offset(y = keyboardOverlap),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().onSizeChanged { onHeightChanged(it.height) }.animateContentSize(
                    animationSpec = spring(dampingRatio = 0.90f, stiffness = 520f),
                ),
                shape = inputShape,
                tonalElevation = 0.dp,
                border = BorderStroke(1.dp, inputBorderColor),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                    if (attachments.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                            items(attachments, key = { it.path }) { attachment ->
                                Surface(
                                    modifier = Modifier.clickable { onPreviewAttachment(attachment) },
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Row(modifier = Modifier.padding(start = 6.dp, top = 5.dp, bottom = 5.dp, end = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (attachment.image) AttachmentThumbnail(attachment.path, Modifier.size(34.dp).clip(MaterialTheme.shapes.small))
                                        else Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                                            Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) { Icon(HugeIcons.Files02, null, modifier = Modifier.size(17.dp)) }
                                        }
                                        Spacer(Modifier.width(7.dp))
                                        Column(modifier = Modifier.widthIn(max = 140.dp)) {
                                            Text(attachment.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                                            Text(formatFileSize(java.io.File(attachment.path).length()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(onClick = { onRemoveAttachment(attachment) }, modifier = Modifier.size(28.dp)) { Icon(HugeIcons.Cancel01, "移除", modifier = Modifier.size(14.dp)) }
                                    }
                                }
                            }
                        }
                    }
                    TextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.Enter && enabled && value.isNotBlank()) {
                                onSend()
                                true
                            } else false
                        },
                        enabled = !loading,
                        minLines = 1,
                        maxLines = 5,
                        shape = MaterialTheme.shapes.largeIncreased,
                        placeholder = { Text("输入消息") },
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                        ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            InputTool(HugeIcons.Sparkles, modelLabel.ifBlank { "模型" }, onModelClick)
                            LiquidEffortTool(effortOptions, selectedEffort, onEffortSelected)
                            InputTool(HugeIcons.Add01, "更多选项", onMoreClick)
                        }
                        Surface(
                            modifier = Modifier.size(42.dp).clickable(enabled = loading || (enabled && value.isNotBlank())) { onSend() },
                            shape = CircleShape,
                            color = when {
                                loading -> MaterialTheme.colorScheme.errorContainer
                                !enabled || value.isBlank() -> MaterialTheme.colorScheme.surfaceContainerHigh
                                else -> MaterialTheme.colorScheme.primary
                            },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (loading) HugeIcons.Cancel01 else HugeIcons.ArrowUp02,
                                    contentDescription = if (loading) "停止" else "发送",
                                    modifier = Modifier.size(21.dp),
                                    tint = when {
                                        loading -> MaterialTheme.colorScheme.onErrorContainer
                                        !enabled || value.isBlank() -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        else -> MaterialTheme.colorScheme.onPrimary
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

private fun decodeSampledBitmap(path: String, maxEdge: Int = 1280): android.graphics.Bitmap? {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxEdge * 2 || bounds.outHeight / sample > maxEdge * 2) sample *= 2
    return android.graphics.BitmapFactory.decodeFile(path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
}

@Composable
private fun AttachmentThumbnail(path: String, modifier: Modifier = Modifier) {
    val bitmap = remember(path) { decodeSampledBitmap(path) }
    AndroidView(
        modifier = modifier,
        factory = { context -> android.widget.ImageView(context).apply { scaleType = android.widget.ImageView.ScaleType.CENTER_CROP } },
        update = { view -> view.setImageBitmap(bitmap) },
    )
}

@Composable
private fun AttachmentPreviewDialog(attachment: NativeAttachment, onDismiss: () -> Unit) {
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(attachment.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (attachment.image) AttachmentThumbnail(attachment.path, Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 420.dp).clip(MaterialTheme.shapes.large))
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (attachment.image) "图片附件" else "文件附件", style = MaterialTheme.typography.labelLarge)
                        Text(formatFileSize(java.io.File(attachment.path).length()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(attachment.path, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
private fun RikkaFilesPicker(onPickImage: () -> Unit, onPickFile: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RikkaFileAction(HugeIcons.Image02, "图片", onPickImage)
            RikkaFileAction(HugeIcons.Files02, "文件", onPickFile)
        }
        HorizontalDivider(modifier = Modifier.fillMaxWidth())
        Text("附件会随下一条消息发送", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RikkaFileAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(onClick = onClick, modifier = Modifier.size(56.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, label, modifier = Modifier.size(24.dp)) }
        }
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private fun effortLabel(value: String): String = when (value.lowercase()) {
    "none" -> "关闭"
    "minimal" -> "极低"
    "low" -> "低"
    "medium" -> "中"
    "high" -> "高"
    "xhigh" -> "极高"
    "ultra" -> "Ultra"
    else -> value
}

@Composable
private fun LiquidEffortTool(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var popupVisible by remember { mutableStateOf(false) }
    var contentReady by remember { mutableStateOf(false) }
    var previewEffort by remember(options, selected) { mutableStateOf(selected) }
    var effortDragging by remember { mutableStateOf(false) }
    val effortLabelScale by animateFloatAsState(
        targetValue = if (effortDragging) 1.14f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "effortLabelScale",
    )
    val menuVisibility = remember { MutableTransitionState(false) }
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = when { pressed -> 1.13f; expanded -> 1.045f; else -> 1f },
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 460f),
        label = "effortPressScale",
    )
    val panelElevation by animateDpAsState(
        targetValue = if (contentReady && expanded) 10.dp else 0.dp,
        animationSpec = tween(190, delayMillis = if (expanded) 35 else 0, easing = LinearOutSlowInEasing),
        label = "effortPanelElevation",
    )
    fun open() { popupVisible = true; expanded = true; contentReady = false; menuVisibility.targetState = false }
    fun close() {
        contentReady = false; expanded = false; menuVisibility.targetState = false
        scope.launch { delay(265); popupVisible = false }
    }
    LaunchedEffect(popupVisible, expanded) {
        if (popupVisible && expanded) { delay(20); menuVisibility.targetState = true; delay(35); contentReady = true }
    }
    val gapPx = with(density) { 8.dp.roundToPx() }
    val shadowPadPx = with(density) { 14.dp.roundToPx() }
    val positionProvider = remember(gapPx, shadowPadPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val x = (anchorBounds.left - shadowPadPx).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                val above = anchorBounds.top - popupContentSize.height + shadowPadPx - gapPx
                val y = if (above >= 0) above else (anchorBounds.bottom + gapPx - shadowPadPx).coerceAtMost(windowSize.height - popupContentSize.height)
                return IntOffset(x, y)
            }
        }
    }
    Box {
        Surface(
            modifier = Modifier.size(40.dp).graphicsLayer { scaleX = buttonScale; scaleY = buttonScale },
            shape = CircleShape,
            color = if (expanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (expanded) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Box(Modifier.fillMaxSize().clickable(interactionSource = interaction, indication = null) { if (expanded) close() else open() }, contentAlignment = Alignment.Center) {
                Icon(HugeIcons.Zap, "思维强度", modifier = Modifier.size(21.dp))
            }
        }
        if (popupVisible) {
            Popup(popupPositionProvider = positionProvider, onDismissRequest = { close() }, properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)) {
                AnimatedVisibility(
                    visibleState = menuVisibility,
                    enter = fadeIn(tween(155, easing = LinearOutSlowInEasing)) +
                        scaleIn(initialScale = 0.76f, transformOrigin = TransformOrigin(0f, 1f), animationSpec = spring(dampingRatio = 0.52f, stiffness = 315f)) +
                        slideInVertically(initialOffsetY = { -it / 45 }, animationSpec = spring(dampingRatio = 0.66f, stiffness = 370f)),
                    exit = fadeOut(tween(135)) + scaleOut(targetScale = 0.84f, transformOrigin = TransformOrigin(0f, 1f), animationSpec = tween(185, easing = FastOutSlowInEasing)),
                ) {
                    Box(Modifier.padding(14.dp)) {
                        Surface(
                            modifier = Modifier.width(310.dp), shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 5.dp, shadowElevation = panelElevation,
                        ) {
                            Column(Modifier.padding(vertical = 8.dp)) {
                                AnimatedVisibility(
                                    visible = contentReady,
                                    enter = fadeIn(tween(145, delayMillis = 20)) + slideInHorizontally(initialOffsetX = { -it / 12 }, animationSpec = spring(dampingRatio = 0.66f, stiffness = 430f)),
                                    exit = fadeOut(tween(90)),
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(HugeIcons.Zap, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Text("思维强度", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                        AnimatedContent(
                                            targetState = previewEffort,
                                            modifier = Modifier.graphicsLayer { scaleX = effortLabelScale; scaleY = effortLabelScale },
                                            transitionSpec = {
                                                (fadeIn(tween(90)) + slideInVertically(initialOffsetY = { it / 5 }, animationSpec = spring(dampingRatio = 0.72f, stiffness = 560f)))
                                                    .togetherWith(fadeOut(tween(70)) + slideOutVertically(targetOffsetY = { -it / 6 }, animationSpec = tween(90)))
                                            },
                                            label = "effortLabelPreview",
                                        ) { effort ->
                                            Text(effortLabel(effort), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                                AnimatedVisibility(
                                    visible = contentReady,
                                    enter = fadeIn(tween(160, delayMillis = 65)) + scaleIn(initialScale = 0.94f, transformOrigin = TransformOrigin(0.5f, 0.5f), animationSpec = spring(dampingRatio = 0.64f, stiffness = 390f)),
                                    exit = fadeOut(tween(90)),
                                ) {
                                    LiquidEffortSlider(
                                        options = options,
                                        selected = selected,
                                        onPreview = { previewEffort = it },
                                        onDraggingChanged = { effortDragging = it },
                                        onSelect = onSelect,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                }
                                AnimatedVisibility(
                                    visible = contentReady,
                                    enter = fadeIn(tween(150, delayMillis = 105)) + slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(190, easing = LinearOutSlowInEasing)),
                                    exit = fadeOut(tween(80)),
                                ) {
                                    Text("拖动或点击选择，松手后自动吸附", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidEffortSlider(
    options: List<String>, selected: String,
    onPreview: (String) -> Unit,
    onDraggingChanged: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    val scope = rememberCoroutineScope()
    var visualIndex by remember(options) { mutableFloatStateOf(options.indexOf(selected).coerceAtLeast(0).toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val previewIndex = visualIndex.roundToInt().coerceIn(options.indices)
    val thumbScale by animateFloatAsState(if (dragging) 1.22f else 1f, spring(dampingRatio = 0.72f, stiffness = 480f), label = "liquidThumbScale")

    fun updateDragging(value: Boolean) { dragging = value; onDraggingChanged(value) }
    fun settle(index: Float, commit: Boolean) {
        val target = index.roundToInt().coerceIn(options.indices)
        settleJob?.cancel()
        settling = true
        settleJob = scope.launch {
            animate(visualIndex, target.toFloat(), animationSpec = spring(dampingRatio = 0.78f, stiffness = 500f)) { value, _ ->
                visualIndex = value
                onPreview(options[value.roundToInt().coerceIn(options.indices)])
            }
            visualIndex = target.toFloat()
            onPreview(options[target])
            if (commit && options[target] != selected) onSelect(options[target])
            settling = false
        }
    }
    LaunchedEffect(selected, options, dragging, settling) {
        if (!dragging && !settling) {
            visualIndex = options.indexOf(selected).coerceAtLeast(0).toFloat()
            onPreview(options[visualIndex.roundToInt().coerceIn(options.indices)])
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.CenterStart) {
            val steps = (options.size - 1).coerceAtLeast(1)
            val trackWidthPx = constraints.maxWidth.toFloat()
            val segmentPx = trackWidthPx / steps
            val progress = (visualIndex / steps).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)))
            Box(Modifier.fillMaxWidth(progress.coerceAtLeast(0.02f)).height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                options.forEachIndexed { index, _ -> Box(Modifier.size(if (index == previewIndex) 7.dp else 5.dp).clip(CircleShape).background(if (index <= visualIndex + 0.01f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))) }
            }
            Surface(
                modifier = Modifier.size(42.dp, 28.dp).graphicsLayer {
                    translationX = (progress * (trackWidthPx - size.width)).coerceIn(0f, (trackWidthPx - size.width).coerceAtLeast(0f))
                    scaleX = thumbScale; scaleY = if (dragging) 1.10f else thumbScale
                },
                shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = if (dragging) 0.18f else 0.10f)),
                shadowElevation = if (dragging) 7.dp else 3.dp,
            ) { Box(contentAlignment = Alignment.Center) { Box(Modifier.size(25.dp, 12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = if (dragging) 0.22f else 0.14f))) } }
            // Keep the gesture target fixed across the full track. A translated graphicsLayer does
            // not move hit-testing bounds, which made the visually moved thumb fail on the next drag.
            Box(
                Modifier.fillMaxWidth().height(46.dp)
                    .pointerInput(options, selected) {
                        detectTapGestures { offset ->
                            val target = ((offset.x / size.width) * steps).coerceIn(0f, steps.toFloat())
                            onPreview(options[target.roundToInt().coerceIn(options.indices)])
                            settle(target, commit = true)
                        }
                    }
                    .pointerInput(options, selected) {
                        var gestureIndex = visualIndex
                        detectDragGestures(
                            onDragStart = { offset ->
                                settleJob?.cancel()
                                updateDragging(true)
                                gestureIndex = ((offset.x / size.width) * steps).coerceIn(0f, steps.toFloat())
                                visualIndex = gestureIndex
                                onPreview(options[gestureIndex.roundToInt().coerceIn(options.indices)])
                            },
                            onDragCancel = {
                                settle(options.indexOf(selected).coerceAtLeast(0).toFloat(), commit = false)
                                updateDragging(false)
                            },
                            onDragEnd = {
                                settle(gestureIndex, commit = true)
                                updateDragging(false)
                            },
                        ) { change, _ ->
                            change.consume()
                            gestureIndex = ((change.position.x / size.width) * steps).coerceIn(0f, steps.toFloat())
                            visualIndex = gestureIndex
                            onPreview(options[gestureIndex.roundToInt().coerceIn(options.indices)])
                        }
                    },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            options.forEachIndexed { index, effort ->
                val active = index == previewIndex
                val tickScale = if (active && dragging) 1.08f else 1f
                Text(effortLabel(effort), modifier = Modifier.graphicsLayer { scaleX = tickScale; scaleY = tickScale }, style = MaterialTheme.typography.labelSmall, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InputTool(icon: ImageVector, description: String, onClick: () -> Unit = {}) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, description, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun RikkaDrawer(
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
    var selectedCategory by remember { mutableStateOf("全部") }
    val projectPaths = conversations.map { it.projectPath }.filter { it.isNotBlank() }.distinct()
    val visibleConversations = conversations.filter { conversation ->
        when (selectedCategory) {
            "全部" -> true
            "收藏" -> conversation.favorite
            else -> conversation.projectPath == selectedCategory
        }
    }
    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
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
                        Text("Fcode 用户", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Icon(HugeIcons.PencilEdit01, "编辑昵称", modifier = Modifier.padding(start = 6.dp).size(16.dp))
                    }
                    Text("今天想聊点什么？", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DrawerQuickAction(HugeIcons.Search01, "搜索", Modifier.weight(1f), onSearch)
                DrawerQuickAction(HugeIcons.TransactionHistory, "历史", Modifier.weight(1f))
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                items(listOf("全部", "收藏") + projectPaths) { category ->
                    val label = when (category) { "全部", "收藏" -> category; else -> category.trimEnd('/').substringAfterLast('/').ifBlank { "无项目" } }
                    Surface(onClick = { selectedCategory = category }, shape = RoundedCornerShape(50), color = if (category == selectedCategory) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (category == "收藏") HugeIcons.InLove else HugeIcons.Folder01, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(visibleConversations, key = { it.threadId }) { conversation ->
                    NavigationDrawerItem(
                        label = { Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        selected = false,
                        onClick = { onResumeConversation(conversation.threadId) },
                        icon = {
                            val tint = when (conversation.state) {
                                "running" -> MaterialTheme.colorScheme.primary
                                "failed" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Icon(HugeIcons.Sparkles, conversation.state, modifier = Modifier.size(19.dp), tint = tint)
                        },
                        badge = { ConversationMenu(conversation, onRenameConversation, onDeleteConversation, onToggleFavorite) },
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                    )
                }
            }

            NavigationDrawerItem(
                label = { Text(modelLabel.ifBlank { "选择助手" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
private fun ConversationMenu(
    conversation: NativeConversation,
    onRename: (NativeConversation) -> Unit,
    onDelete: (NativeConversation) -> Unit,
    onToggleFavorite: (NativeConversation) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(32.dp)) { Icon(HugeIcons.MoreVertical, "更多", modifier = Modifier.size(18.dp)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(if (conversation.favorite) "取消收藏" else "收藏") }, leadingIcon = { Icon(HugeIcons.InLove, null) }, onClick = { expanded = false; onToggleFavorite(conversation) })
            DropdownMenuItem(text = { Text("重命名") }, leadingIcon = { Icon(HugeIcons.PencilEdit01, null) }, onClick = { expanded = false; onRename(conversation) })
            DropdownMenuItem(text = { Text("删除") }, leadingIcon = { Icon(HugeIcons.Delete01, null) }, onClick = { expanded = false; onDelete(conversation) })
        }
    }
}

@Composable
private fun ConversationSearchDialog(conversations: List<NativeConversation>, onDismiss: () -> Unit, onSelect: (NativeConversation) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = conversations.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text("搜索对话") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(HugeIcons.Search01, null) }, placeholder = { Text("重命名对话") })
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(results, key = { it.threadId }) { conversation ->
                        NavigationDrawerItem(label = { Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, selected = false, onClick = { onSelect(conversation) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun RenameConversationDialog(conversation: NativeConversation, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var title by remember(conversation.threadId) { mutableStateOf(conversation.title) }
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名对话") },
        text = { OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), singleLine = true) },
        confirmButton = { TextButton(onClick = { if (title.isNotBlank()) onConfirm(title.trim()) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun DrawerQuickAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DrawerMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = false,
        onClick = onClick,
        icon = { Icon(icon, null) },
        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
    )
}
