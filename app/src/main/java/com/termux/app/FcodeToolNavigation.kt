package com.termux.app

import android.content.Context
import android.content.Intent

/** Shared intent contract for opening legacy tools without destroying the native back stack. */
object FcodeToolNavigation {
    @JvmStatic
    @JvmOverloads
    fun webUiIntent(context: Context, returnToNative: Boolean = true): Intent =
        toolIntent(context, CodexHomeActivity.ACTION_OPEN_WEBUI, returnToNative)

    @JvmStatic
    @JvmOverloads
    fun termuxIntent(context: Context, returnToNative: Boolean = true): Intent =
        toolIntent(context, CodexHomeActivity.ACTION_OPEN_TERMUX, returnToNative)

    internal fun shortcutIntent(context: Context, terminal: Boolean): Intent =
        Intent(context, CodexHomeActivity::class.java)
            .setAction(if (terminal) CodexHomeActivity.ACTION_OPEN_TERMUX else CodexHomeActivity.ACTION_OPEN_WEBUI)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    private fun toolIntent(context: Context, action: String, returnToNative: Boolean): Intent =
        Intent(context, CodexHomeActivity::class.java)
            .setAction(action)
            .putExtra(CodexHomeActivity.EXTRA_RETURN_TO_NATIVE, returnToNative)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}
