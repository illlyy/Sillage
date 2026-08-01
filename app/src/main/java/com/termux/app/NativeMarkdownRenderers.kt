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

internal data class MarkdownBlock(val code: Boolean, val text: String, val language: String = "")

internal fun markdownBlocks(source: String): List<MarkdownBlock> {
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
internal fun RichResponseText(
    text: String,
    onQuoteSelection: ((String) -> Unit)? = null,
) {
    val initialDocument = remember(text) { NativeMarkdownDocumentParser.cached(text) }
    val document by produceState<NativeMarkdownDocument?>(initialDocument, text) {
        if (value == null) {
            value = withContext(Dispatchers.Default) { NativeMarkdownDocumentParser.parseCached(text) }
        }
    }
    val parsed = document
    if (parsed == null) {
        // Keep content visible while block segmentation runs off-main-thread.
        StableLiveTextChunk(text, reasoning = false)
        return
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        parsed.blocks.forEachIndexed { index, block ->
            androidx.compose.runtime.key(index, block.type, block.text.hashCode()) {
                when (block.type) {
                    NativeMarkdownBlockType.CODE -> RikkaCodeBlock(block.language, block.text, onQuoteSelection)
                    NativeMarkdownBlockType.INLINE_CODE -> FcodeStandaloneInlineCode(block.text, onQuoteSelection)
                    NativeMarkdownBlockType.TABLE -> block.table?.let { table ->
                        FcodeMarkdownTable(table, block.text)
                    }
                    NativeMarkdownBlockType.PROSE -> if (block.text.isNotBlank()) {
                        RichMarkdownText(block.text, onQuoteSelection)
                    }
                }
            }
        }
    }
}

@Composable
internal fun RikkaCodeBlock(
    language: String,
    code: String,
    onQuoteSelection: ((String) -> Unit)? = null,
) {
    val languageUi = LocalNativeLanguage.current
    val clipboard = LocalClipboardManager.current
    val colors = LocalFcodeMarkdownColors.current
    val lines = remember(code) { code.lines() }
    val collapsible = lines.size > 18 || code.length > 1800
    var expanded by remember(code) { mutableStateOf(!collapsible) }
    val visibleCode = remember(code, expanded) {
        if (expanded || !collapsible) code else lines.take(16).joinToString("\n")
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.codeBlockBackground,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 5.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    language.ifBlank { nativeText(languageUi, "\u4ee3\u7801", "Code") },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(nativeText(languageUi, "${lines.size} \u884c", "${lines.size} lines"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                IconButton(
                    onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(code)) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(HugeIcons.Copy01, nativeText(languageUi, "\u590d\u5236\u4ee3\u7801", "Copy code"), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = if (collapsible) 6.dp else 14.dp),
            ) {
                FcodeSelectableCodeText(
                    text = visibleCode,
                    modifier = Modifier.fillMaxWidth(),
                    onQuoteSelection = onQuoteSelection,
                    codeLanguage = language,
                )
            }
            if (collapsible) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(if (expanded) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (expanded) nativeText(languageUi, "\u6536\u8d77\u4ee3\u7801", "Collapse code") else nativeText(languageUi, "\u5c55\u5f00\u5168\u90e8 ${lines.size} \u884c", "Expand all ${lines.size} lines"))
                }
            }
        }
    }
}

@Composable
internal fun FcodeSelectableCodeText(
    text: String,
    modifier: Modifier = Modifier,
    onQuoteSelection: ((String) -> Unit)? = null,
    textColor: Color? = null,
    codeLanguage: String = "",
) {
    val language = LocalNativeLanguage.current
    val colors = LocalFcodeMarkdownColors.current
    val chatFontScale = LocalFcodeChatFontScale.current.coerceIn(0.5f, 2f)
    val onSelectionActivityChanged = LocalTextSelectionActivityChanged.current
    // Syntax coloring is computed off the render path and cached per (text, language, theme).
    val highlighted = remember(text, codeLanguage, colors.codeBlockBackground) {
        NativeCodeHighlighter.highlight(text, codeLanguage, colors.codeBlockBackground.toArgb())
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FcodeSelectableTextView(context).apply {
                includeFontPadding = false
                setHorizontallyScrolling(true)
                setSingleLine(false)
                restingMovementMethod = android.text.method.ScrollingMovementMethod.getInstance()
                movementMethod = restingMovementMethod
                isHorizontalScrollBarEnabled = true
                scrollBarStyle = android.view.View.SCROLLBARS_INSIDE_OVERLAY
                textSize = 13f
                typeface = android.graphics.Typeface.MONOSPACE
                setLineSpacing(resources.displayMetrics.density * 2f, 1.04f)
            }
        },
        onReset = { view -> view.finishSelection() },
        update = { view ->
            view.copyLabel = nativeText(language, "\u590d\u5236", "Copy")
            view.quoteLabel = nativeText(language, "\u5f15\u7528", "Quote")
            view.selectAllLabel = nativeText(language, "\u5168\u9009", "Select all")
            view.onSelectionActivityChanged = onSelectionActivityChanged
            view.onQuoteSelection = onQuoteSelection
            view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f * chatFontScale)
            view.setTextColor((textColor ?: colors.codeBlockText).toArgb())
            val display: CharSequence = highlighted ?: text
            // Re-set only when the source changed or the highlighted spans were rebuilt
            // (e.g. theme switch), preserving scroll/selection otherwise.
            if (view.text.toString() != text || view.text !== display) {
                view.finishSelection()
                view.text = display
            }
        },
    )
}

@Composable
internal fun FcodeStandaloneInlineCode(
    code: String,
    onQuoteSelection: ((String) -> Unit)? = null,
) {
    val colors = LocalFcodeMarkdownColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(11.dp),
        color = colors.inlineCodeBackground,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)),
    ) {
        FcodeSelectableCodeText(
            text = code,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            onQuoteSelection = onQuoteSelection,
            textColor = colors.inlineCodeText,
        )
    }
}

@Composable
internal fun FcodeMarkdownTable(table: NativeMarkdownTable, source: String) {
    val language = LocalNativeLanguage.current
    val clipboard = LocalClipboardManager.current
    val widths = remember(table) {
        List(table.columnCount) { column -> markdownTableColumnWidth(table, column).dp }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.68f)),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 13.dp, end = 5.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    nativeText(language, "\u8868\u683c", "Table"),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    nativeText(language, "${table.rows.size} \u884c", "${table.rows.size} rows"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                )
                IconButton(
                    onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(source)) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        HugeIcons.Copy01,
                        nativeText(language, "\u590d\u5236\u8868\u683c", "Copy table"),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Column {
                    FcodeMarkdownTableRow(table.header, widths, header = true, alternate = false)
                    table.rows.forEachIndexed { index, row ->
                        FcodeMarkdownTableRow(row, widths, header = false, alternate = index % 2 == 1)
                    }
                }
            }
        }
    }
}

@Composable
internal fun FcodeMarkdownTableRow(
    cells: List<NativeMarkdownTableCell>,
    widths: List<androidx.compose.ui.unit.Dp>,
    header: Boolean,
    alternate: Boolean,
) {
    val background = when {
        header -> MaterialTheme.colorScheme.surfaceContainerHigh
        alternate -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.46f)
        else -> Color.Transparent
    }
    Row(Modifier.background(background)) {
        widths.forEachIndexed { column, width ->
            val cell = cells.getOrElse(column) { NativeMarkdownTableCell("") }
            val annotated = fcodeTableInlineText(cell.text)
            Box(
                Modifier
                    .width(width)
                    .padding(horizontal = 11.dp, vertical = if (header) 9.dp else 8.dp),
            ) {
                Text(
                    text = annotated,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                    lineHeight = 19.sp,
                    textAlign = when (cell.alignment) {
                        NativeMarkdownAlignment.START -> TextAlign.Start
                        NativeMarkdownAlignment.CENTER -> TextAlign.Center
                        NativeMarkdownAlignment.END -> TextAlign.End
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
}

@Composable
internal fun fcodeTableInlineText(source: String): androidx.compose.ui.text.AnnotatedString {
    val codeBackground = LocalFcodeMarkdownColors.current.inlineCodeBackground
    val codeText = LocalFcodeMarkdownColors.current.inlineCodeText
    val linkColor = LocalFcodeMarkdownColors.current.link
    return remember(source, codeBackground, codeText, linkColor) {
        androidx.compose.ui.text.buildAnnotatedString {
            val pattern = Regex("""(`+)(.+?)\1|\*\*(.+?)\*\*|__(.+?)__|\[([^]]+)]\(([^)]+)\)""")
            var cursor = 0
            pattern.findAll(source).forEach { match ->
                if (match.range.first > cursor) append(source.substring(cursor, match.range.first))
                when {
                    match.groupValues[2].isNotEmpty() -> withStyle(
                        androidx.compose.ui.text.SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground,
                            color = codeText,
                        ),
                    ) { append(match.groupValues[2]) }
                    match.groupValues[3].isNotEmpty() || match.groupValues[4].isNotEmpty() -> withStyle(
                        androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold),
                    ) { append(match.groupValues[3].ifEmpty { match.groupValues[4] }) }
                    match.groupValues[5].isNotEmpty() -> withStyle(
                        androidx.compose.ui.text.SpanStyle(color = linkColor),
                    ) { append(match.groupValues[5]) }
                }
                cursor = match.range.last + 1
            }
            if (cursor < source.length) append(source.substring(cursor))
        }
    }
}

internal fun markdownTableColumnWidth(table: NativeMarkdownTable, column: Int): Float {
    fun visualUnits(text: String): Float = text.codePoints().toArray().sumOf { codePoint ->
        if (codePoint > 0xFF) 1.0 else 0.58
    }.toFloat()
    var units = visualUnits(table.header.getOrNull(column)?.text.orEmpty())
    table.rows.forEach { row -> units = maxOf(units, visualUnits(row.getOrNull(column)?.text.orEmpty())) }
    return (units.coerceAtMost(24f) * 13f + 28f).coerceIn(104f, 268f)
}

internal fun normalizeMarkdownLists(source: String): String =
    NativeMarkdownDocumentParser.normalizeProse(source)

internal data class MarkdownRenderCacheKey(val themeKey: String, val text: String)

internal object NativeMarkdownRenderer {
    private val renderers = LinkedHashMap<String, Markwon>()
    private val renderedCache = object : LinkedHashMap<MarkdownRenderCacheKey, android.text.Spanned>(48, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<MarkdownRenderCacheKey, android.text.Spanned>?): Boolean = size > 48
    }

    @Synchronized
    fun get(
        context: android.content.Context,
        colors: FcodeMarkdownColors,
        chatFontScale: Float,
    ): Markwon {
        val normalizedScale = chatFontScale.coerceIn(0.5f, 2f)
        val scaleKey = (normalizedScale * 100f).roundToInt()
        val rendererKey = "${colors.cacheKey}|chat-font-$scaleKey"
        renderers[rendererKey]?.let { return it }
        val density = context.resources.displayMetrics.density
        val scaledDensity = density * context.resources.configuration.fontScale
        val renderer = Markwon.builder(context)
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .blockMargin((12f * density).roundToInt())
                        .codeTextSize((14f * scaledDensity * normalizedScale).roundToInt())
                        .headingTextSizeMultipliers(floatArrayOf(1.46f, 1.28f, 1.16f, 1.08f, 1.02f, 1.0f))
                        .headingBreakHeight(0)
                        .listItemColor(colors.listMarker.toArgb())
                        .bulletWidth((8f * density).roundToInt())
                        .bulletListItemStrokeWidth((2f * density).roundToInt())
                        .blockQuoteColor(colors.quote.toArgb())
                        .blockQuoteWidth((4f * density).roundToInt())
                        .linkColor(colors.link.toArgb())
                        .isLinkUnderlined(false)
                        .codeBackgroundColor(colors.inlineCodeBackground.toArgb())
                        .codeTextColor(colors.inlineCodeText.toArgb())
                }

                override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                    builder.setFactory(Code::class.java) { _, _ ->
                        RoundedInlineCodeSpan(
                            horizontalPadding = 5f * density,
                            verticalPadding = 2f * density,
                            radius = 8f * density,
                            backgroundColor = colors.inlineCodeBackground.toArgb(),
                            textColor = colors.inlineCodeText.toArgb(),
                        )
                    }
                    builder.setFactory(BlockQuote::class.java) { _, _ ->
                        RoundedBlockQuoteSpan(
                            margin = 16f * density,
                            barWidth = 4f * density,
                            radius = 2f * density,
                            color = colors.quote.toArgb(),
                        )
                    }
                    // Markwon's default CodeBlockSpan paints the whole block as a hard-edged
                    // rectangle; the rounded variant below keeps the monospace/metrics and left
                    // indent but draws a pill-shaped background that matches RikkaCodeBlock.
                    builder.setFactory(FencedCodeBlock::class.java) { _, _ ->
                        RoundedCodeBlockSpan(
                            margin = 16f * density,
                            radius = 12f * density,
                            backgroundColor = colors.codeBlockBackground.toArgb(),
                            paddingHorizontal = 14f * density,
                            paddingVertical = 5f * density,
                        )
                    }
                    builder.setFactory(IndentedCodeBlock::class.java) { _, _ ->
                        RoundedCodeBlockSpan(
                            margin = 16f * density,
                            radius = 12f * density,
                            backgroundColor = colors.codeBlockBackground.toArgb(),
                            paddingHorizontal = 14f * density,
                            paddingVertical = 5f * density,
                        )
                    }
                }
            })
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(JLatexMathPlugin.create(42f * normalizedScale) { builder -> builder.inlinesEnabled(true) })
            .build()
        if (renderers.size >= 8) renderers.remove(renderers.keys.first())
        renderers[rendererKey] = renderer
        return renderer
    }

    fun cached(themeKey: String, text: String): android.text.Spanned? =
        synchronized(renderedCache) { renderedCache[MarkdownRenderCacheKey(themeKey, text)] }

    fun render(markwon: Markwon, themeKey: String, text: String): android.text.Spanned {
        val key = MarkdownRenderCacheKey(themeKey, text)
        synchronized(renderedCache) { renderedCache[key] }?.let { return it }
        val rendered = markwon.toMarkdown(normalizeMarkdownLists(text))
        synchronized(renderedCache) { renderedCache[key] = rendered }
        return rendered
    }
}

internal class RoundedInlineCodeSpan(
    private val horizontalPadding: Float,
    private val verticalPadding: Float,
    private val radius: Float,
    private val backgroundColor: Int,
    private val textColor: Int,
) : android.text.style.ReplacementSpan() {
    override fun getSize(
        paint: android.graphics.Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: android.graphics.Paint.FontMetricsInt?,
    ): Int {
        val oldTypeface = paint.typeface
        paint.typeface = android.graphics.Typeface.MONOSPACE
        if (fm != null) {
            val source = paint.fontMetricsInt
            fm.ascent = (source.ascent - verticalPadding).roundToInt()
            fm.descent = (source.descent + verticalPadding).roundToInt()
            fm.top = fm.ascent
            fm.bottom = fm.descent
        }
        val width = (paint.measureText(text, start, end) + horizontalPadding * 2f).roundToInt()
        paint.typeface = oldTypeface
        return width
    }

    override fun draw(
        canvas: android.graphics.Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: android.graphics.Paint,
    ) {
        val oldColor = paint.color
        val oldTypeface = paint.typeface
        val oldStyle = paint.style
        paint.typeface = android.graphics.Typeface.MONOSPACE
        // A preceding span can leave the shared paint in STROKE mode, which would render the
        // code chip as an outline (or a device-specific angular box). Force a filled round rect.
        paint.style = android.graphics.Paint.Style.FILL
        val width = paint.measureText(text, start, end) + horizontalPadding * 2f
        paint.color = backgroundColor
        canvas.drawRoundRect(
            x,
            top + verticalPadding * 0.35f,
            x + width,
            bottom - verticalPadding * 0.35f,
            radius,
            radius,
            paint,
        )
        paint.color = textColor
        canvas.drawText(text, start, end, x + horizontalPadding, y.toFloat(), paint)
        paint.color = oldColor
        paint.typeface = oldTypeface
        paint.style = oldStyle
    }
}

internal class RoundedBlockQuoteSpan(
    private val margin: Float,
    private val barWidth: Float,
    private val radius: Float,
    private val color: Int,
) : android.text.style.LeadingMarginSpan {
    override fun getLeadingMargin(first: Boolean): Int = margin.roundToInt()

    override fun drawLeadingMargin(
        canvas: android.graphics.Canvas,
        paint: android.graphics.Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        layout: android.text.Layout,
    ) {
        val oldColor = paint.color
        val left = if (dir > 0) x.toFloat() else x - barWidth
        paint.color = color
        canvas.drawRoundRect(left, top.toFloat(), left + barWidth, bottom.toFloat(), radius, radius, paint)
        paint.color = oldColor
    }
}

/**
 * Rounded multi-line code block background for the Markwon rich-text path. Replaces Markwon's
 * default `CodeBlockSpan`, which fills each line with a hard-edged rectangle. The span keeps the
 * monospace metrics and left indent but paints one pill-shaped background across the whole block:
 * the first line rounds its top corners, the last line its bottom corners, inner lines stay
 * rectangular, and adjacent lines overlap so the block reads as a single rounded card.
 */
internal class RoundedCodeBlockSpan(
    private val margin: Float,
    private val radius: Float,
    private val backgroundColor: Int,
    private val paddingHorizontal: Float,
    private val paddingVertical: Float,
) : android.text.style.MetricAffectingSpan(), android.text.style.LeadingMarginSpan {

    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    override fun updateMeasureState(textPaint: android.text.TextPaint) = applyTypeface(textPaint)
    override fun updateDrawState(textPaint: android.text.TextPaint) = applyTypeface(textPaint)

    private fun applyTypeface(textPaint: android.text.TextPaint) {
        textPaint.typeface = android.graphics.Typeface.MONOSPACE
    }

    override fun getLeadingMargin(first: Boolean): Int = margin.roundToInt()

    override fun drawLeadingMargin(
        canvas: android.graphics.Canvas,
        paint: android.graphics.Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        layout: android.text.Layout,
    ) {
        // The line's char range reveals whether this is the block's first/last line, letting the
        // background round only the outer corners.
        val spanned = text as? android.text.Spanned
        val spanStart = spanned?.getSpanStart(this) ?: start
        val spanEnd = spanned?.getSpanEnd(this) ?: end
        if (spanStart < 0) return
        val isFirstLine = start <= spanStart
        val isLastLine = end >= spanEnd
        val left = (if (dir > 0) x.toFloat() else x - margin) - paddingHorizontal
        val right = canvas.width.toFloat() + paddingHorizontal
        val path = android.graphics.Path()
        path.addRoundRect(
            android.graphics.RectF(left, top - paddingVertical, right, bottom + paddingVertical),
            floatArrayOf(
                if (isFirstLine) radius else 0f, if (isFirstLine) radius else 0f,
                if (isFirstLine) radius else 0f, if (isFirstLine) radius else 0f,
                if (isLastLine) radius else 0f, if (isLastLine) radius else 0f,
                if (isLastLine) radius else 0f, if (isLastLine) radius else 0f,
            ),
            android.graphics.Path.Direction.CW,
        )
        paint.style = android.graphics.Paint.Style.FILL
        paint.color = backgroundColor
        canvas.drawPath(path, paint)
    }
}

internal data class MarkdownViewBindingTag(
    val themeKey: String,
    val sourceHash: Int,
    val sourceLength: Int,
    val rendered: Boolean,
)

@Composable
internal fun RichMarkdownText(
    text: String,
    onQuoteSelection: ((String) -> Unit)? = null,
) {
    val needsRichRenderer = remember(text) { NativeUiRenderSafety.requiresRichMarkdown(text) }
    val context = LocalContext.current
    val language = LocalNativeLanguage.current
    val colors = LocalFcodeMarkdownColors.current
    val chatFontScale = LocalFcodeChatFontScale.current.coerceIn(0.5f, 2f)
    val fontScaleKey = (chatFontScale * 100f).roundToInt()
    val themeKey = "${colors.cacheKey}|chat-font-$fontScaleKey"
    val onSelectionActivityChanged = LocalTextSelectionActivityChanged.current
    val markwon: Markwon? = if (needsRichRenderer) {
        remember(context.applicationContext, themeKey) {
            NativeMarkdownRenderer.get(context.applicationContext, colors, chatFontScale)
        }
    } else null
    val parsed: android.text.Spanned? = if (markwon != null) {
        val result by produceState<android.text.Spanned?>(
            initialValue = NativeMarkdownRenderer.cached(themeKey, text),
            key1 = MarkdownRenderCacheKey(themeKey, text),
            key2 = markwon,
        ) {
            if (value == null) {
                value = withContext(Dispatchers.Default) {
                    // Malformed model output must fall back to the visible plain source.
                    runCatching { NativeMarkdownRenderer.render(markwon, themeKey, text) }.getOrNull()
                }
            }
        }
        result
    } else null
    val renderedTag = MarkdownViewBindingTag(themeKey, text.hashCode(), text.length, rendered = true)
    val plainTag = renderedTag.copy(rendered = false)
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { viewContext ->
            FcodeSelectableTextView(viewContext).apply {
                includeFontPadding = false
                textSize = 15.5f
                letterSpacing = if (resources.configuration.locales[0].language == "zh") 0f else 0.0025f
                setLineSpacing(resources.displayMetrics.density * 2f, 1.12f)
                val horizontal = (3f * resources.displayMetrics.density).roundToInt()
                val vertical = (2f * resources.displayMetrics.density).roundToInt()
                setPadding(horizontal, vertical, horizontal, vertical)
                breakStrategy = android.text.Layout.BREAK_STRATEGY_SIMPLE
                hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
                linksClickable = true
                restingMovementMethod = android.text.method.LinkMovementMethod.getInstance()
                movementMethod = restingMovementMethod
                setTextColor(colors.text.toArgb())
                this.text = text
                tag = plainTag
            }
        },
        onReset = { view ->
            view.finishSelection()
            view.text = ""
            view.tag = null
        },
        update = { view ->
            view.copyLabel = nativeText(language, "\u590d\u5236", "Copy")
            view.quoteLabel = nativeText(language, "\u5f15\u7528", "Quote")
            view.selectAllLabel = nativeText(language, "\u5168\u9009", "Select all")
            view.onSelectionActivityChanged = onSelectionActivityChanged
            view.onQuoteSelection = onQuoteSelection
            view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15.5f * chatFontScale)
            view.setTextColor(colors.text.toArgb())
            val rendered = parsed
            if (rendered != null && markwon != null && view.tag != renderedTag) {
                view.finishSelection()
                val applied = runCatching { markwon.setParsedMarkdown(view, rendered) }.isSuccess
                if (applied) view.tag = renderedTag else {
                    view.text = text
                    view.tag = plainTag
                }
            } else if (rendered == null && view.tag != plainTag) {
                view.finishSelection()
                view.text = text
                view.tag = plainTag
            }
        },
    )
}

@Composable
internal fun MarkdownLikeText(text: String) {
    val colors = LocalFcodeMarkdownColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        markdownBlocks(text).forEach { block ->
            if (block.code) {
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = colors.codeBlockBackground) {
                    Text(
                        block.text,
                        modifier = Modifier.padding(14.dp),
                        color = colors.codeBlockText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            } else {
                Text(block.text, color = colors.text, style = MaterialTheme.typography.bodyLarge, lineHeight = 22.sp)
            }
        }
    }
}

