package com.termux.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.AttributeSet
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.AppCompatTextView

/**
 * TextView whose expensive selectable/editor machinery is activated only after a long press.
 * The floating action menu is intentionally limited to Fcode's document actions.
 */
internal class FcodeSelectableTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatTextView(context, attrs) {
    var copyLabel: String = "Copy"
    var quoteLabel: String = "Quote"
    var selectAllLabel: String = "Select all"
    var onSelectionActivityChanged: ((Boolean) -> Unit)? = null
    var onQuoteSelection: ((String) -> Unit)? = null
    var restingMovementMethod: android.text.method.MovementMethod? = null

    private var selectionActionMode: ActionMode? = null
    private var selectionReported = false

    private val selectionCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            selectionActionMode = mode
            reportSelection(true)
            menu.clear()
            menu.add(Menu.NONE, ACTION_COPY, 0, copyLabel)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            if (onQuoteSelection != null) {
                menu.add(Menu.NONE, ACTION_QUOTE, 1, quoteLabel)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            }
            menu.add(Menu.NONE, ACTION_SELECT_ALL, 2, selectAllLabel)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = when (item.itemId) {
            ACTION_COPY -> {
                selectedText()?.let { selected ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(copyLabel, selected))
                }
                mode.finish()
                true
            }
            ACTION_QUOTE -> {
                selectedText()?.let { onQuoteSelection?.invoke(it) }
                mode.finish()
                true
            }
            ACTION_SELECT_ALL -> {
                (text as? android.text.Spannable)?.let { selectable ->
                    android.text.Selection.setSelection(selectable, 0, selectable.length)
                }
                true
            }
            else -> false
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            if (selectionActionMode === mode) selectionActionMode = null
            reportSelection(false)
            post {
                if (selectionActionMode == null) deactivateSelectionEngine()
            }
        }
    }

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        setTextIsSelectable(false)
        isLongClickable = true
        customSelectionActionModeCallback = selectionCallback
    }

    override fun performLongClick(): Boolean {
        if (!isTextSelectable) {
            setTextIsSelectable(true)
            // Some platform TextView implementations clear the callback when selection toggles.
            customSelectionActionModeCallback = selectionCallback
        }
        val handled = super.performLongClick()
        if (handled) reportSelection(true)
        return handled
    }

    fun finishSelection() {
        val mode = selectionActionMode
        selectionActionMode = null
        mode?.finish()
        deactivateSelectionEngine()
        reportSelection(false)
    }

    private fun deactivateSelectionEngine() {
        setTextIsSelectable(false)
        movementMethod = restingMovementMethod
        isLongClickable = true
    }

    private fun selectedText(): String? {
        val start = minOf(selectionStart, selectionEnd).coerceAtLeast(0)
        val end = maxOf(selectionStart, selectionEnd).coerceAtMost(text.length)
        return if (end > start) text.subSequence(start, end).toString() else null
    }

    private fun reportSelection(active: Boolean) {
        if (selectionReported == active) return
        selectionReported = active
        onSelectionActivityChanged?.invoke(active)
    }

    override fun onDetachedFromWindow() {
        finishSelection()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val ACTION_COPY = 0xF001
        const val ACTION_QUOTE = 0xF002
        const val ACTION_SELECT_ALL = 0xF003
    }
}
