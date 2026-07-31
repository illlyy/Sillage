package com.termux.app

import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import com.termux.R
import java.util.Locale

/** Creates the optional launcher shortcuts shared by legacy and native settings. */
object FcodeLauncherShortcuts {
    @JvmStatic
    @JvmOverloads
    fun pinWebUi(context: Context, language: String = defaultLanguage(context)): Boolean =
        requestPin(context, Target.WEB_UI, language)

    @JvmStatic
    @JvmOverloads
    fun pinTermux(context: Context, language: String = defaultLanguage(context)): Boolean =
        requestPin(context, Target.TERMUX, language)

    private fun requestPin(context: Context, target: Target, language: String): Boolean {
        val zh = language != "en"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            toast(
                context,
                if (zh) "当前桌面不支持应用内添加快捷图标" else "This launcher cannot add shortcuts from the app",
            )
            return false
        }
        val manager = context.getSystemService(ShortcutManager::class.java)
        if (manager == null || !manager.isRequestPinShortcutSupported) {
            toast(
                context,
                if (zh) "当前桌面不支持固定快捷图标" else "This launcher does not support pinned shortcuts",
            )
            return false
        }

        val shortcut = shortcutInfo(context, target == Target.TERMUX, language)
        val requested = runCatching { manager.requestPinShortcut(shortcut, null) }.getOrDefault(false)
        toast(
            context,
            when {
                requested && zh -> "已请求将 ${target.label} 添加到桌面"
                requested -> "Requested ${target.label} on the home screen"
                zh -> "桌面快捷图标添加失败"
                else -> "Could not add the launcher shortcut"
            },
        )
        return requested
    }

    internal fun shortcutInfo(context: Context, terminal: Boolean, language: String): ShortcutInfo {
        val target = if (terminal) Target.TERMUX else Target.WEB_UI
        val zh = language != "en"
        return ShortcutInfo.Builder(context, target.id)
            .setShortLabel(target.label)
            .setLongLabel(if (zh) target.longLabelZh else target.longLabelEn)
            .setIcon(Icon.createWithResource(context, target.iconResource))
            .setIntent(FcodeToolNavigation.shortcutIntent(context, terminal))
            .build()
    }

    private fun defaultLanguage(context: Context): String {
        val configured = context.getSharedPreferences("codex_mobile", Context.MODE_PRIVATE)
            .getString("native_language_v1", "system")
        return when (configured) {
            "en" -> "en"
            "zh" -> "zh"
            else -> if (Locale.getDefault().language == "en") "en" else "zh"
        }
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private enum class Target(
        val id: String,
        val label: String,
        val longLabelZh: String,
        val longLabelEn: String,
        val iconResource: Int,
    ) {
        WEB_UI(
            id = "codex_webui",
            label = "Sillage WebUI",
            longLabelZh = "打开 Codex WebUI",
            longLabelEn = "Open Codex WebUI",
            iconResource = R.drawable.ic_codex_logo,
        ),
        TERMUX(
            id = "codex_termux",
            label = "Sillage Termux",
            longLabelZh = "启动 Codex 内置 Termux",
            longLabelEn = "Open the built-in Codex terminal",
            iconResource = R.drawable.ic_new_session,
        ),
    }
}
