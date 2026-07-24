package com.termux.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File

/** Bounded, process-local cache for attachment thumbnails decoded on a background dispatcher. */
internal object NativeAttachmentImageLoader {
    private const val CACHE_KIB = 16 * 1024
    private const val DEFAULT_THUMBNAIL_EDGE = 256

    private val cache = object : LruCache<String, Bitmap>(CACHE_KIB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    fun load(path: String, maxEdge: Int = DEFAULT_THUMBNAIL_EDGE): Bitmap? {
        val requestedEdge = maxEdge.coerceIn(64, 2_048)
        val file = File(path)
        val key = "$path:${file.lastModified()}:${file.length()}:$requestedEdge"
        cache.get(key)?.takeUnless(Bitmap::isRecycled)?.let { return it }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > requestedEdge || bounds.outHeight / sample > requestedEdge) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        cache.put(key, decoded)
        return decoded
    }
}
