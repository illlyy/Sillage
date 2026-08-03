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
internal fun FlClashAnimatedDialog(
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
                enter = fadeIn(tween(120, easing = LinearEasing)) + scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f)),
                exit = fadeOut(tween(95, easing = LinearEasing)) + scaleOut(targetScale = 0.96f, animationSpec = spring(dampingRatio = 0.9f, stiffness = 520f)),
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
internal fun MessageSearchDialog(
    messages: List<NativeChatMessage>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val language = LocalNativeLanguage.current
    val normalizedQuery = query.trim()
    val results = if (normalizedQuery.isBlank()) emptyList() else messages.filter { message ->
        message.role != NativeChatRole.ACTIVITY && message.content.contains(normalizedQuery, ignoreCase = true)
    }
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(nativeText(language, "\u641c\u7d22\u5f53\u524d\u5bf9\u8bdd", "Search conversation")) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(HugeIcons.Search01, null) },
                    placeholder = { Text(nativeText(language, "\u641c\u7d22\u6d88\u606f", "Search messages")) },
                )
                    if (normalizedQuery.isNotBlank()) {
                        Text(nativeText(language, "\u627e\u5230 ${results.size} \u6761\u7ed3\u679c", "${results.size} results found"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (results.isEmpty()) {
                            Text(
                                nativeText(language, "\u6ca1\u6709\u5339\u914d\u7684\u6d88\u606f", "No matching messages"),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(results, key = { it.id }) { message ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fcodePressClickable(
                                                onClickLabel = nativeText(language, "跳转到消息", "Go to message"),
                                            ) { onSelect(message.id) },
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(if (message.role == NativeChatRole.USER) nativeText(language, "\u7528\u6237", "User") else nativeText(language, "\u52a9\u624b", "Assistant"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            Text(message.content.replace('\n', ' '), maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(nativeText(language, "\u5173\u95ed", "Close")) } },
    )
}

@Composable
internal fun NativeUserInputBanner(raw: String, drawerOpen: Boolean, onToggle: () -> Unit) {
    val language = LocalNativeLanguage.current
    val payload = remember(raw) { runCatching { JSONObject(raw) }.getOrNull() }
    val params = payload?.optJSONObject("params")
    val questions = params?.optJSONArray("questions")
    val count = questions?.length()?.coerceAtLeast(1) ?: 1
    val firstQuestion = questions?.optJSONObject(0)?.optString("question").orEmpty()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .fcodePressClickable(
                onClickLabel = if (drawerOpen) {
                    nativeText(language, "收起待回答问题", "Collapse pending questions")
                } else {
                    nativeText(language, "展开待回答问题", "Expand pending questions")
                },
                onClick = onToggle,
            ),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.84f),
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(HugeIcons.MessageAdd01, null, Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    nativeText(language, "\u5f85\u56de\u7b54 \u00b7 $count \u4e2a\u95ee\u9898", "Answer needed \u00b7 $count question${if (count == 1) "" else "s"}"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (firstQuestion.isNotBlank()) Text(firstQuestion, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            Icon(HugeIcons.ArrowDown01, null, Modifier.size(16.dp).graphicsLayer { rotationZ = if (drawerOpen) 180f else 0f })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeUserInputDrawer(raw: String, onAnswer: (String) -> Unit, onDismiss: () -> Unit) {
    val language = LocalNativeLanguage.current
    val payload = remember(raw) { runCatching { JSONObject(raw) }.getOrNull() }
    val params = payload?.optJSONObject("params")
    val questionArray = params?.optJSONArray("questions") ?: JSONArray().also { array -> if (params != null) array.put(params) }
    val questions = remember(raw) {
        buildList { for (index in 0 until questionArray.length()) questionArray.optJSONObject(index)?.let(::add) }
    }
    var answers by remember(raw) { mutableStateOf(emptyMap<String, String>()) }
    val complete = questions.isNotEmpty() && questions.all { question ->
        val id = question.optString("id", "answer").ifBlank { "answer" }
        answers[id].orEmpty().isNotBlank()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(nativeText(language, "\u9700\u8981\u4f60\u7684\u56de\u7b54", "Your input is needed"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(nativeText(language, "\u8bf7\u56de\u7b54\u4ee5\u4e0b ${questions.size} \u4e2a\u95ee\u9898", "Answer the ${questions.size} question${if (questions.size == 1) "" else "s"} below"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) { Icon(HugeIcons.Cancel01, nativeText(language, "\u5173\u95ed", "Close")) }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(questions, key = { index, question -> question.optString("id").ifBlank { index.toString() } }) { index, question ->
                    val questionId = question.optString("id", "answer").ifBlank { "answer" }
                    val header = question.optString("header").ifBlank { nativeText(language, "\u95ee\u9898 ${index + 1}", "Question ${index + 1}") }
                    val prompt = question.optString("question", question.optString("prompt", "")).ifBlank { nativeText(language, "\u6a21\u578b\u9700\u8981\u4f60\u7684\u56de\u7b54", "The model needs your input") }
                    val multi = nativeQuestionMultiSelect(question)
                    val optionList = nativeQuestionOptions(question)
                    val optionLabels = optionList.map { it.label }.toSet()
                    val allowOther = nativeQuestionOtherAllowed(question)
                    val secret = question.optBoolean("isSecret", false)
                    val currentValue = answers[questionId].orEmpty()
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text(header, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            Text(prompt, style = MaterialTheme.typography.bodyLarge, lineHeight = 23.sp)
                            if (multi) {
                                Text(
                                    nativeText(language, "\u53ef\u591a\u9009", "Select all that apply"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            optionList.forEach { option ->
                                val selected = if (multi) {
                                    option.label in nativeSelectedLabels(currentValue)
                                } else {
                                    currentValue == option.label
                                }
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        answers = answers + (questionId to nativeToggleOption(currentValue, option.label, multi))
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)) else null,
                                ) {
                                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                        Text(option.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        if (option.description.isNotBlank()) Text(option.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (allowOther || optionList.isEmpty()) {
                                OutlinedTextField(
                                    value = nativeOtherPart(currentValue, optionLabels),
                                    onValueChange = { answers = answers + (questionId to nativeApplyOtherValue(currentValue, optionLabels, it)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(nativeText(language, "\u5176\u4ed6\u56de\u7b54", "Other answer")) },
                                    placeholder = { Text(nativeText(language, "\u8f93\u5165\u56de\u7b54", "Type your answer")) },
                                    visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                                    minLines = 1,
                                    maxLines = 4,
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { onAnswer(encodeNativeUserInputAnswers(emptyMap())) },
                    modifier = Modifier.weight(1f),
                ) { Text(nativeText(language, "\u5168\u90e8\u8df3\u8fc7", "Skip all")) }
                Button(
                    onClick = {
                        onAnswer(encodeNativeUserInputAnswers(answers))
                    },
                    enabled = complete,
                    modifier = Modifier.weight(2.2f),
                    shape = RoundedCornerShape(18.dp),
                ) { Text(nativeText(language, "\u63d0\u4ea4\u5168\u90e8\u56de\u7b54", "Submit answers")) }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
internal fun NativePlanImplementationDialog(plan: String, onExecute: () -> Unit, onRevise: (String) -> Unit, onCancel: () -> Unit) {
    val language = LocalNativeLanguage.current
    var feedback by remember(plan) { mutableStateOf("") }
    FlClashAnimatedDialog(
        onDismissRequest = onCancel,
        title = { Text(nativeText(language, "\u6267\u884c\u8fd9\u4e2a\u8ba1\u5212\uff1f", "Implement this plan?")) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 470.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Box(Modifier.padding(14.dp)) { DeferredHistoricalRichText(plan) }
                }
                OutlinedTextField(
                    value = feedback,
                    onValueChange = { feedback = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(nativeText(language, "\u9700\u8981\u66f4\u6539\u7684\u5185\u5bb9", "Changes to make")) },
                    placeholder = { Text(nativeText(language, "\u4f8b\u5982\uff1a\u5148\u8865\u5145\u6d4b\u8bd5\uff0c\u4e0d\u8981\u4fee\u6539 API", "For example: add tests first and keep the API unchanged")) },
                    minLines = 2,
                    maxLines = 5,
                )
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel) { Text(nativeText(language, "\u53d6\u6d88", "Cancel")) }
                TextButton(enabled = feedback.isNotBlank(), onClick = { onRevise(feedback.trim()) }) { Text(nativeText(language, "\u66f4\u6539\u8ba1\u5212", "Change plan")) }
            }
        },
        confirmButton = { Button(onClick = onExecute) { Text(nativeText(language, "\u6267\u884c\u8ba1\u5212", "Implement plan")) } },
    )
}

@Composable
internal fun NativeApprovalDialog(raw: String, onDecision: (String) -> Unit) {
    val language = LocalNativeLanguage.current
    val payload = remember(raw) { runCatching { JSONObject(raw) }.getOrNull() }
    val method = payload?.optString("method").orEmpty()
    val params = payload?.optJSONObject("params") ?: JSONObject()
    val isCommand = method.contains("commandExecution") || method == "execCommandApproval"
    val isPermission = method.contains("permissions/requestApproval")
    val command = NativeCommandPresentation.rawCommand(params)
    val action = NativeCommandPresentation.action(params)
    val actionLabel = NativeCommandPresentation.label(action, language != "en")
    val reason = params.optString("reason")
    val cwd = params.optString("cwd")
    val available = params.optJSONArray("availableDecisions")
    val allowForSession = available == null || (0 until available.length()).any { index ->
        available.opt(index)?.toString()?.contains("acceptForSession") == true
    }
    val title = when {
        isCommand -> nativeText(language, "\u5141\u8bb8\u6267\u884c\u547d\u4ee4\uff1f", "Allow command?")
        isPermission -> nativeText(language, "\u5141\u8bb8\u989d\u5916\u6743\u9650\uff1f", "Allow additional permissions?")
        else -> nativeText(language, "\u5141\u8bb8\u4fee\u6539\u6587\u4ef6\uff1f", "Allow file changes?")
    }
    val summary = when {
        isCommand -> actionLabel
        isPermission -> nativeText(language, "\u6a21\u578b\u8bf7\u6c42\u6269\u5927\u5f53\u524d\u8bbf\u95ee\u8303\u56f4", "The model requests additional access")
        else -> nativeText(language, "\u6a21\u578b\u8bf7\u6c42\u5199\u5165\u5de5\u4f5c\u533a\u4ee5\u5916\u7684\u4f4d\u7f6e", "The model requests writes outside the workspace")
    }
    FlClashAnimatedDialog(
        onDismissRequest = { onDecision("decline") },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(summary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (reason.isNotBlank()) Text(reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (command.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        SelectionContainer {
                            Text(
                                command.take(4_000),
                                Modifier.fillMaxWidth().padding(12.dp),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (cwd.isNotBlank()) Text(cwd, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (isPermission) {
                    Text(params.optJSONObject("permissions")?.toString(2).orEmpty().take(2_000), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    nativeText(language, "\u4ec5\u5728\u4f60\u7406\u89e3\u8be5\u64cd\u4f5c\u65f6\u5141\u8bb8\u3002\u9009\u62e9\u201c\u672c\u4f1a\u8bdd\u5141\u8bb8\u201d\u540e\uff0c\u76f8\u4f3c\u64cd\u4f5c\u672c\u6b21\u5bf9\u8bdd\u4e0d\u518d\u8be2\u95ee\u3002", "Only allow operations you understand. Session approval skips similar prompts for this conversation."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onDecision("decline") }) { Text(nativeText(language, "\u62d2\u7edd", "Deny")) }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (allowForSession) TextButton(onClick = { onDecision("acceptForSession") }) {
                    Text(nativeText(language, "\u672c\u4f1a\u8bdd\u5141\u8bb8", "Allow session"))
                }
                TextButton(onClick = { onDecision("accept") }) { Text(nativeText(language, "\u5141\u8bb8\u4e00\u6b21", "Allow once")) }
            }
        },
    )
}

@Composable
internal fun EditMessageDialog(initialText: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember(initialText) { mutableStateOf(initialText) }
    val language = LocalNativeLanguage.current
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(nativeText(language, "\u7f16\u8f91\u6d88\u606f", "Edit message")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("保存后将从这条消息重新生成后续内容。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = value, onValueChange = { value = it }, modifier = Modifier.fillMaxWidth().focusRequester(focusRequester), minLines = 3, maxLines = 10)
            }
        },
        confirmButton = { TextButton(onClick = { if (value.isNotBlank()) onConfirm(value.trim()) }) { Text(nativeText(language, "\u4fdd\u5b58\u5e76\u91cd\u65b0\u751f\u6210", "Save and regenerate")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(nativeText(language, "\u53d6\u6d88", "Cancel")) } },
    )
}

@Composable
internal fun RestoreCheckpointDialog(prompt: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val language = LocalNativeLanguage.current
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(nativeText(language, "\u4ece\u6b64\u68c0\u67e5\u70b9\u91cd\u65b0\u6267\u884c\uff1f", "Rerun from this checkpoint?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    nativeText(language, "\u6b64\u68c0\u67e5\u70b9\u4e4b\u540e\u7684\u5bf9\u8bdd\u8f6e\u6b21\u4f1a\u88ab\u56de\u6eda\uff0c\u7136\u540e\u91cd\u65b0\u63d0\u4ea4\u8fd9\u6761\u4efb\u52a1\u3002Codex \u4f1a\u6839\u636e\u5f53\u524d\u5de5\u4f5c\u533a\u518d\u6b21\u6267\u884c\u3002", "Later conversation turns will be rolled back and this task will be submitted again. Codex will rerun it against the current workspace."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Text(prompt.take(2_000), Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall, maxLines = 10, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(nativeText(language, "\u53d6\u6d88", "Cancel")) } },
        confirmButton = { Button(onClick = onConfirm) { Text(nativeText(language, "\u56de\u6eda\u5e76\u91cd\u65b0\u6267\u884c", "Rollback and rerun")) } },
    )
}

@Composable
internal fun RestoreSnapshotFileDialog(path: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val language = LocalNativeLanguage.current
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(nativeText(language, "\u6062\u590d\u8fd9\u4e2a\u6587\u4ef6\uff1f", "Restore this file?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    nativeText(language, "\u5f53\u524d\u5de5\u4f5c\u533a\u4e2d\u7684\u8be5\u6587\u4ef6\u5c06\u66ff\u6362\u4e3a\u5feb\u7167\u7248\u672c\u3002\u6062\u590d\u524d\u4f1a\u81ea\u52a8\u518d\u521b\u5efa\u4e00\u4efd\u5b89\u5168\u5feb\u7167\u3002\u5f53\u524d Git \u6682\u5b58\u533a\u4e0d\u4f1a\u88ab\u4fee\u6539\u3002", "The working-tree file will be replaced by the snapshot version. A safety snapshot is created first, and the Git index is left unchanged."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Text(path, Modifier.fillMaxWidth().padding(12.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(nativeText(language, "\u53d6\u6d88", "Cancel")) } },
        confirmButton = { Button(onClick = onConfirm) { Text(nativeText(language, "\u521b\u5efa\u5907\u4efd\u5e76\u6062\u590d", "Back up and restore")) } },
    )
}

@Composable
internal fun MergeWorktreeDialog(sourceBranch: String, targetBranch: String, commits: String, stat: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val language = LocalNativeLanguage.current
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(nativeText(language, "\u5408\u5e76\u9694\u79bb\u4efb\u52a1\uff1f", "Merge isolated task?")) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(nativeText(language, "\u5c06 $sourceBranch \u5408\u5e76\u5230 $targetBranch\u3002\u5408\u5e76\u524d\u4f1a\u521b\u5efa\u9690\u85cf\u5b89\u5168\u5feb\u7167\uff1b\u82e5\u51fa\u73b0\u51b2\u7a81\uff0c\u4f1a\u81ea\u52a8\u53d6\u6d88\u5408\u5e76\u3002", "Merge $sourceBranch into $targetBranch. A hidden safety snapshot is created first; conflicted merges are aborted automatically."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (commits.isNotBlank()) {
                    Text(nativeText(language, "\u5f85\u5408\u5e76\u63d0\u4ea4", "Commits"), fontWeight = FontWeight.SemiBold)
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        SelectionContainer { Text(commits.take(12_000), Modifier.fillMaxWidth().padding(12.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                if (stat.isNotBlank()) {
                    Text(nativeText(language, "\u53d8\u66f4\u7edf\u8ba1", "Change summary"), fontWeight = FontWeight.SemiBold)
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        SelectionContainer { Text(stat.take(12_000), Modifier.fillMaxWidth().padding(12.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(nativeText(language, "\u53d6\u6d88", "Cancel")) } },
        confirmButton = { Button(onClick = onConfirm) { Text(nativeText(language, "\u521b\u5efa\u5907\u4efd\u5e76\u5408\u5e76", "Back up and merge")) } },
    )
}

@Composable
internal fun RemoveWorktreeDialog(path: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val language = LocalNativeLanguage.current
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(nativeText(language, "\u79fb\u9664 worktree\uff1f", "Remove worktree?")) },
        text = { Text(nativeText(language, "\u53ea\u4f1a\u79fb\u9664\u8fd9\u4e2a\u5e72\u51c0\u7684 worktree \u76ee\u5f55\uff0c\u5176 Git \u5206\u652f\u4f1a\u4fdd\u7559\u3002\n\n$path", "Only the clean worktree directory will be removed. Its Git branch will be kept.\n\n$path")) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(nativeText(language, "\u53d6\u6d88", "Cancel")) } },
        confirmButton = { Button(onClick = onConfirm) { Text(nativeText(language, "\u79fb\u9664 worktree", "Remove worktree")) } },
    )
}

@Composable
internal fun ChoiceDialog(
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
internal fun Modifier.interactiveLiquidAction(
    enabled: Boolean,
    backdrop: Backdrop,
    glassConfig: LiquidGlassConfig,
    isLightTheme: Boolean,
    tint: Color,
    onClick: () -> Unit,
): Modifier {
    // pointerInput is intentionally keyed only by enabled so unrelated recompositions do not
    // cancel an in-progress press. Keep the action itself current, though: the composer can move
    // from "send this draft" to "stop the active turn" while both states remain enabled. Capturing
    // the original lambda here would let an empty composer resend the previous draft.
    val latestOnClick by rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var startPointer by remember { mutableStateOf(Offset.Zero) }
    var measuredSize by remember { mutableStateOf(IntSize.Zero) }
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f),
        label = "liquidActionPress",
    )
    val width = measuredSize.width.coerceAtLeast(1).toFloat()
    val height = measuredSize.height.coerceAtLeast(1).toFloat()
    val drag = pointer - startPointer
    val stretchX = kotlin.math.abs(drag.x / width).coerceIn(0f, 1f) * 0.08f
    val stretchY = kotlin.math.abs(drag.y / height).coerceIn(0f, 1f) * 0.08f
    val targetScaleX = 1f + pressProgress * (0.11f + stretchX)
    val targetScaleY = 1f + pressProgress * (0.11f + stretchY)
    val scaleXSpring by animateFloatAsState(
        targetValue = targetScaleX,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 250f, visibilityThreshold = 0.001f),
        label = "liquidActionScaleX",
    )
    val scaleYSpring by animateFloatAsState(
        targetValue = targetScaleY,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 250f, visibilityThreshold = 0.001f),
        label = "liquidActionScaleY",
    )
    val targetTranslationX = if (pressed) 10f * tanh(drag.x / width * 1.8f) else 0f
    val targetTranslationY = if (pressed) 10f * tanh(drag.y / height * 1.8f) else 0f
    val translationX by animateFloatAsState(
        targetValue = targetTranslationX,
        animationSpec = spring(dampingRatio = 0.56f, stiffness = 360f),
        label = "liquidActionTranslationX",
    )
    val translationY by animateFloatAsState(
        targetValue = targetTranslationY,
        animationSpec = spring(dampingRatio = 0.56f, stiffness = 360f),
        label = "liquidActionTranslationY",
    )

    return this
        .onSizeChanged { measuredSize = it }
        .drawBackdrop(
            backdrop = backdrop,
            shape = { CircleShape },
            effects = {
                applyInteractiveLiquidActionEffects(
                    spec = glassConfig.spec,
                    isLight = isLightTheme,
                    pressProgress = pressProgress,
                )
            },
            highlight = { Highlight.Default.copy(alpha = pressProgress) },
            layerBlock = {
                scaleX = scaleXSpring
                scaleY = scaleYSpring
                this.translationX = translationX
                this.translationY = translationY
            },
            onDrawSurface = {
                drawRect(tint)
                drawRect(
                    if (isLightTheme) Color.Black.copy(alpha = 0.035f * pressProgress)
                    else Color.White.copy(alpha = 0.045f * pressProgress),
                )
            },
        )
        .semantics {
            role = Role.Button
            onClick { if (enabled) latestOnClick(); enabled }
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                startPointer = down.position
                pointer = down.position
                pressed = true
                var moved = false
                var canceled = false
                var active = true
                while (active) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    pointer = change.position
                    if ((change.position - startPointer).getDistance() > viewConfiguration.touchSlop) moved = true
                    if (change.isConsumed && moved) canceled = true
                    active = change.pressed
                }
                pressed = false
                if (!moved && !canceled) latestOnClick()
            }
        }
}

@Composable
internal fun Modifier.liquidPress(enabled: Boolean = true, onClick: () -> Unit): Modifier {
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

internal fun shareConversation(context: android.content.Context, title: String, messages: List<NativeChatMessage>) {
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
        .putExtra(android.content.Intent.EXTRA_SUBJECT, "Sillage 对话")
        .putExtra(android.content.Intent.EXTRA_TEXT, body)
    context.startActivity(android.content.Intent.createChooser(intent, "导出对话"))
}

