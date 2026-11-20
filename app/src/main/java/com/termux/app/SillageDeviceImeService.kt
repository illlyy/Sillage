package com.termux.app

import android.inputmethodservice.InputMethodService
import android.view.View

/** Hidden IME used only for Unicode commits requested by the device-control MCP server. */
class SillageDeviceImeService : InputMethodService() {
    override fun onCreate() {
        super.onCreate()
        DeviceImeBridge.attach(this)
    }

    override fun onDestroy() {
        DeviceImeBridge.detach(this)
        super.onDestroy()
    }

    override fun onCreateInputView(): View? = null

    override fun onEvaluateInputViewShown(): Boolean = false
}
