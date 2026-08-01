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

class NativeSettingsActivity : ComponentActivity() {
    private var resumeRevision by mutableIntStateOf(0)
    private var hasResumedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 34) {
            // Keep Activity motion in the same choreography as the Compose gesture below. The
            // root page owns its own live chat preview, so a second platform close animation
            // would create a visible handoff seam.
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                R.anim.codex_settings_enter,
                R.anim.codex_chat_hold,
            )
            // A short window fade (instead of an instant removal) keeps the chat preview
            // visible while the paused chat window underneath resumes and draws its first
            // frame, closing the one-frame gap that flashed on finish().
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, R.anim.codex_settings_close_exit)
        }
        enableEdgeToEdge()
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        applyNativeStatusBarVisibility(prefs.getBoolean(NATIVE_HIDE_STATUS_BAR_PREFERENCE, false))
        val providerStore = CodexProviderStore(prefs)
        setContent {
            val navigator = remember { NativeSettingsNavigator() }
            val settingsScope = rememberCoroutineScope()
            val page = navigator.page
            val editingProfileId = navigator.editingProfileId
            val editingMcpKey = navigator.editingMcpKey
            var predictiveBackSource by remember { mutableStateOf<SettingsPage?>(null) }
            var predictiveBackPage by remember { mutableStateOf<SettingsPage?>(null) }
            var predictiveBackToChat by remember { mutableStateOf(false) }
            var predictiveBackHandoff by remember { mutableStateOf(false) }
            var suppressNextPageTransition by remember { mutableStateOf(false) }
            var providerRevision by remember { mutableIntStateOf(0) }
            val providerSnapshot = remember(providerRevision) { providerStore.snapshot() }
            var theme by remember { mutableStateOf(FcodeAppearancePreferences.normalizeColorMode(prefs.getString(KEY_THEME, "system"))) }
            var colorPalette by remember { mutableStateOf(FcodeColorPalette.from(prefs.getString(FcodeAppearancePreferences.COLOR_PALETTE, FcodeColorPalette.ROSE.value)).value) }
            var interfaceStyle by remember { mutableStateOf(FcodeInterfaceStyle.from(prefs.getString(FcodeAppearancePreferences.INTERFACE_STYLE, FcodeInterfaceStyle.MATERIAL.value)).value) }
            var chatBackground by remember { mutableStateOf(FcodeChatBackgroundStyle.from(prefs.getString(FcodeAppearancePreferences.CHAT_BACKGROUND, FcodeChatBackgroundStyle.THEME.value)).value) }
            var chatBackgroundImage by remember { mutableStateOf(prefs.getString(FcodeAppearancePreferences.CHAT_BACKGROUND_IMAGE, "").orEmpty()) }
            var chatBackgroundDim by remember { mutableFloatStateOf(prefs.getFloat(FcodeAppearancePreferences.CHAT_BACKGROUND_DIM, 0.32f).coerceIn(0f, 0.72f)) }
            var chatDynamicBackground by remember { mutableStateOf(readFcodeChatDynamicBackgroundConfig(this@NativeSettingsActivity)) }
            var language by remember { mutableStateOf(prefs.getString(KEY_LANGUAGE, "system").orEmpty()) }
            var animations by remember { mutableStateOf(prefs.getBoolean(KEY_STREAM_ANIMATIONS, true)) }
            var fixedStreamingViewport by remember { mutableStateOf(prefs.getBoolean(KEY_STREAM_FIXED_VIEWPORT, true)) }
            var reasoning by remember { mutableStateOf(prefs.getBoolean(KEY_SHOW_REASONING, true)) }
            var follow by remember { mutableStateOf(prefs.getBoolean(KEY_AUTO_FOLLOW, true)) }
            var showResponseStats by remember { mutableStateOf(prefs.getBoolean(KEY_SHOW_RESPONSE_STATS, true)) }
            var showModelSubtitle by remember { mutableStateOf(prefs.getBoolean(KEY_SHOW_MODEL_SUBTITLE, true)) }
            var showReasoningTitles by remember { mutableStateOf(prefs.getBoolean(KEY_SHOW_REASONING_TITLES, true)) }
            var hideStatusBar by remember { mutableStateOf(prefs.getBoolean(NATIVE_HIDE_STATUS_BAR_PREFERENCE, false)) }
            var compactComposerOnScroll by remember { mutableStateOf(prefs.getBoolean(FcodeAppearancePreferences.COMPACT_COMPOSER_ON_SCROLL, true)) }
            var chatFontScale by remember { mutableFloatStateOf(readFcodeChatFontScale(this@NativeSettingsActivity)) }
            var materialTransparency by remember { mutableStateOf(readFcodeMaterialTransparencyConfig(this@NativeSettingsActivity)) }
            var dialog by remember { mutableStateOf<String?>(null) }
            var missingCliFeature by remember { mutableStateOf<CodexDependentFeature?>(null) }
            var codexCliInstalled by remember { mutableStateOf(isCodexCliInstalled()) }
            val lang = resolveLanguage(language)
            val requestCodexFeature: (CodexDependentFeature) -> Unit = { feature ->
                if (codexCliInstalled || isCodexCliInstalled()) {
                    codexCliInstalled = true
                    when (feature) {
                        CodexDependentFeature.WEB_UI -> openWebUi()
                        CodexDependentFeature.TERMUX -> openTermux()
                        CodexDependentFeature.SETUP -> Unit
                    }
                } else {
                    missingCliFeature = feature
                }
            }
            val navigateBack = {
                if (!navigator.navigateBack()) finish()
            }
            val updateMaterialTransparency: (FcodeMaterialTransparencyConfig) -> Unit = { value ->
                materialTransparency = value.normalized()
                prefs.edit()
                    .putFloat(FcodeAppearancePreferences.MATERIAL_USER_BUBBLE_ALPHA, materialTransparency.userBubbleAlpha)
                    .putFloat(FcodeAppearancePreferences.MATERIAL_ACTIVITY_ALPHA, materialTransparency.activityAlpha)
                    .putFloat(FcodeAppearancePreferences.MATERIAL_COMPOSER_ALPHA, materialTransparency.composerAlpha)
                    .apply()
            }
            FcodeChatTheme(
                theme, lang, animations, reasoning, follow,
                colorPalette = colorPalette,
                interfaceStyle = interfaceStyle,
                chatBackground = chatBackground,
                chatBackgroundImage = chatBackgroundImage,
                chatBackgroundDim = chatBackgroundDim,
                chatDynamicBackground = chatDynamicBackground,
                showResponseStats = showResponseStats,
                showModelSubtitle = showModelSubtitle,
                showReasoningTitles = showReasoningTitles,
                chatFontScale = chatFontScale,
                materialTransparency = materialTransparency,
            ) {
                val settingsBackplate = MaterialTheme.colorScheme.surfaceContainer
                val settingsPageShape = remember { RoundedCornerShape(28.dp) }
                val predictiveBackProgress = remember { androidx.compose.animation.core.Animatable(0f) }
                val predictiveBackHandoffProgress = remember { androidx.compose.animation.core.Animatable(1f) }
                var predictiveBackHandoffStartProgress by remember { mutableFloatStateOf(1f) }
                var predictiveBackFromLeft by remember { mutableStateOf(true) }
                val latestPage by rememberUpdatedState(page)
                // Always register root back as well as child-page back. The chat snapshot is
                // optional presentation data; a late/failed capture must never disable the
                // gesture that finishes settings and returns to the live chat Activity.
                PredictiveBackHandler(enabled = true) { progress ->
                    val source = latestPage
                    val destination = source.previousPage ?: SettingsPage.ROOT
                    val returningToChat = destination == SettingsPage.ROOT && source == SettingsPage.ROOT
                    predictiveBackProgress.snapTo(0f)
                    predictiveBackHandoff = false
                    predictiveBackHandoffProgress.snapTo(1f)
                    predictiveBackSource = source
                    predictiveBackToChat = returningToChat
                    predictiveBackPage = destination.takeUnless { returningToChat }
                    try {
                        progress.collect { event ->
                            predictiveBackFromLeft = event.swipeEdge == BackEventCompat.EDGE_LEFT
                            predictiveBackProgress.snapTo(event.progress)
                        }
                        // Continue from the finger's presentation value. A very short finish
                        // closes the small gap left by gestures released just before 100%.
                        withContext(NonCancellable) {
                            // Sweep the page off-screen immediately from wherever the finger
                            // released. The destination settles to its resting state in
                            // parallel. The old "animate progress to 100% first, then hand
                            // off" ordering caused a visible ~240ms stall before the slide
                            // began.
                            predictiveBackHandoffStartProgress = predictiveBackProgress.value.coerceIn(0.05f, 1f)
                            predictiveBackHandoff = true
                            predictiveBackSource = null
                            predictiveBackHandoffProgress.snapTo(0f)
                            coroutineScope {
                                launch {
                                    predictiveBackProgress.animateTo(
                                        targetValue = 1f,
                                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                                    )
                                }
                                launch {
                                    predictiveBackHandoffProgress.animateTo(
                                        targetValue = 1f,
                                        animationSpec = tween(
                                            if (returningToChat) 320 else 300,
                                            easing = FastOutSlowInEasing,
                                        ),
                                    )
                                }
                            }
                            if (returningToChat) {
                                // The chat Activity is paused underneath us. Finish only after
                                // the captured chat surface has fully replaced settings, avoiding
                                // a second platform close animation or a sudden disappearance.
                                finish()
                            } else {
                                // The destination has already been painted underneath for the
                                // entire handoff. Commit the route after the slide-out so the
                                // AnimatedContent swap itself is visually invisible. The
                                // destination layer is intentionally kept for one extra frame
                                // (removed by the LaunchedEffect above); dropping it in this
                                // same frame as the route swap exposed the Activity backplate
                                // for a single frame on slower GPUs.
                                suppressNextPageTransition = true
                                navigator.navigate(destination)
                                predictiveBackToChat = false
                                predictiveBackHandoff = false
                                predictiveBackProgress.snapTo(0f)
                                predictiveBackHandoffProgress.snapTo(1f)
                            }
                        }
                    } catch (_: CancellationException) {
                        // A cancelled gesture settles from its current presentation value.
                        withContext(NonCancellable) {
                            predictiveBackProgress.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(dampingRatio = 1f, stiffness = 360f),
                            )
                            predictiveBackSource = null
                            predictiveBackPage = null
                            predictiveBackToChat = false
                            predictiveBackHandoff = false
                            predictiveBackHandoffProgress.snapTo(1f)
                        }
                    }
                }
                LaunchedEffect(page) {
                    if (suppressNextPageTransition) {
                        // Bridge the AnimatedContent route swap. Its enter/exit animations tick
                        // one frame AFTER the state change, so on the swap frame the outgoing
                        // page would otherwise be painted once at its resting position (a
                        // visible flash). Keep this layer's sibling hidden (alpha 0 above) and
                        // the already-painted destination visible for two frames, until the new
                        // target is guaranteed fully entered.
                        withFrameNanos { }
                        withFrameNanos { }
                        suppressNextPageTransition = false
                        if (predictiveBackPage == page && !predictiveBackToChat) {
                            predictiveBackPage = null
                        }
                    }
                }
                val settingsPageContent: @Composable (SettingsPage) -> Unit = { target ->
                    when (target) {
                        SettingsPage.ROOT -> SettingsRootPage(
                            lang, providerSnapshot.active,
                            onBack = { finish() },
                            onModels = { navigator.navigate(SettingsPage.MODEL_CONFIGS) },
                            onWebUi = { navigator.navigate(SettingsPage.WEB_UI) },
                            onTermux = { requestCodexFeature(CodexDependentFeature.TERMUX) },
                            onPinWebUiShortcut = { FcodeLauncherShortcuts.pinWebUi(this@NativeSettingsActivity, lang) },
                            onPinTermuxShortcut = { FcodeLauncherShortcuts.pinTermux(this@NativeSettingsActivity, lang) },
                            onInstallCodexCli = { requestCodexFeature(CodexDependentFeature.SETUP) },
                            codexCliInstalled = codexCliInstalled,
                            onProxy = { navigator.navigate(SettingsPage.PROXY) },
                            onOverlay = { navigator.navigate(SettingsPage.OVERLAY) },
                            onDevelopmentTools = { navigator.navigate(SettingsPage.DEVELOPMENT_TOOLS) },
                            onAppearance = { navigator.navigate(SettingsPage.APPEARANCE) },
                            onMcp = { navigator.navigate(SettingsPage.MCP) },
                            onSkills = { navigator.navigate(SettingsPage.SKILLS) },
                            onLanguage = { dialog = "language" },
                            onTypography = { navigator.navigate(SettingsPage.TYPOGRAPHY) },
                            onDeveloper = { navigator.navigate(SettingsPage.DEVELOPER) },
                            environmentRevision = resumeRevision,
                            prefs = prefs,
                            onAbout = { navigator.navigate(SettingsPage.ABOUT) },
                        )
                        SettingsPage.APPEARANCE -> AppearanceSettingsPage(
                            lang = lang,
                            theme = theme,
                            colorPalette = colorPalette,
                            interfaceStyle = interfaceStyle,
                            onBack = navigateBack,
                            onTheme = { navigator.navigate(SettingsPage.THEME) },
                            onChat = { navigator.navigate(SettingsPage.CHAT_APPEARANCE) },
                        )
                        SettingsPage.THEME -> ThemeSettingsPage(
                            lang = lang,
                            colorMode = theme,
                            paletteValue = colorPalette,
                            interfaceStyleValue = interfaceStyle,
                            backgroundValue = chatBackground,
                            onBack = navigateBack,
                            onColorModeChange = { value ->
                                theme = FcodeAppearancePreferences.normalizeColorMode(value)
                                prefs.edit().putString(KEY_THEME, theme).apply()
                            },
                            onPaletteChange = { value ->
                                colorPalette = FcodeColorPalette.from(value).value
                                prefs.edit().putString(FcodeAppearancePreferences.COLOR_PALETTE, colorPalette).apply()
                            },
                            onInterfaceStyleChange = { value ->
                                interfaceStyle = FcodeInterfaceStyle.from(value).value
                                prefs.edit().putString(FcodeAppearancePreferences.INTERFACE_STYLE, interfaceStyle).apply()
                            },
                            onOpenChatBackground = { navigator.navigate(SettingsPage.CHAT_BACKGROUND) },
                        )
                        SettingsPage.CHAT_APPEARANCE -> ChatAppearanceSettingsPage(
                            lang = lang,
                            animations = animations,
                            fixedStreamingViewport = fixedStreamingViewport,
                            reasoning = reasoning,
                            follow = follow,
                            showResponseStats = showResponseStats,
                            showModelSubtitle = showModelSubtitle,
                            showReasoningTitles = showReasoningTitles,
                            hideStatusBar = hideStatusBar,
                            compactComposerOnScroll = compactComposerOnScroll,
                            onBack = navigateBack,
                            onAnimations = { animations = it; prefs.edit().putBoolean(KEY_STREAM_ANIMATIONS, it).apply() },
                            onFixedStreamingViewport = { fixedStreamingViewport = it; prefs.edit().putBoolean(KEY_STREAM_FIXED_VIEWPORT, it).apply() },
                            onReasoning = { reasoning = it; prefs.edit().putBoolean(KEY_SHOW_REASONING, it).apply() },
                            onFollow = { follow = it; prefs.edit().putBoolean(KEY_AUTO_FOLLOW, it).apply() },
                            onShowResponseStats = { showResponseStats = it; prefs.edit().putBoolean(KEY_SHOW_RESPONSE_STATS, it).apply() },
                            onShowModelSubtitle = { showModelSubtitle = it; prefs.edit().putBoolean(KEY_SHOW_MODEL_SUBTITLE, it).apply() },
                            onShowReasoningTitles = { showReasoningTitles = it; prefs.edit().putBoolean(KEY_SHOW_REASONING_TITLES, it).apply() },
                            onHideStatusBar = {
                                hideStatusBar = it
                                prefs.edit().putBoolean(NATIVE_HIDE_STATUS_BAR_PREFERENCE, it).apply()
                                applyNativeStatusBarVisibility(it)
                            },
                            onCompactComposerOnScroll = {
                                compactComposerOnScroll = it
                                prefs.edit().putBoolean(FcodeAppearancePreferences.COMPACT_COMPOSER_ON_SCROLL, it).apply()
                            },
                        )
                        SettingsPage.CHAT_BACKGROUND -> ChatBackgroundSettingsPage(
                            lang = lang,
                            selectedValue = chatBackground,
                            imagePath = chatBackgroundImage,
                            imageDim = chatBackgroundDim,
                            dynamicConfig = chatDynamicBackground,
                            onBack = navigateBack,
                            onSelected = { value ->
                                chatBackground = FcodeChatBackgroundStyle.from(value).value
                                prefs.edit().putString(FcodeAppearancePreferences.CHAT_BACKGROUND, chatBackground).apply()
                            },
                            onImageChanged = { path ->
                                chatBackgroundImage = path
                                prefs.edit().putString(FcodeAppearancePreferences.CHAT_BACKGROUND_IMAGE, path).apply()
                            },
                            onDimChanged = { value ->
                                chatBackgroundDim = value.coerceIn(0f, 0.72f)
                                prefs.edit().putFloat(FcodeAppearancePreferences.CHAT_BACKGROUND_DIM, chatBackgroundDim).apply()
                            },
                            onDynamicConfigChanged = { value ->
                                chatDynamicBackground = value
                                prefs.edit()
                                    .putBoolean(FcodeAppearancePreferences.CHAT_DYNAMIC_BACKGROUND_ENABLED, value.enabled)
                                    .putInt(FcodeAppearancePreferences.CHAT_DYNAMIC_BACKGROUND_DELAY_MS, value.autoStartDelayMs)
                                    .putInt(FcodeAppearancePreferences.CHAT_DYNAMIC_BACKGROUND_DURATION_MS, value.expansionDurationMs)
                                    .putFloat(FcodeAppearancePreferences.CHAT_DYNAMIC_BACKGROUND_BLUR_DP, value.initialBlurDp)
                                    .putFloat(FcodeAppearancePreferences.CHAT_DYNAMIC_BACKGROUND_REFRACTION_HEIGHT_DP, value.refractionHeightDp)
                                    .putFloat(FcodeAppearancePreferences.CHAT_DYNAMIC_BACKGROUND_REFRACTION_AMOUNT_DP, value.refractionAmountDp)
                                    .putBoolean(FcodeAppearancePreferences.CHAT_DYNAMIC_BACKGROUND_CHROMATIC, value.chromaticAberration)
                                    .apply()
                            },
                        )
                        SettingsPage.TYPOGRAPHY -> TypographySettingsPage(
                            lang = lang,
                            chatFontScale = chatFontScale,
                            interfaceStyle = FcodeInterfaceStyle.from(interfaceStyle),
                            transparency = materialTransparency,
                            onBack = navigateBack,
                            onChatFontScaleChange = { value ->
                                chatFontScale = value.coerceIn(0.5f, 2f)
                                prefs.edit().putFloat(FcodeAppearancePreferences.CHAT_FONT_SCALE, chatFontScale).apply()
                            },
                            onTransparencyChange = updateMaterialTransparency,
                        )
                        SettingsPage.DEVELOPMENT_TOOLS -> DevelopmentToolsSettingsPage(
                            lang = lang,
                            prefs = prefs,
                            onBack = navigateBack,
                        )
                        SettingsPage.OVERLAY -> OverlaySettingsPage(
                            lang = lang,
                            prefs = prefs,
                            onBack = navigateBack,
                        )
                        SettingsPage.MODEL_CONFIGS -> ModelConfigurationsPage(
                            lang, providerSnapshot.profiles, providerSnapshot.active?.id, providerRevision,
                            onBack = navigateBack,
                            onAdd = { navigator.openProfileEditor(null) },
                            onEdit = { navigator.openProfileEditor(it) },
                            onActivate = { profile ->
                                settingsScope.launch {
                                    withContext(kotlinx.coroutines.Dispatchers.IO) { providerStore.activate(profile) }
                                    providerRevision++
                                }
                            },
                        )
                        SettingsPage.MODEL_EDITOR -> key(editingProfileId, providerRevision) {
                            ModelConfigurationEditor(
                                lang, editingProfileId?.let(providerSnapshot::find), providerStore,
                                onBack = navigateBack,
                                onSaved = { providerRevision++; navigator.finishProfileEditor() },
                                onDeleted = { providerRevision++; navigator.finishProfileEditor() },
                            )
                        }
                        SettingsPage.MCP -> McpSettingsPage(
                            lang = lang,
                            prefs = prefs,
                            onBack = navigateBack,
                            onAdd = { navigator.openMcpEditor(null) },
                            onEdit = { navigator.openMcpEditor(it) },
                        )
                        SettingsPage.MCP_EDITOR -> McpServerEditorPage(
                            lang = lang,
                            existingKey = editingMcpKey,
                            prefs = prefs,
                            onBack = navigateBack,
                            onSaved = { navigator.finishMcpEditor() },
                            onDeleted = { navigator.finishMcpEditor() },
                        )
                        SettingsPage.SKILLS -> SkillsSettingsPage(lang, prefs, navigateBack)
                        SettingsPage.PROXY -> ProxySettingsPage(
                            lang = lang,
                            prefs = prefs,
                            onBack = navigateBack,
                            onOpenDashboard = { startActivity(Intent(this@NativeSettingsActivity, MihomoDashboardActivity::class.java)) },
                        )
                        SettingsPage.WEB_UI -> WebUiSettingsPage(
                            lang, prefs, providerSnapshot.active, navigateBack,
                            onOpenWebUi = { requestCodexFeature(CodexDependentFeature.WEB_UI) },
                            onOpenDashboard = { startActivity(Intent(this@NativeSettingsActivity, MihomoDashboardActivity::class.java)) },
                            onClearCache = { resetWebUiPreferences(lang) },
                        )
                        SettingsPage.DEVELOPER -> DeveloperSettingsPage(lang, navigateBack)
                        SettingsPage.ABOUT -> AboutSettingsPage(
                            lang = lang,
                            onBack = navigateBack,
                            onCheckUpdates = {
                                AppUpdateManager.checkForUpdates(
                                    this@NativeSettingsActivity,
                                    lang,
                                    userInitiated = true,
                                )
                            },
                            onOpenRepository = {
                                runCatching {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FcodeAboutInfo.REPOSITORY_URL)))
                                }.onFailure {
                                    Toast.makeText(
                                        this@NativeSettingsActivity,
                                        tr(lang, "无法打开 GitHub 仓库", "Unable to open the GitHub repository"),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        )
                    }
                }
                val chatPreview = if (predictiveBackToChat) NativeSettingsBackPreview.current() else null
                val backplateDrawable = remember(chatPreview, settingsBackplate) {
                    chatPreview?.let { preview ->
                        BitmapDrawable(resources, preview).apply {
                            gravity = android.view.Gravity.FILL
                        }
                    } ?: ColorDrawable(settingsBackplate.toArgb())
                }
                // Use the same captured surface as the Activity fallback. If Android removes the
                // window between the last Compose frame and the resumed chat Activity, the exposed
                // pixel is still the expected destination instead of a colored flash.
                LaunchedEffect(backplateDrawable) {
                    window.setBackgroundDrawable(backplateDrawable)
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(if (chatPreview == null) MaterialTheme.colorScheme.surfaceContainerHighest else androidx.compose.ui.graphics.Color.Transparent),
                ) {
                    chatPreview?.let { preview ->
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    val gestureActive = predictiveBackSource != null || predictiveBackHandoff
                    predictiveBackPage?.let { destination ->
                        // The destination is a real, independently composed page. The current
                        // AnimatedContent stays alive above it, preserving scroll/input state while
                        // its layer follows the user's finger.
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    // Rest at the settled state whenever the gesture is not
                                    // live, so the layer never snaps back to its peek transform
                                    // after the route commits.
                                    val progress = if (gestureActive) predictiveBackProgress.value else 1f
                                    val direction = if (predictiveBackFromLeft) 1f else -1f
                                    val destinationScale = 0.982f + 0.018f * progress
                                    translationX = -size.width * 0.025f * (1f - progress) * direction
                                    scaleX = destinationScale
                                    scaleY = destinationScale
                                    alpha = 0.84f + 0.16f * progress
                                }
                                .background(settingsBackplate),
                        ) {
                            settingsPageContent(destination)
                        }
                    }
                    AnimatedContent(
                        targetState = page,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val progress = if (gestureActive) predictiveBackProgress.value else 0f
                                val direction = if (predictiveBackFromLeft) 1f else -1f
                                if (predictiveBackHandoff) {
                                    // Once the gesture commits, sweep the page off the left
                                    // edge of the screen instead of cross-fading it away. The
                                    // slide starts exactly where the finger left the page, so
                                    // the motion stays continuous with no settle stall.
                                    val handoff = predictiveBackHandoffProgress.value
                                    val startP = predictiveBackHandoffStartProgress
                                    val startX = size.width * 0.16f * startP * direction
                                    val endX = -size.width * 1.1f
                                    translationX = startX + (endX - startX) * handoff
                                    translationY = size.height * 0.008f * startP * (1f - handoff)
                                    val handoffScale = 1f - 0.045f * startP
                                    scaleX = handoffScale
                                    scaleY = handoffScale
                                    alpha = 1f
                                } else {
                                    // Leave enough room for the destination to remain legible
                                    // beside Android's own edge-back indicator, not merely a
                                    // few pixels.
                                    translationX = size.width * 0.16f * progress * direction
                                    translationY = size.height * 0.008f * progress
                                    scaleX = 1f - 0.045f * progress
                                    scaleY = 1f - 0.045f * progress
                                    alpha = 1f
                                }
                                // While the route swap is settling, keep this layer invisible;
                                // the already-painted destination layer underneath shows through,
                                // so the AnimatedContent swap can never flash the outgoing page.
                                if (suppressNextPageTransition) alpha = 0f
                                transformOrigin = TransformOrigin(if (predictiveBackFromLeft) 0.35f else 0.65f, 0.5f)
                                // Corners and elevation appear only after the page separates
                                // from the display edges, preserving the resting layout.
                                shape = settingsPageShape
                                clip = predictiveBackHandoff || progress > 0.001f
                                shadowElevation = 8.dp.toPx() * (if (predictiveBackHandoff) 1f else progress)
                                ambientShadowColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.08f)
                                spotShadowColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.1f)
                            }
                            .background(settingsBackplate),
                        transitionSpec = {
                            if (suppressNextPageTransition) {
                                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                            } else {
                                val forward = targetState.navigationDepth > initialState.navigationDepth
                                val goingBack = targetState.navigationDepth < initialState.navigationDepth
                                val enterOffset: (Int) -> Int = { width -> if (forward) width * 2 / 5 else -width * 2 / 5 }
                                val exitOffset: (Int) -> Int = { width -> if (forward) -width / 5 else width / 5 }
                                // One shared emphasized curve drives every property so the two
                                // pages settle as a single physical motion. The incoming page
                                // starts partially opaque (never a ghost) and the outgoing page
                                // only dims while it is still mostly covered.
                                val motionEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
                                (fadeIn(tween(200, easing = FastOutSlowInEasing), initialAlpha = 0.4f) +
                                    slideInHorizontally(
                                        animationSpec = tween(340, easing = motionEasing),
                                        initialOffsetX = enterOffset,
                                    ) +
                                    scaleIn(
                                        initialScale = if (goingBack) 0.97f else 0.996f,
                                        animationSpec = tween(340, easing = motionEasing),
                                    )).togetherWith(
                                    fadeOut(tween(260, easing = FastOutSlowInEasing), targetAlpha = 0.25f) +
                                        slideOutHorizontally(
                                            animationSpec = tween(340, easing = motionEasing),
                                            targetOffsetX = exitOffset,
                                        ) +
                                        scaleOut(
                                            targetScale = if (goingBack) 0.985f else 0.992f,
                                            animationSpec = tween(340, easing = motionEasing),
                                        ),
                                )
                            }
                        },
                        label = "settingsPage",
                    ) { target ->
                        settingsPageContent(target)
                    }
                }
                when (dialog) {
                    "language" -> SettingsChoiceDialog(
                        tr(lang, "选择语言", "Choose language"),
                        listOf("system" to tr(lang, "跟随系统", "System"), "zh" to "简体中文", "en" to "English"),
                        language, { dialog = null },
                    ) { language = it; prefs.edit().putString(KEY_LANGUAGE, it).apply(); dialog = null }
                }
                missingCliFeature?.let { feature ->
                    MissingCodexCliDialog(
                        lang = lang,
                        feature = feature,
                        onDismiss = { missingCliFeature = null },
                        onInstall = {
                            missingCliFeature = null
                            CodexInstaller.setupBootstrapIfNeeded(this@NativeSettingsActivity) {
                                codexCliInstalled = isCodexCliInstalled()
                                when (feature) {
                                    CodexDependentFeature.WEB_UI -> openWebUi()
                                    CodexDependentFeature.TERMUX -> openTermux()
                                    CodexDependentFeature.SETUP -> Unit
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasResumedOnce) resumeRevision++ else hasResumedOnce = true
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        applyNativeStatusBarVisibility(prefs.getBoolean(NATIVE_HIDE_STATUS_BAR_PREFERENCE, false))
        AppUpdateManager.clearLegacyDownloadState(this)
    }

    private fun applyNativeStatusBarVisibility(hidden: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (hidden) controller.hide(WindowInsetsCompat.Type.statusBars())
        else controller.show(WindowInsetsCompat.Type.statusBars())
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT < 34) {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.codex_chat_reenter, R.anim.codex_settings_exit)
        }
    }

    private fun isCodexCliInstalled(): Boolean =
        File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "codex").canExecute()

    private fun openWebUi() {
        startActivity(FcodeToolNavigation.webUiIntent(this))
    }

    private fun openTermux() {
        startActivity(FcodeToolNavigation.termuxIntent(this))
    }

    private fun resetWebUiPreferences(lang: String) {
        getSharedPreferences("codex_desktop_bridge", MODE_PRIVATE).edit().clear().apply()
        WebView(this).also { it.clearCache(true); it.destroy() }
        Toast.makeText(this, tr(lang, "WebUI 偏好和缓存已重置", "WebUI preferences and cache reset"), Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val PREFS_NAME = "codex_mobile"
        const val KEY_THEME = "native_theme_mode_v1"
        const val KEY_LANGUAGE = "native_language_v1"
        const val KEY_STREAM_ANIMATIONS = "native_stream_animations_v1"
        const val KEY_STREAM_FIXED_VIEWPORT = "native_stream_fixed_viewport_v1"
        const val KEY_SHOW_REASONING = "native_show_reasoning_v1"
        const val KEY_AUTO_FOLLOW = "native_auto_follow_v1"
        const val KEY_SHOW_RESPONSE_STATS = "native_show_response_stats_v1"
        const val KEY_SHOW_MODEL_SUBTITLE = "native_show_model_subtitle_v1"
        const val KEY_SHOW_REASONING_TITLES = "native_show_reasoning_titles_v1"
    }
}
