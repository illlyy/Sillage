package com.termux.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** In-app host for the loopback-only MetaCubeXD dashboard. */
public final class MihomoDashboardActivity extends Activity {
    private static final int BG = Color.rgb(252, 238, 226);
    private static final int TEXT = Color.rgb(31, 31, 31);
    private WebView webView;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(Build.VERSION.SDK_INT >= 26 ? 8192 | 16 : 8192);

        MihomoManager manager = MihomoManager.get(this);
        if (!manager.isRunning()) {
            Toast.makeText(this, "Mihomo 尚未运行", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), 0, dp(8), 0);
        toolbar.setBackgroundColor(BG);
        TextView back = label("\u8fd4\u56de", 14, TEXT);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("返回");
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(52), dp(58)));
        TextView title = label("Mihomo 控制台", 17, TEXT);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(58), 1));
        TextView browser = label("浏览器", 13, TEXT);
        browser.setGravity(Gravity.CENTER);
        browser.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(manager.dashboardUrl()))); }
            catch (Exception error) { Toast.makeText(this, "没有可用的外部浏览器", Toast.LENGTH_SHORT).show(); }
        });
        toolbar.addView(browser, new LinearLayout.LayoutParams(dp(64), dp(58)));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(58)));

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        webView.setBackgroundColor(Color.rgb(255, 248, 242));
        webView.setWebViewClient(new WebViewClient() {
            private boolean local(String url) {
                try {
                    Uri uri = Uri.parse(url);
                    String host = uri.getHost();
                    return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host);
                } catch (Exception ignored) { return false; }
            }
            private boolean route(String url) {
                if (local(url)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                catch (Exception ignored) {}
                return true;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return route(request.getUrl().toString()); }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return route(url); }
        });
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        webView.loadUrl(manager.dashboardUrl());
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    private TextView label(String text, float sp, int color) {
        TextView view = new TextView(this);
        view.setText(text); view.setTextSize(sp); view.setTextColor(color);
        view.setIncludeFontPadding(false);
        return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
