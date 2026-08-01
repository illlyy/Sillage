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
internal fun NativeGoalBanner(objective: String, paused: Boolean, enabled: Boolean, onEdit: () -> Unit, onTogglePause: () -> Unit, onClear: () -> Unit) {
    val language = LocalNativeLanguage.current
    var expanded by remember(objective) { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, spring(dampingRatio = 0.78f, stiffness = 420f), label = "goalArrow")
    Surface(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .fcodePressClickable(
                        onClickLabel = if (expanded) nativeText(language, "\u6536\u8d77\u76ee\u6807", "Collapse goal")
                        else nativeText(language, "\u5c55\u5f00\u76ee\u6807", "Expand goal"),
                    ) { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(HugeIcons.LookTop, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(nativeText(language, if (paused) "\u76ee\u6807\u5df2\u6682\u505c" else "\u76ee\u6807\u8fdb\u884c\u4e2d", if (paused) "Goal paused" else "Goal active"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Text(objective, maxLines = if (expanded) 6 else 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                Icon(HugeIcons.ArrowDown01, null, Modifier.size(16.dp).graphicsLayer { rotationZ = arrowRotation })
            }
            QElasticExpand(expanded) {
                Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onEdit, enabled = enabled) { Text(nativeText(language, "\u7f16\u8f91", "Edit")) }
                    TextButton(onClick = onTogglePause, enabled = enabled) { Text(nativeText(language, if (paused) "\u6062\u590d" else "\u6682\u505c", if (paused) "Resume" else "Pause")) }
                    TextButton(onClick = onClear, enabled = enabled) { Text(nativeText(language, "\u6e05\u9664", "Clear")) }
                }
            }
        }
    }
}

@Composable
internal fun ComposerModeCapsules(
    selectedMode: String,
    activeGoal: String,
    onModeSelected: (String) -> Unit,
    onRequestGoal: () -> Unit,
    onClearGoal: () -> Unit,
    selectedSkills: List<NativeSkill>,
    onRemoveSkill: (NativeSkill) -> Unit,
) {
    val language = LocalNativeLanguage.current
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 8.dp, end = 8.dp, top = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Surface(
                modifier = Modifier.clickable { menuExpanded = true },
                shape = CircleShape,
                color = if (selectedMode == "plan") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (selectedMode == "plan") MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (selectedMode == "plan") HugeIcons.Zap else HugeIcons.Sparkles, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    AnimatedContent(
                        targetState = selectedMode,
                        transitionSpec = {
                            (fadeIn(tween(150)) + slideInVertically(tween(190, easing = FastOutSlowInEasing)) { it / 3 }) togetherWith
                                (fadeOut(tween(90)) + slideOutVertically(tween(140, easing = FastOutSlowInEasing)) { -it / 3 })
                        },
                        label = "composerModeLabel",
                    ) { mode ->
                        Text(if (mode == "plan") nativeText(language, "\u8ba1\u5212\u6a21\u5f0f", "Plan mode") else nativeText(language, "\u9ed8\u8ba4\u6a21\u5f0f", "Default mode"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(3.dp))
                    Icon(HugeIcons.ArrowDown01, null, Modifier.size(13.dp))
                }
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(nativeText(language, "\u9ed8\u8ba4\u6a21\u5f0f", "Default mode")) },
                    leadingIcon = { Icon(HugeIcons.Sparkles, null, Modifier.size(18.dp)) },
                    onClick = { onModeSelected("default"); menuExpanded = false },
                )
                DropdownMenuItem(
                    text = { Text(nativeText(language, "\u8ba1\u5212\u6a21\u5f0f", "Plan mode")) },
                    leadingIcon = { Icon(HugeIcons.Zap, null, Modifier.size(18.dp)) },
                    onClick = { onModeSelected("plan"); menuExpanded = false },
                )
                DropdownMenuItem(
                    text = { Text(if (activeGoal.isBlank()) nativeText(language, "\u8bbe\u7f6e\u76ee\u6807", "Set goal") else nativeText(language, "\u7f16\u8f91\u76ee\u6807", "Edit goal")) },
                    leadingIcon = { Icon(HugeIcons.LookTop, null, Modifier.size(18.dp)) },
                    onClick = { menuExpanded = false; onRequestGoal() },
                )
            }
        }
        AnimatedVisibility(
            visible = activeGoal.isNotBlank(),
            enter = fadeIn(tween(150)) + expandHorizontally(tween(230, easing = FastOutSlowInEasing), expandFrom = Alignment.Start),
            exit = fadeOut(tween(100)) + shrinkHorizontally(tween(180, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Start),
        ) {
            Surface(
                modifier = Modifier.clickable { onClearGoal() },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Row(Modifier.padding(start = 10.dp, end = 7.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(HugeIcons.LookTop, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(nativeText(language, "\u76ee\u6807", "Goal"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(5.dp))
                    Icon(HugeIcons.Cancel01, nativeText(language, "\u6e05\u9664\u76ee\u6807", "Clear goal"), Modifier.size(13.dp))
                }
            }
        }
        selectedSkills.forEach { skill ->
            Surface(
                    modifier = Modifier.clickable { onRemoveSkill(skill) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Row(Modifier.padding(start = 10.dp, end = 7.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(HugeIcons.Files02, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(skill.name, maxLines = 1, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(5.dp))
                        Icon(HugeIcons.Cancel01, "\u79fb\u9664 Skill", Modifier.size(13.dp))
                    }
                }
        }
    }
}

@Composable
internal fun GoalEditorDialog(initialValue: String, enabled: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    val language = LocalNativeLanguage.current
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialValue.isBlank()) nativeText(language, "\u8bbe\u7f6e\u6d3b\u8dc3\u76ee\u6807", "Set active goal") else nativeText(language, "\u7f16\u8f91\u6d3b\u8dc3\u76ee\u6807", "Edit active goal")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(nativeText(language, "Codex \u4f1a\u5728\u540e\u7eed\u5bf9\u8bdd\u4e2d\u6301\u7eed\u8ddf\u8e2a\u8fd9\u4e2a\u76ee\u6807\u3002", "Codex will keep tracking this goal in later turns."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 7,
                    shape = RoundedCornerShape(18.dp),
                    placeholder = { Text(nativeText(language, "\u63cf\u8ff0\u5e0c\u671b Codex \u6301\u7eed\u8ddf\u8fdb\u7684\u76ee\u6807", "Describe what Codex should keep working toward")) },
                )
            }
        },
        confirmButton = { TextButton(enabled = enabled && value.isNotBlank(), onClick = { onConfirm(value.trim()) }) { Text(nativeText(language, "\u542f\u7528\u76ee\u6807", "Enable goal")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(nativeText(language, "\u53d6\u6d88", "Cancel")) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RikkaChatInput(
    value: String,
    enabled: Boolean,
    loading: Boolean,
    compactEnabled: Boolean,
    compactRunning: Boolean,
    submitEnabled: Boolean,
    conversationKey: String,
    conversationMoving: Boolean,
    hasConversation: Boolean,
    followUpAction: NativeFollowUpSubmitAction,
    queuedFollowUps: List<NativeQueuedFollowUp>,
    onFollowUpActionChange: (NativeFollowUpSubmitAction) -> Unit,
    onRemoveQueuedFollowUp: (String) -> Unit,
    onStop: () -> Unit,
    modelLabel: String,
    onModelClick: () -> Unit,
    effortOptions: List<String>,
    selectedEffort: String,
    onEffortSelected: (String) -> Unit,
    onUltraBurst: (Rect) -> Unit,
    selectedMode: String,
    permissionMode: String,
    onPermissionModeSelected: (String) -> Unit,
    activeGoal: String,
    onModeSelected: (String) -> Unit,
    onRequestGoal: () -> Unit,
    onClearGoal: () -> Unit,
    selectedSkills: List<NativeSkill>,
    onRemoveSkill: (NativeSkill) -> Unit,
    attachments: List<NativeAttachment>,
    onMoreClick: () -> Unit,
    onCompact: () -> Unit,
    onRetryLast: () -> Unit,
    onEditLast: () -> Unit,
    onRemoveAttachment: (NativeAttachment) -> Unit,
    onPreviewAttachment: (NativeAttachment) -> Unit,
    onValueChange: (String) -> Unit,
    onSend: (String, Rect?) -> NativeSubmitResult?,
    onHeightChanged: (Int) -> Unit,
    backdrop: Backdrop? = null,
    modifier: Modifier = Modifier,
) {
    val language = LocalNativeLanguage.current
    val appearanceRevision = LocalFcodeAppearanceRevision.current
    val inputContext = LocalContext.current
    val glassConfig = remember(appearanceRevision, inputContext) { readLiquidGlassConfig(inputContext) }
    val compactOnScrollEnabled = remember(appearanceRevision, inputContext) {
        inputContext.getSharedPreferences("codex_mobile", Context.MODE_PRIVATE)
            .getBoolean(FcodeAppearancePreferences.COMPACT_COMPOSER_ON_SCROLL, true)
    }
    val useLiquidGlass = LocalFcodeInterfaceStyle.current == FcodeInterfaceStyle.LIQUID_GLASS &&
        glassConfig.enabled && liquidGlassSupported && backdrop != null
    val isLightTheme = rememberIsLightTheme()
    val liquidGlassTint = rememberLiquidGlassTint()
    val textState = remember(conversationKey) { TextFieldState(initialText = value) }
    val composerFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val latestExternalValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    var toolsExpanded by remember { mutableStateOf(false) }
    var composerTextBounds by remember { mutableStateOf<Rect?>(null) }
    var permissionExpanded by remember { mutableStateOf(false) }

    // System speech recognition. The recognized phrase is appended to the current draft so
    // voice can be mixed with typed text; the draft mirror picks it up via the text watcher.
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val recognized = result.data
                ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull().orEmpty().trim()
            if (recognized.isNotEmpty()) {
                val current = textState.text.toString()
                val merged = if (current.isBlank()) recognized else "${current.trimEnd()} $recognized"
                textState.setTextAndPlaceCursorAtEnd(merged)
                onValueChange(merged)
            }
        }
    }
    val startVoiceInput: () -> Unit = {
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, if (language == "en") "en-US" else "zh-CN")
            putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, nativeText(language, "请说话…", "Speak now…"))
        }
        runCatching { speechLauncher.launch(intent) }
    }

    // Do not read textState.text in this parent restart scope. The TextField and send button
    // below observe it in their own small scopes, so an IME edit cannot recompose attachments,
    // mode capsules, animated surfaces and the rest of the composer.
    LaunchedEffect(textState) {
        snapshotFlow { textState.text.toString() }.collectLatest { localText ->
            // Keep the large composer subtree isolated from every IME edit, but persist drafts
            // quickly enough that an immediate route change does not lose the last sentence.
            delay(250L)
            if (localText != latestExternalValue) latestOnValueChange(localText)
        }
    }
    DisposableEffect(conversationKey, textState) {
        val flushDraft = onValueChange
        onDispose { flushDraft(textState.text.toString()) }
    }
    LaunchedEffect(value) {
        if (value != textState.text.toString()) textState.setTextAndPlaceCursorAtEnd(value)
    }
    if (permissionExpanded) {
        PermissionModeSheet(
            selected = permissionMode,
            onSelect = {
                onPermissionModeSelected(it)
                permissionExpanded = false
            },
            onDismiss = { permissionExpanded = false },
        )
    }
    if (toolsExpanded) {
        ModalBottomSheet(
            onDismissRequest = { toolsExpanded = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            NativeComposerToolSheet(
                compactEnabled = compactEnabled,
                compactRunning = compactRunning,
                onDismiss = { toolsExpanded = false },
                onCommand = { command ->
                    when (command) {
                        "__attachments__" -> onMoreClick()
                        "__compact__" -> onCompact()
                        "__retry__" -> onRetryLast()
                        "__edit__" -> onEditLast()
                        else -> {
                            textState.setTextAndPlaceCursorAtEnd(command)
                            onValueChange(command)
                        }
                    }
                    toolsExpanded = false
                },
            )
        }
    }
    val imeVisible = WindowInsets.isImeVisible
    var collapsedAfterScroll by remember(conversationKey) { mutableStateOf(false) }
    var focusAfterExpand by remember(conversationKey) { mutableStateOf(false) }
    LaunchedEffect(compactOnScrollEnabled, hasConversation, conversationMoving, imeVisible) {
        when {
            !compactOnScrollEnabled || !hasConversation -> collapsedAfterScroll = false
            conversationMoving && !imeVisible -> collapsedAfterScroll = true
        }
    }
    val compactComposer = compactOnScrollEnabled && hasConversation && collapsedAfterScroll && !imeVisible
    LaunchedEffect(compactComposer, focusAfterExpand) {
        if (!compactComposer && focusAfterExpand) {
            withFrameNanos { }
            composerFocusRequester.requestFocus()
            keyboardController?.show()
            focusAfterExpand = false
        }
    }
    val expandCompactComposer = {
        collapsedAfterScroll = false
        focusAfterExpand = true
    }
    val compactProgress by animateFloatAsState(
        targetValue = if (compactComposer) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.64f, stiffness = 390f, visibilityThreshold = 0.001f),
        label = "compactComposerMorph",
    )
    val boundedCompactProgress = compactProgress.coerceIn(0f, 1f)
    val compactHorizontalInset by animateDpAsState(
        targetValue = if (compactComposer) 12.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 420f),
        label = "compactComposerInset",
    )
    val fieldMinHeight by animateDpAsState(
        targetValue = if (compactComposer) 52.dp else 58.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 460f),
        label = "compactComposerFieldHeight",
    )
    val fieldVerticalPadding by animateDpAsState(
        targetValue = if (compactComposer) 12.dp else 15.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 480f),
        label = "compactComposerFieldPadding",
    )
    val composerSurfaceVerticalPadding by animateDpAsState(
        targetValue = if (compactComposer) 0.dp else 7.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 440f),
        label = "compactComposerSurfacePadding",
    )
    val sendButtonSize by animateDpAsState(
        targetValue = if (compactComposer) 52.dp else 42.dp,
        animationSpec = spring(dampingRatio = 0.66f, stiffness = 420f),
        label = "compactComposerSendSize",
    )
    // The action is visually outside only in the exclamation-mark compact state. At full size
    // the same action overlays the lower-right corner of the composer and reads as part of it.
    val compactActionReservation by animateDpAsState(
        targetValue = if (compactComposer) 60.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 390f),
        label = "compactComposerActionReservation",
    )
    val actionEndInset by animateDpAsState(
        targetValue = if (compactComposer) 0.dp else 10.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
        label = "composerActionEndInset",
    )
    val actionBottomInset by animateDpAsState(
        targetValue = if (compactComposer) 0.dp else 7.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
        label = "composerActionBottomInset",
    )
    val fieldEndPadding = 12.dp
    val compactInteraction = remember { MutableInteractionSource() }
    val compactPressed by compactInteraction.collectIsPressedAsState()
    val compactPressProgress by animateFloatAsState(
        targetValue = if (compactPressed && compactComposer) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f),
        label = "compactComposerLiquidPress",
    )
    val compactPressScale by animateFloatAsState(
        targetValue = if (compactPressed && compactComposer) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 250f, visibilityThreshold = 0.001f),
        label = "compactComposerPressScale",
    )
    val submitCurrentText: (String) -> Unit = submit@ { currentText ->
        if (!submitEnabled) return@submit
        val result = onSend(currentText, composerTextBounds)
        if (result?.accepted == true && enabled && (currentText.isNotBlank() || attachments.isNotEmpty())) {
            textState.setTextAndPlaceCursorAtEnd("")
            onValueChange("")
        }
    }
    // Apple-style spring: interruptible, slightly under-damped and transform-only. The input
    // keeps a stable layout width while the GPU scales it from the resting inset to full width,
    // so opening the IME does not remeasure the complete composer on every animation frame.
    val keyboardMorph by animateFloatAsState(
        targetValue = if (imeVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.74f,
            stiffness = 520f,
            visibilityThreshold = 0.001f,
        ),
        label = "inputKeyboardSpring",
    )
    val boundedKeyboardMorph = keyboardMorph.coerceIn(0f, 1f)
    val composerScaleX = 0.958f + 0.042f * boundedKeyboardMorph
    val composerScaleY = 0.988f + 0.012f * boundedKeyboardMorph
    val restingCornerRadius = glassConfig.spec.cornerRadiusDp.dp
    val cornerRadius = restingCornerRadius + (30.dp - restingCornerRadius) * boundedCompactProgress
    // Treat IME docking and compacting as one continuous shape transition. Once the compact
    // morph starts, the square keyboard corners must round away with the pill instead of
    // lingering as translucent rectangles underneath it.
    val keyboardDockProgress = if (imeVisible) {
        1f
    } else {
        boundedKeyboardMorph * (1f - boundedCompactProgress)
    }
    val bottomCorner = cornerRadius * (1f - keyboardDockProgress)
    val bottomPadding = if (imeVisible) 0.dp else 10.dp
    val keyboardOverlap = 3.dp * boundedKeyboardMorph
    val inputBorderColor = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        MaterialTheme.colorScheme.surfaceContainerLow,
        boundedKeyboardMorph,
    )
    val inputShape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius, bottomEnd = bottomCorner, bottomStart = bottomCorner)
    // Apply exactly one bottom inset. The old layout stacked an always-on navigationBarsPadding()
    // on the outside AND an imePadding() here, which doubled the offset on devices that keep
    // reporting a navigation-bar inset while the IME is up, leaving a gap under the composer.
    val insetModifier = if (imeVisible) Modifier.imePadding() else Modifier.navigationBarsPadding()
    // Use Box instead of Surface to avoid clipping the press-scaled inner composer.
    // Surface clips its content to RectangleShape by default, which cuts off the
    // enlarged liquid-glass pill during the long-press zoom animation.
    Box(modifier = modifier) {
        Column(
            modifier = insetModifier
                // Measure the entire visible composer (queue strip, padding and card), but not
                // the IME/navigation inset owned by insetModifier.
                .onSizeChanged { onHeightChanged(it.height) }
                .padding(start = maxOf(0.dp, compactHorizontalInset), end = maxOf(0.dp, compactHorizontalInset), top = 8.dp, bottom = bottomPadding)
                .offset(y = keyboardOverlap)
                .graphicsLayer {
                    scaleX = composerScaleX
                    scaleY = composerScaleY
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
        ) {
            AnimatedVisibility(
                visible = queuedFollowUps.isNotEmpty() && !compactComposer,
                enter = fadeIn(tween(140)) + expandVertically(tween(180)),
                exit = fadeOut(tween(100)) + shrinkVertically(tween(140)),
            ) {
                QueuedFollowUpStrip(queuedFollowUps, onRemoveQueuedFollowUp)
            }
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = maxOf(0.dp, compactActionReservation))
                    // Keep the press transform outside animateContentSize. That modifier clips
                    // its child to the animated layout bounds; placing the scale inside it cut off
                    // the enlarged liquid surface at the pill's original top edge.
                    .graphicsLayer {
                        scaleX = compactPressScale
                        scaleY = compactPressScale
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        clip = false
                    }
                    .animateContentSize(
                        animationSpec = spring(dampingRatio = 0.66f, stiffness = 360f),
                        alignment = Alignment.BottomCenter,
                    )
                    .then(
                        if (useLiquidGlass && backdrop != null) Modifier
                            // Clip the backdrop and tint to inputShape so the liquid glass
                            // fills the exact shape (including square bottom corners when the
                            // keyboard is docked) instead of rendering as a full rectangle
                            // that leaks translucent corners during shape transitions.
                            .clip(inputShape)
                            .drawBackdrop(
                            backdrop = backdrop,
                            shape = { inputShape },
                            effects = {
                                if (compactPressProgress > 0.001f) {
                                    applyInteractiveLiquidActionEffects(
                                        spec = glassConfig.spec,
                                        isLight = isLightTheme,
                                        pressProgress = compactPressProgress,
                                    )
                                } else {
                                    // A radial depth vector makes a zero-radius corner read like a
                                    // rounded glass body plus a translucent filler. Use edge-normal
                                    // refraction while docked so the whole straight corner remains
                                    // one continuous liquid-glass material.
                                    applyLiquidGlassEffects(
                                        spec = glassConfig.spec,
                                        isLight = isLightTheme,
                                        depthEffect = keyboardDockProgress <= 0.001f,
                                    )
                                }
                            },
                            highlight = {
                                if (compactPressProgress > 0.001f) Highlight.Default.copy(alpha = compactPressProgress)
                                else Highlight.Plain
                            },
                            // Disable the default shadow: it renders a separate clipped shape
                            // underneath the backdrop, which shows through the semi-transparent
                            // glass as a translucent rectangle during shape transitions.
                            shadow = { null },
                            onDrawSurface = { drawRect(liquidGlassTint) },
                        ) else Modifier
                    ),
                shape = inputShape,
                tonalElevation = 0.dp,
                border = if (useLiquidGlass) null else BorderStroke(1.dp, inputBorderColor),
                color = if (useLiquidGlass) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow.copy(
                        alpha = LocalFcodeMaterialTransparency.current.composerAlpha,
                    )
                },
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = maxOf(0.dp, composerSurfaceVerticalPadding))) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AnimatedVisibility(
                            visible = attachments.isNotEmpty() && !compactComposer,
                            enter = fadeIn(tween(130)) + expandVertically(
                                animationSpec = spring(dampingRatio = 0.72f, stiffness = 430f),
                                expandFrom = Alignment.Bottom,
                            ),
                            exit = fadeOut(tween(90)) + shrinkVertically(
                                animationSpec = spring(dampingRatio = 0.78f, stiffness = 500f),
                                shrinkTowards = Alignment.Bottom,
                            ),
                        ) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                                items(attachments, key = { it.path }) { attachment ->
                                    val sizeLabel = remember(attachment.path) {
                                        formatFileSize(java.io.File(attachment.path).length())
                                    }
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
                                                Text(sizeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            IconButton(onClick = { onRemoveAttachment(attachment) }, modifier = Modifier.size(28.dp)) { Icon(HugeIcons.Cancel01, "移除", modifier = Modifier.size(14.dp)) }
                                        }
                                    }
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = !compactComposer,
                            enter = fadeIn(tween(145)) + expandVertically(
                                animationSpec = spring(dampingRatio = 0.68f, stiffness = 390f),
                                expandFrom = Alignment.Bottom,
                            ),
                            exit = fadeOut(tween(90)) + shrinkVertically(
                                animationSpec = spring(dampingRatio = 0.74f, stiffness = 470f),
                                shrinkTowards = Alignment.Bottom,
                            ),
                        ) {
                            ComposerModeCapsules(
                                selectedMode = selectedMode,
                                activeGoal = activeGoal,
                                onModeSelected = onModeSelected,
                                onRequestGoal = onRequestGoal,
                                onClearGoal = onClearGoal,
                                selectedSkills = selectedSkills,
                                onRemoveSkill = onRemoveSkill,
                            )
                        }
                        FcodeChatTypography {
                            BasicTextField(
                                state = textState,
                                modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = fieldMinHeight)
                                .padding(start = 12.dp, end = fieldEndPadding, top = fieldVerticalPadding, bottom = fieldVerticalPadding)
                                .focusRequester(composerFocusRequester)
                                .onGloballyPositioned { composerTextBounds = it.boundsInWindow() }
                                .onPreviewKeyEvent { event ->
                                    val currentText = textState.text.toString()
                                    if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.Enter && enabled && submitEnabled && currentText.isNotBlank()) {
                                        val result = onSend(currentText, composerTextBounds)
                                        if (result?.accepted == true) {
                                            textState.setTextAndPlaceCursorAtEnd("")
                                            onValueChange("")
                                        }
                                        result?.accepted == true
                                    } else false
                                },
                                enabled = enabled && !compactComposer,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 24.sp,
                                ),
                                lineLimits = TextFieldLineLimits.MultiLine(
                                    minHeightInLines = 1,
                                    maxHeightInLines = if (compactComposer) 1 else 5,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorator = { innerTextField ->
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                        if (textState.text.isEmpty()) {
                                            Text(
                                                nativeText(language, "输入消息", "Type a message"),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodyLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                        }
                        AnimatedVisibility(
                            visible = !compactComposer,
                            enter = fadeIn(tween(145)) + expandVertically(
                                animationSpec = spring(dampingRatio = 0.66f, stiffness = 400f),
                                expandFrom = Alignment.Bottom,
                            ),
                            exit = fadeOut(tween(85)) + shrinkVertically(
                                animationSpec = spring(dampingRatio = 0.76f, stiffness = 500f),
                                shrinkTowards = Alignment.Bottom,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 52.dp, top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    InputTool(HugeIcons.Sparkles, modelLabel.ifBlank { "模型" }, onModelClick)
                                    LiquidEffortTool(effortOptions, selectedEffort, onEffortSelected, onUltraBurst)
                                    InputTool(
                                        HugeIcons.Settings03,
                                        NativePermissionMode.label(permissionMode, language != "en"),
                                        { permissionExpanded = true },
                                    )
                                    InputTool(HugeIcons.Add01, "工具", { toolsExpanded = true })
                                    if (loading) {
                                        InputTool(
                                            if (followUpAction == NativeFollowUpSubmitAction.STEER) HugeIcons.Zap else HugeIcons.TransactionHistory,
                                            if (followUpAction == NativeFollowUpSubmitAction.STEER)
                                                nativeText(language, "引导当前", "Steer")
                                            else nativeText(language, "排队下一条", "Queue"),
                                            {
                                                onFollowUpActionChange(
                                                    if (followUpAction == NativeFollowUpSubmitAction.STEER) NativeFollowUpSubmitAction.QUEUE
                                                    else NativeFollowUpSubmitAction.STEER,
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // The disabled BasicTextField still owns pointer input on some Compose/OEM
                    // combinations. Keep a top-most hit target in compact mode so every point of
                    // the glass pill expands it, while sharing the interaction source that drives
                    // the press spring on the whole pill.
                    if (compactComposer) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(fieldMinHeight)
                                .align(Alignment.Center)
                                .zIndex(1f)
                                .clickable(
                                    interactionSource = compactInteraction,
                                    indication = null,
                                    onClick = expandCompactComposer,
                                ),
                        )
                    }
                }
            }
                ComposerActionButton(
                    textState = textState,
                    enabled = enabled && submitEnabled,
                    loading = loading,
                    hasAttachments = attachments.isNotEmpty(),
                    onStop = onStop,
                    onSend = submitCurrentText,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = maxOf(0.dp, actionEndInset),
                            bottom = maxOf(0.dp, actionBottomInset),
                        )
                        .size(sendButtonSize),
                    liquidGlass = useLiquidGlass,
                    backdrop = backdrop,
                    glassConfig = glassConfig,
                    isLightTheme = isLightTheme,
                    liquidGlassTint = liquidGlassTint,
                )
            }
        }
    }
}

@Composable
internal fun QueuedFollowUpStrip(
    queuedFollowUps: List<NativeQueuedFollowUp>,
    onRemove: (String) -> Unit,
) {
    val language = LocalNativeLanguage.current
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "queue-count") {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.88f),
            ) {
                Row(
                    Modifier.height(34.dp).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(HugeIcons.TransactionHistory, null, Modifier.size(15.dp))
                    Text(
                        nativeText(language, "排队 ${queuedFollowUps.size}", "Queued ${queuedFollowUps.size}"),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        items(queuedFollowUps, key = { it.id }) { followUp ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
            ) {
                Row(
                    Modifier.height(34.dp).padding(start = 10.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        followUp.text.ifBlank { nativeText(language, "附件消息", "Attachment message") },
                        modifier = Modifier.widthIn(max = 150.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    IconButton(
                        onClick = { onRemove(followUp.id) },
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(HugeIcons.Cancel01, nativeText(language, "移出队列", "Remove from queue"), Modifier.size(13.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ComposerActionButton(
    textState: TextFieldState,
    enabled: Boolean,
    loading: Boolean,
    hasAttachments: Boolean,
    onStop: () -> Unit,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    liquidGlass: Boolean = false,
    backdrop: Backdrop? = null,
    glassConfig: LiquidGlassConfig? = null,
    isLightTheme: Boolean = true,
    liquidGlassTint: Color = Color.Transparent,
) {
    val inputText = textState.text.toString()
    val action = nativeComposerAction(inputText, hasAttachments, enabled, loading)
    val stopMode = action == NativeComposerAction.STOP
    val canActivate = action != NativeComposerAction.DISABLED
    val latestEnabled by rememberUpdatedState(enabled)
    val latestLoading by rememberUpdatedState(loading)
    val latestHasAttachments by rememberUpdatedState(hasAttachments)
    val latestOnStop by rememberUpdatedState(onStop)
    val latestOnSend by rememberUpdatedState(onSend)
    val activate: () -> Unit = remember(textState) {
        {
            // The field is the source of truth at gesture completion. In particular, a steer send
            // clears it while the active-turn action stays enabled and changes meaning to Stop.
            // Never let a pointer callback captured before that transition reuse the old draft.
            val currentInput = textState.text.toString()
            when (nativeComposerAction(currentInput, latestHasAttachments, latestEnabled, latestLoading)) {
                NativeComposerAction.SEND -> latestOnSend(currentInput)
                NativeComposerAction.STOP -> latestOnStop()
                NativeComposerAction.DISABLED -> Unit
            }
        }
    }
    val actionModifier = if (liquidGlass && backdrop != null && glassConfig != null) {
        modifier.interactiveLiquidAction(
            enabled = canActivate,
            backdrop = backdrop,
            glassConfig = glassConfig,
            isLightTheme = isLightTheme,
            tint = liquidGlassTint,
            onClick = activate,
        )
    } else {
        modifier.clickable(enabled = canActivate, onClick = activate)
    }
    Surface(
        modifier = actionModifier,
        shape = CircleShape,
        color = when {
            liquidGlass -> Color.Transparent
            stopMode -> MaterialTheme.colorScheme.errorContainer
            canActivate -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (stopMode) HugeIcons.Cancel01 else HugeIcons.ArrowUp02,
                contentDescription = if (stopMode) {
                    nativeText(LocalNativeLanguage.current, "停止生成", "Stop generating")
                } else {
                    nativeText(LocalNativeLanguage.current, "发送", "Send")
                },
                modifier = Modifier.size(if (stopMode) 18.dp else 21.dp),
                tint = when {
                    !canActivate -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    liquidGlass -> MaterialTheme.colorScheme.onSurface
                    stopMode -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onPrimary
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PermissionModeSheet(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val language = LocalNativeLanguage.current
    val options = listOf(
        Triple(
            NativePermissionMode.WORKSPACE,
            nativeText(language, "\u5de5\u4f5c\u533a\u8bbf\u95ee", "Workspace access"),
            nativeText(language, "\u53ef\u8bfb\u5199\u5f53\u524d\u9879\u76ee\uff1b\u8d8a\u754c\u64cd\u4f5c\u4f1a\u5148\u8be2\u95ee", "Read and write the project; ask before broader access"),
        ),
        Triple(
            NativePermissionMode.FULL_ACCESS,
            nativeText(language, "\u5b8c\u5168\u8bbf\u95ee", "Full access"),
            nativeText(language, "\u4e0d\u4f7f\u7528\u6c99\u7bb1\u4e14\u4e0d\u8be2\u95ee\uff0c\u547d\u4ee4\u53ef\u4ee5\u76f4\u63a5\u8fd0\u884c", "No sandbox or prompts; commands run directly"),
        ),
        Triple(
            NativePermissionMode.READ_ONLY,
            nativeText(language, "\u53ea\u8bfb", "Read only"),
            nativeText(language, "\u5141\u8bb8\u68c0\u67e5\u6587\u4ef6\uff0c\u4fee\u6539\u6216\u8d8a\u754c\u547d\u4ee4\u9700\u8981\u786e\u8ba4", "Inspect files; writes and broader commands require approval"),
        ),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(nativeText(language, "\u8bbf\u95ee\u6743\u9650", "Access permissions"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                nativeText(language, "\u6743\u9650\u4f1a\u5e94\u7528\u5230\u4e0b\u4e00\u6761\u6d88\u606f\uff0c\u4e5f\u4f1a\u8986\u76d6\u65e7\u5bf9\u8bdd\u4fdd\u5b58\u7684\u6c99\u7bb1\u8bbe\u7f6e\u3002", "Applies to the next message and overrides sandbox settings saved in older conversations."),
                Modifier.padding(top = 4.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            options.forEach { (mode, title, description) ->
                val active = NativePermissionMode.normalize(selected) == mode
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onSelect(mode) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = if (mode == NativePermissionMode.FULL_ACCESS) BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.28f)) else null,
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (active) HugeIcons.Tick02 else HugeIcons.Settings03,
                            null,
                            Modifier.size(20.dp),
                            tint = if (mode == NativePermissionMode.FULL_ACCESS) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeComposerToolSheet(
    compactEnabled: Boolean,
    compactRunning: Boolean,
    onDismiss: () -> Unit,
    onCommand: (String) -> Unit,
) {
    val language = LocalNativeLanguage.current
    val tools = listOf(
        Triple(HugeIcons.Sparkles, "\u538b\u7f29\u4e0a\u4e0b\u6587", "__compact__"),
        Triple(HugeIcons.Add01, "\u6dfb\u52a0\u56fe\u7247\u6216\u6587\u4ef6", "__attachments__"),
        Triple(HugeIcons.Files02, "\u5217\u51fa\u5f53\u524d\u76ee\u5f55", "/ls"),
        Triple(HugeIcons.Search01, "\u641c\u7d22\u6587\u4ef6", "/search "),
        Triple(HugeIcons.PencilEdit01, "\u7f16\u8f91\u4e0a\u4e00\u6761\u6d88\u606f", "__edit__"),
        Triple(HugeIcons.Refresh03, "\u91cd\u65b0\u751f\u6210\u56de\u7b54", "__retry__"),
        Triple(HugeIcons.Cancel01, "\u6e05\u7a7a\u8f93\u5165", ""),
    )
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(nativeText(language, "\u5de5\u5177", "Tools"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(HugeIcons.Cancel01, "\u5173\u95ed") }
        }
        Text(nativeText(language, "\u8f93\u5165 / \u4e5f\u53ef\u4ee5\u968f\u65f6\u6253\u5f00\u6b64\u9762\u677f", "Type / to open this panel"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
        tools.forEach { (icon, title, command) ->
            val itemEnabled = command != "__compact__" || compactEnabled
            val trailingLabel = when (command) {
                "__compact__" -> if (compactEnabled) {
                    nativeText(language, "\u6267\u884c", "Run")
                } else if (compactRunning) {
                    nativeText(language, "\u538b\u7f29\u8fdb\u884c\u4e2d", "Compacting")
                } else {
                    nativeText(language, "\u4efb\u52a1\u4e2d\u4e0d\u53ef\u7528", "Unavailable during a task")
                }
                "__attachments__" -> "\u9009\u62e9"
                "__retry__" -> "\u6267\u884c"
                "__edit__" -> "\u7f16\u8f91"
                "" -> "\u6e05\u9664"
                else -> command
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable(enabled = itemEnabled) { onCommand(command) },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        icon,
                        null,
                        Modifier.size(20.dp),
                        tint = if (itemEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .42f),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        color = if (itemEnabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f),
                    )
                    Text(
                        trailingLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (itemEnabled) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

internal fun formatFileSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

@Composable
internal fun AttachmentThumbnail(path: String, modifier: Modifier = Modifier, maxEdge: Int = 256) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = path, key2 = maxEdge) {
        value = withContext(Dispatchers.IO) { NativeAttachmentImageLoader.load(path, maxEdge) }
    }
    AndroidView(
        modifier = modifier,
        factory = { context -> android.widget.ImageView(context).apply { scaleType = android.widget.ImageView.ScaleType.CENTER_CROP } },
        update = { view -> view.setImageBitmap(bitmap) },
    )
}

@Composable
internal fun AttachmentPreviewDialog(attachment: NativeAttachment, onDismiss: () -> Unit) {
    val language = LocalNativeLanguage.current
    val sizeLabel = remember(attachment.path) {
        formatFileSize(java.io.File(attachment.path).length())
    }
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(attachment.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (attachment.image) AttachmentThumbnail(
                    attachment.path,
                    Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 420.dp).clip(MaterialTheme.shapes.large),
                    maxEdge = 1280,
                )
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (attachment.image) nativeText(language, "\u56fe\u7247\u9644\u4ef6", "Image attachment") else nativeText(language, "\u6587\u4ef6\u9644\u4ef6", "File attachment"), style = MaterialTheme.typography.labelLarge)
                        Text(sizeLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(attachment.path, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(nativeText(language, "\u5b8c\u6210", "Done")) } },
    )
}

@Composable
internal fun SkillPickerDialog(
    skills: List<NativeSkill>,
    selected: List<NativeSkill>,
    onDismiss: () -> Unit,
    onSelect: (NativeSkill) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val language = LocalNativeLanguage.current
    val results = remember(skills, query) {
        skills.filter { query.isBlank() || it.name.contains(query, true) || it.description.contains(query, true) }
    }
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text("\u5f15\u7528 Skill") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = CircleShape,
                    leadingIcon = { Icon(HugeIcons.Search01, null, Modifier.size(18.dp)) },
                    placeholder = { Text("\u641c\u7d22 Skill") },
                )
                if (results.isEmpty()) {
                    Text(nativeText(language, "\u6ca1\u6709\u627e\u5230\u53ef\u7528 Skill", "No skills found"), modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(results, key = { it.path }) { skill ->
                            val isSelected = selected.any { it.path == skill.path }
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable(enabled = !isSelected) { onSelect(skill) },
                                shape = RoundedCornerShape(17.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Row(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.Top) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) { Icon(HugeIcons.Sparkles, null, Modifier.padding(7.dp).size(15.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer) }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(skill.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        if (skill.description.isNotBlank()) Text(skill.description, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
                                    }
                                    Text(nativeText(language, "\u5df2\u5f15\u7528", "Added"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(nativeText(language, "\u5173\u95ed", "Close")) } },
    )
}

@Composable
internal fun RikkaFilesPicker(onPickImage: () -> Unit, onPickFile: () -> Unit, onPickSkill: () -> Unit) {
    val language = LocalNativeLanguage.current
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
internal fun RikkaFileAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(onClick = onClick, modifier = Modifier.size(56.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, label, modifier = Modifier.size(24.dp)) }
        }
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun InputTool(icon: ImageVector, description: String, onClick: () -> Unit = {}) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, description, modifier = Modifier.size(21.dp))
    }
}

