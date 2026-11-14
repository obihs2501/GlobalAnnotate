package com.globalannotate.app.root

import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs and talks to [HelperMain] (`su -c app_process …`).
 *
 * The helper owns the stylus-only input window, the finger spy and the raw pen
 * keyboard, which is what makes "pen annotates, finger browses" work without
 * fighting the input dispatcher in the app process.
 */
class HelperClient(
    private val apkPath: String,
    private val onEvent: Callback,
) {

    interface Callback {
        fun onHelperReady(flags: Int)
        fun onStylus(event: MotionEvent)
        fun onFinger(event: MotionEvent)
        fun onPenKey(scanCode: Int, value: Int, uptimeMs: Long)
        fun onHelperLog(message: String)
        fun onHelperExit()
    }

    val readyFlags = java.util.concurrent.atomic.AtomicInteger(0)
    private val main = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private var process: Process? = null
    private var input: OutputStream? = null

    fun start() {
        if (running.get()) return
        running.set(true)
        Thread({ launch() }, "ga-helper-launch").start()
    }

    private fun launch() {
        val command = arrayOf(
            "su", "-c",
            "CLASSPATH=$apkPath app_process /system/bin ${HelperProtocol.MAIN_CLASS}",
        )
        val p = try {
            ProcessBuilder(*command).redirectErrorStream(false).start()
        } catch (error: Throwable) {
            Log.e(TAG, "helper exec failed", error)
            main.post { onEvent.onHelperLog("exec failed: $error") }
            finish()
            return
        }
        process = p
        input = p.outputStream
        val stderr = p.errorStream
        Thread({
            runCatching {
                val text = stderr.bufferedReader().readText()
                if (text.isNotBlank()) main.post { onEvent.onHelperLog("stderr: ${text.take(400)}") }
            }
        }, "ga-helper-stderr").start()
        readFrames(p.inputStream)
        val code = runCatching { p.waitFor() }.getOrDefault(-1)
        main.post {
            onEvent.onHelperLog("helper exited: $code")
            onEvent.onHelperExit()
        }
        finish()
    }

    private fun readFrames(stream: InputStream) {
        val din = DataInputStream(stream)
        val parcel = Parcel.obtain()
        while (true) {
            val type = try {
                din.readUnsignedByte()
            } catch (e: IOException) {
                return
            }
            val length = try {
                din.readInt()
            } catch (e: IOException) {
                return
            }
            if (length < 0 || length > 2 shl 20) {
                Log.w(TAG, "bad frame length $length")
                return
            }
            val payload = ByteArray(length)
            try {
                din.readFully(payload)
            } catch (e: IOException) {
                return
            }
            when (type) {
                HelperProtocol.FRAME_READY -> {
                    val flags = if (length >= 4) java.nio.ByteBuffer.wrap(payload).int else 0
                    readyFlags.set(flags)
                    main.post { onEvent.onHelperReady(flags) }
                }
                HelperProtocol.FRAME_LOG -> {
                    val text = String(payload, Charsets.UTF_8)
                    main.post { onEvent.onHelperLog(text) }
                }
                HelperProtocol.FRAME_STYLUS, HelperProtocol.FRAME_FINGER -> {
                    val event = try {
                        parcel.unmarshall(payload, 0, payload.size)
                        parcel.setDataPosition(0)
                        MotionEvent.CREATOR.createFromParcel(parcel)
                    } catch (error: Throwable) {
                        Log.w(TAG, "bad motion frame: $error")
                        null
                    }
                    if (event != null) {
                        main.post {
                            if (type == HelperProtocol.FRAME_STYLUS) onEvent.onStylus(event)
                            else onEvent.onFinger(event)
                        }
                    }
                }
                HelperProtocol.FRAME_PEN_KEY -> {
                    if (length >= 16) {
                        val buf = java.nio.ByteBuffer.wrap(payload)
                        val scan = buf.int
                        val value = buf.int
                        val uptime = buf.long
                        main.post { onEvent.onPenKey(scan, value, uptime) }
                    }
                }
            }
        }
    }

    private fun finish() {
        running.set(false)
        readyFlags.set(0)
        input = null
    }

    fun send(command: String) {
        val out = input ?: return
        try {
            synchronized(this) {
                out.write((command + "\n").toByteArray())
                out.flush()
            }
        } catch (error: Throwable) {
            Log.w(TAG, "send failed: $error")
        }
    }

    fun setIntercept(active: Boolean) = send("${HelperProtocol.CMD_INTERCEPT} ${if (active) 1 else 0}")

    fun setOpacityLifted(active: Boolean) = send("${HelperProtocol.CMD_OPACITY} ${if (active) 1 else 0}")

    fun setExcludedRegion(rects: List<android.graphics.Rect>) {
        if (rects.isEmpty()) return send("${HelperProtocol.CMD_REGION} ")
        send(HelperProtocol.CMD_REGION + " " + rects.joinToString(";") { "${it.left},${it.top},${it.right},${it.bottom}" })
    }

    fun stop() {
        send(HelperProtocol.CMD_QUIT)
        runCatching { process?.destroy() }
    }

    fun isRunning(): Boolean = running.get()

    private companion object {
        const val TAG = "GA.HelperClient"
        @Suppress("unused")
        private val startedAt = SystemClock.uptimeMillis()
    }
}
