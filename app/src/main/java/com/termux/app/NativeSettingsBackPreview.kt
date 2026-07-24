package com.termux.app

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** A short-lived, in-memory snapshot used only to bridge the settings Activity back to chat. */
internal object NativeSettingsBackPreview {
    @Volatile
    private var bitmap: Bitmap? = null
    private val captureGeneration = AtomicInteger()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val captureScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Queue a GPU-backed window copy without drawing the complete Compose hierarchy on the main
     * thread. The former decor.draw(Canvas) ran during the Activity transition and stalled both
     * chat and settings because they share one UI thread.
     */
    fun captureAsync(window: Window) {
        val requestId = captureGeneration.incrementAndGet()
        bitmap = null
        if (Build.VERSION.SDK_INT < 26) return
        val decor = window.decorView
        val sourceWidth = decor.width
        val sourceHeight = decor.height
        if (sourceWidth <= 0 || sourceHeight <= 0) return
        val previewWidth = (sourceWidth * 0.75f).roundToInt().coerceAtLeast(1)
        val previewHeight = (sourceHeight * previewWidth.toFloat() / sourceWidth).roundToInt().coerceAtLeast(1)

        // Allocation is also kept off the animation thread. PixelCopy scales the full window into
        // this smaller destination, avoiding a second full-resolution bitmap and CPU resample.
        captureScope.launch {
            val preview = runCatching {
                Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
            }.getOrNull() ?: return@launch
            mainHandler.post {
                if (requestId != captureGeneration.get()) {
                    preview.recycle()
                    return@post
                }
                runCatching {
                    PixelCopy.request(
                        window,
                        preview,
                        { result ->
                            if (result == PixelCopy.SUCCESS && requestId == captureGeneration.get()) {
                                bitmap = preview
                            } else {
                                preview.recycle()
                            }
                        },
                        mainHandler,
                    )
                }.onFailure { preview.recycle() }
            }
        }
    }

    fun current(): Bitmap? = bitmap
}
