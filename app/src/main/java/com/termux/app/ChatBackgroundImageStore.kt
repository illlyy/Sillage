package com.termux.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

internal data class ImportedChatBackground(
    val path: String,
    val width: Int,
    val height: Int,
    val bytes: Long,
)

internal object ChatBackgroundImageStore {
    private const val MAX_BYTES = 24L * 1024L * 1024L
    private const val DIRECTORY = "chat-backgrounds"

    fun importImage(context: Context, uri: Uri, previousPath: String?): Result<ImportedChatBackground> = runCatching {
        val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        require(directory.isDirectory) { "Unable to create the chat background directory" }
        val temp = File(directory, ".import-${System.nanoTime()}.tmp")
        var bytes = 0L
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Unable to open the selected image")
            input.use { source ->
                FileOutputStream(temp).use { target ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        bytes += count
                        require(bytes <= MAX_BYTES) { "Image is larger than 24 MB" }
                        target.write(buffer, 0, count)
                    }
                    target.fd.sync()
                }
            }
            require(bytes > 0L) { "The selected image is empty" }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The selected file is not a supported image" }
            val destination = File(directory, "background-${System.currentTimeMillis()}-${System.nanoTime()}.img")
            require(temp.renameTo(destination)) { "Unable to save the selected image" }
            deleteManaged(previousPath, directory, except = destination)
            ImportedChatBackground(destination.absolutePath, bounds.outWidth, bounds.outHeight, bytes)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    fun remove(context: Context, path: String?) {
        deleteManaged(path, File(context.filesDir, DIRECTORY), except = null)
    }

    fun readInfo(path: String?): ImportedChatBackground? {
        val file = path?.takeIf(String::isNotBlank)?.let(::File)?.takeIf(File::isFile) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return ImportedChatBackground(file.absolutePath, bounds.outWidth, bounds.outHeight, file.length())
    }

    fun decodeForDisplay(path: String?, maxDimension: Int = 2048): Bitmap? {
        val file = path?.takeIf(String::isNotBlank)?.let(::File)?.takeIf(File::isFile) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDimension) sample *= 2
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return null
        val rotation = runCatching {
            when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (rotation == 0f) return decoded
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation) }, true)
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    private fun deleteManaged(path: String?, directory: File, except: File?) {
        val candidate = path?.takeIf(String::isNotBlank)?.let(::File) ?: return
        runCatching {
            val root = directory.canonicalFile
            val file = candidate.canonicalFile
            if (file.parentFile == root && file != except?.canonicalFile) file.delete()
        }
    }
}
