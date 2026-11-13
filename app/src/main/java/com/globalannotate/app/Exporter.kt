package com.globalannotate.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.provider.MediaStore

object Exporter {

    private const val PNG_DIR = "Pictures/GlobalAnnotate"
    private const val PDF_DIR = "Documents/GlobalAnnotate"

    /** Why the last root screenshot failed; surfaced in logcat for diagnosis. */
    @Volatile
    var lastCaptureError: String? = null
        private set

    fun savePng(context: Context, bitmap: Bitmap): String? {
        val name = "GA_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, PNG_DIR)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            values,
        ) ?: return null
        val ok = resolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        } ?: false
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return if (ok) name else null
    }

    fun savePdf(context: Context, bitmap: Bitmap): String? {
        val name = "GA_${System.currentTimeMillis()}.pdf"
        val document = PdfDocument()
        return try {
            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
            val page = document.startPage(pageInfo)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            document.finishPage(page)

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, PDF_DIR)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                values,
            ) ?: return null
            val ok = resolver.openOutputStream(uri)?.use(document::writeTo) != null
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            if (ok) name else null
        } catch (error: Throwable) {
            null
        } finally {
            document.close()
        }
    }

    /**
     * Root screenshot used for "save with underlying content".
     *
     * Works with Magisk and KernelSU: both expose `su -c`. On KernelSU the app
     * must be granted superuser once in the KernelSU manager — that grant is
     * persistent, so unlike MediaProjection there is no per-save consent dialog.
     *
     * Two command forms are tried, stderr/exit code are retained in
     * [lastCaptureError], and a leading non-PNG prefix from `su` is skipped.
     */
    fun captureScreenRoot(): Bitmap? {
        lastCaptureError = null
        val commands = listOf(
            arrayOf("su", "-c", "screencap -p"),
            arrayOf("su", "-c", "/system/bin/screencap -p"),
        )
        for (command in commands) {
            val outcome = runCatching { exec(command) }.getOrElse { error ->
                lastCaptureError = "exec '${command.joinToString(" ")}' threw: $error"
                null
            } ?: continue

            val (exit, bytes, stderr) = outcome
            if (exit != 0 || bytes.isEmpty()) {
                lastCaptureError = "exit=$exit stderr=${stderr.take(200)} " +
                    "(KernelSU/Magisk: grant com.globalannotate.app superuser)"
                continue
            }
            val offset = pngOffset(bytes)
            val bitmap = BitmapFactory.decodeByteArray(bytes, offset, bytes.size - offset)
            if (bitmap != null) return bitmap
            lastCaptureError = "PNG decode failed: ${bytes.size} bytes, head=" +
                bytes.take(12).joinToString(" ") { "%02x".format(it) }
        }
        return null
    }

    private fun exec(command: Array<String>): Triple<Int, ByteArray, String> {
        val process = Runtime.getRuntime().exec(command)
        val bytes = process.inputStream.use { it.readBytes() }
        val stderr = process.errorStream.use { String(it.readBytes(), Charsets.UTF_8) }
        val exit = process.waitFor()
        return Triple(exit, bytes, stderr)
    }

    /** Some `su` implementations print a banner before the PNG stream. */
    private fun pngOffset(bytes: ByteArray): Int {
        val last = minOf(bytes.size - 4, 64)
        for (i in 0..last) {
            if (bytes[i] == 0x89.toByte() && bytes[i + 1] == 'P'.code.toByte() &&
                bytes[i + 2] == 'N'.code.toByte() && bytes[i + 3] == 'G'.code.toByte()
            ) return i
        }
        return 0
    }
}
