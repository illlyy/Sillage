package com.termux.app;

import android.R;
import android.Manifest;
import android.animation.StateListAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.button.MaterialButton;
import com.termux.BuildConfig;
import com.termux.app.CodexProviderStore;
import com.termux.app.MihomoControllerClient;
import com.termux.app.MihomoManager;
import com.termux.shared.termux.TermuxConstants;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* loaded from: D:\Androiddevev\projects\codex-mobile\app\build\intermediates\project_dex_archive\debug\out\com\termux\app\CodexHomeActivity.dex */
public final class CodexHomeActivity extends Activity {
    static final String ACTION_OPEN_WEBUI = "com.ilyop.codex.OPEN_WEBUI";
    static final String ACTION_OPEN_TERMUX = "com.ilyop.codex.OPEN_TERMUX";
    private static final int OVERLAY_PERMISSION_REQUEST = 4111;
    private static final int STORAGE_PERMISSION_REQUEST = 4112;
    private ScrollView overlaySettingsPage;
    private ScrollView aboutPage;
    private View topNoticeView;
    private Runnable hideTopNoticeRunnable;
    private boolean mihomoDelayTesting;
    private String mihomoDelayTestingGroup = "";
    private boolean appServerUsesMihomo;
    private boolean mihomoButtonBusy;
    private boolean mihomoLaunchStarting;
    private final ArrayList<Runnable> mihomoLaunchQueue = new ArrayList<>();
    private String pendingLaunchAction;
    private String pendingSharePayload;
    private boolean pendingShareNewConversation;
    private boolean editorProxyEnabled, editorProxyWebUi, editorProxyTermux;
    private boolean editorForwardReasoningContext;
    private boolean editorCustomSubagentStability;

    void onCodexTaskCompleted() { CodexOverlayService.notifyTaskCompleted(this); }
    private static final int FILE_CHOOSER_REQUEST = 4107;
    private static final int MIHOMO_CONFIG_REQUEST = 4109;
    private static final String MODEL_CATALOG_FILENAME = "ilyop-model-catalog.json";
    private static final int RAISED = -1;
    private static LocalApiProxy terminalApiProxy;
    private EditText apiKey;
    private CodexAppServerBridge appServerBridge;
    private boolean appServerStarted;
    private EditText baseUrl;
    private MaterialButton configFab;
    private ScrollView configPage;
    private LinearLayout configPanel;
    private FrameLayout contentHost;
    private CodexDesktopBridge desktopBridge;
    private LinearLayout drawer;
    private boolean drawerOpen;
    private View drawerScrim;
    private View homePage;
    private LinearLayout mainShell;
    private LinearLayout mihomoBottomNav;
    private MihomoControllerClient mihomoController;
    private Button mihomoDashboardButton;
    private HorizontalScrollView mihomoGroupScroll;
    private LinearLayout mihomoGroupTabs;
    private Button mihomoInstallButton;
    private List<MihomoControllerClient.ProxyGroup> mihomoLoadedGroups = new ArrayList();
    private MihomoManager mihomoManager;
    private View mihomoPage;
    private FrameLayout mihomoProxyBody;
    private LinearLayout mihomoProxyGroupHeader;
    private TextView mihomoProxyGroupTitle;
    private ListView mihomoProxyList;
    private boolean mihomoProxyLoading;
    private TextView mihomoProxySelectedNode;
    private LinearLayout mihomoProxySortRow;
    private Button mihomoProxyTestButton;
    private TextView mihomoRuntimeDetails;
    private int mihomoSelectedTab;
    private Button mihomoStartStopButton;
    private TextView mihomoStatus;
    private FrameLayout mihomoTabHost;
    private EditText model;
    private TextView onboardingRuntimeState;
    private LinearLayout onboardingView;
    private TextView pageTitle;
    private ValueCallback<Uri[]> pendingFileChooser;
    private SharedPreferences prefs;
    private ProgressBar progress;
    private boolean providerEditorOpen;
    private CodexProviderStore providerStore;
    private FrameLayout root;
    private TextView runtimeState;
    private ScrollView settingsPage;
    private View splashView;
    private TextView status;
    private View topToolbar;
    private boolean webUiLoaded;
    private boolean webUiReloadPending;
    private long webUiConfigRevision;
    private int appServerRestartAttempts;
    private WebView webView;
    private static final int BG = Color.rgb(252, 238, 226);
    private static final int SURFACE = Color.rgb(255, 248, 242);
    private static final int TEXT = Color.rgb(31, 31, 31);
    private static final int MUTED = Color.rgb(95, 83, 73);
    private static final int BORDER = Color.rgb(226, 207, 191);
    private static final int GREEN = Color.rgb(42, 119, 81);
    private static final int AMBER = Color.rgb(145, 94, 21);

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: D:\Androiddevev\projects\codex-mobile\app\build\intermediates\project_dex_archive\debug\out\com\termux\app\CodexHomeActivity$ControllerAction.dex */
    interface ControllerAction {
        void run() throws Exception;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: D:\Androiddevev\projects\codex-mobile\app\build\intermediates\project_dex_archive\debug\out\com\termux\app\CodexHomeActivity$MihomoAction.dex */
    interface MihomoAction {
        void run() throws Exception;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: D:\Androiddevev\projects\codex-mobile\app\build\intermediates\project_dex_archive\debug\out\com\termux\app\CodexHomeActivity$ModelsCallback.dex */
    interface ModelsCallback {
        void complete(ArrayList<CodexProviderStore.ModelConfig> models, String error);
    }

    @Override // android.app.Activity
    public void onCreate(Bundle state) {
        super.onCreate(state);
        configureWindow();
        this.prefs = getSharedPreferences("codex_mobile", 0);
        captureLaunchIntent(getIntent());
        this.providerStore = new CodexProviderStore(this.prefs);
        this.mihomoManager = MihomoManager.get(this);
        this.mihomoController = new MihomoControllerClient(this.mihomoManager);
        writeModelCatalogFile(this.providerStore.active());
        try {
            ChatCompletionsAdapter.selfTest();
            Log.i("IlyopApiProxy", "Chat adapter self-test passed");
        } catch (Exception e) {
            Log.e("IlyopApiProxy", "Chat adapter self-test failed", e);
        }
        new File("/data/data/com.ilyop.codex/xhome", ".codex/skills").mkdirs();
        try { applyConfiguredProjectRoot(); } catch (IOException e) { Log.e("IlyopProject", "\u65e0\u6cd5\u5e94\u7528\u9ed8\u8ba4\u9879\u76ee\u76ee\u5f55", e); }
        new File("/data/data/com.ilyop.codex/xhome", "projects").mkdirs();
        buildUi();
        if (this.prefs.getBoolean("overlay_enabled", false)) {
            if (android.provider.Settings.canDrawOverlays(this)) CodexOverlayService.start(this);
            else this.prefs.edit().putBoolean("overlay_enabled", false).apply();
        }
        refreshRuntimeState();
        animateSplashLogo();
        if (this.prefs.getBoolean("mihomo_auto_start", false) && this.mihomoManager.isInstalled()) {
            new Thread(new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$YdunMXBTJc-3T3JFYdN1-IuYCSE
                @Override // java.lang.Runnable
                public final void run() {
                    CodexHomeActivity.this.lambda$onCreate$0$CodexHomeActivity();
                }
            }, "MihomoAutoStart").start();
        }
        this.root.postDelayed(new Runnable() { // from class: com.termux.app.CodexHomeActivity.1
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.routeAfterSplash();
            }
        }, 850L);
        this.root.postDelayed(this::requestStartupPermissions, 1600L);
    }

    public /* synthetic */ void lambda$onCreate$0$CodexHomeActivity() {
        try {
            this.mihomoManager.start();
        } catch (Exception error) {
            Log.e("IlyopMihomo", "Auto-start failed", error);
        }
        runOnUiThread(new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$cNaZMjCp-KuRBWpFel1hQj9XPgo
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.refreshMihomoUi();
            }
        });
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        if (this.runtimeState != null) {
            refreshRuntimeState();
        }
        refreshMihomoUi();
        if (this.prefs != null && this.prefs.getBoolean("overlay_enabled", false) && !Settings.canDrawOverlays(this)) {
            this.prefs.edit().putBoolean("overlay_enabled", false).apply(); CodexOverlayService.stop(this);
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        captureLaunchIntent(intent);
        if (this.root != null) this.root.postDelayed(this::handlePendingLaunchAction, 140L);
    }

    private void captureLaunchIntent(Intent intent) {
        this.pendingLaunchAction = intent == null ? null : intent.getAction();
        if (intent != null && (CodexShareActivity.ACTION_SHARE_TO_CURRENT_UI.equals(this.pendingLaunchAction)
            || CodexShareActivity.ACTION_SHARE_TO_NEW_UI.equals(this.pendingLaunchAction))) {
            this.pendingSharePayload = intent.getStringExtra(CodexShareActivity.EXTRA_SHARE_PAYLOAD);
            this.pendingShareNewConversation = CodexShareActivity.ACTION_SHARE_TO_NEW_UI.equals(this.pendingLaunchAction);
        }
    }

    private void handlePendingLaunchAction() {
        String action = this.pendingLaunchAction;
        if (action == null) return;
        this.pendingLaunchAction = null;
        if (ACTION_OPEN_WEBUI.equals(action)) {
            startWebUi();
        } else if (ACTION_OPEN_TERMUX.equals(action)) {
            openInternalTerminal();
        } else if (CodexShareActivity.ACTION_SHARE_TO_CURRENT_UI.equals(action)
            || CodexShareActivity.ACTION_SHARE_TO_NEW_UI.equals(action)) {
            startWebUi();
            this.root.postDelayed(this::deliverPendingShare, 900L);
        }
    }

    private void deliverPendingShare() {
        String payload = this.pendingSharePayload;
        if (payload == null || this.desktopBridge == null) return;
        this.pendingSharePayload = null;
        this.desktopBridge.deliverAndroidShare(payload, this.pendingShareNewConversation);
    }

    private void configureWindow() {
        Window window = getWindow();
        int i = BG;
        window.setStatusBarColor(i);
        window.setNavigationBarColor(i);
        int flags = Build.VERSION.SDK_INT >= 26 ? 8208 : 8192;
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private void buildUi() {
        FrameLayout frameLayout = new FrameLayout(this);
        this.root = frameLayout;
        frameLayout.setBackgroundColor(BG);
        LinearLayout linearLayoutBuildMainShell = buildMainShell();
        this.mainShell = linearLayoutBuildMainShell;
        linearLayoutBuildMainShell.setVisibility(4);
        this.root.addView(this.mainShell, match());
        View view = new View(this);
        this.drawerScrim = view;
        view.setBackgroundColor(Color.argb(82, 31, 31, 31));
        this.drawerScrim.setVisibility(8);
        this.drawerScrim.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.CodexHomeActivity.2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view2) {
                CodexHomeActivity.this.lambda$buildUi$0$CodexHomeActivity(view2);
            }
        });
        this.root.addView(this.drawerScrim, match());
        LinearLayout linearLayoutBuildDrawer = buildDrawer();
        this.drawer = linearLayoutBuildDrawer;
        linearLayoutBuildDrawer.setVisibility(8);
        int drawerWidth = Math.min(dp(340), (int) (getResources().getDisplayMetrics().widthPixels * 0.86f));
        FrameLayout.LayoutParams drawerParams = new FrameLayout.LayoutParams(drawerWidth, RAISED, 8388611);
        this.root.addView(this.drawer, drawerParams);
        LinearLayout linearLayoutBuildOnboarding = buildOnboarding();
        this.onboardingView = linearLayoutBuildOnboarding;
        linearLayoutBuildOnboarding.setVisibility(8);
        this.root.addView(this.onboardingView, match());
        View viewBuildSplash = buildSplash();
        this.splashView = viewBuildSplash;
        this.root.addView(viewBuildSplash, match());
        setContentView(this.root);
    }

    public void lambda$buildUi$0$CodexHomeActivity(View v) {
        closeDrawer();
    }

    private LinearLayout buildMainShell() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        int i = BG;
        linearLayout.setBackgroundColor(i);
        LinearLayout toolbar = new LinearLayout(this);
        this.topToolbar = toolbar;
        toolbar.setGravity(16);
        toolbar.setPadding(dp(10), 0, dp(14), 0);
        toolbar.setBackgroundColor(i);
        int i2 = TEXT;
        TextView menu = text("☰", 27.0f, i2);
        menu.setGravity(17);
        menu.setContentDescription("打开侧边栏");
        menu.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.CodexHomeActivity.3
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CodexHomeActivity.this.lambda$buildMainShell$1$CodexHomeActivity(view);
            }
        });
        toolbar.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(58)));
        TextView textViewText = text("Codex", 17.0f, i2);
        this.pageTitle = textViewText;
        textViewText.setTypeface(Typeface.DEFAULT, 1);
        textViewText.setGravity(16);
        textViewText.setIncludeFontPadding(false);
        toolbar.addView(this.pageTitle, new LinearLayout.LayoutParams(0, dp(58), 1.0f));
        linearLayout.addView(toolbar, new LinearLayout.LayoutParams(RAISED, dp(58)));
        View line = new View(this);
        line.setBackgroundColor(0);
        linearLayout.addView(line, new LinearLayout.LayoutParams(RAISED, dp(1)));
        this.contentHost = new FrameLayout(this);
        View viewBuildHomePage = buildHomePage();
        this.homePage = viewBuildHomePage;
        this.contentHost.addView(viewBuildHomePage, match());
        ScrollView scrollViewBuildConfigPage = buildConfigPage();
        this.configPage = scrollViewBuildConfigPage;
        scrollViewBuildConfigPage.setVisibility(8);
        this.contentHost.addView(this.configPage, match());
        MaterialButton materialButtonBuildConfigFab = buildConfigFab();
        this.configFab = materialButtonBuildConfigFab;
        materialButtonBuildConfigFab.setVisibility(8);
        FrameLayout.LayoutParams fabParams = new FrameLayout.LayoutParams(dp(58), dp(58), 85);
        fabParams.rightMargin = dp(20);
        fabParams.bottomMargin = dp(20);
        this.contentHost.addView((View) this.configFab, (ViewGroup.LayoutParams) fabParams);
        ScrollView scrollViewBuildSettingsPage = buildSettingsPage();
        this.settingsPage = scrollViewBuildSettingsPage;
        scrollViewBuildSettingsPage.setVisibility(8);
        this.contentHost.addView(this.settingsPage, match());
        this.overlaySettingsPage = buildOverlaySettingsPage();
        this.overlaySettingsPage.setVisibility(View.GONE);
        this.contentHost.addView(this.overlaySettingsPage, match());
        this.aboutPage = buildAboutPage();
        this.aboutPage.setVisibility(View.GONE);
        this.contentHost.addView(this.aboutPage, match());
        View viewBuildMihomoPage = buildMihomoPage();
        this.mihomoPage = viewBuildMihomoPage;
        viewBuildMihomoPage.setVisibility(8);
        this.contentHost.addView(this.mihomoPage, match());
        WebView webViewBuildWebView = buildWebView();
        this.webView = webViewBuildWebView;
        webViewBuildWebView.setVisibility(8);
        this.contentHost.addView(this.webView, match());
        linearLayout.addView(this.contentHost, new LinearLayout.LayoutParams(RAISED, 0, 1.0f));
        return linearLayout;
    }

    public void lambda$buildMainShell$1$CodexHomeActivity(View v) {
        openDrawer();
    }

    private View buildHomePage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(1);
        body.setPadding(dp(22), dp(30), dp(22), dp(30));
        ImageView mark = logo(dp(60));
        body.addView(mark, new LinearLayout.LayoutParams(dp(60), dp(60)));
        int i = MUTED;
        TextView eyebrow = text("CODEX ON ANDROID", 11.0f, i);
        eyebrow.setTypeface(Typeface.DEFAULT, 1);
        eyebrow.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams eyebrowParams = wrap();
        eyebrowParams.topMargin = dp(22);
        body.addView(eyebrow, eyebrowParams);
        TextView heading = text("让 Codex 在手机上工作", 31.0f, TEXT);
        heading.setTypeface(Typeface.DEFAULT, 1);
        heading.setLineSpacing(0.0f, 1.05f);
        LinearLayout.LayoutParams headingParams = wrap();
        headingParams.topMargin = dp(8);
        body.addView(heading, headingParams);
        TextView description = text("WebUI、Codex CLI 与调试终端都在同一个应用里。你的会话会在页面切换时保持原样。", 15.0f, i);
        description.setLineSpacing(dp(4), 1.0f);
        LinearLayout.LayoutParams descriptionParams = wrap();
        descriptionParams.topMargin = dp(12);
        body.addView(description, descriptionParams);
        TextView section = text("快速开始", 13.0f, i);
        section.setTypeface(Typeface.DEFAULT, 1);
        LinearLayout.LayoutParams sectionParams = wrap();
        sectionParams.topMargin = dp(34);
        sectionParams.bottomMargin = dp(10);
        body.addView(section, sectionParams);
        body.addView(actionCard("启动 WebUI", "继续上次的页面与会话", "WEB", this::startWebUi), cardParams(0));
        body.addView(actionCard("启动 Termux", "打开应用内调试终端", "CLI", this::openInternalTerminal), cardParams(10));
        body.addView(actionCard("配置管理", "API、模型与 Codex 运行环境", "CFG", this::showConfiguration), cardParams(10));
        scroll.addView(body, new FrameLayout.LayoutParams(RAISED, -2));
        return scroll;
    }

    private ScrollView buildConfigPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout linearLayout = new LinearLayout(this);
        this.configPanel = linearLayout;
        linearLayout.setOrientation(1);
        scroll.addView(this.configPanel, new FrameLayout.LayoutParams(RAISED, -2));
        this.baseUrl = field("https://api.example.com/v1", "", false);
        this.apiKey = field("sk-...", "", true);
        this.model = field("gpt-5.4", "", false);
        syncActiveProviderFields();
        renderProviderList();
        return scroll;
    }

    private void syncActiveProviderFields() {
        CodexProviderStore.Profile active = this.providerStore.active();
        if (active == null) {
            this.baseUrl.setText("");
            this.apiKey.setText("");
            this.model.setText("");
        } else {
            this.baseUrl.setText(active.baseUrl);
            this.apiKey.setText(active.apiKey);
            this.model.setText(active.model);
        }
    }

    private void renderProviderList() {
        this.providerEditorOpen = false;
        this.configPanel.removeAllViews();
        this.configPanel.setPadding(dp(20), dp(20), dp(20), dp(96));
        int i = TEXT;
        TextView title = text("配置管理", 29.0f, i);
        title.setTypeface(Typeface.DEFAULT, 1);
        title.setIncludeFontPadding(false);
        this.configPanel.addView(title);
        TextView intro = text("管理 Codex API 地址、密钥和默认模型", 14.0f, MUTED);
        LinearLayout.LayoutParams ip = wrap();
        ip.topMargin = dp(7);
        ip.bottomMargin = dp(20);
        this.configPanel.addView(intro, ip);
        List<CodexProviderStore.Profile> profiles = this.providerStore.all();
        if (profiles.isEmpty()) {
            TextView empty = text("还没有配置，点击右下角 + 添加", 15.0f, i);
            empty.setGravity(17);
            empty.setBackground(rounded(SURFACE, 18, 0, 0));
            empty.setPadding(dp(18), dp(32), dp(18), dp(32));
            this.configPanel.addView(empty, wrap());
        } else {
            for (CodexProviderStore.Profile profile : profiles) {
                this.configPanel.addView(providerRow(profile), providerRowParams());
            }
        }
        MaterialButton materialButton = this.configFab;
        if (materialButton != null) {
            materialButton.setVisibility(0);
        }
    }

    private MaterialButton buildConfigFab() {
        MaterialButton add = new MaterialButton(this);
        add.setText("+");
        add.setTextSize(26.0f);
        add.setTextColor(TEXT);
        add.setAllCaps(false);
        add.setGravity(17);
        add.setInsetTop(0);
        add.setInsetBottom(0);
        add.setMinWidth(0);
        add.setMinimumWidth(0);
        add.setElevation(0.0f);
        add.setStateListAnimator((StateListAnimator) null);
        add.setCornerRadius(dp(29));
        add.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(239, 218, 202)));
        add.setRippleColor(ColorStateList.valueOf(Color.rgb(214, 188, 169)));
        add.setContentDescription("添加 API 配置");
        add.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$-5hIEUjBvzNyVcN9BW5ojeyYGPc
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CodexHomeActivity.this.lambda$buildConfigFab$1$CodexHomeActivity(view);
            }
        });
        return add;
    }

    public /* synthetic */ void lambda$buildConfigFab$1$CodexHomeActivity(View v) {
        showProviderEditor(null);
    }

    private LinearLayout.LayoutParams providerRowParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(RAISED, -2);
        p.bottomMargin = dp(10);
        return p;
    }

    private View providerRow(final CodexProviderStore.Profile profile) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(1);
        row.setPadding(dp(14), dp(13), dp(8), dp(10));
        int normal = this.providerStore.isActive(profile) ? Color.rgb(248, 230, 216) : 0;
        row.setBackground(interactiveBackground(normal, Color.rgb(238, 218, 203), 16));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(16);
        TextView name = text(profile.name, 17.0f, TEXT);
        name.setTypeface(Typeface.DEFAULT, 1);
        name.setIncludeFontPadding(false);
        head.addView(name, new LinearLayout.LayoutParams(0, -2, 1.0f));
        if (this.providerStore.isActive(profile)) {
            TextView badge = text("已启用", 12.0f, GREEN);
            badge.setTypeface(Typeface.DEFAULT, 1);
            head.addView(badge);
        }
        row.addView(head);
        if (profile.note != null && !profile.note.trim().isEmpty()) {
            TextView note = text(profile.note, 13.0f, MUTED);
            LinearLayout.LayoutParams np = wrap();
            np.topMargin = dp(5);
            row.addView(note, np);
        }
        String str = profile.baseUrl;
        int i = MUTED;
        TextView endpoint = text(str, 12.0f, i);
        endpoint.setSingleLine(true);
        endpoint.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams ep = wrap();
        ep.topMargin = dp(7);
        row.addView(endpoint, ep);
        TextView format = text(apiFormatLabel(profile.apiFormat), 12.0f, i);
        LinearLayout.LayoutParams fp = wrap();
        fp.topMargin = dp(3);
        row.addView(format, fp);
        TextView agents = text("\u5b50\u4ee3\u7406\uff1aV2/Ultra " + profile.ultraSubagentLimit
            + "  \u00b7  V1/\u666e\u901a " + profile.normalSubagentLimit, 12.0f, i);
        LinearLayout.LayoutParams agentsParams = wrap();
        agentsParams.topMargin = dp(3);
        row.addView(agents, agentsParams);
        if (profile.proxyEnabled) {
            String proxyModes = (profile.proxyWebUi ? "WebUI" : "") +
                (profile.proxyWebUi && profile.proxyTermux ? " + " : "") +
                (profile.proxyTermux ? "Termux" : "");
            TextView proxy = text("内置代理" + (proxyModes.isEmpty() ? "" : "  /  " + proxyModes), 12, GREEN);
            proxy.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            LinearLayout.LayoutParams proxyParams = wrap(); proxyParams.topMargin = dp(4);
            row.addView(proxy, proxyParams);
        }
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(21);
        Button enable = compactButton(this.providerStore.isActive(profile) ? "已启用" : "启用");
        enable.setEnabled(true ^ this.providerStore.isActive(profile));
        enable.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$F4sYy_9UXHyYz436UnJlFfc_Yas
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CodexHomeActivity.this.lambda$providerRow$2$CodexHomeActivity(profile, view);
            }
        });
        actions.addView(enable);
        final Button test = compactButton("测试连接");
        test.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$_IAi3PXzxZauEsWFSKrr_W8RNQw
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CodexHomeActivity.this.lambda$providerRow$3$CodexHomeActivity(profile, test, view);
            }
        });
        actions.addView(test);
        Button edit = compactButton("编辑");
        edit.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$Ay-yVoegZV1P2kdu0UmKzqtSZKg
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CodexHomeActivity.this.lambda$providerRow$4$CodexHomeActivity(profile, view);
            }
        });
        actions.addView(edit);
        LinearLayout.LayoutParams ac = wrap();
        ac.topMargin = dp(8);
        row.addView(actions, ac);
        return row;
    }

    public /* synthetic */ void lambda$providerRow$2$CodexHomeActivity(CodexProviderStore.Profile profile, View v) {
        activateProvider(profile);
    }

    public /* synthetic */ void lambda$providerRow$3$CodexHomeActivity(CodexProviderStore.Profile profile, Button test, View v) {
        testProvider(profile, test);
    }

    public /* synthetic */ void lambda$providerRow$4$CodexHomeActivity(CodexProviderStore.Profile profile, View v) {
        showProviderEditor(profile);
    }

    private Button compactButton(String label) {
        MaterialButton b = new MaterialButton(this);
        b.setText(label);
        b.setTextSize(13.0f);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setElevation(0.0f);
        b.setStateListAnimator((StateListAnimator) null);
        b.setCornerRadius(dp(20));
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setBackgroundTintList(ColorStateList.valueOf(0));
        b.setRippleColor(ColorStateList.valueOf(Color.rgb(220, 201, 187)));
        b.setLayoutParams(new LinearLayout.LayoutParams(-2, dp(40)));
        return b;
    }

    private void showProviderEditor(final CodexProviderStore.Profile existing) {
        this.providerEditorOpen = true;
        MaterialButton materialButton = this.configFab;
        if (materialButton != null) {
            materialButton.setVisibility(8);
        }
        this.configPanel.removeAllViews();
        this.configPanel.setPadding(dp(20), dp(18), dp(20), dp(36));
        Button back = compactButton("←  配置列表");
        back.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$gLKyoG03SyUr1YbdlbHMHPLDBfU
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CodexHomeActivity.this.lambda$showProviderEditor$5$CodexHomeActivity(view);
            }
        });
        this.configPanel.addView(back);
        TextView heading = text(existing == null ? "添加配置" : "编辑配置", 28.0f, TEXT);
        heading.setTypeface(Typeface.DEFAULT, 1);
        LinearLayout.LayoutParams hp = wrap();
        hp.topMargin = dp(18);
        hp.bottomMargin = dp(8);
        this.configPanel.addView(heading, hp);
        final EditText name = field("例如：工作 API", existing == null ? "" : existing.name, false);
        final EditText id = field("provider-id", existing == null ? this.providerStore.newId() : existing.id, false);
        id.setEnabled(existing == null);
        final EditText note = field("可选备注", existing == null ? "" : existing.note, false);
        final EditText url = field("https://api.example.com/v1", existing == null ? "" : existing.baseUrl, false);
        final EditText key = field("sk-...", existing == null ? "" : existing.apiKey, true);
        final String[] selectedFormat = new String[1];
        selectedFormat[0] = existing == null ? "auto" : existing.apiFormat;
        final String[] selectedModel = new String[1];
        selectedModel[0] = existing != null ? existing.model : "";
        final ArrayList<CodexProviderStore.ModelConfig> modelCatalog = new ArrayList<>();
        if (existing != null) {
            Iterator it = existing.models.iterator();
            while (it.hasNext()) {
                CodexProviderStore.ModelConfig item = (CodexProviderStore.ModelConfig) it.next();
                modelCatalog.add(item.copy());
            }
        }
        if (modelCatalog.isEmpty() && !selectedModel[0].trim().isEmpty()) {
            modelCatalog.add(new CodexProviderStore.ModelConfig(selectedModel[0], selectedModel[0], 0L));
        }
        addField(this.configPanel, "配置名称", name);
        addField(this.configPanel, "ID", id);
        addField(this.configPanel, "备注（可选）", note);
        addField(this.configPanel, "API Base URL", url);
        addField(this.configPanel, "API Key", key);
        int i = TEXT;
        TextView formatLabel = text("API 格式", 13.0f, i);
        formatLabel.setTypeface(Typeface.DEFAULT, 1);
        LinearLayout.LayoutParams flp = wrap();
        flp.topMargin = dp(15);
        flp.bottomMargin = dp(7);
        this.configPanel.addView(formatLabel, flp);
        final Button formatButton = secondaryButton(apiFormatLabel(selectedFormat[0]));
        this.configPanel.addView(formatButton, new LinearLayout.LayoutParams(RAISED, dp(52)));
        formatButton.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$ux1pyd9o6m9GPNnoDXt2Nk2_uJw
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CodexHomeActivity.this.lambda$showProviderEditor$6$CodexHomeActivity(selectedFormat, formatButton, url, key, view);
            }
        });
        this.editorForwardReasoningContext = existing != null && existing.forwardReasoningContext;
        SettingToggle forwardReasoningContext = settingSwitch("\u8f6c\u53d1 reasoning.context",
            "\u9ed8\u8ba4\u5173\u95ed\uff0c\u4ec5\u63a7\u5236 Responses reasoning.context\uff1bDeepSeek Chat \u7684 reasoning_content \u5de5\u5177\u56de\u5408\u4f1a\u81ea\u52a8\u4fdd\u7559\uff0c\u4e0e\u6b64\u5f00\u5173\u65e0\u5173",
            this.editorForwardReasoningContext);
        forwardReasoningContext.setOnCheckedChangeListener((button, checked) -> this.editorForwardReasoningContext = checked);
        this.configPanel.addView(forwardReasoningContext, new LinearLayout.LayoutParams(RAISED, dp(76)));

        TextView agentsTitle = text("\u5b50\u4ee3\u7406\u5e76\u53d1", 18.0f, i);
        agentsTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams agentsTitleParams = wrap();
        agentsTitleParams.topMargin = dp(24);
        this.configPanel.addView(agentsTitle, agentsTitleParams);
        TextView agentsHint = text("V2/Ultra \u6570\u91cf\u7528\u4e8e\u6a21\u578b\u76ee\u5f55\u542f\u7528 V2 \u7684\u6a21\u578b\uff1bV2 \u603b\u5e76\u53d1\u8fd8\u4f1a\u989d\u5916\u5305\u542b\u4e3b\u4ee3\u7406\u3002\u5b98\u65b9 Codex \u4e0d\u5141\u8bb8 V2 \u4e0e agents.max_threads \u540c\u65f6\u8bbe\u7f6e\uff1a\u5f53\u672c\u914d\u7f6e\u542b V2 \u6a21\u578b\u65f6\uff0cV1 \u4f7f\u7528 Codex \u9ed8\u8ba4\u503c\uff1b\u5982\u9700\u81ea\u5b9a\u4e49 V1 \u6570\u91cf\uff0c\u8bf7\u4f7f\u7528\u4e0d\u542b V2 \u6a21\u578b\u7684\u72ec\u7acb API \u914d\u7f6e\u3002", 13, MUTED);
        LinearLayout.LayoutParams agentsHintParams = wrap();
        agentsHintParams.topMargin = dp(6);
        agentsHintParams.bottomMargin = dp(8);
        this.configPanel.addView(agentsHint, agentsHintParams);
        final EditText ultraSubagentLimit = field("3", String.valueOf(existing == null
            ? CodexProviderStore.Profile.DEFAULT_ULTRA_SUBAGENT_LIMIT : existing.ultraSubagentLimit), false);
        final EditText normalSubagentLimit = field("6", String.valueOf(existing == null
            ? CodexProviderStore.Profile.DEFAULT_NORMAL_SUBAGENT_LIMIT : existing.normalSubagentLimit), false);
        ultraSubagentLimit.setInputType(InputType.TYPE_CLASS_NUMBER);
        normalSubagentLimit.setInputType(InputType.TYPE_CLASS_NUMBER);
        addField(this.configPanel, "Ultra / V2 \u6700\u5927\u5b50\u4ee3\u7406\u6570", ultraSubagentLimit);
        addField(this.configPanel, "\u666e\u901a / V1 \u6700\u5927\u5b50\u4ee3\u7406\u6570", normalSubagentLimit);
        this.editorCustomSubagentStability = existing == null || existing.customSubagentStability;
        SettingToggle customSubagentStability = settingSwitch("\u81ea\u5b9a\u4e49\u6a21\u578b\u5b50\u4ee3\u7406\u7a33\u5b9a\u6a21\u5f0f",
            "\u9ed8\u8ba4\u5f00\u542f\uff1a\u4ee3\u7406\u5c42\u4f1a\u786c\u6027\u79fb\u9664\u5b50\u4ee3\u7406\u7684 spawn_agent \u5de5\u5177\uff0c\u9632\u6b62\u7a7a\u4efb\u52a1\u9012\u5f52\u548c\u6574\u94fe\u8def\u5361\u4f4f\uff1b\u540c\u65f6\u9650\u5236 wait \u65f6\u957f\uff0c\u8d85\u65f6\u540e\u4e2d\u65ad\u5e76\u7ee7\u7eed\u6c47\u603b\u3002\u4ec5\u5bf9\u975e Sol \u7684 V2/Ultra \u6a21\u578b\u751f\u6548",
            this.editorCustomSubagentStability);
        customSubagentStability.setOnCheckedChangeListener((button, checked) -> this.editorCustomSubagentStability = checked);
        this.configPanel.addView(customSubagentStability, new LinearLayout.LayoutParams(RAISED, dp(92)));

        this.editorProxyEnabled = existing != null && existing.proxyEnabled;
        this.editorProxyWebUi = existing != null && existing.proxyWebUi;
        this.editorProxyTermux = existing != null && existing.proxyTermux;
        SettingToggle useProxy = settingSwitch("此配置使用内置代理", "仅影响 Codex Mobile 自己发出的 API 请求", this.editorProxyEnabled);
        SettingToggle proxyWebUi = settingSwitch("WebUI 一键开启代理", "进入 WebUI 时自动启动 Mihomo", this.editorProxyWebUi);
        SettingToggle proxyTermux = settingSwitch("Termux 一键开启代理", "打开内置 Termux 时自动启动 Mihomo", this.editorProxyTermux);
        proxyWebUi.setToggleEnabled(this.editorProxyEnabled); proxyTermux.setToggleEnabled(this.editorProxyEnabled);
        useProxy.setOnCheckedChangeListener((button, checked) -> { this.editorProxyEnabled = checked; proxyWebUi.setToggleEnabled(checked); proxyTermux.setToggleEnabled(checked); });
        proxyWebUi.setOnCheckedChangeListener((button, checked) -> this.editorProxyWebUi = checked);
        proxyTermux.setOnCheckedChangeListener((button, checked) -> this.editorProxyTermux = checked);
        TextView proxyTitle = text("内置代理", 18.0f, i); proxyTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams proxyTitleParams = wrap(); proxyTitleParams.topMargin = dp(24); this.configPanel.addView(proxyTitle, proxyTitleParams);
        TextView proxyHint = text("可以为这个 API 配置单独设置 WebUI 或 Termux 一键启动 Mihomo", 13, MUTED);
        LinearLayout.LayoutParams proxyHintParams = wrap(); proxyHintParams.topMargin = dp(6); proxyHintParams.bottomMargin = dp(8); this.configPanel.addView(proxyHint, proxyHintParams);
        this.configPanel.addView(useProxy, new LinearLayout.LayoutParams(RAISED, dp(76)));
        this.configPanel.addView(proxyWebUi, new LinearLayout.LayoutParams(RAISED, dp(76)));
        this.configPanel.addView(proxyTermux, new LinearLayout.LayoutParams(RAISED, dp(76)));
        TextView modelsTitle = text("自定义模型", 18.0f, i);
        modelsTitle.setTypeface(Typeface.DEFAULT, 1);
        modelsTitle.setIncludeFontPadding(false);
        LinearLayout.LayoutParams mtp = wrap();
        mtp.topMargin = dp(24);
        this.configPanel.addView(modelsTitle, mtp);
        TextView modelsHint = text("从 API 获取模型，或手动添加名称、ID 和上下文长度", 13.0f, MUTED);
        LinearLayout.LayoutParams mhp = wrap();
        mhp.topMargin = dp(6);
        mhp.bottomMargin = dp(10);
        this.configPanel.addView(modelsHint, mhp);
        LinearLayout modelActions = new LinearLayout(this);
        modelActions.setOrientation(0);
        final Button fetchModelsButton = secondaryButton("获取模型");
        Button addModelButton = secondaryButton("添加模型");
        LinearLayout.LayoutParams actionLeft = new LinearLayout.LayoutParams(0, dp(52), 1.0f);
        actionLeft.rightMargin = dp(6);
        LinearLayout.LayoutParams actionRight = new LinearLayout.LayoutParams(0, dp(52), 1.0f);
        actionRight.leftMargin = dp(6);
        modelActions.addView(fetchModelsButton, actionLeft);
        modelActions.addView(addModelButton, actionRight);
        this.configPanel.addView(modelActions, new LinearLayout.LayoutParams(RAISED, dp(52)));
        final LinearLayout modelList = new LinearLayout(this);
        modelList.setOrientation(1);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(RAISED, -2);
        mlp.topMargin = dp(12);
        this.configPanel.addView(modelList, mlp);
        renderModelCatalog(modelList, modelCatalog, selectedModel);
        addModelButton.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$4y2RiuHRli7e36AYw2ndvSE4WoY
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CodexHomeActivity.this.lambda$showProviderEditor$7$CodexHomeActivity(modelCatalog, selectedModel, modelList, view);
            }
        });
        fetchModelsButton.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$t9hZiwDKWHzLtKPXmmJx8_EzYSU
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CodexHomeActivity.this.lambda$showProviderEditor$8$CodexHomeActivity(url, key, modelCatalog, selectedModel, modelList, fetchModelsButton, view);
            }
        });
        Button save = primaryButton("保存配置");
        addButton(this.configPanel, save, 22);
        save.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$H3KsiY6w4DamR_8FfCzeiuqr0xU
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CodexHomeActivity.this.lambda$showProviderEditor$9$CodexHomeActivity(name, id, url, key, selectedModel, modelCatalog, note, selectedFormat, ultraSubagentLimit, normalSubagentLimit, view);
            }
        });
        final Button test = secondaryButton("测试连接");
        addButton(this.configPanel, test, 10);
        test.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$PyBAYMoXLnwPSsSZaYzSeDusX2Y
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CodexHomeActivity.this.lambda$showProviderEditor$10$CodexHomeActivity(id, name, note, url, key, selectedModel, selectedFormat, modelCatalog, test, view);
            }
        });
        if (existing != null) {
            Button delete = secondaryButton("删除配置");
            delete.setTextColor(Color.rgb(160, 45, 45));
            addButton(this.configPanel, delete, 10);
            delete.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$2U845MuxFaz26zG5NpwFg8ldRHA
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    CodexHomeActivity.this.lambda$showProviderEditor$11$CodexHomeActivity(existing, view);
                }
            });
        }
        this.configPage.scrollTo(0, 0);
    }

    public /* synthetic */ void lambda$showProviderEditor$5$CodexHomeActivity(View v) {
        renderProviderList();
    }

    public /* synthetic */ void lambda$showProviderEditor$6$CodexHomeActivity(String[] selectedFormat, Button formatButton, EditText url, EditText key, View v) {
        showApiFormatPicker(selectedFormat, formatButton, url, key);
    }

    public /* synthetic */ void lambda$showProviderEditor$7$CodexHomeActivity(ArrayList modelCatalog, String[] selectedModel, LinearLayout modelList, View v) {
        lambda$renderModelCatalog$13$CodexHomeActivity(null, null, modelCatalog, selectedModel, modelList);
    }

    public /* synthetic */ void lambda$showProviderEditor$8$CodexHomeActivity(EditText url, EditText key, ArrayList modelCatalog, String[] selectedModel, LinearLayout modelList, Button fetchModelsButton, View v) {
        fetchAndChooseModels(url.getText().toString().trim(), key.getText().toString().trim(), modelCatalog, selectedModel, modelList, fetchModelsButton);
    }

    public /* synthetic */ void lambda$showProviderEditor$9$CodexHomeActivity(EditText name, EditText id, EditText url, EditText key, String[] selectedModel, ArrayList modelCatalog, EditText note, String[] selectedFormat, EditText ultraSubagentLimit, EditText normalSubagentLimit, View v) {
        String n = name.getText().toString().trim();
        String identifier = id.getText().toString().trim();
        String u = url.getText().toString().trim();
        String k = key.getText().toString().trim();
        if (n.isEmpty() || identifier.isEmpty() || u.isEmpty() || k.isEmpty()) {
            Toast.makeText(this, "请填写名称、ID、URL 和 API Key", 0).show();
            return;
        }
        if (selectedModel[0].isEmpty() && !modelCatalog.isEmpty()) {
            selectedModel[0] = ((CodexProviderStore.ModelConfig) modelCatalog.get(0)).id;
        }
        int ultraLimit = parseSubagentLimit(ultraSubagentLimit, "\u8bf7\u8f93\u5165 1 \u5230 " + CodexProviderStore.Profile.MAX_SUBAGENT_LIMIT);
        if (ultraLimit < 0) return;
        int normalLimit = parseSubagentLimit(normalSubagentLimit, "\u8bf7\u8f93\u5165 1 \u5230 " + CodexProviderStore.Profile.MAX_SUBAGENT_LIMIT);
        if (normalLimit < 0) return;
        CodexProviderStore.Profile value = new CodexProviderStore.Profile(identifier, n, note.getText().toString().trim(), u, k, selectedModel[0], selectedFormat[0], modelCatalog,
            this.editorProxyEnabled, this.editorProxyEnabled && this.editorProxyWebUi,
            this.editorProxyEnabled && this.editorProxyTermux, this.editorForwardReasoningContext,
            ultraLimit, normalLimit, this.editorCustomSubagentStability);
        this.providerStore.save(value);
        if (this.providerStore.active() == null || this.providerStore.isActive(value)) {
            activateProvider(value);
        } else {
            renderProviderList();
        }
    }

    private int parseSubagentLimit(EditText field, String message) {
        try {
            int value = Integer.parseInt(field.getText().toString().trim());
            if (value < 1 || value > CodexProviderStore.Profile.MAX_SUBAGENT_LIMIT) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException error) {
            field.setError(message);
            field.requestFocus();
            return -1;
        }
    }

    public /* synthetic */ void lambda$showProviderEditor$10$CodexHomeActivity(EditText id, EditText name, EditText note, EditText url, EditText key, String[] selectedModel, String[] selectedFormat, ArrayList modelCatalog, Button test, View v) {
        testProvider(new CodexProviderStore.Profile(id.getText().toString(), name.getText().toString(), note.getText().toString(), url.getText().toString().trim(), key.getText().toString().trim(), selectedModel[0], selectedFormat[0], modelCatalog), test);
    }

    public /* synthetic */ void lambda$showProviderEditor$11$CodexHomeActivity(CodexProviderStore.Profile existing, View v) {
        confirmDeleteProvider(existing);
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r8v0 */
    /* JADX WARN: Type inference failed for: r8v1, types: [boolean, int] */
    /* JADX WARN: Type inference failed for: r8v6 */
    private void renderModelCatalog(final LinearLayout parent, final ArrayList<CodexProviderStore.ModelConfig> models, final String[] selectedModel) {
        String strModelDisplayName;
        String defaultText;
        CodexProviderStore.ModelConfig current;
        String str;
        final CodexProviderStore.ModelConfig item;
        String defaultText2;
        LinearLayout card;
        int i;
        int i2;
        LinearLayout actions;
        parent.removeAllViews();
        int r8 = 0;
        CodexProviderStore.ModelConfig current2 = findModel(models, selectedModel[0]);
        if (selectedModel[0].isEmpty()) {
            defaultText = "默认模型：尚未设置";
        } else {
            StringBuilder sbAppend = new StringBuilder().append("默认模型：");
            if (current2 != null) {
                strModelDisplayName = modelDisplayName(current2);
            } else {
                strModelDisplayName = selectedModel[0];
            }
            defaultText = sbAppend.append(strModelDisplayName).toString();
        }
        TextView defaultView = text(defaultText, 13.0f, selectedModel[0].isEmpty() ? MUTED : GREEN);
        int i3 = 1;
        defaultView.setTypeface(Typeface.DEFAULT, 1);
        defaultView.setPadding(dp(2), 0, dp(2), dp(10));
        parent.addView(defaultView);
        int i4 = 18;
        int i5 = 16;
        if (models.isEmpty()) {
            TextView empty = text("还没有模型，可以从 API 获取或手动添加", 14.0f, MUTED);
            empty.setGravity(17);
            empty.setPadding(dp(16), dp(24), dp(16), dp(24));
            empty.setBackground(rounded(SURFACE, 18, 0, 0));
            parent.addView(empty, new LinearLayout.LayoutParams(RAISED, -2));
            return;
        }
        Iterator<CodexProviderStore.ModelConfig> it = models.iterator();
        while (it.hasNext()) {
            CodexProviderStore.ModelConfig item2 = it.next();
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(i3);
            linearLayout.setPadding(dp(15), dp(13), dp(8), dp(9));
            int normal = item2.id.equals(selectedModel[r8]) ? Color.rgb(248, 230, 216) : SURFACE;
            linearLayout.setBackground(interactiveBackground(normal, Color.rgb(238, 218, 203), i4));
            LinearLayout linearLayout2 = new LinearLayout(this);
            linearLayout2.setGravity(i5);
            TextView textViewText = text(modelDisplayName(item2), 16.0f, TEXT);
            textViewText.setTypeface(Typeface.DEFAULT, 1);
            textViewText.setIncludeFontPadding(false);
            linearLayout2.addView(textViewText, new LinearLayout.LayoutParams(r8, -2, 1.0f));
            if (item2.id.equals(selectedModel[r8])) {
                TextView textViewText2 = text("已默认", 12.0f, GREEN);
                textViewText2.setTypeface(Typeface.DEFAULT, 1);
                textViewText2.setPadding(dp(8), 0, dp(8), 0);
                linearLayout2.addView(textViewText2);
            }
            linearLayout.addView(linearLayout2);
            String str2 = "ID  " + item2.id;
            int i6 = MUTED;
            TextView modelId = text(str2, 13.0f, i6);
            modelId.setSingleLine(true);
            modelId.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            LinearLayout.LayoutParams mip = wrap();
            mip.topMargin = dp(6);
            linearLayout.addView(modelId, mip);
            if (item2.contextWindow > 0) {
                current = current2;
                str = "上下文  " + String.format(Locale.US, "%,d", Long.valueOf(item2.contextWindow)) + " tokens";
            } else {
                current = current2;
                str = "上下文  未设置";
            }
            String contextText = str;
            View context = text(contextText, 12.0f, i6);
            LinearLayout.LayoutParams cip = wrap();
            cip.topMargin = dp(3);
            linearLayout.addView(context, cip);
            StringBuilder capabilityText = new StringBuilder("能力  ");
            capabilityText.append(item2.imageInput ? "图片输入" : "仅文本");
            if (item2.imageDetailOriginal) {
                capabilityText.append("  ·  原图细节");
            }
            if (item2.reasoningSummaries) {
                capabilityText.append("  ·  推理摘要");
            }
            if (item2.parallelToolCalls) {
                capabilityText.append("  ·  并行工具");
            }
            if (item2.verbosity) {
                capabilityText.append("  ·  详细度");
            }
            if (item2.webSearch) {
                capabilityText.append("  ·  Web 搜索");
            }
            View capabilities = text(capabilityText.toString(), 12.0f, i6);
            LinearLayout.LayoutParams cap = wrap();
            cap.topMargin = dp(3);
            linearLayout.addView(capabilities, cap);
            LinearLayout actions2 = new LinearLayout(this);
            actions2.setGravity(21);
            final CodexProviderStore.ModelConfig rowModel = item2;
            actions = actions2;
            card = linearLayout;
            i = 16;
            defaultText2 = defaultText;
            i2 = 18;
            if (!rowModel.id.equals(selectedModel[0])) {
                Button use = compactButton("\u8bbe\u4e3a\u9ed8\u8ba4");
                use.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        CodexHomeActivity.this.lambda$renderModelCatalog$12$CodexHomeActivity(selectedModel, rowModel, parent, models, view);
                    }
                });
                actions.addView(use);
            }
            Button edit = compactButton("\u7f16\u8f91");
            final CodexProviderStore.ModelConfig modelConfig = rowModel;
            edit.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    CodexHomeActivity.this.lambda$renderModelCatalog$13$CodexHomeActivity(modelConfig, modelConfig, models, selectedModel, parent);
                }
            });
            actions.addView(edit);
            Button delete = compactButton("删除");
            delete.setTextColor(Color.rgb(160, 45, 45));
            delete.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$8MDHAYC7z5HKid7HiMZ9pqZJCFU
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    CodexHomeActivity.this.lambda$renderModelCatalog$14$CodexHomeActivity(modelConfig, models, selectedModel, parent, view);
                }
            });
            actions.addView(delete);
            LinearLayout.LayoutParams ap = wrap();
            ap.topMargin = dp(5);
            card.addView(actions, ap);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(RAISED, -2);
            cp.bottomMargin = dp(9);
            parent.addView(card, cp);
            r8 = 0;
            current2 = current;
            i5 = i;
            defaultText = defaultText2;
            i4 = i2;
            i3 = 1;
        }
    }

    public /* synthetic */ void lambda$renderModelCatalog$12$CodexHomeActivity(String[] selectedModel, CodexProviderStore.ModelConfig item, LinearLayout parent, ArrayList models, View v) {
        selectedModel[0] = item.id;
        renderModelCatalog(parent, models, selectedModel);
    }

    public /* synthetic */ void lambda$renderModelCatalog$14$CodexHomeActivity(CodexProviderStore.ModelConfig item, ArrayList models, String[] selectedModel, LinearLayout parent, View v) {
        confirmDeleteModel(item, models, selectedModel, parent);
    }

    private String modelDisplayName(CodexProviderStore.ModelConfig model) {
        return (model.name == null || model.name.trim().isEmpty()) ? model.id : model.name.trim();
    }

    private CodexProviderStore.ModelConfig findModel(List<CodexProviderStore.ModelConfig> models, String id) {
        if (id == null) {
            return null;
        }
        for (CodexProviderStore.ModelConfig model : models) {
            if (id.equals(model.id)) {
                return model;
            }
        }
        return null;
    }

    private MaterialButton capabilityButton(String label, boolean checked) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setTextSize(13.0f);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, 1);
        button.setCheckable(true);
        button.setChecked(checked);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setCornerRadius(dp(22));
        button.setElevation(0.0f);
        button.setStateListAnimator((StateListAnimator) null);
        int greenPressed = Color.rgb(30, 101, 69);
        int green = Color.rgb(45, 125, 88);
        int inactivePressed = Color.rgb(231, 210, 196);
        int inactive = Color.rgb(247, 232, 220);
        button.setBackgroundTintList(new ColorStateList(new int[][]{new int[]{R.attr.state_checked, R.attr.state_pressed}, new int[]{R.attr.state_checked}, new int[]{R.attr.state_pressed}, new int[0]}, new int[]{greenPressed, green, inactivePressed, inactive}));
        button.setTextColor(new ColorStateList(new int[][]{new int[]{R.attr.state_checked}, new int[0]}, new int[]{RAISED, TEXT}));
        button.setRippleColor(ColorStateList.valueOf(Color.rgb(214, 188, 169)));
        return button;
    }

    private void addCapabilityButtonRow(LinearLayout parent, MaterialButton left, MaterialButton right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(0);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, dp(46), 1.0f);
        leftParams.rightMargin = dp(5);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, dp(46), 1.0f);
        rightParams.leftMargin = dp(5);
        row.addView((View) left, (ViewGroup.LayoutParams) leftParams);
        row.addView((View) right, (ViewGroup.LayoutParams) rightParams);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(RAISED, dp(46));
        rowParams.bottomMargin = dp(8);
        parent.addView(row, rowParams);
    }

    private EditText modelDialogField(String hint, String value, boolean password) {
        EditText field = field(hint, value, password);
        field.setPadding(dp(16), 0, dp(16), 0);
        field.setBackground(interactiveBackground(Color.rgb(247, 232, 220), Color.rgb(238, 214, 198), 16));
        field.setSaveEnabled(false);
        if (Build.VERSION.SDK_INT >= 26) {
            field.setImportantForAutofill(8);
        }
        return field;
    }

    private Button modelChoiceButton(String value) {
        Button button = compactButton(value);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPadding(dp(16), 0, dp(16), 0);
        button.setBackground(interactiveBackground(Color.rgb(247, 232, 220), Color.rgb(238, 214, 198), 16));
        button.setLayoutParams(new LinearLayout.LayoutParams(RAISED, dp(50)));
        return button;
    }

    private void addModelChoiceField(LinearLayout parent, String label, Button button) {
        TextView title = text(label, 13.0f, MUTED);
        LinearLayout.LayoutParams lp = wrap();
        lp.topMargin = dp(10); lp.bottomMargin = dp(5);
        parent.addView(title, lp);
        parent.addView(button, new LinearLayout.LayoutParams(RAISED, dp(50)));
    }

    private void bindModelChoice(Button button, String title, String[] labels, String[] values, String[] selected) {
        int current = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(selected[0])) current = i;
        button.setText(labels[current] + "  \u203a");
        button.setOnClickListener(v -> {
            int checked = 0;
            for (int i = 0; i < values.length; i++) if (values[i].equals(selected[0])) checked = i;
            AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    selected[0] = values[which];
                    button.setText(labels[which] + "  \u203a");
                    d.dismiss();
                }).setNegativeButton("\u53d6\u6d88", null).create();
            dialog.setOnShowListener(d -> {
                Window window = dialog.getWindow();
                if (window != null) window.setBackgroundDrawable(rounded(SURFACE, 26, 0, 0));
            });
            dialog.show();
        });
    }

    /* Model editor used by add, edit and fetched-model flows. */
    public void lambda$renderModelCatalog$13$CodexHomeActivity(final CodexProviderStore.ModelConfig editing,
            final CodexProviderStore.ModelConfig seed, final ArrayList<CodexProviderStore.ModelConfig> models,
            final String[] selectedModel, final LinearLayout modelList) {
        final CodexProviderStore.ModelConfig value = (seed == null ? new CodexProviderStore.ModelConfig("", "", 0L) : seed.copy());
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(2), dp(22), dp(18));

        final EditText modelName = modelDialogField("\u4f8b\u5982\uff1aGPT 5.6 Sol", value.name, false);
        final EditText modelId = modelDialogField("\u4f8b\u5982\uff1agpt-5.6-sol", value.id, false);
        final EditText description = modelDialogField("\u6a21\u578b\u7528\u9014\u3001\u7279\u70b9\u6216\u4f9b\u5e94\u5546\u5907\u6ce8", value.description, false);
        final EditText context = modelDialogField("\u4f8b\u5982\uff1a200000", value.contextWindow > 0 ? String.valueOf(value.contextWindow) : "", false);
        final EditText compact = modelDialogField("\u4f8b\u5982\uff1a160000\uff08\u53ef\u7559\u7a7a\uff09", value.autoCompactTokenLimit > 0 ? String.valueOf(value.autoCompactTokenLimit) : "", false);
        final EditText effectivePercent = modelDialogField("1 - 100", String.valueOf(value.effectiveContextWindowPercent), false);
        final EditText supportedEfforts = modelDialogField("none,minimal,low,medium,high,xhigh,max,ultra", value.supportedReasoningEfforts, false);
        final EditText baseInstructions = modelDialogField("\u7559\u7a7a\u5219\u4f7f\u7528 Fcode \u9ed8\u8ba4 Codex \u6307\u4ee4", value.baseInstructions, false);
        context.setInputType(InputType.TYPE_CLASS_NUMBER);
        compact.setInputType(InputType.TYPE_CLASS_NUMBER);
        effectivePercent.setInputType(InputType.TYPE_CLASS_NUMBER);
        baseInstructions.setSingleLine(false);
        baseInstructions.setMinLines(3);
        baseInstructions.setGravity(Gravity.TOP | Gravity.START);
        baseInstructions.setPadding(dp(16), dp(13), dp(16), dp(13));

        addField(content, "\u6a21\u578b\u540d\u79f0", modelName);
        addField(content, "\u6a21\u578b ID", modelId);
        addField(content, "\u6a21\u578b\u63cf\u8ff0", description);
        addField(content, "\u4e0a\u4e0b\u6587\u7a97\u53e3\uff08tokens\uff09", context);
        addField(content, "\u81ea\u52a8\u538b\u7f29\u9608\u503c\uff08tokens\uff09", compact);
        addField(content, "\u53ef\u7528\u4e0a\u4e0b\u6587\u6bd4\u4f8b\uff08%\uff09", effectivePercent);

        TextView reasoningTitle = text("\u63a8\u7406\u4e0e\u8f93\u51fa", 16.0f, TEXT);
        reasoningTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams section = wrap(); section.topMargin = dp(18); section.bottomMargin = dp(4);
        content.addView(reasoningTitle, section);

        final String[] defaultEffort = {value.defaultReasoningEffort};
        final String[] defaultSummary = {value.defaultReasoningSummary};
        final String[] defaultVerbosity = {value.defaultVerbosity};
        final String[] shellType = {value.shellType};
        final String[] multiAgentVersion = {value.multiAgentVersion};
        final String[] toolMode = {value.toolMode};
        final String[] ultraTransportEffort = {value.ultraTransportEffort};
        Button effortButton = modelChoiceButton("");
        Button summaryButton = modelChoiceButton("");
        Button verbosityButton = modelChoiceButton("");
        Button shellButton = modelChoiceButton("");
        Button multiAgentButton = modelChoiceButton("");
        Button toolModeButton = modelChoiceButton("");
        Button ultraTransportButton = modelChoiceButton("");
        bindModelChoice(effortButton, "\u9ed8\u8ba4\u63a8\u7406\u5f3a\u5ea6",
            new String[]{"\u5173\u95ed", "\u6700\u5c11", "\u4f4e", "\u4e2d", "\u9ad8", "\u8d85\u9ad8", "\u6700\u5927", "Ultra"},
            new String[]{"none", "minimal", "low", "medium", "high", "xhigh", "max", "ultra"}, defaultEffort);
        bindModelChoice(summaryButton, "\u9ed8\u8ba4\u63a8\u7406\u6458\u8981",
            new String[]{"\u4e0d\u751f\u6210", "\u81ea\u52a8", "\u7b80\u6d01", "\u8be6\u7ec6"},
            new String[]{"none", "auto", "concise", "detailed"}, defaultSummary);
        bindModelChoice(verbosityButton, "\u9ed8\u8ba4\u8f93\u51fa\u8be6\u7ec6\u5ea6",
            new String[]{"\u7b80\u6d01", "\u9002\u4e2d", "\u8be6\u7ec6"}, new String[]{"low", "medium", "high"}, defaultVerbosity);
        bindModelChoice(shellButton, "Shell \u5de5\u5177\u6a21\u5f0f",
            new String[]{"Shell Command", "\u9ed8\u8ba4", "\u672c\u5730", "\u7edf\u4e00\u6267\u884c", "\u7981\u7528"},
            new String[]{"shell_command", "default", "local", "unified_exec", "disabled"}, shellType);
        bindModelChoice(multiAgentButton, "\u591a\u4ee3\u7406\u7248\u672c",
            new String[]{"\u5173\u95ed", "V1", "V2\uff08Ultra\uff09"}, new String[]{"", "v1", "v2"}, multiAgentVersion);
        bindModelChoice(toolModeButton, "\u5de5\u5177\u6a21\u5f0f",
            new String[]{"\u9ed8\u8ba4", "Code Mode Only"}, new String[]{"", "code_mode_only"}, toolMode);
        bindModelChoice(ultraTransportButton, "Ultra \u4e0a\u6e38\u63a8\u7406\u5f3a\u5ea6",
            new String[]{"\u81ea\u52a8\uff08\u6700\u9ad8\u517c\u5bb9\uff09", "None", "Minimal", "Low", "Medium", "High", "XHigh", "Max"},
            new String[]{"", "none", "minimal", "low", "medium", "high", "xhigh", "max"}, ultraTransportEffort);
        addModelChoiceField(content, "\u9ed8\u8ba4\u63a8\u7406\u5f3a\u5ea6", effortButton);
        addField(content, "\u652f\u6301\u7684\u63a8\u7406\u5f3a\u5ea6\uff08\u9017\u53f7\u5206\u9694\uff09", supportedEfforts);
        addModelChoiceField(content, "Ultra \u4e0a\u6e38\u63a8\u7406\u5f3a\u5ea6", ultraTransportButton);
        TextView ultraTransportHint = text("\u672c\u5730\u4ecd\u4fdd\u6301 Ultra \u4e3b\u52a8\u591a\u4ee3\u7406\uff1b\u8be5\u503c\u53ea\u63a7\u5236\u53d1\u9001\u7ed9\u4e0a\u6e38\u7684 reasoning effort\u3002\u81ea\u52a8\u4f1a\u9009\u62e9\u652f\u6301\u5217\u8868\u4e2d\u6700\u9ad8\u7684\u975e Ultra \u6863\u4f4d\uff08\u4f8b\u5982 GLM \u4e3a xhigh\uff0cSol/GPT \u4e3a max\uff09\u3002", 12.0f, MUTED);
        LinearLayout.LayoutParams ultraTransportHintParams = wrap();
        ultraTransportHintParams.topMargin = dp(4);
        ultraTransportHintParams.bottomMargin = dp(6);
        content.addView(ultraTransportHint, ultraTransportHintParams);
        addModelChoiceField(content, "\u9ed8\u8ba4\u63a8\u7406\u6458\u8981", summaryButton);
        addModelChoiceField(content, "\u9ed8\u8ba4\u8f93\u51fa\u8be6\u7ec6\u5ea6", verbosityButton);
        addModelChoiceField(content, "Shell \u5de5\u5177", shellButton);
        addModelChoiceField(content, "\u591a\u4ee3\u7406\u7248\u672c", multiAgentButton);
        TextView multiAgentHint = text("V2 \u5e76\u975e Sol \u4e13\u5c5e\uff1a\u5176\u4ed6\u6a21\u578b\u9009\u62e9 V2\uff0c\u5e76\u5728\u652f\u6301\u5f3a\u5ea6\u4e2d\u52a0\u5165 ultra\uff0c\u5373\u53ef\u83b7\u5f97 Ultra \u4e3b\u52a8\u591a\u4ee3\u7406\uff1b\u4f4e/\u4e2d/\u9ad8\u6863\u4ecd\u4e3a\u6309\u9700\u5b50\u4ee3\u7406\u3002", 12.0f, MUTED);
        LinearLayout.LayoutParams multiAgentHintParams = wrap();
        multiAgentHintParams.topMargin = dp(4);
        multiAgentHintParams.bottomMargin = dp(6);
        content.addView(multiAgentHint, multiAgentHintParams);
        Button solMultiAgentPreset = secondaryButton("\u542f\u7528 Ultra/V2 \u5b50\u4ee3\u7406\u9884\u8bbe");
        LinearLayout.LayoutParams solPresetParams = new LinearLayout.LayoutParams(RAISED, dp(50));
        solPresetParams.topMargin = dp(4);
        solPresetParams.bottomMargin = dp(6);
        content.addView(solMultiAgentPreset, solPresetParams);
        solMultiAgentPreset.setOnClickListener(v -> {
            String normalized = CodexProviderStore.ModelConfig.normalizeEfforts(supportedEfforts.getText().toString());
            if (!CodexProviderStore.ModelConfig.supportsEffort(normalized, "ultra")) {
                normalized = normalized.isEmpty() ? "ultra" : normalized + ",ultra";
            }
            supportedEfforts.setText(CodexProviderStore.ModelConfig.normalizeEfforts(normalized));
            multiAgentVersion[0] = "v2";
            // code_mode_only is an official Sol capability, not a safe default for third-party models.
            toolMode[0] = "";
            ultraTransportEffort[0] = "";
            multiAgentButton.setText("V2\uff08Ultra\uff09  \u203a");
            toolModeButton.setText("\u9ed8\u8ba4  \u203a");
            ultraTransportButton.setText("\u81ea\u52a8\uff08\u6700\u9ad8\u517c\u5bb9\uff09  \u203a");
            Toast.makeText(this, "\u5df2\u542f\u7528 Ultra + V2\uff0c\u4e0a\u6e38\u5f3a\u5ea6\u4e3a\u81ea\u52a8", Toast.LENGTH_SHORT).show();
        });
        addModelChoiceField(content, "Codex \u5de5\u5177\u6a21\u5f0f", toolModeButton);
        TextView toolModeHint = text("Code Mode Only \u662f Sol \u5b98\u65b9\u80fd\u529b\uff1bGLM \u7b49\u7b2c\u4e09\u65b9\u6a21\u578b\u5efa\u8bae\u4fdd\u6301\u9ed8\u8ba4\uff0c\u5426\u5219\u53ef\u80fd\u770b\u4e0d\u5230 spawn_agent \u4e0e Shell \u5de5\u5177\u3002", 12.0f, MUTED);
        LinearLayout.LayoutParams toolModeHintParams = wrap();
        toolModeHintParams.topMargin = dp(4);
        toolModeHintParams.bottomMargin = dp(6);
        content.addView(toolModeHint, toolModeHintParams);
        addField(content, "\u57fa\u7840\u6307\u4ee4\uff08\u9ad8\u7ea7\uff09", baseInstructions);

        TextView capabilityTitle = text("\u6a21\u578b\u80fd\u529b", 16.0f, TEXT);
        capabilityTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams ctp = wrap(); ctp.topMargin = dp(20); ctp.bottomMargin = dp(6);
        content.addView(capabilityTitle, ctp);
        View capabilityHint = text("\u7eff\u8272\u8868\u793a\u542f\u7528\uff1b\u8fd9\u4e9b\u503c\u4f1a\u5199\u5165 Codex \u6a21\u578b\u76ee\u5f55", 12.0f, MUTED);
        LinearLayout.LayoutParams chp = wrap(); chp.bottomMargin = dp(10); content.addView(capabilityHint, chp);

        final MaterialButton imageInput = capabilityButton("\u56fe\u7247\u8f93\u5165", value.imageInput);
        final MaterialButton imageDetail = capabilityButton("\u539f\u56fe\u7ec6\u8282", value.imageDetailOriginal);
        final MaterialButton reasoning = capabilityButton("\u63a8\u7406\u6458\u8981\u53c2\u6570", value.reasoningSummaries);
        final MaterialButton parallelTools = capabilityButton("\u5e76\u884c\u5de5\u5177", value.parallelToolCalls);
        final MaterialButton verbosity = capabilityButton("\u8f93\u51fa\u8be6\u7ec6\u5ea6", value.verbosity);
        final MaterialButton webSearch = capabilityButton("Web \u641c\u7d22", value.webSearch);
        final MaterialButton skillsInstructions = capabilityButton("\u6280\u80fd\u8bf4\u660e", value.includeSkillsInstructions);
        final MaterialButton responsesLite = capabilityButton("Responses Lite", value.responsesLite);
        final MaterialButton applyPatch = capabilityButton("Apply Patch", value.applyPatchTool);
        final MaterialButton reserved = capabilityButton("\u4fdd\u7559\u9ed8\u8ba4", true);
        reserved.setEnabled(false);
        addCapabilityButtonRow(content, imageInput, imageDetail);
        addCapabilityButtonRow(content, reasoning, parallelTools);
        addCapabilityButtonRow(content, verbosity, webSearch);
        addCapabilityButtonRow(content, skillsInstructions, responsesLite);
        addCapabilityButtonRow(content, applyPatch, reserved);
        imageDetail.setEnabled(imageInput.isChecked());
        imageInput.setOnClickListener(v -> {
            imageDetail.setEnabled(imageInput.isChecked());
            if (!imageInput.isChecked()) imageDetail.setChecked(false);
        });

        ScrollView dialogScroll = new ScrollView(this);
        dialogScroll.setFillViewport(false);
        dialogScroll.addView(content, new FrameLayout.LayoutParams(RAISED, -2));
        final AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(editing == null ? "\u6dfb\u52a0\u6a21\u578b" : "\u7f16\u8f91\u6a21\u578b")
            .setView(dialogScroll).setNegativeButton("\u53d6\u6d88", null).setPositiveButton("\u4fdd\u5b58", null).create();
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) window.setBackgroundDrawable(rounded(SURFACE, 26, 0, 0));
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            save.setAllCaps(false); save.setTextColor(RAISED); save.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            save.setBackground(interactiveBackground(Color.rgb(45, 125, 88), Color.rgb(30, 101, 69), 20));
            cancel.setAllCaps(false); cancel.setTextColor(TEXT);
            cancel.setBackground(interactiveBackground(0, Color.rgb(238, 218, 203), 20));
            save.setOnClickListener(v -> {
                String nextId = modelId.getText().toString().trim();
                if (nextId.isEmpty()) { modelId.setError("\u8bf7\u586b\u5199\u6a21\u578b ID"); modelId.requestFocus(); return; }
                long nextContext;
                long nextCompact;
                int nextPercent;
                try {
                    nextContext = context.getText().toString().trim().isEmpty() ? 0L : Long.parseLong(context.getText().toString().trim());
                    if (nextContext < 0L) throw new NumberFormatException();
                } catch (NumberFormatException error) { context.setError("\u8bf7\u8f93\u5165\u6b63\u6574\u6570"); context.requestFocus(); return; }
                try {
                    nextCompact = compact.getText().toString().trim().isEmpty() ? 0L : Long.parseLong(compact.getText().toString().trim());
                    if (nextCompact < 0L) throw new NumberFormatException();
                } catch (NumberFormatException error) { compact.setError("\u8bf7\u8f93\u5165\u6b63\u6574\u6570"); compact.requestFocus(); return; }
                try {
                    nextPercent = Integer.parseInt(effectivePercent.getText().toString().trim());
                    if (nextPercent < 1 || nextPercent > 100) throw new NumberFormatException();
                } catch (NumberFormatException error) { effectivePercent.setError("\u8bf7\u8f93\u5165 1 \u5230 100"); effectivePercent.requestFocus(); return; }
                if (nextContext > 0 && nextCompact > nextContext) {
                    compact.setError("\u538b\u7f29\u9608\u503c\u4e0d\u80fd\u8d85\u8fc7\u4e0a\u4e0b\u6587\u7a97\u53e3"); compact.requestFocus(); return;
                }
                String rawEfforts = supportedEfforts.getText().toString().trim();
                if (rawEfforts.replace(",", "").trim().isEmpty()) {
                    supportedEfforts.setError("\u81f3\u5c11\u9009\u62e9\u4e00\u4e2a\u63a8\u7406\u5f3a\u5ea6"); supportedEfforts.requestFocus(); return;
                }
                String invalidEffort = CodexProviderStore.ModelConfig.invalidReasoningEffort(rawEfforts);
                if (!invalidEffort.isEmpty()) {
                    supportedEfforts.setError("\u4e0d\u652f\u6301\u7684\u63a8\u7406\u5f3a\u5ea6: " + invalidEffort); supportedEfforts.requestFocus(); return;
                }
                String normalizedEfforts = CodexProviderStore.ModelConfig.normalizeEfforts(rawEfforts);
                String selectedMultiAgentVersion = multiAgentVersion[0];
                if (CodexProviderStore.ModelConfig.supportsEffort(normalizedEfforts, "ultra")
                        && (selectedMultiAgentVersion == null || selectedMultiAgentVersion.isEmpty())) {
                    selectedMultiAgentVersion = "v2";
                }
                if (!CodexProviderStore.ModelConfig.supportsEffort(normalizedEfforts, defaultEffort[0])) {
                    supportedEfforts.setError("\u652f\u6301\u5217\u8868\u5fc5\u987b\u5305\u542b\u9ed8\u8ba4\u63a8\u7406\u5f3a\u5ea6 " + defaultEffort[0]);
                    supportedEfforts.requestFocus(); return;
                }
                if (ultraTransportEffort[0] != null && !ultraTransportEffort[0].isEmpty()
                        && !CodexProviderStore.ModelConfig.supportsEffort(normalizedEfforts, ultraTransportEffort[0])) {
                    supportedEfforts.setError("\u652f\u6301\u5217\u8868\u5fc5\u987b\u5305\u542b Ultra \u4e0a\u6e38\u5f3a\u5ea6 " + ultraTransportEffort[0]);
                    supportedEfforts.requestFocus(); return;
                }
                for (CodexProviderStore.ModelConfig item : models) {
                    if (item != editing && nextId.equalsIgnoreCase(item.id)) {
                        modelId.setError("\u8be5\u6a21\u578b ID \u5df2\u5b58\u5728"); modelId.requestFocus(); return;
                    }
                }
                String oldId = editing == null ? "" : editing.id;
                CodexProviderStore.ModelConfig target = new CodexProviderStore.ModelConfig(
                    modelName.getText().toString().trim(), nextId,
                    description.getText().toString().trim(), baseInstructions.getText().toString().trim(),
                    nextContext, nextCompact, nextPercent, defaultEffort[0],
                    normalizedEfforts, defaultSummary[0], defaultVerbosity[0], shellType[0],
                    imageInput.isChecked(), imageDetail.isChecked(), reasoning.isChecked(), parallelTools.isChecked(),
                    verbosity.isChecked(), webSearch.isChecked(), skillsInstructions.isChecked(),
                    responsesLite.isChecked(), applyPatch.isChecked(), selectedMultiAgentVersion, toolMode[0],
                    ultraTransportEffort[0]);
                if (editing == null) models.add(target);
                else {
                    int index = models.indexOf(editing);
                    if (index >= 0) models.set(index, target);
                }
                if (selectedModel[0].isEmpty() || selectedModel[0].equals(oldId)) selectedModel[0] = nextId;
                dialog.dismiss();
                renderModelCatalog(modelList, models, selectedModel);
            });
        });
        dialog.show();
    }

    private void confirmDeleteModel(final CodexProviderStore.ModelConfig item, final ArrayList<CodexProviderStore.ModelConfig> models, final String[] selectedModel, final LinearLayout modelList) {
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("删除模型").setMessage("确定删除「" + modelDisplayName(item) + "」？").setNegativeButton("取消", (DialogInterface.OnClickListener) null).setPositiveButton("删除", new DialogInterface.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$Ztec_rIEgWmpoHMkC48yd3yY5VU
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                CodexHomeActivity.this.lambda$confirmDeleteModel$18$CodexHomeActivity(item, selectedModel, models, modelList, dialogInterface, i);
            }
        }).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$O6Yz1KRMIkwF0fsd76rJ5rRFnb4
            @Override // android.content.DialogInterface.OnShowListener
            public final void onShow(DialogInterface dialogInterface) {
                CodexHomeActivity.this.lambda$confirmDeleteModel$19$CodexHomeActivity(dialog, dialogInterface);
            }
        });
        dialog.show();
    }

    public /* synthetic */ void lambda$confirmDeleteModel$18$CodexHomeActivity(CodexProviderStore.ModelConfig item, String[] selectedModel, ArrayList models, LinearLayout modelList, DialogInterface d, int which) {
        boolean wasDefault = item.id.equals(selectedModel[0]);
        models.remove(item);
        if (wasDefault) {
            selectedModel[0] = models.isEmpty() ? "" : ((CodexProviderStore.ModelConfig) models.get(0)).id;
        }
        renderModelCatalog(modelList, models, selectedModel);
    }

    public /* synthetic */ void lambda$confirmDeleteModel$19$CodexHomeActivity(AlertDialog dialog, DialogInterface d) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(rounded(SURFACE, 26, 0, 0));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void restartWebUiAndBackend() {
        final CodexProviderStore.Profile active = this.providerStore.active();
        if (active == null) {
            Toast.makeText(this, "\u8bf7\u5148\u521b\u5efa\u5e76\u542f\u7528 API \u914d\u7f6e", Toast.LENGTH_SHORT).show();
            return;
        }
        writeModelCatalogFile(active);
        this.webUiConfigRevision++;
        this.webUiReloadPending = true;
        this.appServerStarted = true;
        this.appServerUsesMihomo = shouldRouteWebUi(active);
        this.appServerBridge.start(active.baseUrl, active.apiKey, active.model,
            resolvedApiFormat(active), this.appServerUsesMihomo, active.forwardReasoningContext,
            active.ultraSubagentLimit, active.normalSubagentLimit, active.ultraTransportEfforts(),
            active.customSubagentStability && active.hasCustomV2Models());
        Toast.makeText(this, "\u6b63\u5728\u5e94\u7528\u65b0\u914d\u7f6e", Toast.LENGTH_SHORT).show();
    }

    void onCodexAppServerReady() {
        this.appServerRestartAttempts = 0;
        if (!this.webUiReloadPending) return;
        this.webUiReloadPending = false;
        loadWebUiForCurrentRevision();
        Toast.makeText(this, "\u914d\u7f6e\u5df2\u66f4\u65b0", Toast.LENGTH_SHORT).show();
    }

    void onCodexAppServerExited(int exitCode) {
        if (!this.appServerStarted || isFinishing() || isDestroyed()) return;
        this.appServerStarted = false;
        this.webUiReloadPending = true;
        int attempt = Math.min(5, ++this.appServerRestartAttempts);
        long delay = Math.min(12000L, 1000L << (attempt - 1));
        Log.w("IlyopCodexBridge", "Scheduling app-server recovery after exit " + exitCode + ", attempt " + attempt);
        this.webView.postDelayed(() -> {
            CodexProviderStore.Profile active = this.providerStore.active();
            if (active == null || this.appServerStarted || isFinishing() || isDestroyed()) return;
            this.appServerStarted = true;
            this.appServerUsesMihomo = shouldRouteWebUi(active);
            this.appServerBridge.start(active.baseUrl, active.apiKey, active.model,
                resolvedApiFormat(active), this.appServerUsesMihomo, active.forwardReasoningContext,
                active.ultraSubagentLimit, active.normalSubagentLimit, active.ultraTransportEfforts(),
            active.customSubagentStability && active.hasCustomV2Models());
        }, delay);
    }

    private void loadWebUiForCurrentRevision() {
        if (this.webView == null) return;
        this.webUiLoaded = true;
        this.webView.loadUrl("file:///android_asset/codex-desktop/index.html?configRevision=" + this.webUiConfigRevision);
    }

    private String activeApiFormat() {
        CodexProviderStore.Profile active = this.providerStore.active();
        return resolvedApiFormat(active);
    }

    private String resolvedApiFormat(CodexProviderStore.Profile profile) {
        if (profile == null || profile.apiFormat == null || "auto".equals(profile.apiFormat)) {
            return "openai_responses";
        }
        return profile.apiFormat;
    }

    private String apiFormatLabel(String format) {
        return "openai_chat".equals(format) ? "Chat Completions" : "openai_responses".equals(format) ? "Responses API" : "自动检测";
    }

    private void activateProvider(CodexProviderStore.Profile profile) {
        this.providerStore.activate(profile);
        try { writeModelCatalogFile(profile); } catch (Exception e) { Log.e("IlyopProvider", "\u6a21\u578b\u76ee\u5f55\u5199\u5165\u5931\u8d25", e); }
        syncActiveProviderFields();
        persistCodexConfiguration(profile.baseUrl, profile.apiKey, profile.model);
        if (this.appServerStarted && this.appServerBridge != null) {
            if (this.prefs.getBoolean("restart_backend_on_config_change", true)) {
                restartWebUiAndBackend();
            } else {
                Toast.makeText(this, "配置已切换，请手动重启 WebUI 后端", 1).show();
            }
        }
        Toast.makeText(this, "已启用 " + profile.name, 0).show();
        renderProviderList();
    }

    private void confirmDeleteProvider(final CodexProviderStore.Profile profile) {
        new AlertDialog.Builder(this).setTitle("删除配置").setMessage("确定删除「" + profile.name + "」？").setNegativeButton("取消", (DialogInterface.OnClickListener) null).setPositiveButton("删除", new DialogInterface.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$a_xCTofbofbBjPnzauhSyun0CJo
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                CodexHomeActivity.this.lambda$confirmDeleteProvider$21$CodexHomeActivity(profile, dialogInterface, i);
            }
        }).show();
    }

    public /* synthetic */ void lambda$confirmDeleteProvider$21$CodexHomeActivity(CodexProviderStore.Profile profile, DialogInterface d, int w) {
        this.providerStore.delete(profile);
        syncActiveProviderFields();
        renderProviderList();
    }

    /* JADX WARN: Incorrect condition in loop: B:4:0x0019 */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    private void showApiFormatPicker(final String[] selected, final Button button, final EditText url, final EditText key) {
        final String[] labels = new String[]{"\u81ea\u52a8\u68c0\u6d4b", "OpenAI Responses", "OpenAI Chat Completions"};
        final String[] values = new String[]{"auto", "openai_responses", "openai_chat"};
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(selected[0])) checked = i;
        new AlertDialog.Builder(this)
            .setTitle("API \u683c\u5f0f")
            .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                selected[0] = values[which];
                button.setText(labels[which]);
                dialog.dismiss();
            })
            .setNegativeButton("\u53d6\u6d88", null)
            .show();
    }

    public /* synthetic */ void lambda$showApiFormatPicker$22$CodexHomeActivity(String[] selected, String[] values, Button button, String[] labels, EditText url, EditText key, DialogInterface d, int which) {
        selected[0] = values[which];
        button.setText(labels[which]);
        d.dismiss();
        if (which == 0) {
            detectApiFormat(url.getText().toString().trim(), key.getText().toString().trim(), selected, button);
        }
    }

    private void detectApiFormat(final String baseUrl, final String apiKey, final String[] selected, final Button button) {
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            Toast.makeText(this, "请先填写 URL 和 API Key", 0).show();
            return;
        }
        button.setEnabled(false);
        button.setText("检测中…");
        new Thread(new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$f8vrO0aQW5aDqcLsI-Mr2eHZlco
            @Override // java.lang.Runnable
            public final void run() {
                try { CodexHomeActivity.this.lambda$detectApiFormat$24$CodexHomeActivity(baseUrl, apiKey, button, selected); }
                catch (Exception e) { runOnUiThread(() -> lambda$detectApiFormat$23$CodexHomeActivity(button, e.getMessage(), selected, "auto")); }
            }
        }, "CodexApiDetect").start();
    }

    public /* synthetic */ void lambda$detectApiFormat$24$CodexHomeActivity(String baseUrl, String apiKey, final Button button, final String[] selected) throws IOException {
        String detected = "openai_responses";
        String error = null;
        try {
            int responses = probeApiEndpoint(baseUrl, "/responses", apiKey);
            int chat = probeApiEndpoint(baseUrl, "/chat/completions", apiKey);
            boolean hasChat = true;
            boolean hasResponses = (responses == 404 || responses == 405) ? false : true;
            if (chat == 404 || chat == 405) {
                hasChat = false;
            }
            if (!hasResponses && hasChat) {
                detected = "openai_chat";
            } else if (!hasResponses && !hasChat) {
                throw new IOException("未找到 Responses 或 Chat Completions 端点");
            }
        } catch (Exception e) {
            error = e.getMessage();
        }
        final String value = detected;
        final String failure = error;
        runOnUiThread(new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$1E4GRLVzpTyDljSJf9sVx7oEyVU
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.lambda$detectApiFormat$23$CodexHomeActivity(button, failure, selected, value);
            }
        });
    }

    public /* synthetic */ void lambda$detectApiFormat$23$CodexHomeActivity(Button button, String failure, String[] selected, String value) {
        Toast toastMakeText;
        button.setEnabled(true);
        if (failure == null) {
            selected[0] = value;
            button.setText(apiFormatLabel(value));
            toastMakeText = Toast.makeText(this, "已检测：" + apiFormatLabel(value), 0);
        } else {
            selected[0] = "auto";
            button.setText(apiFormatLabel("auto"));
            toastMakeText = Toast.makeText(this, "检测失败：" + failure, 1);
        }
        toastMakeText.show();
    }

    private URLConnection openAppUrlConnection(URL url) throws Exception {
        if (!this.prefs.getBoolean("mihomo_route_api", false)) {
            return url.openConnection();
        }
        if (!this.mihomoManager.isInstalled()) {
            throw new IOException("已启用应用内代理，但 Mihomo 尚未安装");
        }
        if (!this.mihomoManager.isRunning()) {
            this.mihomoManager.start();
        }
        return url.openConnection(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", this.mihomoManager.mixedPort())));
    }

    private int probeApiEndpoint(String baseUrl, String suffix, String apiKey) throws Exception {
        String base = baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        HttpURLConnection c = (HttpURLConnection) openAppUrlConnection(new URL(base + suffix));
        c.setRequestMethod("POST");
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setDoOutput(true);
        c.setRequestProperty("Authorization", "Bearer " + apiKey);
        c.setRequestProperty("Content-Type", "application/json");
        byte[] empty = "{}".getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(empty.length);
        OutputStream out = c.getOutputStream();
        try {
            out.write(empty);
            if (out != null) {
                out.close();
            }
            int code = c.getResponseCode();
            c.disconnect();
            return code;
        } catch (Throwable th) {
            if (out != null) {
                try {
                    out.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    private void fetchModels(final String baseUrl, final String apiKey, final ModelsCallback callback) {
        new Thread(new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$C8KzE58beGSvq0eZ5nVxUMzvgKQ
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.lambda$fetchModels$27$CodexHomeActivity(baseUrl, apiKey, callback);
            }
        }, "CodexModelFetch").start();
    }

    /* JADX WARN: Removed duplicated region for block: B:77:0x0221  */
    /* JADX WARN: Removed duplicated region for block: B:78:0x022a  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public /* synthetic */ void lambda$fetchModels$27$CodexHomeActivity(final String baseUrl, final String apiKey, final ModelsCallback callback) {
        ArrayList<CodexProviderStore.ModelConfig> models = new ArrayList<>();
        String error = null;
        HttpURLConnection connection = null;
        try {
            String base = baseUrl == null ? "" : baseUrl.trim();
            while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            connection = (HttpURLConnection) openAppUrlConnection(new URL(base + "/models"));
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json");
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            StringBuilder body = new StringBuilder();
            if (stream != null) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = stream.read(buffer)) >= 0) body.append(new String(buffer, 0, count, StandardCharsets.UTF_8));
                stream.close();
            }
            if (status >= 400) throw new IOException("HTTP " + status + (body.length() == 0 ? "" : ": " + body));
            JSONObject root = new JSONObject(body.toString());
            JSONArray data = root.optJSONArray("data");
            if (data == null) data = root.optJSONArray("models");
            if (data == null) throw new IOException("\u54cd\u5e94\u4e2d\u6ca1\u6709 models/data");
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;
                CodexProviderStore.ModelConfig modelConfig = CodexProviderStore.ModelConfig.from(item);
                if (!modelConfig.id.isEmpty()) models.add(modelConfig);
            }
            if (models.isEmpty()) throw new IOException("\u6ca1\u6709\u53ef\u7528\u6a21\u578b");
        } catch (Exception e) {
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        } finally {
            if (connection != null) connection.disconnect();
        }
        final String failure = error;
        final ArrayList<CodexProviderStore.ModelConfig> result = models;
        runOnUiThread(() -> callback.complete(result, failure));
    }

    private void fetchAndChooseModels(String url, String key, final ArrayList<CodexProviderStore.ModelConfig> catalog, final String[] selectedModel, final LinearLayout modelList, final Button button) {
        if (url.isEmpty() || key.isEmpty()) {
            Toast.makeText(this, "请先填写 URL 和 API Key", 0).show();
            return;
        }
        button.setEnabled(false);
        button.setText("获取中…");
        fetchModels(url, key, new ModelsCallback() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$zgeEtJn64OQzMsrS7575DgLLM3Q
            @Override // com.termux.app.CodexHomeActivity.ModelsCallback
            public final void complete(ArrayList arrayList, String str) {
                CodexHomeActivity.this.lambda$fetchAndChooseModels$30$CodexHomeActivity(button, catalog, selectedModel, modelList, arrayList, str);
            }
        });
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r10v0 */
    /* JADX WARN: Type inference failed for: r10v4 */
    public /* synthetic */ void lambda$fetchAndChooseModels$30$CodexHomeActivity(Button button, final ArrayList catalog, final String[] selectedModel, final LinearLayout modelList, ArrayList models, String error) {
        LinearLayout content;
        int i = 1;
        button.setEnabled(true);
        button.setText("获取模型");
        if (error != null) {
            Toast.makeText(this, "获取失败：" + error, 1).show();
            return;
        }
        LinearLayout content2 = new LinearLayout(this);
        content2.setOrientation(1);
        int i2 = 16;
        content2.setPadding(dp(16), dp(4), dp(16), dp(12));
        TextView hint = text("点击右侧「选择」，确认名称、ID 和上下文后加入当前配置。", 13.0f, MUTED);
        LinearLayout.LayoutParams hp = wrap();
        int i3 = 10;
        hp.bottomMargin = dp(10);
        content2.addView(hint, hp);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("获取到 " + models.size() + " 个模型").setNegativeButton("关闭", (DialogInterface.OnClickListener) null).create();
        Iterator it = models.iterator();
        while (it.hasNext()) {
            final CodexProviderStore.ModelConfig fetched = (CodexProviderStore.ModelConfig) it.next();
            LinearLayout row = new LinearLayout(this);
            row.setGravity(i2);
            row.setPadding(dp(13), dp(i3), dp(7), dp(i3));
            row.setBackground(interactiveBackground(SURFACE, Color.rgb(238, 218, 203), 17));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(i);
            TextView idView = text(fetched.id, 14.0f, TEXT);
            idView.setTypeface(Typeface.DEFAULT, i);
            idView.setSingleLine(true);
            idView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            info.addView(idView);
            String details = modelDisplayName(fetched);
            if (details.equals(fetched.id)) {
                details = "可用模型";
            }
            if (fetched.contextWindow <= 0) {
                content = content2;
            } else {
                StringBuilder sbAppend = new StringBuilder().append(details).append("  ·  ");
                Locale locale = Locale.US;
                Object[] objArr = new Object[i];
                content = content2;
                objArr[0] = Long.valueOf(fetched.contextWindow);
                details = sbAppend.append(String.format(locale, "%,d", objArr)).append(" tokens").toString();
            }
            TextView detailView = text(details, 12.0f, MUTED);
            LinearLayout.LayoutParams dip = wrap();
            dip.topMargin = dp(3);
            info.addView(detailView, dip);
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1.0f));
            Button choose = compactButton("选择");
            final AlertDialog alertDialog = dialog;
            choose.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$HdHdfAPhbwGz6uIzxw-lEWKytm4
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    CodexHomeActivity.this.lambda$fetchAndChooseModels$28$CodexHomeActivity(catalog, fetched, alertDialog, selectedModel, modelList, view);
                }
            });
            row.addView(choose);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(RAISED, -2);
            rp.bottomMargin = dp(8);
            LinearLayout content3 = content;
            content3.addView(row, rp);
            content2 = content3;
            dialog = dialog;
            i = 1;
            i2 = 16;
            i3 = 10;
        }
        final AlertDialog dialog2 = dialog;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(content2, new FrameLayout.LayoutParams(RAISED, -2));
        dialog2.setView(scroll);
        dialog2.setOnShowListener(new DialogInterface.OnShowListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$12J-V-jd7hWT9x6R41O1bIu5uMQ
            @Override // android.content.DialogInterface.OnShowListener
            public final void onShow(DialogInterface dialogInterface) {
                CodexHomeActivity.this.lambda$fetchAndChooseModels$29$CodexHomeActivity(dialog2, dialogInterface);
            }
        });
        dialog2.show();
    }

    public /* synthetic */ void lambda$fetchAndChooseModels$28$CodexHomeActivity(ArrayList catalog, CodexProviderStore.ModelConfig fetched, AlertDialog dialog, String[] selectedModel, LinearLayout modelList, View v) {
        CodexProviderStore.ModelConfig existing = findModel(catalog, fetched.id);
        dialog.dismiss();
        lambda$renderModelCatalog$13$CodexHomeActivity(existing, fetched, catalog, selectedModel, modelList);
    }

    public /* synthetic */ void lambda$fetchAndChooseModels$29$CodexHomeActivity(AlertDialog dialog, DialogInterface d) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(rounded(SURFACE, 26, 0, 0));
        }
    }

    private void testProvider(final CodexProviderStore.Profile profile, final Button button) {
        if (profile.baseUrl == null || profile.baseUrl.trim().isEmpty() || profile.apiKey == null || profile.apiKey.trim().isEmpty()) {
            Toast.makeText(this, "请先填写 URL 和 API Key", 0).show();
            return;
        }
        button.setEnabled(false);
        button.setText("测试中…");
        new Thread(new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$qT-Y1fJ-vJtjCvvJfn7FWQxRW2A
            @Override // java.lang.Runnable
            public final void run() {
                try { CodexHomeActivity.this.lambda$testProvider$32$CodexHomeActivity(profile, button); }
                catch (Exception e) { runOnUiThread(() -> { button.setEnabled(true); button.setText("\u6d4b\u8bd5\u8fde\u63a5"); Toast.makeText(CodexHomeActivity.this, "\u6d4b\u8bd5\u5931\u8d25\uff1a" + e.getMessage(), Toast.LENGTH_LONG).show(); }); }
            }
        }, "CodexProviderTest").start();
    }

    public /* synthetic */ void lambda$testProvider$32$CodexHomeActivity(CodexProviderStore.Profile profile, final Button button) throws IOException {
        String result;
        boolean z;
        boolean ok = false;
        try {
            String base = profile.baseUrl.trim();
            while (true) {
                z = true;
                if (!base.endsWith("/")) {
                    break;
                } else {
                    base = base.substring(0, base.length() - 1);
                }
            }
            HttpURLConnection c = (HttpURLConnection) openAppUrlConnection(new URL(base + "/models"));
            c.setRequestMethod("GET");
            c.setConnectTimeout(12000);
            c.setReadTimeout(12000);
            c.setRequestProperty("Authorization", "Bearer " + profile.apiKey);
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) {
                z = false;
            }
            ok = z;
            result = ok ? "连接成功" : "HTTP " + code;
            c.disconnect();
        } catch (Exception e) {
            result = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        final String message = result;
        final boolean success = ok;
        runOnUiThread(new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$7HBt9W6FXONxJRjHXBibd8B3pIg
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.lambda$testProvider$31$CodexHomeActivity(button, message, success);
            }
        });
    }

    public /* synthetic */ void lambda$testProvider$31$CodexHomeActivity(Button button, String str, boolean z) {
        button.setEnabled(true);
        button.setText("测试连接");
        Toast.makeText(this, str, !z ? 1 : 0).show();
    }

    public void lambda$buildConfigPage$2$CodexHomeActivity(View v) {
        saveConfiguration();
    }

    public void lambda$buildConfigPage$3$CodexHomeActivity(View v) {
        installInternalRuntime(false);
    }

    public void lambda$buildConfigPage$4$CodexHomeActivity(View v) {
        installDevelopmentExtensions();
    }

    private ScrollView buildSettingsPage() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(BG);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(1);
        panel.setPadding(dp(20), dp(18), dp(20), dp(30));
        TextView title = text("设置", 28.0f, TEXT);
        title.setTypeface(Typeface.DEFAULT, 1);
        title.setIncludeFontPadding(false);
        panel.addView(title);
        TextView intro = text("根据你的使用方式调整 Codex", 13.0f, MUTED);
        LinearLayout.LayoutParams introParams = wrap();
        introParams.topMargin = dp(6);
        introParams.bottomMargin = dp(18);
        panel.addView(intro, introParams);
        addSettingsSection(panel, "界面");
        SettingToggle fullScreen = settingSwitch("WebUI 全屏", "进入 WebUI 时隐藏原生工具栏", this.prefs.getBoolean("webui_fullscreen", true));
        fullScreen.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$rI27Ty_Kk3GK_ZluP3vaI7bT42U
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                CodexHomeActivity.this.lambda$buildSettingsPage$33$CodexHomeActivity(compoundButton, z);
            }
        });
        panel.addView(fullScreen, new LinearLayout.LayoutParams(RAISED, dp(76)));
        SettingToggle restartBackend = settingSwitch("切换配置后重启", "切换 API 后自动重启 WebUI 和 Codex 后端", this.prefs.getBoolean("restart_backend_on_config_change", true));
        restartBackend.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$3bmMN4LCl1RIw_ZDc-ufWkmsBs4
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                CodexHomeActivity.this.lambda$buildSettingsPage$34$CodexHomeActivity(compoundButton, z);
            }
        });
        panel.addView(restartBackend, new LinearLayout.LayoutParams(RAISED, dp(76)));
        addSettingsSection(panel, "项目与存储");
        SettingToggle customRoot = settingSwitch("自定义项目默认读取", "使用指定目录作为项目默认位置", this.prefs.getBoolean("custom_project_root_enabled", true));
        panel.addView(customRoot, new LinearLayout.LayoutParams(RAISED, dp(76)));
        final EditText path = field("/storage/emulated/0/", this.prefs.getString("custom_project_root", "/storage/emulated/0/"), false);
        path.setVisibility(customRoot.isChecked() ? 0 : 8);
        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(RAISED, dp(54));
        pathParams.topMargin = dp(6);
        pathParams.bottomMargin = dp(10);
        panel.addView(path, pathParams);
        customRoot.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$9mezhzca18R4d2G1mXMGB5HAqCo
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                CodexHomeActivity.this.lambda$buildSettingsPage$35$CodexHomeActivity(path, compoundButton, z);
            }
        });
        path.setOnFocusChangeListener(new View.OnFocusChangeListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$iz8o4a7mBzv_rYo5HE-Hi14FiBk
            @Override // android.view.View.OnFocusChangeListener
            public final void onFocusChange(View view, boolean z) {
                CodexHomeActivity.this.lambda$buildSettingsPage$36$CodexHomeActivity(path, view, z);
            }
        });
        addSettingsSection(panel, "网络与代理");
        panel.addView(settingsAction("Mihomo 内置代理", "固定版本内核、订阅配置与 MetaCubeXD 网页控制台", "可选扩展", new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$nfwFz021pvvV0hk79y4NFoY3_wU
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.showMihomoSettings();
            }
        }));
        addSettingsSection(panel, "WebUI");
        panel.addView(settingsAction("重载 WebUI", "保留设置并重新载入页面", "", new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$PZCskt94wC8tMF32sNzuNauT3yE
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.lambda$buildSettingsPage$37$CodexHomeActivity();
            }
        }));
        panel.addView(settingsAction("重启 WebUI 和 Codex 后端", "切换 API 、修改模型后立即应用新配置", "", new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$56ijD49qRDHg9vUsdXTyE9gyh2c
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.restartWebUiAndBackend();
            }
        }));
        panel.addView(settingsAction("清理 WebUI 缓存", "清除离线页面缓存，不删除设置", "", new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$SuyUlBrSU3h4GNLRgxkH-WavLvg
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.lambda$buildSettingsPage$38$CodexHomeActivity();
            }
        }));
        addSettingsSection(panel, "运行环境");
        panel.addView(settingsAction("安装 / 更新 Codex CLI", "保留会话、技能和配置文件", "", new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$__vgKt7bxGBikFLB3DcbvgME-hU
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.lambda$buildSettingsPage$39$CodexHomeActivity();
            }
        }));
        panel.addView(settingsAction("安装开发工具扩展", "Git、Python、Node.js、Clang、Rust 和 Go", "", new Runnable() { // from class: com.termux.app.-$$Lambda$7UqddL6kfzocgBL6O6siM6N5EUw
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.installDevelopmentExtensions();
            }
        }));
        panel.addView(settingsAction("打开 Termux 终端", "检查工具、技能或管理项目文件", "", this::openInternalTerminal));
        addSettingsSection(panel, "\u5feb\u6377\u65b9\u5f0f");
        panel.addView(settingsAction("\u6dfb\u52a0 WebUI \u5230\u684c\u9762", "\u4ece\u684c\u9762\u4e00\u952e\u8fdb\u5165 Codex WebUI", "WebUI", () -> pinLauncherShortcut(false)));
        panel.addView(settingsAction("\u6dfb\u52a0 Termux \u5230\u684c\u9762", "\u4ece\u684c\u9762\u4e00\u952e\u542f\u52a8\u5185\u7f6e\u7ec8\u7aef", "CLI", () -> pinLauncherShortcut(true)));
        addSettingsSection(panel, "\u540e\u53f0\u4e0e\u63d0\u9192");
        panel.addView(settingsAction("Codex \u60ac\u6d6e\u7a97", "\u53ef\u79fb\u52a8\u56fe\u6807\u3001\u540e\u53f0\u4fdd\u6d3b\u4e0e\u4efb\u52a1\u5b8c\u6210\u63d0\u9192",
            prefs.getBoolean("overlay_enabled", false) ? "\u5df2\u5f00\u542f" : "\u672a\u5f00\u542f", this::showOverlaySettings));
        addSettingsSection(panel, "\u6570\u636e");
        panel.addView(settingsAction("重置 WebUI 偏好", "不会删除会话、技能和 Codex 配置", "", new Runnable() { // from class: com.termux.app.-$$Lambda$z1zFGaOP-I6T_fNdTuUSSs6KfIc
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.confirmResetWebUiPreferences();
            }
        }));
        addSettingsSection(panel, "\u5173\u4e8e");
        panel.addView(settingsAction("\u5173\u4e8e Fcode", "\u7248\u672c\u4fe1\u606f\u3001\u4f5c\u8005\u4e0e\u5e94\u7528\u8bf4\u660e", "ILY_op", this::showAboutPage));
        scrollView.addView(panel, new FrameLayout.LayoutParams(RAISED, -2));
        return scrollView;
    }

    public /* synthetic */ void lambda$buildSettingsPage$33$CodexHomeActivity(CompoundButton button, boolean checked) {
        this.prefs.edit().putBoolean("webui_fullscreen", checked).apply();
    }

    public /* synthetic */ void lambda$buildSettingsPage$34$CodexHomeActivity(CompoundButton button, boolean checked) {
        this.prefs.edit().putBoolean("restart_backend_on_config_change", checked).apply();
    }

    public /* synthetic */ void lambda$buildSettingsPage$35$CodexHomeActivity(EditText path, CompoundButton button, boolean checked) {
        this.prefs.edit().putBoolean("custom_project_root_enabled", checked).apply();
        path.setVisibility(checked ? 0 : 8);
        if (checked) {
            saveCustomProjectPath(path);
        }
    }

    public /* synthetic */ void lambda$buildSettingsPage$36$CodexHomeActivity(EditText path, View v, boolean focused) {
        if (focused) {
            return;
        }
        saveCustomProjectPath(path);
    }

    public /* synthetic */ void lambda$buildSettingsPage$37$CodexHomeActivity() {
        if (this.webUiLoaded) {
            this.webView.reload();
        }
        Toast.makeText(this, "WebUI 已重载", 0).show();
    }

    public /* synthetic */ void lambda$buildSettingsPage$38$CodexHomeActivity() {
        this.webView.clearCache(true);
        Toast.makeText(this, "缓存已清理", 0).show();
    }

    public /* synthetic */ void lambda$buildSettingsPage$39$CodexHomeActivity() {
        installInternalRuntime(false);
    }

    public void lambda$buildSettingsPage$5$CodexHomeActivity() {
        if (this.webUiLoaded) {
            this.webView.reload();
        }
        Toast.makeText(this, "WebUI 已重载", 0).show();
    }

    public void lambda$buildSettingsPage$6$CodexHomeActivity() {
        this.webView.clearCache(true);
        Toast.makeText(this, "WebUI 缓存已清理", 0).show();
    }

    public void lambda$buildSettingsPage$7$CodexHomeActivity() {
        installInternalRuntime(false);
    }

    private void pinLauncherShortcut(boolean terminal) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) { Toast.makeText(this, "\u5f53\u524d\u684c\u9762\u4e0d\u652f\u6301\u5e94\u7528\u5185\u6dfb\u52a0\u5feb\u6377\u56fe\u6807", Toast.LENGTH_SHORT).show(); return; }
        ShortcutManager manager = getSystemService(ShortcutManager.class);
        if (manager == null || !manager.isRequestPinShortcutSupported()) { Toast.makeText(this, "\u5f53\u524d\u684c\u9762\u4e0d\u652f\u6301\u56fa\u5b9a\u5feb\u6377\u56fe\u6807", Toast.LENGTH_SHORT).show(); return; }
        String id = terminal ? "codex_termux" : "codex_webui"; String label = terminal ? "Fcode Termux" : "Fcode WebUI";
        Intent launch = new Intent(this, CodexHomeActivity.class).setAction(terminal ? ACTION_OPEN_TERMUX : ACTION_OPEN_WEBUI).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        ShortcutInfo shortcut = new ShortcutInfo.Builder(this, id).setShortLabel(label).setLongLabel(terminal ? "\u542f\u52a8 Codex \u5185\u7f6e Termux" : "\u6253\u5f00 Codex WebUI").setIcon(Icon.createWithResource(this, terminal ? com.termux.R.drawable.ic_new_session : com.termux.R.drawable.ic_codex_logo)).setIntent(launch).build();
        Toast.makeText(this, manager.requestPinShortcut(shortcut, null) ? "\u5df2\u8bf7\u6c42\u5c06 " + label + " \u6dfb\u52a0\u5230\u684c\u9762" : "\u684c\u9762\u5feb\u6377\u56fe\u6807\u6dfb\u52a0\u5931\u8d25", Toast.LENGTH_SHORT).show();
    }

    private ScrollView buildAboutPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(36));
        Button back = compactButton("\u2190  \u8bbe\u7f6e");
        back.setOnClickListener(v -> showSettings());
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(-2, dp(42));
        backParams.gravity = Gravity.START;
        panel.addView(back, backParams);
        ImageView logo = new ImageView(this);
        logo.setImageResource(com.termux.R.drawable.ic_codex_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setContentDescription("Fcode");
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(88), dp(88));
        logoParams.topMargin = dp(34);
        panel.addView(logo, logoParams);
        TextView title = text("Fcode", 30.0f, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleParams = wrap();
        titleParams.topMargin = dp(16);
        panel.addView(title, titleParams);
        TextView description = text("Codex Mobile \u5de5\u4f5c\u73af\u5883", 14.0f, MUTED);
        description.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descParams = wrap();
        descParams.topMargin = dp(7);
        descParams.bottomMargin = dp(28);
        panel.addView(description, descParams);
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(17), dp(15), dp(17), dp(15));
        info.setBackground(rounded(SURFACE, 24, 0, 0));
        TextView authorLabel = text("\u4f5c\u8005", 12.0f, MUTED);
        TextView author = text("ILY_op", 18.0f, TEXT);
        author.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams authorParams = wrap();
        authorParams.topMargin = dp(5);
        info.addView(authorLabel);
        info.addView(author, authorParams);
        TextView versionLabel = text("\u7248\u672c", 12.0f, MUTED);
        LinearLayout.LayoutParams versionLabelParams = wrap();
        versionLabelParams.topMargin = dp(18);
        info.addView(versionLabel, versionLabelParams);
        TextView version = text(BuildConfig.VERSION_NAME + "  (" + BuildConfig.VERSION_CODE + ")", 16.0f, TEXT);
        version.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams versionParams = wrap();
        versionParams.topMargin = dp(5);
        info.addView(version, versionParams);
        TextView packageLabel = text("\u5305\u540d", 12.0f, MUTED);
        LinearLayout.LayoutParams packageLabelParams = wrap();
        packageLabelParams.topMargin = dp(18);
        info.addView(packageLabel, packageLabelParams);
        TextView packageValue = text(getPackageName(), 14.0f, TEXT);
        LinearLayout.LayoutParams packageParams = wrap();
        packageParams.topMargin = dp(5);
        info.addView(packageValue, packageParams);
        panel.addView(info, new LinearLayout.LayoutParams(RAISED, -2));
        TextView footer = text("\u4e3a\u79fb\u52a8\u7aef Codex \u5de5\u4f5c\u6d41\u6253\u9020", 12.0f, MUTED);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = wrap();
        footerParams.topMargin = dp(22);
        panel.addView(footer, footerParams);
        scroll.addView(panel, new ScrollView.LayoutParams(RAISED, -2));
        return scroll;
    }

    private void showAboutPage() {
        closeDrawer();
        if (this.configFab != null) this.configFab.setVisibility(View.GONE);
        this.pageTitle.setText("\u5173\u4e8e Fcode");
        this.topToolbar.setVisibility(View.VISIBLE);
        this.homePage.setVisibility(View.GONE);
        this.configPage.setVisibility(View.GONE);
        this.settingsPage.setVisibility(View.GONE);
        this.overlaySettingsPage.setVisibility(View.GONE);
        this.mihomoPage.setVisibility(View.GONE);
        this.webView.setVisibility(View.GONE);
        this.aboutPage.setVisibility(View.VISIBLE);
        fadeIn(this.aboutPage);
    }

    private ScrollView buildOverlaySettingsPage() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(20), dp(18), dp(20), dp(34));
        Button back = compactButton("\u2190  \u8bbe\u7f6e"); back.setOnClickListener(v -> showSettings()); panel.addView(back, new LinearLayout.LayoutParams(-2, dp(42)));
        TextView title = text("Codex \u60ac\u6d6e\u7a97", 28, TEXT); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD); title.setIncludeFontPadding(false); LinearLayout.LayoutParams tp = wrap(); tp.topMargin = dp(18); panel.addView(title, tp);
        TextView intro = text("\u8ba9 Codex \u5728\u540e\u53f0\u4fdd\u6301\u8fde\u63a5\uff0c\u5e76\u901a\u8fc7\u53ef\u79fb\u52a8\u56fe\u6807\u63d0\u9192\u4efb\u52a1\u72b6\u6001", 13, MUTED); LinearLayout.LayoutParams ip = wrap(); ip.topMargin = dp(7); ip.bottomMargin = dp(18); panel.addView(intro, ip);
        boolean enabled = prefs.getBoolean("overlay_enabled", false) && Settings.canDrawOverlays(this);
        SettingToggle overlay = settingSwitch("\u542f\u7528\u60ac\u6d6e\u56fe\u6807", "\u663e\u793a Codex \u56fe\u6807\uff1b\u9ed8\u8ba4\u5355\u51fb\u67e5\u770b\u4efb\u52a1\u3001\u53cc\u51fb\u6253\u5f00 WebUI", enabled);
        overlay.setOnCheckedChangeListener((button, checked) -> { if (checked) requestEnableOverlay(); else { prefs.edit().putBoolean("overlay_enabled", false).apply(); CodexOverlayService.stop(this); Toast.makeText(this, "\u60ac\u6d6e\u7a97\u5df2\u5173\u95ed", Toast.LENGTH_SHORT).show(); } }); panel.addView(overlay, new LinearLayout.LayoutParams(-1, dp(80)));
        SettingToggle edgeSnap = settingSwitch("\u62d6\u52a8\u540e\u81ea\u52a8\u8d34\u8fb9", "\u677e\u624b\u540e\u5438\u9644\u5230\u6700\u8fd1\u7684\u5de6\u53f3\u8fb9\u7f18\uff0c\u5e76\u6309\u5c4f\u5e55\u9ad8\u5ea6\u6bd4\u4f8b\u4fdd\u5b58\u4f4d\u7f6e", prefs.getBoolean("overlay_edge_snap", true));
        edgeSnap.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean("overlay_edge_snap", checked).apply()); panel.addView(edgeSnap, new LinearLayout.LayoutParams(-1, dp(80)));
        addSettingsSection(panel, "\u60ac\u6d6e\u7403\u624b\u52bf");
        addOverlayGestureSetting(panel, "\u5355\u51fb\u60ac\u6d6e\u7403", "overlay_ball_tap_action", "tasks");
        addOverlayGestureSetting(panel, "\u53cc\u51fb\u60ac\u6d6e\u7403", "overlay_ball_double_action", "open");
        addOverlayGestureSetting(panel, "\u957f\u6309\u60ac\u6d6e\u7403", "overlay_ball_long_action", "snap");
        addSettingsSection(panel, "\u4efb\u52a1\u6c14\u6ce1\u624b\u52bf");
        addOverlayGestureSetting(panel, "\u5355\u51fb\u4efb\u52a1\u6c14\u6ce1", "overlay_bubble_tap_action", "open");
        addOverlayGestureSetting(panel, "\u53cc\u51fb\u4efb\u52a1\u6c14\u6ce1", "overlay_bubble_double_action", "toggle_bubble");
        addOverlayGestureSetting(panel, "\u957f\u6309\u4efb\u52a1\u6c14\u6ce1", "overlay_bubble_long_action", "open");
        addSettingsSection(panel, "\u4efb\u52a1\u5b8c\u6210\u63d0\u9192");
        SettingToggle bubble = settingSwitch("\u60ac\u6d6e\u6c14\u6ce1", "\u5bf9\u8bdd\u5b8c\u6210\u65f6\u5728\u60ac\u6d6e\u56fe\u6807\u65c1\u663e\u793a\u300c\u4efb\u52a1\u5df2\u5b8c\u6210\u300d", prefs.getBoolean("completion_bubble", true)); bubble.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean("completion_bubble", checked).apply()); panel.addView(bubble, new LinearLayout.LayoutParams(-1, dp(80)));
        SettingToggle notification = settingSwitch("\u7cfb\u7edf\u901a\u77e5", "\u5bf9\u8bdd\u5b8c\u6210\u65f6\u53d1\u9001\u53ef\u70b9\u51fb\u7684\u5b8c\u6210\u901a\u77e5", prefs.getBoolean("completion_notification", false)); notification.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean("completion_notification", checked).apply()); panel.addView(notification, new LinearLayout.LayoutParams(-1, dp(80)));
        addSettingsSection(panel, "\u540e\u53f0\u8fde\u63a5");
        LinearLayout keepAlive = new LinearLayout(this); keepAlive.setOrientation(LinearLayout.VERTICAL); keepAlive.setPadding(dp(16), dp(15), dp(16), dp(15)); keepAlive.setBackground(rounded(SURFACE, 22, 0, 0)); TextView kt = text("\u524d\u53f0\u670d\u52a1 + Wi-Fi \u4fdd\u6301", 16, TEXT); kt.setTypeface(Typeface.DEFAULT, Typeface.BOLD); keepAlive.addView(kt); TextView kd = text("\u5f00\u542f\u60ac\u6d6e\u7a97\u540e\uff0c\u5e94\u7528\u4f1a\u4f7f\u7528\u5e38\u9a7b\u901a\u77e5\u3001CPU \u5524\u9192\u9501\u548c Wi-Fi \u9501\uff0c\u964d\u4f4e\u5207\u5230\u540e\u53f0\u540e\u65ad\u7f51\u7684\u6982\u7387\u3002", 13, MUTED); kd.setLineSpacing(dp(3), 1f); LinearLayout.LayoutParams kdp = wrap(); kdp.topMargin = dp(7); keepAlive.addView(kd, kdp); panel.addView(keepAlive, wrap());
        panel.addView(settingsAction("\u5141\u8bb8\u540e\u53f0\u4e0d\u53d7\u9650", "\u6253\u5f00\u7cfb\u7edf\u7535\u6c60\u4f18\u5316\u9875\uff0c\u5efa\u8bae\u5141\u8bb8 Codex \u540e\u53f0\u8fd0\u884c", "\u7cfb\u7edf\u8bbe\u7f6e", this::requestIgnoreBatteryOptimizations));
        scroll.addView(panel, new ScrollView.LayoutParams(-1, -2)); return scroll;
    }


    private void addOverlayGestureSetting(LinearLayout panel, String title, String key, String fallback) {
        String current = prefs.getString(key, fallback);
        panel.addView(settingsAction(title, "\u9009\u62e9\u89e6\u53d1\u8be5\u624b\u52bf\u65f6\u6267\u884c\u7684\u64cd\u4f5c", CodexOverlayService.actionLabel(current),
            () -> showOverlayGestureDialog(title, key, fallback)));
    }

    private void showOverlayGestureDialog(String title, String key, String fallback) {
        String current = prefs.getString(key, fallback);
        int checked = 0;
        for (int i = 0; i < CodexOverlayService.GESTURE_ACTION_VALUES.length; i++)
            if (CodexOverlayService.GESTURE_ACTION_VALUES[i].equals(current)) { checked = i; break; }
        new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(CodexOverlayService.GESTURE_ACTION_LABELS, checked,
            (dialog, which) -> {
                prefs.edit().putString(key, CodexOverlayService.GESTURE_ACTION_VALUES[which]).apply();
                dialog.dismiss(); showOverlaySettings();
            }).setNegativeButton("\u53d6\u6d88", null).show();
    }

    private void showOverlaySettings() {
        closeDrawer(); if (configFab != null) configFab.setVisibility(View.GONE); if (overlaySettingsPage != null) contentHost.removeView(overlaySettingsPage); overlaySettingsPage = buildOverlaySettingsPage(); contentHost.addView(overlaySettingsPage, match()); pageTitle.setText("\u60ac\u6d6e\u7a97"); topToolbar.setVisibility(View.VISIBLE); homePage.setVisibility(View.GONE); configPage.setVisibility(View.GONE); settingsPage.setVisibility(View.GONE); mihomoPage.setVisibility(View.GONE); webView.setVisibility(View.GONE); if (aboutPage != null) aboutPage.setVisibility(View.GONE); overlaySettingsPage.setVisibility(View.VISIBLE); fadeIn(overlaySettingsPage);
    }

    private void requestStartupPermissions() {
        if (this.prefs == null || this.prefs.getBoolean("startup_permissions_prompted_v1", false)) return;
        this.prefs.edit().putBoolean("startup_permissions_prompted_v1", true).apply();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST);
            return;
        }
        requestStartupOverlayPermission();
    }

    private void requestStartupOverlayPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
            requestStartupBatteryPermission();
            return;
        }
        this.prefs.edit().putBoolean("overlay_enable_pending", true).putBoolean("startup_overlay_flow", true).apply();
        try {
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())), OVERLAY_PERMISSION_REQUEST);
        } catch (Exception error) {
            this.prefs.edit().putBoolean("overlay_enable_pending", false).putBoolean("startup_overlay_flow", false).apply();
            requestStartupBatteryPermission();
        }
    }

    private void requestStartupBatteryPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || this.prefs.getBoolean("startup_battery_prompted_v1", false)) return;
        this.prefs.edit().putBoolean("startup_battery_prompted_v1", true).apply();
        try {
            android.os.PowerManager power = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            if (power != null && !power.isIgnoringBatteryOptimizations(getPackageName())) {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
            }
        } catch (Exception ignored) {}
    }

    private void requestEnableOverlay() {
        this.prefs.edit().putBoolean("startup_overlay_flow", false).apply();
        if (Settings.canDrawOverlays(this)) { prefs.edit().putBoolean("overlay_enabled", true).apply(); CodexOverlayService.start(this); Toast.makeText(this, "Codex \u60ac\u6d6e\u7a97\u5df2\u5f00\u542f", Toast.LENGTH_SHORT).show(); return; }
        prefs.edit().putBoolean("overlay_enable_pending", true).apply(); try { startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())), OVERLAY_PERMISSION_REQUEST); Toast.makeText(this, "\u8bf7\u5141\u8bb8 Codex \u663e\u793a\u5728\u5176\u4ed6\u5e94\u7528\u4e0a\u5c42", Toast.LENGTH_LONG).show(); } catch (Exception error) { prefs.edit().putBoolean("overlay_enable_pending", false).apply(); Toast.makeText(this, "\u65e0\u6cd5\u6253\u5f00\u60ac\u6d6e\u7a97\u6743\u9650\u8bbe\u7f6e", Toast.LENGTH_SHORT).show(); }
    }

    private void requestIgnoreBatteryOptimizations() {
        try { android.os.PowerManager power = (android.os.PowerManager) getSystemService(POWER_SERVICE); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !power.isIgnoringBatteryOptimizations(getPackageName())) startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))); else Toast.makeText(this, "Codex \u5df2\u5141\u8bb8\u540e\u53f0\u4e0d\u53d7\u9650", Toast.LENGTH_SHORT).show(); } catch (Exception error) { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
    }

    private View buildMihomoPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(1);
        page.setBackgroundColor(BG);
        FrameLayout frameLayout = new FrameLayout(this);
        this.mihomoTabHost = frameLayout;
        page.addView(frameLayout, new LinearLayout.LayoutParams(RAISED, 0, 1.0f));
        LinearLayout linearLayout = new LinearLayout(this);
        this.mihomoBottomNav = linearLayout;
        linearLayout.setOrientation(0);
        this.mihomoBottomNav.setGravity(17);
        this.mihomoBottomNav.setPadding(dp(8), dp(7), dp(8), dp(7));
        this.mihomoBottomNav.setBackgroundColor(SURFACE);
        String[] labels = {"首页", "代理", "订阅", "设置"};
        for (int i = 0; i < labels.length; i++) {
            final int tab = i;
            TextView item = text(labels[i], 13.0f, TEXT);
            item.setGravity(17);
            item.setTypeface(Typeface.DEFAULT, 1);
            item.setIncludeFontPadding(false);
            item.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$qSpYGudG1QhAgV45wXHZvMawfFE
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    CodexHomeActivity.this.lambda$buildMihomoPage$40$CodexHomeActivity(tab, view);
                }
            });
            this.mihomoBottomNav.addView(item, new LinearLayout.LayoutParams(0, dp(58), 1.0f));
        }
        page.addView(this.mihomoBottomNav, new LinearLayout.LayoutParams(RAISED, dp(72)));
        showMihomoTab(0);
        return page;
    }

    public /* synthetic */ void lambda$buildMihomoPage$40$CodexHomeActivity(int tab, View v) {
        showMihomoTab(tab);
    }

    private void showMihomoTab(int tab) {
        View content;
        if (this.mihomoTabHost == null) {
            return;
        }
        int iMax = Math.max(0, Math.min(3, tab));
        this.mihomoSelectedTab = iMax;
        switch (iMax) {
            case 1:
                content = buildMihomoProxiesTab();
                break;
            case 2:
                content = buildMihomoSubscriptionsTab();
                break;
            case 3:
                content = buildMihomoSettingsTab();
                break;
            default:
                content = buildMihomoHomeTab();
                break;
        }
        this.mihomoTabHost.removeAllViews();
        this.mihomoTabHost.addView(content, match());
        int i = 0;
        while (i < this.mihomoBottomNav.getChildCount()) {
            TextView item = (TextView) this.mihomoBottomNav.getChildAt(i);
            boolean active = i == this.mihomoSelectedTab;
            item.setTextColor(active ? GREEN : MUTED);
            item.setBackground(interactiveBackground(active ? Color.rgb(226, 240, 231) : 0, Color.rgb(232, 216, 203), 20));
            i++;
        }
        fadeIn(content);
    }

    private ScrollView tabScroll(LinearLayout panel) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.addView(panel, new FrameLayout.LayoutParams(RAISED, -2));
        return scroll;
    }

    private LinearLayout tabPanel(String titleValue, String subtitleValue) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(1);
        panel.setPadding(dp(20), dp(18), dp(20), dp(30));
        TextView title = text(titleValue, 27.0f, TEXT);
        title.setTypeface(Typeface.DEFAULT, 1);
        title.setIncludeFontPadding(false);
        panel.addView(title);
        TextView subtitle = text(subtitleValue, 13.0f, MUTED);
        subtitle.setIncludeFontPadding(false);
        LinearLayout.LayoutParams subtitleParams = wrap();
        subtitleParams.topMargin = dp(6);
        subtitleParams.bottomMargin = dp(18);
        panel.addView(subtitle, subtitleParams);
        return panel;
    }

    private ScrollView buildMihomoHomeTab() {
        String activeDetail;
        LinearLayout linearLayoutTabPanel = tabPanel("代理首页", "启动、运行状态与当前订阅");
        boolean installed = this.mihomoManager.isInstalled();
        boolean running = this.mihomoManager.isRunning();
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(1);
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        hero.setBackground(rounded(SURFACE, 26, 0, 0));
        int i = MUTED;
        TextView eyebrow = text("Mihomo 内置代理", 13.0f, i);
        eyebrow.setTypeface(Typeface.DEFAULT, 1);
        hero.addView(eyebrow);
        TextView textViewText = text(!installed ? "未安装" : running ? "运行中" : "已停止", 25.0f, running ? GREEN : installed ? TEXT : AMBER);
        this.mihomoStatus = textViewText;
        textViewText.setTypeface(Typeface.DEFAULT, 1);
        LinearLayout.LayoutParams statusParams = wrap();
        statusParams.topMargin = dp(8);
        hero.addView(this.mihomoStatus, statusParams);
        TextView textViewText2 = text("Mixed Port  127.0.0.1:" + this.mihomoManager.mixedPort() + "\nController  127.0.0.1:" + this.mihomoManager.controllerPort(), 12.0f, i);
        this.mihomoRuntimeDetails = textViewText2;
        textViewText2.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams detailsParams = wrap();
        detailsParams.topMargin = dp(8);
        hero.addView(this.mihomoRuntimeDetails, detailsParams);
        linearLayoutTabPanel.addView(hero, wrap());
        Button buttonPrimaryButton = primaryButton(!installed ? "安装组件" : running ? "停止" : "启动");
        this.mihomoStartStopButton = buttonPrimaryButton;
        buttonPrimaryButton.setEnabled(true);
        this.mihomoStartStopButton.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$zjUWXoduFPzlA6Q3emmB4VCVy1U
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CodexHomeActivity.this.lambda$buildMihomoHomeTab$41$CodexHomeActivity(view);
            }
        });
        addButton(linearLayoutTabPanel, this.mihomoStartStopButton, 12);
        addSettingsSection(linearLayoutTabPanel, "当前订阅");
        MihomoManager.Subscription active = this.mihomoManager.activeSubscription();
        String activeName = active == null ? "暂无当前订阅" : active.name;
        if (active == null) {
            activeDetail = "请前往订阅页添加或导入配置";
        } else {
            activeDetail = active.isRemote() ? active.url : "本地配置文件";
        }
        linearLayoutTabPanel.addView(settingsAction(activeName, activeDetail, active == null ? "" : "当前使用", new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$qAoEIouLvLRZMtsgrJsilUw2lVc
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.lambda$buildMihomoHomeTab$42$CodexHomeActivity();
            }
        }));
        addSettingsSection(linearLayoutTabPanel, "应用内代理");
        SettingToggle routeApi = settingSwitch("应用内 URL 使用 Mihomo", "仅代理 Codex Mobile 自己发出的请求", this.prefs.getBoolean("mihomo_route_api", false));
        routeApi.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$aMYz9Q7LOGNWcsy8rJr7xXwAOvA
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                CodexHomeActivity.this.lambda$buildMihomoHomeTab$44$CodexHomeActivity(compoundButton, z);
            }
        });
        linearLayoutTabPanel.addView(routeApi, new LinearLayout.LayoutParams(RAISED, dp(80)));
        linearLayoutTabPanel.addView(settingsAction("打开 MetaCubeXD", "查看流量、规则和连接详情", "网页控制台", this::openMihomoDashboard));
        return tabScroll(linearLayoutTabPanel);
    }

    public /* synthetic */ void lambda$buildMihomoHomeTab$41$CodexHomeActivity(View v) {
        toggleMihomo();
    }

    public /* synthetic */ void lambda$buildMihomoHomeTab$42$CodexHomeActivity() {
        showMihomoTab(2);
    }

    public /* synthetic */ void lambda$buildMihomoHomeTab$44$CodexHomeActivity(CompoundButton button, boolean checked) {
        this.prefs.edit().putBoolean("mihomo_route_api", checked).apply();
        if (checked && !this.mihomoManager.isRunning()) {
            if (this.mihomoManager.isInstalled()) {
                runMihomoButtonAction(this.mihomoStartStopButton, "\u542f\u52a8\u4e2d\u2026", new MihomoAction() {
                    @Override public void run() throws Exception { CodexHomeActivity.this.lambda$buildMihomoHomeTab$43$CodexHomeActivity(); }
                }, "Mihomo \u5df2\u542f\u52a8");
            } else {
                Toast.makeText(this, "请先安装 Mihomo", 0).show();
            }
        }
        if (this.appServerStarted) {
            Toast.makeText(this, "重启 WebUI 和 Codex 后端后生效", 1).show();
        }
    }

    public /* synthetic */ void lambda$buildMihomoHomeTab$43$CodexHomeActivity() throws Exception {
        this.mihomoManager.start();
    }

    private View buildMihomoProxiesTab() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setBackgroundColor(BG);
        LinearLayout header = tabPanel("代理", "按代理组分页选择节点，大型订阅也能流畅切换");
        header.setPadding(dp(20), dp(18), dp(20), dp(10));
        LinearLayout linearLayout2 = new LinearLayout(this);
        this.mihomoProxySortRow = linearLayout2;
        linearLayout2.setOrientation(0);
        header.addView(this.mihomoProxySortRow, wrap());
        renderProxySortButtons();
        HorizontalScrollView horizontalScrollView = new HorizontalScrollView(this);
        this.mihomoGroupScroll = horizontalScrollView;
        horizontalScrollView.setHorizontalScrollBarEnabled(false);
        this.mihomoGroupScroll.setFillViewport(false);
        LinearLayout linearLayout3 = new LinearLayout(this);
        this.mihomoGroupTabs = linearLayout3;
        linearLayout3.setOrientation(0);
        this.mihomoGroupTabs.setPadding(0, 0, dp(4), 0);
        this.mihomoGroupScroll.addView(this.mihomoGroupTabs, new FrameLayout.LayoutParams(-2, dp(44)));
        LinearLayout.LayoutParams groupScrollParams = new LinearLayout.LayoutParams(RAISED, dp(44));
        groupScrollParams.topMargin = dp(12);
        header.addView(this.mihomoGroupScroll, groupScrollParams);
        LinearLayout linearLayout4 = new LinearLayout(this);
        this.mihomoProxyGroupHeader = linearLayout4;
        linearLayout4.setGravity(16);
        this.mihomoProxyGroupHeader.setPadding(dp(14), dp(10), dp(10), dp(10));
        this.mihomoProxyGroupHeader.setBackground(rounded(SURFACE, 22, 0, 0));
        LinearLayout groupCopy = new LinearLayout(this);
        groupCopy.setOrientation(1);
        TextView textViewText = text("", 17.0f, TEXT);
        this.mihomoProxyGroupTitle = textViewText;
        textViewText.setTypeface(Typeface.DEFAULT, 1);
        this.mihomoProxyGroupTitle.setIncludeFontPadding(false);
        TextView textViewText2 = text("", 12.0f, MUTED);
        this.mihomoProxySelectedNode = textViewText2;
        textViewText2.setIncludeFontPadding(false);
        this.mihomoProxySelectedNode.setMaxLines(1);
        this.mihomoProxySelectedNode.setEllipsize(TextUtils.TruncateAt.END);
        groupCopy.addView(this.mihomoProxyGroupTitle);
        LinearLayout.LayoutParams selectedParams = wrap();
        selectedParams.topMargin = dp(4);
        groupCopy.addView(this.mihomoProxySelectedNode, selectedParams);
        this.mihomoProxyGroupHeader.addView(groupCopy, new LinearLayout.LayoutParams(0, -2, 1.0f));
        Button buttonCapsuleButton = capsuleButton("测速", false);
        this.mihomoProxyTestButton = buttonCapsuleButton;
        this.mihomoProxyGroupHeader.addView(buttonCapsuleButton, new LinearLayout.LayoutParams(dp(76), dp(42)));
        LinearLayout.LayoutParams groupHeaderParams = wrap();
        groupHeaderParams.topMargin = dp(12);
        header.addView(this.mihomoProxyGroupHeader, groupHeaderParams);
        linearLayout.addView(header, wrap());
        FrameLayout frameLayout = new FrameLayout(this);
        this.mihomoProxyBody = frameLayout;
        linearLayout.addView(frameLayout, new LinearLayout.LayoutParams(RAISED, 0, 1.0f));
        if (!this.mihomoManager.isInstalled()) {
            showProxyMessage("请先在设置页安装 Mihomo");
        } else if (!this.mihomoManager.isRunning()) {
            showProxyStoppedState();
        } else if (!this.mihomoLoadedGroups.isEmpty()) {
            renderProxyGroupTabs();
            renderSelectedProxyGroup();
        } else {
            showProxyMessage("正在读取代理组…");
            loadProxyGroups();
        }
        return linearLayout;
    }

    private void renderProxySortButtons() {
        LinearLayout row = this.mihomoProxySortRow;
        if (row == null) {
            return;
        }
        row.removeAllViews();
        String selectedSort = this.prefs.getString("mihomo_proxy_sort", "default");
        String[][] sorts = {new String[]{"default", "默认"}, new String[]{"delay", "延迟"}, new String[]{"name", "名称"}};
        for (final String[] option : sorts) {
            Button button = capsuleButton(option[1], option[0].equals(selectedSort));
            button.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$UI0yNi9D6in884NSHoBmyoBFR6E
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    CodexHomeActivity.this.lambda$renderProxySortButtons$45$CodexHomeActivity(option, view);
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1.0f);
            if (row.getChildCount() > 0) {
                params.leftMargin = dp(7);
            }
            row.addView(button, params);
        }
    }

    public /* synthetic */ void lambda$renderProxySortButtons$45$CodexHomeActivity(String[] option, View v) {
        if (option[0].equals(this.prefs.getString("mihomo_proxy_sort", "default"))) {
            return;
        }
        this.prefs.edit().putString("mihomo_proxy_sort", option[0]).apply();
        renderProxySortButtons();
        renderSelectedProxyGroup();
    }

    private Button capsuleButton(String value, boolean active) {
        int i;
        int i2;
        int i3;
        Button button = secondaryButton(value);
        button.setTextColor(active ? RAISED : TEXT);
        int i4 = active ? GREEN : SURFACE;
        if (active) {
            i = 34;
            i2 = 99;
            i3 = 68;
        } else {
            i = 239;
            i2 = 222;
            i3 = 209;
        }
        button.setBackground(interactiveBackground(i4, Color.rgb(i, i2, i3), 22));
        return button;
    }

    private void hideProxyGroupControls() {
        HorizontalScrollView horizontalScrollView = this.mihomoGroupScroll;
        if (horizontalScrollView != null) {
            horizontalScrollView.setVisibility(8);
        }
        LinearLayout linearLayout = this.mihomoProxyGroupHeader;
        if (linearLayout != null) {
            linearLayout.setVisibility(8);
        }
    }

    private void showProxyMessage(String value) {
        FrameLayout body = this.mihomoProxyBody;
        if (body == null) {
            return;
        }
        hideProxyGroupControls();
        body.removeAllViews();
        TextView empty = text(value, 14.0f, MUTED);
        empty.setGravity(17);
        empty.setPadding(dp(20), dp(34), dp(20), dp(34));
        empty.setBackground(rounded(SURFACE, 22, 0, 0));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(RAISED, -2);
        params.setMargins(dp(20), dp(8), dp(20), dp(8));
        body.addView(empty, params);
    }

    private void showProxyStoppedState() {
        FrameLayout frameLayout = this.mihomoProxyBody;
        if (frameLayout == null) {
            return;
        }
        hideProxyGroupControls();
        frameLayout.removeAllViews();
        LinearLayout state = new LinearLayout(this);
        state.setOrientation(1);
        state.setGravity(17);
        state.setPadding(dp(18), dp(26), dp(18), dp(22));
        state.setBackground(rounded(SURFACE, 22, 0, 0));
        TextView message = text("启动 Mihomo 后即可读取代理组", 14.0f, MUTED);
        message.setGravity(17);
        state.addView(message, wrap());
        Button start = primaryButton("启动 Mihomo");
        start.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$uYDSYmjVEUV-AAK2tKRIeUj9C5U
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CodexHomeActivity.this.lambda$showProxyStoppedState$47$CodexHomeActivity(view);
            }
        });
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(RAISED, dp(48));
        buttonParams.topMargin = dp(16);
        state.addView(start, buttonParams);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(RAISED, -2);
        params.setMargins(dp(20), dp(8), dp(20), dp(8));
        frameLayout.addView(state, params);
    }

    public /* synthetic */ void lambda$showProxyStoppedState$46$CodexHomeActivity() throws Exception {
        this.mihomoManager.start();
    }

    public /* synthetic */ void lambda$showProxyStoppedState$47$CodexHomeActivity(final View v) {
        final Button start = (Button) v;
        runMihomoButtonAction(start, "\u542f\u52a8\u4e2d\u2026", new MihomoAction() {
            @Override public void run() throws Exception { CodexHomeActivity.this.lambda$showProxyStoppedState$46$CodexHomeActivity(); }
        }, "Mihomo \u5df2\u542f\u52a8");
    }

    private void loadProxyGroups() {
        if (this.mihomoProxyLoading) {
            return;
        }
        this.mihomoProxyLoading = true;
        new Thread(new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$vAL_54xCDT7oV3gdDNA5p1hsG0c
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.lambda$loadProxyGroups$49$CodexHomeActivity();
            }
        }, "MihomoProxyGroups").start();
    }

    public /* synthetic */ void lambda$loadProxyGroups$49$CodexHomeActivity() {
        List<MihomoControllerClient.ProxyGroup> groups = null;
        String error = null;
        try {
            groups = this.mihomoController.groups();
        } catch (Exception failure) {
            error = failure.getMessage();
        }
        final List<MihomoControllerClient.ProxyGroup> result = groups;
        final String problem = error;
        runOnUiThread(new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$R0Ut0WkGBxwCD6UiHAZarW_DxHk
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.lambda$loadProxyGroups$48$CodexHomeActivity(problem, result);
            }
        });
    }

    public /* synthetic */ void lambda$loadProxyGroups$48$CodexHomeActivity(String problem, List result) {
        this.mihomoProxyLoading = false;
        if (problem == null) {
            this.mihomoLoadedGroups = result == null ? new ArrayList() : result;
        }
        if (this.mihomoSelectedTab != 1 || this.mihomoProxyBody == null) {
            return;
        }
        if (problem == null) {
            if (!this.mihomoLoadedGroups.isEmpty()) {
                renderProxyGroupTabs();
                renderSelectedProxyGroup();
                return;
            } else {
                showProxyMessage("当前订阅没有可选择的代理组");
                return;
            }
        }
        showProxyMessage("读取失败：" + problem);
    }

    private MihomoControllerClient.ProxyGroup selectedProxyGroup() {
        List<MihomoControllerClient.ProxyGroup> list = this.mihomoLoadedGroups;
        if (list == null || list.isEmpty()) {
            return null;
        }
        String saved = this.prefs.getString("mihomo_proxy_group", "");
        for (MihomoControllerClient.ProxyGroup group : this.mihomoLoadedGroups) {
            if (group.name.equals(saved)) {
                return group;
            }
        }
        MihomoControllerClient.ProxyGroup first = this.mihomoLoadedGroups.get(0);
        this.prefs.edit().putString("mihomo_proxy_group", first.name).apply();
        return first;
    }

    private void renderProxyGroupTabs() {
        final LinearLayout tabs = this.mihomoGroupTabs;
        final HorizontalScrollView scroll = this.mihomoGroupScroll;
        if (tabs == null || scroll == null) {
            return;
        }
        tabs.removeAllViews();
        MihomoControllerClient.ProxyGroup selected = selectedProxyGroup();
        int selectedIndex = 0;
        int i = 0;
        while (true) {
            boolean active = false;
            if (i < this.mihomoLoadedGroups.size()) {
                final MihomoControllerClient.ProxyGroup group = this.mihomoLoadedGroups.get(i);
                if (selected != null && group.name.equals(selected.name)) {
                    active = true;
                }
                if (active) {
                    selectedIndex = i;
                }
                Button chip = capsuleButton(group.name, active);
                chip.setSingleLine(true);
                chip.setEllipsize(TextUtils.TruncateAt.END);
                chip.setMaxWidth(dp(190));
                chip.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$ioG5M7fWq0Wa1CyTPjqjFefdREk
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view) {
                        CodexHomeActivity.this.lambda$renderProxyGroupTabs$50$CodexHomeActivity(group, view);
                    }
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(42));
                if (i > 0) {
                    params.leftMargin = dp(8);
                }
                tabs.addView(chip, params);
                i++;
            } else {
                scroll.setVisibility(0);
                final int index = selectedIndex;
                scroll.post(new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$SJIPQdP9o5PV4QHGUOFMBLUPeSI
                    @Override // java.lang.Runnable
                    public final void run() {
                        CodexHomeActivity.this.lambda$renderProxyGroupTabs$51$CodexHomeActivity(index, tabs, scroll);
                    }
                });
                return;
            }
        }
    }

    public /* synthetic */ void lambda$renderProxyGroupTabs$50$CodexHomeActivity(MihomoControllerClient.ProxyGroup group, View v) {
        if (group.name.equals(this.prefs.getString("mihomo_proxy_group", ""))) {
            return;
        }
        this.prefs.edit().putString("mihomo_proxy_group", group.name).apply();
        renderProxyGroupTabs();
        renderSelectedProxyGroup();
    }

    public /* synthetic */ void lambda$renderProxyGroupTabs$51$CodexHomeActivity(int index, LinearLayout tabs, HorizontalScrollView scroll) {
        if (index < tabs.getChildCount()) {
            View child = tabs.getChildAt(index);
            int target = Math.max(0, child.getLeft() - dp(20));
            scroll.smoothScrollTo(target, 0);
        }
    }

    private void renderSelectedProxyGroup() {
        FrameLayout body = this.mihomoProxyBody;
        final MihomoControllerClient.ProxyGroup group = selectedProxyGroup();
        if (body == null || group == null) {
            return;
        }
        LinearLayout linearLayout = this.mihomoProxyGroupHeader;
        if (linearLayout != null) {
            linearLayout.setVisibility(0);
        }
        TextView textView = this.mihomoProxyGroupTitle;
        if (textView != null) {
            textView.setText(group.name);
        }
        if (this.mihomoProxySelectedNode != null) {
            String current = group.selected.isEmpty() ? group.type : "当前：" + group.selected;
            this.mihomoProxySelectedNode.setText(current + "  /  " + group.nodes.size() + " 个节点");
        }
        Button button = this.mihomoProxyTestButton;
        if (button != null) {
            boolean testingThisGroup = this.mihomoDelayTesting && group.name.equals(this.mihomoDelayTestingGroup);
            button.setEnabled(!this.mihomoDelayTesting);
            button.setText(testingThisGroup ? "\u6d4b\u901f\u4e2d" : "\u6d4b\u901f");
            button.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$eb08gB8YiKhLunQhA2oxy5itp-c
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    CodexHomeActivity.this.lambda$renderSelectedProxyGroup$52$CodexHomeActivity(group, view);
                }
            });
        }
        List<MihomoControllerClient.ProxyNode> nodes = new ArrayList<>(group.nodes);
        String sort = this.prefs.getString("mihomo_proxy_sort", "default");
        if ("name".equals(sort)) {
            Collections.sort(nodes, new Comparator() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$HRiPWSBwncMDVvDd1B2MVJhZnrI
                @Override // java.util.Comparator
                public final int compare(Object obj, Object obj2) {
                    return ((MihomoControllerClient.ProxyNode) obj).name.compareToIgnoreCase(((MihomoControllerClient.ProxyNode) obj2).name);
                }
            });
        } else if ("delay".equals(sort)) {
            Collections.sort(nodes, new Comparator() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$PTkV8DFt17l5PMFgMvfwt_U_i6w
                @Override // java.util.Comparator
                public final int compare(Object obj, Object obj2) {
                    return CodexHomeActivity.lambda$renderSelectedProxyGroup$54((MihomoControllerClient.ProxyNode) obj, (MihomoControllerClient.ProxyNode) obj2);
                }
            });
        }
        body.removeAllViews();
        ListView listView = new ListView(this);
        this.mihomoProxyList = listView;
        listView.setDivider(null);
        this.mihomoProxyList.setDividerHeight(0);
        this.mihomoProxyList.setSelector(R.color.transparent);
        this.mihomoProxyList.setCacheColorHint(0);
        this.mihomoProxyList.setClipToPadding(false);
        this.mihomoProxyList.setPadding(dp(20), dp(2), dp(20), dp(12));
        this.mihomoProxyList.setAdapter((ListAdapter) new ProxyRowsAdapter(group, nodes));
        body.addView(this.mihomoProxyList, new FrameLayout.LayoutParams(RAISED, RAISED));
    }

    public /* synthetic */ void lambda$renderSelectedProxyGroup$52$CodexHomeActivity(MihomoControllerClient.ProxyGroup group, View v) {
        testProxyGroup(group);
    }

    static /* synthetic */ int lambda$renderSelectedProxyGroup$54(MihomoControllerClient.ProxyNode a, MihomoControllerClient.ProxyNode b) {
        int ad = a.delay > 0 ? a.delay : Integer.MAX_VALUE;
        int bd = b.delay > 0 ? b.delay : Integer.MAX_VALUE;
        return ad == bd ? a.name.compareToIgnoreCase(b.name) : Integer.compare(ad, bd);
    }

    /* loaded from: D:\Androiddevev\projects\codex-mobile\app\build\intermediates\project_dex_archive\debug\out\com\termux\app\CodexHomeActivity$ProxyRowsAdapter.dex */
    private final class ProxyRowsAdapter extends BaseAdapter {
        private final MihomoControllerClient.ProxyGroup group;
        private final List<MihomoControllerClient.ProxyNode> nodes;

        ProxyRowsAdapter(MihomoControllerClient.ProxyGroup group, List<MihomoControllerClient.ProxyNode> nodes) {
            this.group = group;
            this.nodes = nodes;
        }

        @Override // android.widget.Adapter
        public int getCount() {
            return (this.nodes.size() + 1) / 2;
        }

        @Override // android.widget.Adapter
        public Object getItem(int position) {
            return this.nodes.get(position * 2);
        }

        @Override // android.widget.Adapter
        public long getItemId(int position) {
            return position;
        }

        @Override // android.widget.BaseAdapter, android.widget.Adapter
        public boolean hasStableIds() {
            return false;
        }

        @Override // android.widget.Adapter
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
                row.removeAllViews();
            } else {
                row = new LinearLayout(CodexHomeActivity.this);
                row.setOrientation(0);
                row.setPadding(0, CodexHomeActivity.this.dp(5), 0, CodexHomeActivity.this.dp(5));
            }
            int first = position * 2;
            row.addView(CodexHomeActivity.this.proxyNodeCard(this.group, this.nodes.get(first)), CodexHomeActivity.this.proxyGridParams(true));
            if (first + 1 < this.nodes.size()) {
                row.addView(CodexHomeActivity.this.proxyNodeCard(this.group, this.nodes.get(first + 1)), CodexHomeActivity.this.proxyGridParams(false));
            } else {
                row.addView(new Space(CodexHomeActivity.this), new LinearLayout.LayoutParams(0, CodexHomeActivity.this.dp(78), 1.0f));
            }
            return row;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public LinearLayout.LayoutParams proxyGridParams(boolean left) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(78), 1.0f);
        int iDp = dp(5);
        if (left) {
            params.rightMargin = iDp;
        } else {
            params.leftMargin = iDp;
        }
        return params;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public View proxyNodeCard(final MihomoControllerClient.ProxyGroup group, final MihomoControllerClient.ProxyNode node) {
        int i;
        int i2;
        int i3;
        int i4;
        int i5;
        int i6;
        boolean selected = node.name.equals(group.selected);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(1);
        card.setGravity(16);
        card.setPadding(dp(12), dp(7), dp(10), dp(7));
        if (selected) {
            i = 217;
            i2 = 239;
            i3 = 224;
        } else {
            i = 247;
            i2 = 233;
            i3 = 222;
        }
        int iRgb = Color.rgb(i, i2, i3);
        if (selected) {
            i4 = 198;
            i5 = 226;
            i6 = 207;
        } else {
            i4 = 235;
            i5 = 216;
            i6 = 201;
        }
        card.setBackground(interactiveBackground(iRgb, Color.rgb(i4, i5, i6), 18));
        TextView title = text(node.name, 13.0f, selected ? GREEN : TEXT);
        title.setTypeface(Typeface.DEFAULT, 1);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        boolean delayTesting = this.mihomoDelayTesting && group.name.equals(this.mihomoDelayTestingGroup);
        TextView detail;
        if (delayTesting) {
            detail = new DelayStatusView(node.type);
        } else {
            int delayColor = node.delay <= 0 ? MUTED : node.delay < 300 ? GREEN : node.delay < 800 ? AMBER : Color.rgb(173, 53, 49);
            detail = text((node.delay > 0 ? node.delay + " ms" : "--") + (node.type.isEmpty() ? "" : "  /  " + node.type), 11.0f, delayColor);
        }
        detail.setMaxLines(1);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(title);
        card.addView(detail);
        card.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$6TyFCQEJ8lzoDxhMJzQ7mDPQCnM
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CodexHomeActivity.this.lambda$proxyNodeCard$55$CodexHomeActivity(group, node, view);
            }
        });
        return card;
    }

    public /* synthetic */ void lambda$proxyNodeCard$55$CodexHomeActivity(MihomoControllerClient.ProxyGroup group, MihomoControllerClient.ProxyNode node, View v) {
        selectProxyNode(group, node.name);
    }

    private void selectProxyNode(final MihomoControllerClient.ProxyGroup group, final String node) {
        final ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("正在切换代理…");
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        progress.show();
        new Thread(new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$_I6aQrS4zXI77QT0SQRcziIGZj8
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.lambda$selectProxyNode$57$CodexHomeActivity(group, node, progress);
            }
        }, "MihomoProxySelect").start();
    }

    public /* synthetic */ void lambda$selectProxyNode$57$CodexHomeActivity(final MihomoControllerClient.ProxyGroup group, final String node, final ProgressDialog progress) {
        String error = null;
        try {
            this.mihomoController.select(group.name, node);
        } catch (Exception failure) {
            error = failure.getMessage();
        }
        final String problem = error;
        runOnUiThread(new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$iXa4FUm4Awj21Qq3pCjpCKz87d8
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.lambda$selectProxyNode$56$CodexHomeActivity(progress, problem, group, node);
            }
        });
    }

    public /* synthetic */ void lambda$selectProxyNode$56$CodexHomeActivity(ProgressDialog progress, String problem, MihomoControllerClient.ProxyGroup group, String node) {
        if (!isFinishing()) {
            progress.dismiss();
        }
        if (problem == null) {
            group.selected = node;
            if (this.mihomoSelectedTab == 1 && group == selectedProxyGroup()) {
                renderSelectedProxyGroup();
            }
            Toast.makeText(this, "已切换到 " + node, 0).show();
            return;
        }
        Toast.makeText(this, problem, 1).show();
    }

    private void testProxyGroup(final MihomoControllerClient.ProxyGroup group) {
        if (this.mihomoDelayTesting) {
            showTopNotice("\u6b63\u5728\u6d4b\u901f\u2026", true);
            return;
        }
        this.mihomoDelayTesting = true;
        this.mihomoDelayTestingGroup = group.name;
        renderSelectedProxyGroup();
        new Thread(() -> {
            String error = null;
            try {
                Map<String, Integer> delays = this.mihomoController.testGroup(group.name);
                for (MihomoControllerClient.ProxyNode node : group.nodes) {
                    Integer value = delays.get(node.name);
                    if (value != null) node.delay = value.intValue();
                }
            } catch (Exception failure) {
                error = failure.getMessage();
            }
            final String problem = error;
            runOnUiThread(() -> {
                this.mihomoDelayTesting = false;
                this.mihomoDelayTestingGroup = "";
                if (this.mihomoSelectedTab == 1 && selectedProxyGroup() != null) renderSelectedProxyGroup();
                if (problem == null) showTopNotice("\u6d4b\u901f\u5b8c\u6210", true);
                else showTopNotice("\u6d4b\u901f\u5931\u8d25\uff1a" + problem, false);
            });
        }, "MihomoDelayTest").start();
    }

    private void runControllerAction(String message, final ControllerAction action, final String success) {
        final ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage(message);
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        progress.show();
        new Thread(new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$UAiC_LwNfSbl_A-c3tm6xpXs5ko
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.lambda$runControllerAction$61$CodexHomeActivity(action, progress, success);
            }
        }, "MihomoControllerAction").start();
    }

    public /* synthetic */ void lambda$runControllerAction$61$CodexHomeActivity(ControllerAction action, final ProgressDialog progress, final String success) {
        String error = null;
        try {
            action.run();
        } catch (Exception failure) {
            error = failure.getMessage();
        }
        final String problem = error;
        runOnUiThread(new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$MpVKl7pcd0mWItIYqeqizqi2py0
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.lambda$runControllerAction$60$CodexHomeActivity(progress, problem, success);
            }
        });
    }

    public /* synthetic */ void lambda$runControllerAction$60$CodexHomeActivity(ProgressDialog progress, String problem, String success) {
        if (!isFinishing()) {
            progress.dismiss();
        }
        if (problem == null) {
            Toast.makeText(this, success, 0).show();
            showMihomoTab(1);
        } else {
            Toast.makeText(this, problem, 1).show();
        }
    }

    private ScrollView buildMihomoSubscriptionsTab() {
        LinearLayout linearLayoutTabPanel = tabPanel("订阅", "添加、更新并切换多个订阅配置");
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(0);
        Button addUrl = capsuleButton("添加订阅", true);
        addUrl.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$rBCUMYMtfuDfA42DY9Oo3ARRJLU
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CodexHomeActivity.this.lambda$buildMihomoSubscriptionsTab$62$CodexHomeActivity(view);
            }
        });
        Button importFile = capsuleButton("导入文件", false);
        importFile.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$-A9VP2SWUS2kw250fdm2_4sm5SU
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CodexHomeActivity.this.lambda$buildMihomoSubscriptionsTab$63$CodexHomeActivity(view);
            }
        });
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(48), 1.0f);
        left.rightMargin = dp(6);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(48), 1.0f);
        right.leftMargin = dp(6);
        actions.addView(addUrl, left);
        actions.addView(importFile, right);
        linearLayoutTabPanel.addView(actions, wrap());
        List<MihomoManager.Subscription> subscriptions = this.mihomoManager.subscriptions();
        if (subscriptions.isEmpty()) {
            TextView empty = text("暂无订阅\n可以添加订阅 URL 或导入 YAML 文件", 14.0f, MUTED);
            empty.setGravity(17);
            empty.setLineSpacing(dp(5), 1.0f);
            empty.setPadding(dp(16), dp(38), dp(16), dp(38));
            empty.setBackground(rounded(SURFACE, 22, 0, 0));
            LinearLayout.LayoutParams emptyParams = wrap();
            emptyParams.topMargin = dp(16);
            linearLayoutTabPanel.addView(empty, emptyParams);
        } else {
            addSettingsSection(linearLayoutTabPanel, "订阅列表");
            for (MihomoManager.Subscription item : subscriptions) {
                linearLayoutTabPanel.addView(subscriptionCard(item), subscriptionCardParams());
            }
        }
        return tabScroll(linearLayoutTabPanel);
    }

    public /* synthetic */ void lambda$buildMihomoSubscriptionsTab$62$CodexHomeActivity(View v) {
        showAddSubscriptionDialog();
    }

    public /* synthetic */ void lambda$buildMihomoSubscriptionsTab$63$CodexHomeActivity(View v) {
        chooseMihomoConfig();
    }

    private LinearLayout.LayoutParams subscriptionCardParams() {
        LinearLayout.LayoutParams params = wrap();
        params.bottomMargin = dp(11);
        return params;
    }

    private View subscriptionCard(final MihomoManager.Subscription item) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(dp(15), dp(14), dp(15), dp(14));
        linearLayout.setBackground(interactiveBackground(item.active ? Color.rgb(222, 240, 228) : SURFACE, item.active ? Color.rgb(204, 230, 213) : Color.rgb(239, 222, 209), 22));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(16);
        TextView title = text(item.name, 16.0f, item.active ? GREEN : TEXT);
        title.setTypeface(Typeface.DEFAULT, 1);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1.0f));
        if (item.active) {
            TextView badge = text("当前使用", 11.0f, RAISED);
            badge.setGravity(17);
            badge.setTypeface(Typeface.DEFAULT, 1);
            badge.setBackground(rounded(GREEN, 18, 0, 0));
            header.addView(badge, new LinearLayout.LayoutParams(dp(66), dp(32)));
        }
        linearLayout.addView(header, wrap());
        String detailValue = item.isRemote() ? item.url : "本地 YAML 文件";
        int i = MUTED;
        TextView detail = text(detailValue, 12.0f, i);
        detail.setMaxLines(2);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams detailParams = wrap();
        detailParams.topMargin = dp(6);
        linearLayout.addView(detail, detailParams);
        if (item.updatedAt > 0) {
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(item.updatedAt));
            TextView updated = text("更新于 " + time, 11.0f, i);
            LinearLayout.LayoutParams up = wrap();
            up.topMargin = dp(5);
            linearLayout.addView(updated, up);
        }
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(0);
        if (!item.active) {
            Button activate = capsuleButton("启用", true);
            activate.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$peR7crUdXuRfkHCvybt9x_SUa9A
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    CodexHomeActivity.this.lambda$subscriptionCard$64$CodexHomeActivity(item, view);
                }
            });
            buttons.addView(activate, new LinearLayout.LayoutParams(0, dp(42), 1.0f));
        }
        if (item.isRemote()) {
            Button update = capsuleButton("更新", false);
            update.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$qlQIioS8gzejbD1R9s74FSxayQU
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    CodexHomeActivity.this.lambda$subscriptionCard$65$CodexHomeActivity(item, view);
                }
            });
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(42), 1.0f);
            bp.leftMargin = dp(6);
            buttons.addView(update, bp);
        }
        Button delete = capsuleButton("删除", false);
        delete.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$phOrhvsiLbYJxL8F_xlWGSRYAxU
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CodexHomeActivity.this.lambda$subscriptionCard$66$CodexHomeActivity(item, view);
            }
        });
        LinearLayout.LayoutParams dpv = new LinearLayout.LayoutParams(0, dp(42), 1.0f);
        dpv.leftMargin = dp(6);
        buttons.addView(delete, dpv);
        LinearLayout.LayoutParams buttonsParams = wrap();
        buttonsParams.topMargin = dp(11);
        linearLayout.addView(buttons, buttonsParams);
        if (!item.active) {
            linearLayout.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$khIDpfX58WxWcBawt6XlNMjkua4
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    CodexHomeActivity.this.lambda$subscriptionCard$67$CodexHomeActivity(item, view);
                }
            });
        }
        return linearLayout;
    }

    public /* synthetic */ void lambda$subscriptionCard$64$CodexHomeActivity(MihomoManager.Subscription item, View v) {
        activateSubscription(item);
    }

    public /* synthetic */ void lambda$subscriptionCard$65$CodexHomeActivity(MihomoManager.Subscription item, View v) {
        updateSubscription(item);
    }

    public /* synthetic */ void lambda$subscriptionCard$66$CodexHomeActivity(MihomoManager.Subscription item, View v) {
        confirmDeleteSubscription(item);
    }

    public /* synthetic */ void lambda$subscriptionCard$67$CodexHomeActivity(MihomoManager.Subscription item, View v) {
        activateSubscription(item);
    }

    private void showAddSubscriptionDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(1);
        content.setPadding(dp(18), dp(4), dp(18), dp(8));
        final EditText name = field("配置名称，例如：我的机场", "", false);
        final EditText url = field("订阅地址", "", false);
        url.setInputType(17);
        addField(content, "名称", name);
        addField(content, "URL", url);
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("添加订阅").setView(content).setNegativeButton("取消", (DialogInterface.OnClickListener) null).setPositiveButton("添加并启用", (DialogInterface.OnClickListener) null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$6a2X_k-PnceH6KIvDYyE0qf7SoQ
            @Override // android.content.DialogInterface.OnShowListener
            public final void onShow(DialogInterface dialogInterface) {
                CodexHomeActivity.this.lambda$showAddSubscriptionDialog$70$CodexHomeActivity(dialog, url, name, dialogInterface);
            }
        });
        dialog.show();
    }

    public /* synthetic */ void lambda$showAddSubscriptionDialog$70$CodexHomeActivity(final AlertDialog dialog, final EditText url, final EditText name, DialogInterface ignored) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(rounded(SURFACE, 26, 0, 0));
        }
        Button action = dialog.getButton(RAISED);
        action.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$CzSLl7g4Sejd4thnNUQw0E6dD1g
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CodexHomeActivity.this.lambda$showAddSubscriptionDialog$69$CodexHomeActivity(url, dialog, name, view);
            }
        });
    }

    public /* synthetic */ void lambda$showAddSubscriptionDialog$69$CodexHomeActivity(EditText url, AlertDialog dialog, final EditText name, View v) {
        final String value = url.getText().toString().trim();
        if (value.isEmpty()) {
            url.setError("请输入订阅地址");
        } else {
            dialog.dismiss();
            runMihomoAction("正在下载订阅…", new MihomoAction() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$PAKvQbAPk0mLCE9ab-tEhJ8me2Y
                @Override // com.termux.app.CodexHomeActivity.MihomoAction
                public final void run() throws Exception {
                    CodexHomeActivity.this.lambda$showAddSubscriptionDialog$68$CodexHomeActivity(name, value);
                }
            }, "订阅已添加");
        }
    }

    public /* synthetic */ void lambda$showAddSubscriptionDialog$68$CodexHomeActivity(EditText name, String value) throws Exception {
        this.mihomoManager.addSubscription(name.getText().toString(), value);
    }

    private void activateSubscription(final MihomoManager.Subscription item) {
        runMihomoAction("正在切换订阅…", new MihomoAction() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$RGQNYl-bBmIpqh3UQ5E1G_czoDI
            @Override // com.termux.app.CodexHomeActivity.MihomoAction
            public final void run() throws Exception {
                CodexHomeActivity.this.lambda$activateSubscription$71$CodexHomeActivity(item);
            }
        }, "已切换到 " + item.name);
    }

    public /* synthetic */ void lambda$activateSubscription$71$CodexHomeActivity(MihomoManager.Subscription item) throws Exception {
        this.mihomoManager.activateSubscription(item.id);
    }

    private void updateSubscription(final MihomoManager.Subscription item) {
        runMihomoAction("正在更新 " + item.name + "...", new MihomoAction() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$ITnYLpdAVd3exfvlAvWu25uSYhw
            @Override // com.termux.app.CodexHomeActivity.MihomoAction
            public final void run() throws Exception {
                CodexHomeActivity.this.lambda$updateSubscription$72$CodexHomeActivity(item);
            }
        }, "订阅已更新");
    }

    public /* synthetic */ void lambda$updateSubscription$72$CodexHomeActivity(MihomoManager.Subscription item) throws Exception {
        this.mihomoManager.updateSubscription(item.id);
    }

    private void confirmDeleteSubscription(final MihomoManager.Subscription item) {
        new AlertDialog.Builder(this).setTitle("删除这个订阅？").setMessage(item.name).setNegativeButton("取消", (DialogInterface.OnClickListener) null).setPositiveButton("删除", new DialogInterface.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$bTtXw_VkIaLtd0yMysyYGLnwdwY
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                CodexHomeActivity.this.lambda$confirmDeleteSubscription$74$CodexHomeActivity(item, dialogInterface, i);
            }
        }).show();
    }

    public /* synthetic */ void lambda$confirmDeleteSubscription$73$CodexHomeActivity(MihomoManager.Subscription item) throws Exception {
        this.mihomoManager.deleteSubscription(item.id);
    }

    public /* synthetic */ void lambda$confirmDeleteSubscription$74$CodexHomeActivity(final MihomoManager.Subscription item, DialogInterface dialog, int which) {
        runMihomoAction("正在删除订阅…", new MihomoAction() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$OTZjDFrjRpneqDE84RmwfF79oe8
            @Override // com.termux.app.CodexHomeActivity.MihomoAction
            public final void run() throws Exception {
                CodexHomeActivity.this.lambda$confirmDeleteSubscription$73$CodexHomeActivity(item);
            }
        }, "订阅已删除");
    }

    private ScrollView buildMihomoSettingsTab() {
        LinearLayout panel = tabPanel("代理设置", "内核、启动行为与高级控制台");
        addSettingsSection(panel, "组件");
        panel.addView(settingsAction("Mihomo 内核与网页面板", "Mihomo v1.19.28 / MetaCubeXD v1.268.4", this.mihomoManager.isInstalled() ? "已是最新版" : "未安装", new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$mjVaQyaZgAPnEqlTjl90LVnjYZk
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.installMihomoBundle();
            }
        }));
        this.mihomoInstallButton = null;
        addSettingsSection(panel, "运行行为");
        SettingToggle routeApi = settingSwitch("应用内 URL 使用 Mihomo", "不会影响浏览器或其他 Android 应用", this.prefs.getBoolean("mihomo_route_api", false));
        routeApi.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$qeDsPx1L0WmtObxM8JJ_ZCCXQxQ
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                CodexHomeActivity.this.lambda$buildMihomoSettingsTab$75$CodexHomeActivity(compoundButton, z);
            }
        });
        panel.addView(routeApi, new LinearLayout.LayoutParams(RAISED, dp(80)));
        SettingToggle autoStart = settingSwitch("随应用自动启动", "只启动本地内核，不创建 Android VPN", this.prefs.getBoolean("mihomo_auto_start", false));
        autoStart.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$6oOy9sOT6ydsRpzg7jZpTlbiCOo
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                CodexHomeActivity.this.lambda$buildMihomoSettingsTab$76$CodexHomeActivity(compoundButton, z);
            }
        });
        panel.addView(autoStart, new LinearLayout.LayoutParams(RAISED, dp(80)));
        addSettingsSection(panel, "\u9876\u90e8\u63d0\u793a");
        TextView noticeHint = text("\u4ee3\u7406\u5c31\u7eea\u65f6\u663e\u793a\u5728\u5e94\u7528\u4e0a\u65b9\uff0c\u7559\u7a7a\u5219\u4f7f\u7528\u9ed8\u8ba4\u6587\u5b57", 12.0f, MUTED);
        noticeHint.setPadding(dp(3), 0, dp(3), dp(8));
        panel.addView(noticeHint, wrap());
        final EditText proxyNotice = field("\u4ee3\u7406\u5df2\u5f00\u542f", this.prefs.getString("proxy_notice_text", ""), false);
        proxyNotice.setSingleLine(true);
        proxyNotice.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
        proxyNotice.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable value) { prefs.edit().putString("proxy_notice_text", value.toString()).apply(); }
        });
        panel.addView(proxyNotice, new LinearLayout.LayoutParams(RAISED, dp(56)));
        LinearLayout noticeActions = new LinearLayout(this);
        noticeActions.setOrientation(0);
        Button previewNotice = capsuleButton("\u9884\u89c8", true);
        previewNotice.setOnClickListener(v -> showProxyReadyNotice());
        Button resetNotice = capsuleButton("\u6062\u590d\u9ed8\u8ba4", false);
        resetNotice.setOnClickListener(v -> { proxyNotice.setText(""); showProxyReadyNotice(); });
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(0, dp(44), 1.0f);
        previewParams.rightMargin = dp(5);
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(0, dp(44), 1.0f);
        resetParams.leftMargin = dp(5);
        noticeActions.addView(previewNotice, previewParams);
        noticeActions.addView(resetNotice, resetParams);
        LinearLayout.LayoutParams noticeActionParams = wrap();
        noticeActionParams.topMargin = dp(8);
        panel.addView(noticeActions, noticeActionParams);
        addSettingsSection(panel, "高级功能");
        panel.addView(settingsAction("打开 MetaCubeXD", "查看流量、规则、连接和节点详情", "网页控制台", this::openMihomoDashboard));
        panel.addView(settingsAction("重新安装组件", "校验固定 SHA-256 后替换内核和面板", "离线组件", new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$mjVaQyaZgAPnEqlTjl90LVnjYZk
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.installMihomoBundle();
            }
        }));
        String str = "Mixed Port  127.0.0.1:" + this.mihomoManager.mixedPort() + "\nController  127.0.0.1:" + this.mihomoManager.controllerPort();
        int i = MUTED;
        TextView ports = text(str, 12.0f, i);
        ports.setLineSpacing(dp(4), 1.0f);
        ports.setPadding(dp(15), dp(14), dp(15), dp(14));
        ports.setBackground(rounded(SURFACE, 18, 0, 0));
        LinearLayout.LayoutParams portsParams = wrap();
        portsParams.topMargin = dp(10);
        panel.addView(ports, portsParams);
        TextView security = text("只代理 Codex Mobile 流量。控制器仅监听本机，并使用应用私有随机密钥保护。", 12.0f, i);
        security.setLineSpacing(dp(4), 1.0f);
        security.setPadding(dp(15), dp(14), dp(15), dp(14));
        security.setBackground(rounded(Color.rgb(245, 227, 213), 18, 0, 0));
        LinearLayout.LayoutParams securityParams = wrap();
        securityParams.topMargin = dp(10);
        panel.addView(security, securityParams);
        return tabScroll(panel);
    }

    public /* synthetic */ void lambda$buildMihomoSettingsTab$75$CodexHomeActivity(CompoundButton button, boolean checked) {
        this.prefs.edit().putBoolean("mihomo_route_api", checked).apply();
        if (this.appServerStarted) {
            Toast.makeText(this, "重启 WebUI 和 Codex 后端后生效", 1).show();
        }
    }

    public /* synthetic */ void lambda$buildMihomoSettingsTab$76$CodexHomeActivity(CompoundButton button, boolean checked) {
        this.prefs.edit().putBoolean("mihomo_auto_start", checked).apply();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showMihomoSettings() {
        closeDrawer();
        MaterialButton materialButton = this.configFab;
        if (materialButton != null) {
            materialButton.setVisibility(8);
        }
        this.pageTitle.setText("网络与代理");
        this.topToolbar.setVisibility(0);
        this.homePage.setVisibility(8);
        this.configPage.setVisibility(8);
        this.settingsPage.setVisibility(8);
        this.overlaySettingsPage.setVisibility(View.GONE);
        if (this.aboutPage != null) this.aboutPage.setVisibility(View.GONE);
        this.webView.setVisibility(8);
        this.mihomoPage.setVisibility(0);
        showMihomoTab(0);
        fadeIn(this.mihomoPage);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void refreshMihomoUi() {
        MihomoManager mihomoManager = this.mihomoManager;
        if (mihomoManager == null) {
            return;
        }
        boolean installed = mihomoManager.isInstalled();
        boolean running = this.mihomoManager.isRunning();
        TextView textView = this.mihomoStatus;
        if (textView != null) {
            textView.setText(!installed ? "未安装" : running ? "运行中" : "已停止");
            this.mihomoStatus.setTextColor(running ? GREEN : installed ? TEXT : AMBER);
        }
        TextView textView2 = this.mihomoRuntimeDetails;
        if (textView2 != null) {
            textView2.setText("Mixed Port  127.0.0.1:" + this.mihomoManager.mixedPort() + "\nController  127.0.0.1:" + this.mihomoManager.controllerPort());
        }
        Button button = this.mihomoStartStopButton;
        if (button != null) {
            button.setText(!installed ? "安装组件" : running ? "停止" : "启动");
            this.mihomoStartStopButton.setEnabled(true);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void installMihomoBundle() {
        final ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("安装 Mihomo 组件");
        progress.setMessage("正在准备…");
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        progress.show();
        new Thread(new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$zbbNQwbVc1hwOHYuE8LIsmYeI_U
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.lambda$installMihomoBundle$80$CodexHomeActivity(progress);
            }
        }, "MihomoInstaller").start();
    }

    public /* synthetic */ void lambda$installMihomoBundle$80$CodexHomeActivity(final ProgressDialog progress) {
        String failure = null;
        try {
            this.mihomoManager.install(new MihomoManager.ProgressCallback() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$x3Rpv0pQ-LTNfNhniPHPeFjoVR0
                public final void onProgress(String str) {
                    CodexHomeActivity.this.lambda$installMihomoBundle$78$CodexHomeActivity(progress, str);
                }
            });
        } catch (Exception error) {
            failure = error.getClass().getSimpleName() + ": " + error.getMessage();
            Log.e("IlyopMihomo", "安装失败", error);
        }
        final String result = failure;
        runOnUiThread(new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$xWMbbtsKSDk-6HFDBDWj6w7G1_0
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.lambda$installMihomoBundle$79$CodexHomeActivity(progress, result);
            }
        });
    }

    public /* synthetic */ void lambda$installMihomoBundle$78$CodexHomeActivity(final ProgressDialog progress, final String message) {
        runOnUiThread(new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$RjUSw_fSu6Hn9KzS1jxs3X5WLA0
            @Override // java.lang.Runnable
            public final void run() {
                progress.setMessage(message);
            }
        });
    }

    public /* synthetic */ void lambda$installMihomoBundle$79$CodexHomeActivity(ProgressDialog progress, String result) {
        if (!isFinishing()) {
            progress.dismiss();
        }
        if (result != null) {
            new AlertDialog.Builder(this).setTitle("安装失败").setMessage(result).setPositiveButton("关闭", (DialogInterface.OnClickListener) null).show();
        } else {
            Toast.makeText(this, "Mihomo 组件已安装", 1).show();
            showMihomoTab(this.mihomoSelectedTab);
        }
    }

    private void toggleMihomo() {
        if (this.mihomoButtonBusy) return;
        if (!this.mihomoManager.isInstalled()) {
            installMihomoBundle();
        } else if (!this.mihomoManager.isRunning()) {
            runMihomoButtonAction(this.mihomoStartStopButton, "\u542f\u52a8\u4e2d\u2026", new MihomoAction() {
                @Override public void run() throws Exception { CodexHomeActivity.this.lambda$toggleMihomo$82$CodexHomeActivity(); }
            }, "Mihomo \u5df2\u542f\u52a8");
        } else {
            runMihomoButtonAction(this.mihomoStartStopButton, "\u505c\u6b62\u4e2d\u2026", new MihomoAction() {
                @Override public void run() throws Exception { CodexHomeActivity.this.lambda$toggleMihomo$81$CodexHomeActivity(); }
            }, "Mihomo \u5df2\u505c\u6b62");
        }
    }

    public /* synthetic */ void lambda$toggleMihomo$81$CodexHomeActivity() throws Exception {
        this.mihomoManager.stop();
    }

    public /* synthetic */ void lambda$toggleMihomo$82$CodexHomeActivity() throws Exception {
        this.mihomoManager.start();
    }

    private void chooseMihomoConfig() {
        if (!this.mihomoManager.isInstalled()) {
            Toast.makeText(this, "请先安装 Mihomo", 0).show();
            return;
        }
        try {
            Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
            intent.addCategory("android.intent.category.OPENABLE");
            intent.setType("*/*");
            intent.putExtra("android.intent.extra.MIME_TYPES", new String[]{"application/yaml", "text/yaml", "text/x-yaml", "text/plain", "application/octet-stream"});
            intent.addFlags(65);
            startActivityForResult(Intent.createChooser(intent, "选择 Mihomo YAML 配置"), MIHOMO_CONFIG_REQUEST);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开系统文件选择器", 0).show();
        }
    }

    private String displayNameForUri(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{"_display_name"}, null, null, null);
        } catch (Exception e) {
            if (cursor == null) {
                return "本地配置";
            }
        } catch (Throwable th) {
            if (cursor != null) {
                cursor.close();
            }
            throw th;
        }
        if (cursor == null || !cursor.moveToFirst()) {
            if (cursor == null) {
                return "本地配置";
            }
            cursor.close();
            return "本地配置";
        }
        String string = cursor.getString(0);
        if (cursor != null) {
            cursor.close();
        }
        return string;
    }

    private void importMihomoConfig(final Uri uri) {
        final String name = displayNameForUri(uri);
        runMihomoAction("正在校验并导入配置…", new MihomoAction() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$jRs1629zeeqjjjMbLLoc1kqMvvw
            @Override // com.termux.app.CodexHomeActivity.MihomoAction
            public final void run() throws Exception {
                CodexHomeActivity.this.lambda$importMihomoConfig$83$CodexHomeActivity(uri, name);
            }
        }, "配置已导入");
    }

    public /* synthetic */ void lambda$importMihomoConfig$83$CodexHomeActivity(Uri uri, String name) throws Exception {
        InputStream input = getContentResolver().openInputStream(uri);
        try {
            if (input == null) {
                throw new IOException("无法读取所选文件");
            }
            this.mihomoManager.importSubscription(name, input);
            if (input != null) {
                input.close();
            }
        } catch (Throwable th) {
            if (input != null) {
                try {
                    input.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void openMihomoDashboard() {
        if (this.mihomoManager.isRunning()) {
            startActivity(new Intent(this, (Class<?>) MihomoDashboardActivity.class));
        } else {
            Toast.makeText(this, "请先启动 Mihomo", 0).show();
        }
    }

    private void runMihomoButtonAction(final Button button, final String loadingText, final MihomoAction action, final String success) {
        if (button == null || mihomoButtonBusy) return;
        mihomoButtonBusy = true;
        button.setEnabled(false);
        button.setText(loadingText);
        LoadingDrawable spinner = new LoadingDrawable(Color.rgb(45, 125, 88));
        spinner.setBounds(0, 0, dp(20), dp(20));
        button.setTag(spinner);
        button.setCompoundDrawables(spinner, null, null, null);
        spinner.start();
        new Thread(() -> {
            String failure = null;
            try { action.run(); }
            catch (Exception e) { failure = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); Log.e("IlyopMihomo", loadingText, e); }
            final String result = failure;
            runOnUiThread(() -> {
                Object tag = button.getTag();
                if (tag instanceof LoadingDrawable) ((LoadingDrawable) tag).stop();
                button.setTag(null);
                button.setCompoundDrawables(null, null, null, null);
                mihomoButtonBusy = false;
                if (result == null) {
                    mihomoLoadedGroups = new ArrayList();
                    if (this.mihomoManager.isRunning()) showProxyReadyNotice();
                    else showTopNotice("\u4ee3\u7406\u5df2\u5173\u95ed", true);
                    refreshMihomoUi();
                    if (mihomoPage != null && mihomoPage.getVisibility() == View.VISIBLE) showMihomoTab(mihomoSelectedTab);
                } else {
                    refreshMihomoUi();
                    showTopNotice("\u64cd\u4f5c\u5931\u8d25\uff1a" + result, false);
                }
            });
        }, "MihomoButtonAction").start();
    }

    private final class DelayStatusView extends TextView {
        private final LoadingDrawable spinner;
        DelayStatusView(String type) {
            super(CodexHomeActivity.this);
            setText(type == null || type.isEmpty() ? "" : "  /  " + type);
            setTextSize(11.0f);
            setTextColor(GREEN);
            setIncludeFontPadding(false);
            this.spinner = new LoadingDrawable(GREEN);
            this.spinner.setBounds(0, 0, dp(15), dp(15));
            setCompoundDrawables(this.spinner, null, null, null);
            setCompoundDrawablePadding(dp(4));
        }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); this.spinner.start(); }
        @Override protected void onDetachedFromWindow() { this.spinner.stop(); super.onDetachedFromWindow(); }
    }

    private static final class LoadingDrawable extends Drawable implements Animatable, Runnable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean running;
        private float rotation;
        LoadingDrawable(int color) { paint.setColor(color); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3f); paint.setStrokeCap(Paint.Cap.ROUND); }
        @Override public void draw(Canvas canvas) { RectF bounds = new RectF(getBounds()); bounds.inset(2f, 2f); canvas.drawArc(bounds, rotation, 270f, false, paint); }
        @Override public void start() { if (running) return; running = true; scheduleSelf(this, SystemClock.uptimeMillis() + 16L); }
        @Override public void stop() { running = false; unscheduleSelf(this); invalidateSelf(); }
        @Override public boolean isRunning() { return running; }
        @Override public void run() { if (!running) return; rotation = (rotation + 12f) % 360f; invalidateSelf(); scheduleSelf(this, SystemClock.uptimeMillis() + 16L); }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return 20; }
        @Override public int getIntrinsicHeight() { return 20; }
    }

    private void runMihomoAction(final String message, final MihomoAction action, final String success) {
        final ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage(message);
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        progress.show();
        new Thread(new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$C8f2ORBJpCsEb8SULE3BcZFIPXM
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.lambda$runMihomoAction$85$CodexHomeActivity(action, message, progress, success);
            }
        }, "MihomoAction").start();
    }

    public /* synthetic */ void lambda$runMihomoAction$85$CodexHomeActivity(MihomoAction action, String message, final ProgressDialog progress, final String success) {
        String failure = null;
        try {
            action.run();
        } catch (Exception error) {
            failure = error.getClass().getSimpleName() + ": " + error.getMessage();
            Log.e("IlyopMihomo", message, error);
        }
        final String result = failure;
        runOnUiThread(new Runnable() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$ohv-iC443_DrwilIwz89KykeRCE
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.lambda$runMihomoAction$84$CodexHomeActivity(progress, result, success);
            }
        });
    }

    public /* synthetic */ void lambda$runMihomoAction$84$CodexHomeActivity(ProgressDialog progress, String result, String success) {
        if (!isFinishing()) {
            progress.dismiss();
        }
        if (result == null) {
            this.mihomoLoadedGroups = new ArrayList();
            Toast.makeText(this, success, 0).show();
            showMihomoTab(this.mihomoSelectedTab);
            return;
        }
        new AlertDialog.Builder(this).setTitle("操作失败").setMessage(result).setPositiveButton("关闭", (DialogInterface.OnClickListener) null).show();
    }

    /* loaded from: D:\Androiddevev\projects\codex-mobile\app\build\intermediates\project_dex_archive\debug\out\com\termux\app\CodexHomeActivity$SettingToggle.dex */
    private static final class SettingToggle extends LinearLayout {
        private final SwitchMaterial toggle;

        SettingToggle(CodexHomeActivity activity, String title, String subtitle, boolean checked) {
            super(activity);
            setOrientation(0);
            setGravity(16);
            setPadding(activity.dp(14), activity.dp(8), activity.dp(10), activity.dp(8));
            setBackground(activity.interactiveBackground(0, Color.rgb(239, 224, 213), 18));
            LinearLayout copy = new LinearLayout(activity);
            copy.setOrientation(1);
            copy.setGravity(16);
            TextView main = activity.text(title, 15.0f, CodexHomeActivity.TEXT);
            main.setTypeface(Typeface.DEFAULT, 1);
            main.setIncludeFontPadding(false);
            TextView detail = activity.text(subtitle, 12.0f, CodexHomeActivity.MUTED);
            detail.setIncludeFontPadding(false);
            detail.setMaxLines(2);
            copy.addView(main);
            LinearLayout.LayoutParams dp = activity.wrap();
            dp.topMargin = activity.dp(4);
            copy.addView(detail, dp);
            addView(copy, new LinearLayout.LayoutParams(0, -2, 1.0f));
            SwitchMaterial switchMaterial = new SwitchMaterial(activity);
            this.toggle = switchMaterial;
            switchMaterial.setChecked(checked);
            switchMaterial.setScaleX(1.18f);
            switchMaterial.setScaleY(1.18f);
            switchMaterial.setElevation(0.0f);
            switchMaterial.setStateListAnimator((StateListAnimator) null);
            switchMaterial.setThumbTintList(new ColorStateList(new int[][]{new int[]{R.attr.state_checked}, new int[0]}, new int[]{Color.rgb(255, 248, 242), Color.rgb(112, 91, 78)}));
            switchMaterial.setTrackTintList(new ColorStateList(new int[][]{new int[]{R.attr.state_checked}, new int[0]}, new int[]{Color.rgb(45, 125, 88), Color.rgb(220, 201, 187)}));
            addView(switchMaterial, new LinearLayout.LayoutParams(activity.dp(64), activity.dp(52)));
        }

        boolean isChecked() {
            return this.toggle.isChecked();
        }

        void setChecked(boolean checked) { this.toggle.setChecked(checked); }
        void setToggleEnabled(boolean enabled) { this.toggle.setEnabled(enabled); setAlpha(enabled ? 1f : 0.46f); }

        void setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener listener) {
            this.toggle.setOnCheckedChangeListener(listener);
        }
    }

    private SettingToggle settingSwitch(String title, String subtitle, boolean checked) {
        return new SettingToggle(this, title, subtitle, checked);
    }

    private void applyConfiguredProjectRoot() throws IOException {
        if (this.prefs.getBoolean("custom_project_root_enabled", true)) {
            String rootPath = this.prefs.getString("custom_project_root", "/storage/emulated/0/");
            try {
                File rootDirectory = new File(rootPath).getCanonicalFile();
                if (rootDirectory.isDirectory() || rootDirectory.mkdirs()) {
                    SharedPreferences bridgePrefs = getSharedPreferences("codex_desktop_bridge", 0);
                    JSONArray merged = new JSONArray().put(rootDirectory.getAbsolutePath());
                    JSONArray old = new JSONArray(bridgePrefs.getString("workspace_roots", "[]"));
                    for (int i = 0; i < old.length(); i++) {
                        String value = old.optString(i, "");
                        if (!value.isEmpty() && !value.equals(rootDirectory.getAbsolutePath())) {
                            merged.put(value);
                        }
                    }
                    bridgePrefs.edit().putString("workspace_roots", merged.toString()).putString("active_workspace_roots", new JSONArray().put(rootDirectory.getAbsolutePath()).toString()).apply();
                }
            } catch (Exception e) {
            }
        }
    }

    private void saveCustomProjectPath(EditText path) {
        String value = path.getText().toString().trim();
        if (value.isEmpty()) {
            value = "/storage/emulated/0/";
        }
        File directory = new File(value);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        this.prefs.edit().putString("custom_project_root", value).apply();
        String rootsJson = "[" + JSONObject.quote(value) + "]";
        getSharedPreferences("codex_desktop_bridge", 0).edit().putString("workspace_roots", rootsJson).putString("active_workspace_roots", rootsJson).apply();
        path.setText(value);
    }

    private void addSettingsSection(LinearLayout panel, String label) {
        TextView view = text(label, 12.0f, MUTED);
        view.setTypeface(Typeface.DEFAULT, 1);
        LinearLayout.LayoutParams params = wrap();
        params.topMargin = dp(24);
        params.bottomMargin = dp(8);
        params.leftMargin = dp(4);
        panel.addView(view, params);
    }

    private View settingsAction(String title, String subtitle, String badge, Runnable action) {
        View card = actionCard(title, subtitle, badge, action);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(RAISED, -2);
        params.bottomMargin = dp(10);
        card.setLayoutParams(params);
        return card;
    }

    public void confirmResetWebUiPreferences() {
        new AlertDialog.Builder(this).setTitle("重置 WebUI 偏好？").setMessage("这会删除 WebUI 语言、外观、项目列表和本地界面状态。API 配置和 Codex 会话文件不会删除。").setNegativeButton("取消", (DialogInterface.OnClickListener) null).setPositiveButton("确认重置", new DialogInterface.OnClickListener() { // from class: com.termux.app.CodexHomeActivity.4
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                CodexHomeActivity.this.lambda$confirmResetWebUiPreferences$8$CodexHomeActivity(dialogInterface, i);
            }
        }).show();
    }

    public void lambda$confirmResetWebUiPreferences$8$CodexHomeActivity(DialogInterface dialog, int which) {
        getSharedPreferences("codex_desktop_bridge", 0).edit().clear().apply();
        this.webView.clearCache(true);
        this.webUiLoaded = false;
        Toast.makeText(this, "WebUI 偏好已重置", 0).show();
    }

    private LinearLayout buildDrawer() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(dp(16), dp(20), dp(16), dp(18));
        linearLayout.setBackgroundColor(SURFACE);
        linearLayout.setElevation(0.0f);
        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(16);
        LinearLayout brandCopy = new LinearLayout(this);
        brandCopy.setOrientation(1);
        brandCopy.setPadding(dp(8), 0, 0, 0);
        TextView name = text("Codex Mobile", 17.0f, TEXT);
        name.setTypeface(Typeface.DEFAULT, 1);
        brandCopy.addView(name);
        int i = MUTED;
        brand.addView(brandCopy);
        linearLayout.addView(brand, new LinearLayout.LayoutParams(RAISED, dp(52)));
        TextView label = text("工作区", 12.0f, i);
        label.setTypeface(Typeface.DEFAULT, 1);
        LinearLayout.LayoutParams labelParams = wrap();
        labelParams.topMargin = dp(32);
        labelParams.leftMargin = dp(10);
        labelParams.bottomMargin = dp(7);
        linearLayout.addView(label, labelParams);
        linearLayout.addView(navItem("启动 WebUI", "WEB", this::startWebUi));
        linearLayout.addView(navItem("启动 Termux", "CLI", this::openInternalTerminal));
        linearLayout.addView(navItem("配置管理", "CFG", this::showConfiguration));
        linearLayout.addView(new Space(this), new LinearLayout.LayoutParams(RAISED, 0, 1.0f));
        linearLayout.addView(navItem("设置", "SET", new Runnable() { // from class: com.termux.app.CodexHomeActivity.5
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.showSettings();
            }
        }));
        TextView note = text("WebUI 会在应用内保持运行\n再次打开不会刷新页面", 12.0f, i);
        note.setLineSpacing(dp(3), 1.0f);
        note.setPadding(dp(10), dp(12), dp(10), dp(6));
        linearLayout.addView(note);
        return linearLayout;
    }

    private LinearLayout buildOnboarding() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(1);
        page.setGravity(1);
        page.setPadding(dp(28), dp(38), dp(28), dp(34));
        page.setBackgroundColor(BG);
        Space top = new Space(this);
        page.addView(top, new LinearLayout.LayoutParams(1, 0, 1.0f));
        ImageView logo = logo(dp(92));
        page.addView(logo, new LinearLayout.LayoutParams(dp(92), dp(92)));
        int i = TEXT;
        TextView title = text("准备 Codex 运行环境", 27.0f, i);
        title.setTypeface(Typeface.DEFAULT, 1);
        title.setGravity(17);
        LinearLayout.LayoutParams titleParams = wrap();
        titleParams.topMargin = dp(24);
        page.addView(title, titleParams);
        int i2 = MUTED;
        TextView copy = text("首次使用需要检查应用目录并安装 Codex CLI。安装包来自 OpenAI 官方 Release。", 14.0f, i2);
        copy.setGravity(17);
        copy.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(RAISED, -2);
        copyParams.topMargin = dp(10);
        page.addView(copy, copyParams);
        TextView textViewText = text("", 13.0f, i);
        this.onboardingRuntimeState = textViewText;
        textViewText.setPadding(dp(16), dp(14), dp(16), dp(14));
        this.onboardingRuntimeState.setLineSpacing(dp(5), 1.0f);
        this.onboardingRuntimeState.setBackground(rounded(SURFACE, 16, 0, 0));
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(RAISED, -2);
        stateParams.topMargin = dp(24);
        page.addView(this.onboardingRuntimeState, stateParams);
        Button install = primaryButton("安装 Codex CLI");
        LinearLayout.LayoutParams installParams = new LinearLayout.LayoutParams(RAISED, dp(54));
        installParams.topMargin = dp(18);
        page.addView(install, installParams);
        install.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.CodexHomeActivity.6
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CodexHomeActivity.this.lambda$buildOnboarding$9$CodexHomeActivity(view);
            }
        });
        TextView skip = text("暂时跳过", 13.0f, i2);
        skip.setGravity(17);
        skip.setPadding(dp(16), dp(13), dp(16), dp(13));
        skip.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.CodexHomeActivity.7
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CodexHomeActivity.this.lambda$buildOnboarding$10$CodexHomeActivity(view);
            }
        });
        page.addView(skip, new LinearLayout.LayoutParams(RAISED, dp(48)));
        page.addView(new Space(this), new LinearLayout.LayoutParams(1, 0, 1.0f));
        return page;
    }

    public void lambda$buildOnboarding$9$CodexHomeActivity(View v) {
        installInternalRuntime(true);
    }

    public void lambda$buildOnboarding$10$CodexHomeActivity(View v) {
        this.prefs.edit().putBoolean("setup_skipped", true).apply();
        transitionToMain();
    }

    private View buildSplash() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(BG);
        ImageView logo = logo(dp(100));
        logo.setTag("splash_logo");
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(100), dp(100), 17);
        page.addView(logo, params);
        return page;
    }

    private boolean isAllowedLocalFile(File file) {
        try {
            File canonical = file.getCanonicalFile();
            if (!canonical.isFile()) return false;
            if (isWithin(canonical, new File("/data/data/com.ilyop.codex/xusr/tmp").getCanonicalFile())) return true;
            if (isWithin(canonical, TermuxConstants.TERMUX_HOME_DIR.getCanonicalFile())) return true;

            SharedPreferences desktop = getSharedPreferences("codex_desktop_bridge", MODE_PRIVATE);
            String configuredRoot = this.prefs == null ? null : this.prefs.getString("custom_project_root", null);
            if (configuredRoot != null && isWithin(canonical, new File(configuredRoot).getCanonicalFile())) return true;
            String rootsJson = desktop.getString("workspace_roots", null);
            if (rootsJson != null) {
                JSONArray roots = new JSONArray(rootsJson);
                for (int i = 0; i < roots.length(); i++) {
                    String root = roots.optString(i, "").trim();
                    if (!root.isEmpty() && isWithin(canonical, new File(root).getCanonicalFile())) return true;
                }
            }
        } catch (Exception ignored) {
            // A malformed or inaccessible path must never be exposed to the WebView.
        }
        return false;
    }

    private static boolean isWithin(File file, File root) {
        String filePath = file.getPath();
        String rootPath = root.getPath();
        return filePath.equals(rootPath) || filePath.startsWith(rootPath + File.separator);
    }

    private WebView buildWebView() {
        WebView view = new WebView(this);
        view.setBackgroundColor(BG);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(2);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setUserAgentString(settings.getUserAgentString() + " IlyopCodexAndroid/0.2");
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setDefaultTextEncodingName("UTF-8");
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        if (Build.VERSION.SDK_INT >= 26) view.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        view.setWebViewClient(new WebViewClient() { // from class: com.termux.app.CodexHomeActivity.8
            private boolean openExternal(Uri uri) {
                String scheme = uri == null ? "" : uri.getScheme();
                if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                    return false;
                }
                try {
                    CodexHomeActivity.this.startActivity(new Intent("android.intent.action.VIEW", uri));
                    return true;
                } catch (Exception e) {
                    Toast.makeText(CodexHomeActivity.this, "无法打开外部链接", 0).show();
                    return true;
                }
            }

            @Override // android.webkit.WebViewClient
            public boolean shouldOverrideUrlLoading(WebView webView, WebResourceRequest request) {
                return openExternal(request.getUrl());
            }

            @Override // android.webkit.WebViewClient
            public boolean shouldOverrideUrlLoading(WebView webView, String url) {
                return openExternal(Uri.parse(url));
            }

            @Override // android.webkit.WebViewClient
            public WebResourceResponse shouldInterceptRequest(WebView webView, WebResourceRequest request) {
                try { return localFileResponse(request.getUrl()); } catch (IOException e) { Log.w("IlyopWeb", "\u672c\u5730\u8d44\u6e90\u8bfb\u53d6\u5931\u8d25", e); return null; }
            }

            @Override // android.webkit.WebViewClient
            public WebResourceResponse shouldInterceptRequest(WebView webView, String url) {
                try { return localFileResponse(Uri.parse(url)); } catch (IOException e) { Log.w("IlyopWeb", "\u672c\u5730\u8d44\u6e90\u8bfb\u53d6\u5931\u8d25", e); return null; }
            }

            private WebResourceResponse localFileResponse(Uri uri) throws IOException {
                try {
                    String url = uri.toString();
                    String rawPath = null;
                    int fs = url.indexOf("/@fs/");
                    if (fs >= 0) {
                        rawPath = url.substring(fs + 5);
                    } else if ("file".equalsIgnoreCase(uri.getScheme())) {
                        rawPath = uri.getPath();
                    }
                    if (rawPath == null) {
                        return null;
                    }
                    String path = URLDecoder.decode(rawPath, "UTF-8");
                    if (!path.startsWith("/")) {
                        path = "/" + path;
                    }
                    File file = new File(path).getCanonicalFile();
                    if (!isAllowedLocalFile(file)) {
                        return null;
                    }
                    String mime = CodexHomeActivity.this.getContentResolver().getType(Uri.fromFile(file));
                    if (mime == null) {
                        String name = file.getName();
                        int dot = name.lastIndexOf('.');
                        String extension = dot >= 0 && dot + 1 < name.length() ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
                        mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                    }
                    if (mime == null) {
                        mime = "application/octet-stream";
                    }
                    Map<String, String> headers = new java.util.HashMap<>();
                    headers.put("Cache-Control", "no-store");
                    headers.put("Content-Length", String.valueOf(file.length()));
                    return new WebResourceResponse(mime, null, 200, "OK", headers, new FileInputStream(file));
                } catch (Exception e) {
                    return null;
                }
            }
        });
        view.setWebChromeClient(new WebChromeClient() { // from class: com.termux.app.CodexHomeActivity.9
            @Override // android.webkit.WebChromeClient
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, WebChromeClient.FileChooserParams params) {
                Intent intent;
                if (CodexHomeActivity.this.pendingFileChooser != null) {
                    CodexHomeActivity.this.pendingFileChooser.onReceiveValue(null);
                }
                CodexHomeActivity.this.pendingFileChooser = callback;
                try {
                    intent = params.createIntent();
                } catch (Exception e) {
                    Intent intent2 = new Intent("android.intent.action.OPEN_DOCUMENT");
                    intent2.setType("*/*");
                    intent = intent2;
                }
                intent.addCategory("android.intent.category.OPENABLE");
                intent.putExtra("android.intent.extra.ALLOW_MULTIPLE", params.getMode() == 1);
                try {
                    CodexHomeActivity.this.startActivityForResult(Intent.createChooser(intent, "选择文件或图片"), CodexHomeActivity.FILE_CHOOSER_REQUEST);
                } catch (Exception e2) {
                    CodexHomeActivity.this.pendingFileChooser = null;
                    callback.onReceiveValue(null);
                    Toast.makeText(CodexHomeActivity.this, "无法打开文件选择器", 0).show();
                }
                return true;
            }
        });
        CodexAppServerBridge codexAppServerBridge = new CodexAppServerBridge(this, view);
        this.appServerBridge = codexAppServerBridge;
        view.addJavascriptInterface(codexAppServerBridge, "CodexNative");
        CodexDesktopBridge codexDesktopBridge = new CodexDesktopBridge(this, view);
        this.desktopBridge = codexDesktopBridge;
        codexDesktopBridge.setAppServerBridge(this.appServerBridge);
        this.appServerBridge.setDesktopBridge(this.desktopBridge);
        view.addJavascriptInterface(this.desktopBridge, "CodexDesktopNative");
        return view;
    }

    private View actionCard(String title, String subtitle, String badge, final Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(16);
        card.setPadding(dp(16), dp(12), dp(14), dp(12));
        card.setBackground(interactiveBackground(SURFACE, Color.rgb(239, 224, 213), 18));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$jT2fOq-cypaU-vAxdGHFh0hh8eM
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                action.run();
            }
        });
        int i = MUTED;
        TextView icon = text(badge, 10.0f, i);
        icon.setTypeface(Typeface.MONOSPACE, 1);
        icon.setGravity(17);
        icon.setIncludeFontPadding(false);
        if (!badge.isEmpty()) {
            card.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(48)));
        }
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(1);
        copy.setGravity(16);
        copy.setPadding(dp(12), 0, dp(8), 0);
        TextView heading = text(title, 15.0f, TEXT);
        heading.setTypeface(Typeface.DEFAULT, 1);
        heading.setGravity(16);
        heading.setIncludeFontPadding(false);
        copy.addView(heading, new LinearLayout.LayoutParams(RAISED, dp(24)));
        TextView sub = text(subtitle, 12.0f, i);
        sub.setGravity(16);
        sub.setIncludeFontPadding(false);
        copy.addView(sub, new LinearLayout.LayoutParams(RAISED, dp(22)));
        card.addView(copy, new LinearLayout.LayoutParams(0, dp(48), 1.0f));
        return card;
    }

    private View navItem(String title, String badge, final Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(16);
        row.setPadding(dp(8), 0, dp(10), 0);
        row.setBackground(interactiveBackground(0, Color.rgb(239, 224, 213), 13));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(new View.OnClickListener() { // from class: com.termux.app.-$$Lambda$CodexHomeActivity$HDPChP1QR7dhYaSjRADtI3O32Z0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CodexHomeActivity.this.lambda$navItem$87$CodexHomeActivity(action, view);
            }
        });
        TextView icon = text(badge, 9.0f, MUTED);
        icon.setTypeface(Typeface.MONOSPACE, 1);
        icon.setGravity(17);
        icon.setIncludeFontPadding(false);
        row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(52)));
        TextView label = text(title, 15.0f, TEXT);
        label.setGravity(16);
        label.setIncludeFontPadding(false);
        label.setPadding(dp(10), 0, 0, 0);
        label.setTypeface(Typeface.DEFAULT, 1);
        row.addView(label, new LinearLayout.LayoutParams(0, dp(52), 1.0f));
        return row;
    }

    public /* synthetic */ void lambda$navItem$87$CodexHomeActivity(Runnable action, View v) {
        closeDrawer();
        this.root.postDelayed(action, 160L);
    }

    public void lambda$navItem$12$CodexHomeActivity(Runnable action, View v) {
        closeDrawer();
        this.root.postDelayed(action, 160L);
    }

    public void routeAfterSplash() {
        if (isCodexInstalled() || this.prefs.getBoolean("setup_skipped", false)) {
            transitionToMain();
        } else {
            showOnboardingFromSplash();
        }
    }

    private void animateSplashLogo() {
        View logo = this.splashView.findViewWithTag("splash_logo");
        logo.setAlpha(0.0f);
        logo.setScaleX(0.82f);
        logo.setScaleY(0.82f);
        logo.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setDuration(420L).start();
    }

    private void showOnboardingFromSplash() {
        refreshRuntimeState();
        this.onboardingView.setVisibility(0);
        this.onboardingView.setAlpha(0.0f);
        this.onboardingView.setTranslationY(dp(16));
        this.onboardingView.animate().alpha(1.0f).translationY(0.0f).setStartDelay(70L).setDuration(330L).start();
        this.splashView.animate().alpha(0.0f).scaleX(1.04f).scaleY(1.04f).setDuration(260L).withEndAction(new Runnable() { // from class: com.termux.app.CodexHomeActivity.10
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.lambda$showOnboardingFromSplash$13$CodexHomeActivity();
            }
        }).start();
    }

    public void lambda$showOnboardingFromSplash$13$CodexHomeActivity() {
        this.splashView.setVisibility(8);
    }

    private void showOnboardingFromMain() {
        refreshRuntimeState();
        this.onboardingView.setVisibility(0);
        this.onboardingView.setAlpha(0.0f);
        this.onboardingView.setTranslationY(dp(20));
        this.onboardingView.animate().alpha(1.0f).translationY(0.0f).setDuration(300L).start();
        this.mainShell.animate().alpha(0.0f).setDuration(190L).withEndAction(new Runnable() { // from class: com.termux.app.CodexHomeActivity.11
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.lambda$showOnboardingFromMain$14$CodexHomeActivity();
            }
        }).start();
    }

    public void lambda$showOnboardingFromMain$14$CodexHomeActivity() {
        this.mainShell.setVisibility(4);
    }

    private void transitionToMain() {
        this.mainShell.setVisibility(0);
        this.mainShell.setAlpha(0.0f);
        this.mainShell.setTranslationY(dp(14));
        this.mainShell.animate().alpha(1.0f).translationY(0.0f).setStartDelay(60L).setDuration(330L).start();
        final View outgoing = this.onboardingView.getVisibility() == 0 ? this.onboardingView : this.splashView;
        outgoing.animate().alpha(0.0f).translationY(-dp(8)).setDuration(230L).withEndAction(new Runnable() { // from class: com.termux.app.CodexHomeActivity.12
            @Override // java.lang.Runnable
            public final void run() {
                outgoing.setVisibility(8);
            }
        }).start();
        this.root.postDelayed(this::handlePendingLaunchAction, 420L);
    }

    private void openDrawer() {
        if (this.drawerOpen) {
            return;
        }
        this.drawerOpen = true;
        this.drawerScrim.setVisibility(0);
        this.drawerScrim.setAlpha(0.0f);
        this.drawerScrim.animate().alpha(1.0f).setDuration(220L).start();
        this.drawer.setVisibility(0);
        this.drawer.setTranslationX(-this.drawer.getLayoutParams().width);
        this.drawer.animate().translationX(0.0f).setDuration(260L).start();
    }

    private void closeDrawer() {
        if (this.drawerOpen) {
            this.drawerOpen = false;
            this.drawerScrim.animate().alpha(0.0f).setDuration(180L).withEndAction(new Runnable() { // from class: com.termux.app.CodexHomeActivity.13
                @Override // java.lang.Runnable
                public final void run() {
                    CodexHomeActivity.this.lambda$closeDrawer$16$CodexHomeActivity();
                }
            }).start();
            this.drawer.animate().translationX(-this.drawer.getLayoutParams().width).setDuration(220L).withEndAction(new Runnable() { // from class: com.termux.app.CodexHomeActivity.14
                @Override // java.lang.Runnable
                public final void run() {
                    CodexHomeActivity.this.lambda$closeDrawer$17$CodexHomeActivity();
                }
            }).start();
        }
    }

    public void lambda$closeDrawer$16$CodexHomeActivity() {
        this.drawerScrim.setVisibility(8);
    }

    public void lambda$closeDrawer$17$CodexHomeActivity() {
        this.drawer.setVisibility(8);
    }

    private void showHome() {
        MaterialButton materialButton = this.configFab;
        if (materialButton != null) {
            materialButton.setVisibility(8);
        }
        closeDrawer();
        this.pageTitle.setText("Codex");
        this.topToolbar.setVisibility(0);
        this.homePage.setVisibility(0);
        fadeIn(this.homePage);
        this.configPage.setVisibility(8);
        this.settingsPage.setVisibility(8);
        this.overlaySettingsPage.setVisibility(View.GONE);
        if (this.aboutPage != null) this.aboutPage.setVisibility(View.GONE);
        this.mihomoPage.setVisibility(8);
        this.webView.setVisibility(8);
    }

    public void showConfiguration() {
        closeDrawer();
        refreshRuntimeState();
        renderProviderList();
        this.pageTitle.setText("配置管理");
        this.homePage.setVisibility(8);
        this.configPage.setVisibility(0);
        fadeIn(this.configPage);
        this.settingsPage.setVisibility(8);
        this.overlaySettingsPage.setVisibility(View.GONE);
        if (this.aboutPage != null) this.aboutPage.setVisibility(View.GONE);
        this.mihomoPage.setVisibility(8);
        this.webView.setVisibility(8);
    }

    public void showSettings() {
        closeDrawer();
        if (this.settingsPage != null) this.contentHost.removeView(this.settingsPage);
        this.settingsPage = buildSettingsPage();
        this.contentHost.addView(this.settingsPage, match());
        this.pageTitle.setText("设置");
        this.homePage.setVisibility(8);
        this.configPage.setVisibility(8);
        this.settingsPage.setVisibility(0);
        this.overlaySettingsPage.setVisibility(View.GONE);
        if (this.aboutPage != null) this.aboutPage.setVisibility(View.GONE);
        fadeIn(this.settingsPage);
        this.mihomoPage.setVisibility(8);
        this.webView.setVisibility(8);
    }

    public void startWebUi() {
        closeDrawer();
        final CodexProviderStore.Profile active = providerStore.active();
        final String url = baseUrl.getText().toString().trim(), key = apiKey.getText().toString().trim(), selectedModel = model.getText().toString().trim();
        if (url.isEmpty() || key.isEmpty()) { showConfiguration(); Toast.makeText(this, "\u8bf7\u5148\u521b\u5efa\u5e76\u542f\u7528 API \u914d\u7f6e", Toast.LENGTH_SHORT).show(); return; }
        if (!isCodexInstalled()) { Toast.makeText(this, "\u8bf7\u5148\u5b89\u88c5 Codex CLI", Toast.LENGTH_SHORT).show(); showOnboardingFromMain(); return; }
        final boolean route = shouldRouteWebUi(active); Runnable launch = () -> startWebUiNow(url, key, selectedModel, route);
        if (route) ensureMihomoForLaunch("WebUI", launch); else launch.run();
    }

    private void startWebUiNow(String url, String key, String selectedModel, boolean route) {
        prefs.edit().putString("base_url", url).putString("api_key", key).putString("model", selectedModel).apply(); persistCodexConfiguration(url, key, selectedModel);
        pageTitle.setText("Codex"); topToolbar.setVisibility(prefs.getBoolean("webui_fullscreen", true) ? View.GONE : View.VISIBLE); homePage.setVisibility(View.GONE); configPage.setVisibility(View.GONE); settingsPage.setVisibility(View.GONE); overlaySettingsPage.setVisibility(View.GONE); if (aboutPage != null) aboutPage.setVisibility(View.GONE); mihomoPage.setVisibility(View.GONE); webView.setVisibility(View.VISIBLE); fadeIn(webView);
        boolean hadLoadedWebUi = webUiLoaded;
        if (!webUiLoaded) loadWebUiForCurrentRevision();
        if (!appServerStarted || appServerUsesMihomo != route) {
            appServerStarted = true;
            appServerUsesMihomo = route;
            webUiReloadPending = hadLoadedWebUi;
            appServerBridge.start(url, key, selectedModel, activeApiFormat(), route, shouldForwardReasoningContext(),
                activeUltraSubagentLimit(), activeNormalSubagentLimit(), activeUltraTransportEfforts(),
                activePreventRecursiveSubagents());
        } else if (webUiReloadPending) {
            status.setText("????????");
        }
    }

    private boolean shouldRouteWebUi(CodexProviderStore.Profile profile) { return prefs.getBoolean("mihomo_route_api", false) || (profile != null && profile.proxyEnabled && profile.proxyWebUi); }
    private boolean shouldRouteTermux(CodexProviderStore.Profile profile) { return prefs.getBoolean("mihomo_route_api", false) || (profile != null && profile.proxyEnabled && profile.proxyTermux); }
    private boolean shouldForwardReasoningContext() {
        CodexProviderStore.Profile active = providerStore == null ? null : providerStore.active();
        return active != null && active.forwardReasoningContext;
    }

    private int activeUltraSubagentLimit() {
        CodexProviderStore.Profile active = providerStore == null ? null : providerStore.active();
        return active == null ? CodexProviderStore.Profile.DEFAULT_ULTRA_SUBAGENT_LIMIT : active.ultraSubagentLimit;
    }

    private int activeNormalSubagentLimit() {
        CodexProviderStore.Profile active = providerStore == null ? null : providerStore.active();
        return active == null ? CodexProviderStore.Profile.DEFAULT_NORMAL_SUBAGENT_LIMIT : active.normalSubagentLimit;
    }

    private java.util.Map<String, String> activeUltraTransportEfforts() {
        CodexProviderStore.Profile active = providerStore == null ? null : providerStore.active();
        return active == null ? java.util.Collections.emptyMap() : active.ultraTransportEfforts();
    }

    private boolean activePreventRecursiveSubagents() {
        CodexProviderStore.Profile active = providerStore == null ? null : providerStore.active();
        return active != null && active.customSubagentStability && active.hasCustomV2Models();
    }

    private void ensureMihomoForLaunch(String target, Runnable continuation) {
        if (!this.mihomoManager.isInstalled()) {
            showTopNotice("\u8bf7\u5148\u5b89\u88c5\u4ee3\u7406\u7ec4\u4ef6", false);
            showMihomoSettings();
            return;
        }
        if (this.mihomoManager.isRunning()) {
            showProxyReadyNotice();
            this.root.postDelayed(continuation, 520L);
            return;
        }
        synchronized (this.mihomoLaunchQueue) {
            this.mihomoLaunchQueue.add(continuation);
            if (this.mihomoLaunchStarting) {
                showTopNotice("\u4ee3\u7406\u542f\u52a8\u4e2d\u2026", true);
                return;
            }
            this.mihomoLaunchStarting = true;
        }
        showTopNotice("\u6b63\u5728\u542f\u52a8\u4ee3\u7406\u2026", true);
        new Thread(() -> {
            String error = null;
            try { this.mihomoManager.start(); }
            catch (Exception e) { error = e.getMessage(); }
            final String problem = error;
            runOnUiThread(() -> {
                ArrayList<Runnable> queued;
                synchronized (this.mihomoLaunchQueue) {
                    this.mihomoLaunchStarting = false;
                    queued = new ArrayList<>(this.mihomoLaunchQueue);
                    this.mihomoLaunchQueue.clear();
                }
                if (problem == null) {
                    this.mihomoLoadedGroups = new ArrayList<>();
                    refreshMihomoUi();
                    showProxyReadyNotice();
                    this.root.postDelayed(() -> { for (Runnable next : queued) next.run(); }, 620L);
                } else {
                    showTopNotice("\u4ee3\u7406\u542f\u52a8\u5931\u8d25" + (problem == null || problem.isEmpty() ? "" : "\uff1a" + problem), false);
                }
            });
        }, "MihomoOneTapStart").start();
    }

    private void installInternalRuntime(final boolean enterMainWhenDone) {
        ProgressBar progressBar = this.progress;
        if (progressBar != null) {
            progressBar.setVisibility(0);
        }
        TextView textView = this.status;
        if (textView != null) {
            textView.setText("正在准备安装到应用私有目录…");
        }
        TextView textView2 = this.onboardingRuntimeState;
        if (textView2 != null) {
            textView2.setText("✓ 应用运行目录已就绪\n… 正在准备 Codex CLI");
        }
        TermuxInstaller.setupBootstrapIfNeeded(this, new Runnable() { // from class: com.termux.app.CodexHomeActivity.16
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.lambda$installInternalRuntime$20$CodexHomeActivity(enterMainWhenDone);
            }
        });
    }

    public void lambda$installInternalRuntime$20$CodexHomeActivity(final boolean enterMainWhenDone) {
        CodexInstaller.setupBootstrapIfNeeded(this, new Runnable() { // from class: com.termux.app.CodexHomeActivity.17
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.lambda$installInternalRuntime$19$CodexHomeActivity(enterMainWhenDone);
            }
        });
    }

    public void lambda$installInternalRuntime$19$CodexHomeActivity(boolean enterMainWhenDone) {
        ProgressBar progressBar = this.progress;
        if (progressBar != null) {
            progressBar.setVisibility(8);
        }
        TextView textView = this.status;
        if (textView != null) {
            textView.setText("Codex CLI 已安装，可以启动 WebUI 或 Termux。");
        }
        this.prefs.edit().putBoolean("setup_skipped", false).apply();
        refreshRuntimeState();
        if (enterMainWhenDone) {
            transitionToMain();
        }
    }

    public void installDevelopmentExtensions() {
        if (!isCodexInstalled()) {
            Toast.makeText(this, "请先安装 Codex CLI", 0).show();
            return;
        }
        TextView textView = this.status;
        if (textView != null) {
            textView.setText("正在安装可选开发工具扩展...");
        }
        CodexInstaller.setupDevelopmentExtensions(this, new Runnable() { // from class: com.termux.app.CodexHomeActivity.18
            @Override // java.lang.Runnable
            public final void run() {
                CodexHomeActivity.this.lambda$installDevelopmentExtensions$21$CodexHomeActivity();
            }
        });
    }

    public void lambda$installDevelopmentExtensions$21$CodexHomeActivity() {
        TextView textView = this.status;
        if (textView != null) {
            textView.setText("开发工具扩展已安装。");
        }
        refreshRuntimeState();
    }

    private void saveConfiguration() {
        String url = this.baseUrl.getText().toString().trim();
        String key = this.apiKey.getText().toString().trim();
        String selectedModel = this.model.getText().toString().trim();
        if (url.isEmpty() || key.isEmpty()) {
            Toast.makeText(this, "请填写 API Base URL 和 API Key", 0).show();
            return;
        }
        this.prefs.edit().putString("base_url", url).putString("api_key", key).putString("model", selectedModel).apply();
        persistCodexConfiguration(url, key, selectedModel);
        if (this.appServerStarted && this.appServerBridge != null) {
            this.status.setText("API configuration saved; restarting WebUI backend...");
            this.appServerUsesMihomo = shouldRouteWebUi(providerStore.active());
            this.appServerBridge.start(url, key, selectedModel, activeApiFormat(), this.appServerUsesMihomo, shouldForwardReasoningContext(),
                activeUltraSubagentLimit(), activeNormalSubagentLimit(), activeUltraTransportEfforts(),
                activePreventRecursiveSubagents());
        } else {
            this.status.setText("API configuration saved to private app storage.");
        }
        Toast.makeText(this, "Codex API 配置已保存", 0).show();
    }

    private File writeModelCatalogFile(CodexProviderStore.Profile profile) {
        File codexHome = new File(TermuxConstants.TERMUX_HOME_DIR, ".codex");
        File catalogFile = new File(codexHome, MODEL_CATALOG_FILENAME);
        return CodexModelCatalog.writeAtomic(catalogFile, profile == null ? null : profile.models);
    }

    private void persistCodexConfiguration(final String url, final String key, final String selectedModel) {
        new Thread(() -> {
            try {
                File codexHome = new File(TermuxConstants.TERMUX_HOME_DIR, ".codex");
                codexHome.mkdirs();
                File config = new File(codexHome, "config.toml");
                StringBuilder toml = new StringBuilder();
                toml.append("model = ").append(tomlQuote(selectedModel)).append("\n");
                toml.append("model_provider = \"ilyop_android\"\n\n");
                CodexProviderStore.Profile.appendAgentConfig(toml, providerStore == null ? null : providerStore.active());
                toml.append("[model_providers.ilyop_android]\n");
                toml.append("name = \"Ilyop API\"\n");
                toml.append("base_url = ").append(tomlQuote(url)).append("\n");
                toml.append("wire_api = ").append(tomlQuote(LocalApiProxy.CODEX_WIRE_API)).append("\n");
                toml.append("experimental_bearer_token = ").append(tomlQuote(key)).append("\n");
                try (FileOutputStream output = new FileOutputStream(config)) {
                    output.write(toml.toString().getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception error) {
                runOnUiThread(() -> lambda$persistCodexConfiguration$22$CodexHomeActivity(error));
            }
        }, "CodexConfigWriter").start();
    }

    public void lambda$persistCodexConfiguration$22$CodexHomeActivity(Exception e) {
        TextView textView = this.status;
        if (textView != null) {
            textView.setText("Codex 配置写入失败: " + e.getMessage());
        }
    }

    public void openInternalTerminal() {
        closeDrawer(); if (!isCodexInstalled()) { Toast.makeText(this, "\u8bf7\u5148\u5b89\u88c5 Codex CLI", Toast.LENGTH_SHORT).show(); showOnboardingFromMain(); return; }
        CodexProviderStore.Profile active = providerStore.active(); String url = baseUrl.getText().toString().trim(), key = apiKey.getText().toString().trim(), selectedModel = model.getText().toString().trim();
        if (url.isEmpty() || key.isEmpty()) { showConfiguration(); Toast.makeText(this, "\u8bf7\u5148\u521b\u5efa\u5e76\u542f\u7528 API \u914d\u7f6e", Toast.LENGTH_SHORT).show(); return; }
        boolean route = shouldRouteTermux(active); Runnable launch = () -> openInternalTerminalNow(url, key, selectedModel, route); if (route) ensureMihomoForLaunch("Termux", launch); else launch.run();
    }

    private void openInternalTerminalNow(String url, String key, String selectedModel, boolean route) {
        try { if (terminalApiProxy != null) terminalApiProxy.stop(); LocalApiProxy local = new LocalApiProxy(url, activeApiFormat(), route, mihomoManager.mixedPort(), shouldForwardReasoningContext(), activeUltraTransportEfforts(), activePreventRecursiveSubagents()); terminalApiProxy = local; int port = local.start(); writeTerminalProxyConfig(port, key, selectedModel, activeApiFormat()); startActivity(new Intent(this, TermuxActivity.class)); }
        catch (Exception error) { Toast.makeText(this, "Termux API \u4ee3\u7406\u542f\u52a8\u5931\u8d25\uff1a" + error.getMessage(), Toast.LENGTH_LONG).show(); showConfiguration(); }
    }

    private void writeTerminalProxyConfig(int port, String key, String selectedModel, String apiFormat) throws Exception {
        File codexHome = new File(TermuxConstants.TERMUX_HOME_DIR, ".codex");
        codexHome.mkdirs();
        StringBuilder toml = new StringBuilder();
        toml.append("model_provider = \"android_proxy\"\n");
        if (!selectedModel.isEmpty()) {
            toml.append("model = ").append(tomlQuote(selectedModel)).append("\n");
        }
        toml.append("approval_policy = \"never\"\n");
        toml.append("sandbox_mode = \"danger-full-access\"\n\n");
        CodexProviderStore.Profile.appendAgentConfig(toml, providerStore == null ? null : providerStore.active());
        toml.append("[model_providers.android_proxy]\n");
        toml.append("name = \"Android API Proxy\"\n");
        toml.append("wire_api = ").append(tomlQuote(LocalApiProxy.CODEX_WIRE_API)).append("\n");
        toml.append("requires_openai_auth = true\n");
        toml.append("base_url = \"http://127.0.0.1:").append(port).append("\"\n");
        toml.append("experimental_bearer_token = ").append(tomlQuote(key)).append("\n");
        FileOutputStream output = new FileOutputStream(new File(codexHome, "config.toml"));
        try {
            output.write(toml.toString().getBytes(StandardCharsets.UTF_8));
            output.close();
        } catch (Throwable th) {
            try {
                output.close();
            } catch (Throwable th2) {
                th.addSuppressed(th2);
            }
            throw th;
        }
    }

    private void refreshRuntimeState() {
        String str;
        boolean installed = isCodexInstalled();
        TextView textView = this.runtimeState;
        if (textView != null) {
            if (installed) {
                str = "✓ Codex CLI 已安装\n  /data/data/com.ilyop.codex/xusr/bin/codex";
            } else {
                str = "○ Codex CLI 尚未安装\n  WebUI 和终端需要此运行组件";
            }
            textView.setText(str);
            this.runtimeState.setTextColor(installed ? GREEN : AMBER);
        }
        TextView textView2 = this.onboardingRuntimeState;
        if (textView2 != null) {
            textView2.setText("✓ 应用运行目录已就绪\n" + (installed ? "✓ Codex CLI 已安装" : "○ Codex CLI 等待安装"));
            this.onboardingRuntimeState.setTextColor(installed ? GREEN : TEXT);
        }
    }

    private boolean isCodexInstalled() {
        return new File("/data/data/com.ilyop.codex/xusr/bin", "codex").canExecute();
    }

    private static String tomlQuote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private void addField(LinearLayout parent, String title, EditText field) {
        TextView label = text(title, 13.0f, TEXT);
        label.setTypeface(Typeface.DEFAULT, 1);
        LinearLayout.LayoutParams labelParams = wrap();
        labelParams.topMargin = dp(15);
        labelParams.bottomMargin = dp(7);
        parent.addView(label, labelParams);
        parent.addView(field, new LinearLayout.LayoutParams(RAISED, dp(52)));
    }

    private EditText field(String hint, String value, boolean password) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setText(value);
        field.setTextSize(14.0f);
        field.setTextColor(TEXT);
        field.setHintTextColor(Color.rgb(154, 137, 123));
        field.setSingleLine(true);
        field.setPadding(dp(15), 0, dp(15), 0);
        field.setBackground(rounded(SURFACE, 13, 0, 0));
        if (password) {
            field.setInputType(129);
        }
        return field;
    }

    private Button primaryButton(String value) {
        MaterialButton button = new MaterialButton(this);
        button.setText(value);
        button.setTextSize(15.0f);
        button.setTextColor(RAISED);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, 1);
        button.setBackground(rounded(TEXT, 14, 0, 0));
        button.setStateListAnimator((StateListAnimator) null);
        return button;
    }

    private Button secondaryButton(String value) {
        MaterialButton button = new MaterialButton(this);
        button.setText(value);
        button.setTextSize(14.0f);
        button.setTextColor(TEXT);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, 1);
        button.setBackground(rounded(SURFACE, 14, 0, 0));
        button.setStateListAnimator((StateListAnimator) null);
        return button;
    }

    private void addButton(LinearLayout parent, Button button, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(RAISED, dp(52));
        params.topMargin = dp(topMargin);
        parent.addView(button, params);
    }

    private ImageView logo(int size) {
        ImageView image = new ImageView(this);
        image.setImageResource(2131165290);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription("Codex");
        return image;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setFontFeatureSettings("kern");
        return view;
    }

    private String proxyNoticeText() {
        String value = this.prefs == null ? "" : this.prefs.getString("proxy_notice_text", "");
        value = value == null ? "" : value.trim();
        return value.isEmpty() ? "\u4ee3\u7406\u5df2\u5f00\u542f" : value;
    }

    private void showProxyReadyNotice() {
        showTopNotice(proxyNoticeText(), true);
    }

    private void showTopNotice(String message, boolean positive) {
        if (this.root == null || isFinishing()) return;
        if (message == null || message.trim().isEmpty()) return;
        if (this.hideTopNoticeRunnable != null) this.root.removeCallbacks(this.hideTopNoticeRunnable);
        if (this.topNoticeView != null) {
            this.topNoticeView.animate().cancel();
            this.root.removeView(this.topNoticeView);
        }
        TextView notice = text(message.trim(), 14.0f, Color.WHITE);
        notice.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        notice.setGravity(Gravity.CENTER);
        notice.setMaxLines(2);
        notice.setEllipsize(TextUtils.TruncateAt.END);
        notice.setPadding(dp(18), dp(11), dp(18), dp(11));
        notice.setBackground(rounded(positive ? Color.rgb(38, 105, 73) : Color.rgb(145, 60, 50), 22, 0, 0));
        notice.setAlpha(0.0f);
        notice.setTranslationY(-dp(10));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        params.topMargin = dp(28);
        params.leftMargin = dp(18);
        params.rightMargin = dp(18);
        this.topNoticeView = notice;
        this.root.addView(notice, params);
        notice.animate().alpha(1.0f).translationY(0.0f).setDuration(150L).start();
        final View shown = notice;
        this.hideTopNoticeRunnable = () -> shown.animate().alpha(0.0f).translationY(-dp(6)).setDuration(130L).withEndAction(() -> {
            if (topNoticeView == shown) {
                root.removeView(shown);
                topNoticeView = null;
            }
        }).start();
        this.root.postDelayed(this.hideTopNoticeRunnable, 1850L);
    }

    private void fadeIn(View view) {
        view.animate().cancel();
        view.setAlpha(0.0f);
        view.setTranslationY(dp(6));
        view.animate().alpha(1.0f).translationY(0.0f).setDuration(220L).start();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public StateListDrawable interactiveBackground(int normalColor, int activeColor, int radiusDp) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{R.attr.state_pressed}, rounded(activeColor, radiusDp, 0, 0));
        states.addState(new int[]{R.attr.state_hovered}, rounded(activeColor, radiusDp, 0, 0));
        states.addState(new int[]{R.attr.state_focused}, rounded(activeColor, radiusDp, 0, 0));
        states.addState(new int[0], rounded(normalColor, radiusDp, 0, 0));
        return states;
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) {
            background.setStroke(dp(strokeDp), strokeColor);
        }
        return background;
    }

    private LinearLayout.LayoutParams cardParams(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(RAISED, dp(78));
        params.topMargin = dp(topMargin);
        return params;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(RAISED, -2);
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(RAISED, RAISED);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public void openNativeFileManager(String path) {
        try {
            Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT_TREE");
            intent.addFlags(67);
            if (Build.VERSION.SDK_INT >= 26 && path != null && path.startsWith("/storage/emulated/0")) {
                String relative = path.substring("/storage/emulated/0".length()).replaceFirst("^/", "").replace("/", "%2F");
                intent.putExtra("android.provider.extra.INITIAL_URI", Uri.parse("content://com.android.externalstorage.documents/document/primary%3A" + relative));
            }
            startActivityForResult(intent, 4108);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开系统文件管理器", 0).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) showTopNotice("\u6587\u4ef6\u6743\u9650\u5df2\u5141\u8bb8", true);
            requestStartupOverlayPermission();
        }
    }

    @Override // android.app.Activity
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            boolean granted = Settings.canDrawOverlays(this);
            boolean pending = this.prefs.getBoolean("overlay_enable_pending", false);
            boolean startupFlow = this.prefs.getBoolean("startup_overlay_flow", false);
            this.prefs.edit().putBoolean("overlay_enable_pending", false).putBoolean("startup_overlay_flow", false).putBoolean("overlay_enabled", granted && pending).apply();
            if (granted && pending) {
                CodexOverlayService.start(this);
                showTopNotice("Fcode \u60ac\u6d6e\u7a97\u5df2\u5f00\u542f", true);
            } else if (pending && !startupFlow) {
                showTopNotice("\u672a\u6388\u4e88\u60ac\u6d6e\u7a97\u6743\u9650", false);
            }
            if (startupFlow) requestStartupBatteryPermission();
            else showOverlaySettings();
            return;
        }
        if (requestCode == FILE_CHOOSER_REQUEST) {
            ValueCallback<Uri[]> callback = this.pendingFileChooser;
            this.pendingFileChooser = null;
            if (callback == null) {
                return;
            }
            Uri[] result = null;
            if (resultCode == RAISED && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    result = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        result[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    result = new Uri[]{data.getData()};
                }
            }
            callback.onReceiveValue(result);
            return;
        }
        if (requestCode == MIHOMO_CONFIG_REQUEST) {
            if (resultCode == RAISED && data != null && data.getData() != null) {
                Uri uri = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(uri, 1);
                } catch (Exception e) {
                }
                importMihomoConfig(uri);
                return;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override // android.app.Activity
    public void onBackPressed() {
        if (this.drawerOpen) {
            closeDrawer();
            return;
        }
        if (this.onboardingView.getVisibility() == 0) {
            transitionToMain();
            return;
        }
        if (this.aboutPage != null && this.aboutPage.getVisibility() == View.VISIBLE) {
            showSettings();
            return;
        }
        if (this.overlaySettingsPage != null && this.overlaySettingsPage.getVisibility() == View.VISIBLE) {
            showSettings();
            return;
        }
        View view = this.mihomoPage;
        if (view != null && view.getVisibility() == 0) {
            showSettings();
            return;
        }
        if (this.configPage.getVisibility() == 0 && this.providerEditorOpen) {
            renderProviderList();
        } else if (this.webView.getVisibility() == 0 || this.configPage.getVisibility() == 0 || this.settingsPage.getVisibility() == 0) {
            showHome();
        } else {
            super.onBackPressed();
        }
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        CodexAppServerBridge codexAppServerBridge = this.appServerBridge;
        if (codexAppServerBridge != null) {
            codexAppServerBridge.stop();
        }
        WebView webView = this.webView;
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
