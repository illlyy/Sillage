package com.termux.app

import android.view.KeyEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

internal object DeviceImeBridge {
    @Volatile
    private var serviceRef: WeakReference<SillageDeviceImeService>? = null

    fun attach(service: SillageDeviceImeService) {
        serviceRef = WeakReference(service)
    }

    fun detach(service: SillageDeviceImeService) {
        if (serviceRef?.get() === service) serviceRef = null
    }

    fun isReady(): Boolean = serviceRef?.get()?.currentInputConnection != null

    suspend fun commitText(text: String, mode: String, pressEnter: Boolean): Boolean = withContext(Dispatchers.Main.immediate) {
        val service = serviceRef?.get() ?: return@withContext false
        val connection = service.currentInputConnection ?: return@withContext false
        connection.beginBatchEdit()
        try {
            if (mode == "replace") {
                connection.performContextMenuAction(android.R.id.selectAll)
                delay(40L)
            }
            connection.commitText(text, 1)
            if (pressEnter) {
                connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
            connection.finishComposingText()
        } finally {
            connection.endBatchEdit()
        }
        true
    }
}
