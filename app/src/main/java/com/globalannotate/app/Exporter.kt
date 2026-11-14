package com.globalannotate.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.provider.MediaStore
import java.nio.ByteBuffer
import java.nio.ByteOrder

object Exporter {

    private const val PNG_DIR = "Pictures/GlobalAnnotate"
    private const val PDF_DIR = "Documents/GlobalAnnotate"

    /** Why the last root screenshot failed; surfaced in logcat for diagnosis. */
    @Volatile
    var lastCaptureError: String? = null
        private set

    fun savePng(context: Context, bitmap: Bitmap): String? = savePng(context, bitmap, stamp("png"))

    fun savePng(context: Context, bitmap: Bitmap, name: String): String? {
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

    fun savePdf(context: Context, bitmap: Bitmap): String? = savePdf(context, bitmap, stamp("pdf"))

    fun savePdf(context: Context, bitmap: Bitmap, name: String): String? {
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

    private fun stamp(ext: String): String = "GA_${System.currentTimeMillis()}.$ext"

    /**
     * Root screenshot, raw RGBA instead of PNG: PNG encoding takes ~3 s per
     * frame on this tablet, raw takes ~150 ms, which is what makes scroll
     * capture practical.
     */
    fun captureScreenRoot(): Bitmap? {
        lastCaptureError = null
        val commands = listOf(
            arrayOf("su", "-c", "screencap"),
            arrayOf("su", "-c", "/system/bin/screencap"),
        )
        for (command in commands) {
            val outcome = runCatching { exec(command) }.getOrElse { error ->
                lastCaptureError = "exec '${command.joinToString(" ")}' threw: $error"
                null
            } ?: continue
            val (exit, bytes, stderr) = outcome
            if (exit != 0 || bytes.size < 64) {
                lastCaptureError = "exit=$exit stderr=${stderr.take(200)} " +
                    "(KernelSU/Magisk: grant com.globalannotate.app superuser)"
                continue
            }
            rawDecode(bytes)?.let { return it }
            // Some ROMs answer `screencap` with PNG when it is aliased.
            pngDecode(bytes)?.let { return it }
            lastCaptureError = "raw decode failed: ${bytes.size} bytes, head=" +
                bytes.take(16).joinToString(" ") { "%02x".format(it) }
        }
        return null
    }

    private fun rawDecode(bytes: ByteArray): Bitmap? {
        if (bytes.size < 16) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val w = buf.getInt(0)
        val h = buf.getInt(4)
        if (w <= 0 || h <= 0 || w > 8192 || h > 20000) return null
        val expected = w.toLong() * h * 4
        val header = bytes.size - expected
        if (header < 12 || header > 32) return null
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        val src = ByteBuffer.wrap(bytes, header, w * h * 4).order(ByteOrder.LITTLE_ENDIAN)
        src.asIntBuffer().get(pixels)
        // screencap raw is RGBA_8888; Bitmap wants native ARGB ints.
        for (i in pixels.indices) {
            val v = pixels[i]
            pixels[i] = (v and 0xFF00FF00.toInt()) or
                ((v and 0x00FF0000) ushr 16) or
                ((v and 0x000000FF) shl 16)
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap
    }

    private fun pngDecode(bytes: ByteArray): Bitmap? {
        val start = pngOffset(bytes)
        if (start >= bytes.size) return null
        return BitmapFactory.decodeByteArray(bytes, start, bytes.size - start)
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
