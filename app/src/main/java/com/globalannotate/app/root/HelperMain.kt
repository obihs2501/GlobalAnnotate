package com.globalannotate.app.root

import android.graphics.Rect
import android.graphics.Region
import android.hardware.input.InputManagerGlobal
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.InputApplicationHandle
import android.view.InputChannel
import android.view.InputDevice
import android.view.InputEvent
import android.view.InputEventReceiver
import android.view.InputMonitor
import android.view.InputWindowHandle
import android.view.MotionEvent
import android.view.SurfaceControl
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.system.exitProcess

/**
 * Root-side companion, started with
 * `su -c "CLASSPATH=<apk> app_process /system/bin com.globalannotate.app.root.HelperMain"`.
 *
 * It owns the three things an ordinary app cannot do:
 *
 *  1. A stylus-only input window. The framework already has this concept for
 *     handwriting (`HandwritingEventReceiverSurface`): an input window flagged
 *     `NOT_TOUCHABLE | INTERCEPTS_STYLUS` lets every finger fall through to the
 *     app underneath and takes the pen for itself. We ask InputManager for a
 *     gesture monitor (which gives us a channel and a surface on the overlay
 *     layer) and re-describe that surface the same way.
 *  2. A plain spy monitor for finger gestures, used to follow scrolling.
 *  3. The raw pen keyboard node, because the squeeze key only reaches the
 *     focused window and the annotation layer never takes focus.
 *
 * Everything is fail-soft: a piece that cannot be set up is simply missing from
 * the READY frame and the app falls back.
 */
object HelperMain {

    private const val DISPLAY_ID = 0
    private const val CONFIG_NOT_FOCUSABLE = 4
    private const val CONFIG_NOT_TOUCHABLE = 8
    private const val CONFIG_INTERCEPTS_STYLUS = 32768
    private const val TYPE_SECURE_SYSTEM_OVERLAY = 2015
    private const val DISPATCH_TIMEOUT_MS = 5000L
    private const val OPACITY_KEY = "maximum_obscuring_opacity_for_touch"
    private const val NEWLINE = 10

    private lateinit var out: DataOutputStream
    private val outLock = Any()
    private var textMode = false

    private var stylusMonitor: InputMonitor? = null
    private var stylusHandle: InputWindowHandle? = null
    private var stylusReceiver: InputEventReceiver? = null
    private var fingerMonitor: InputMonitor? = null
    private var fingerReceiver: InputEventReceiver? = null

    private var intercept = false
    private val excluded = Region()
    private var screen = Rect(0, 0, 0, 0)
    private var savedOpacity: String? = null
    private var opacityLifted = false

    @JvmStatic
    fun main(args: Array<String>) {
        textMode = args.contains("--text")
        out = DataOutputStream(FileOutputStream(FileDescriptor.out).buffered(1 shl 16))
        Thread.setDefaultUncaughtExceptionHandler { _, error ->
            log("fatal: " + Log.getStackTraceString(error))
            cleanup()
            exitProcess(1)
        }
        Looper.prepareMainLooper()
        val handler = Handler(Looper.getMainLooper())

        var ready = 0
        screen = queryScreen()
        log("helper uid=${Process.myUid()} pid=${Process.myPid()} screen=$screen")

        runCatching { setupStylusWindow() }
            .onSuccess { ready = ready or HelperProtocol.READY_INTERCEPT }
            .onFailure { log("stylus window failed: " + Log.getStackTraceString(it)) }
        runCatching { setupFingerSpy() }
            .onSuccess { ready = ready or HelperProtocol.READY_SPY }
            .onFailure { log("finger spy failed: " + Log.getStackTraceString(it)) }
        startPenKeyReader()
        ready = ready or HelperProtocol.READY_KEYS

        val flags = ready
        frame(HelperProtocol.FRAME_READY) { it.putInt(flags) }

        Thread({ readCommands(handler) }, "ga-commands").start()
        Looper.loop()
    }

    // ── input windows ───────────────────────────────────────────────────────

    private fun setupStylusWindow() {
        val monitor = InputManagerGlobal.getInstance().monitorGestureInput("ga-stylus", DISPLAY_ID)
        stylusMonitor = monitor
        val channel: InputChannel = monitor.inputChannel
        val handle = InputWindowHandle(
            InputApplicationHandle(null, "ga-stylus", DISPATCH_TIMEOUT_MS),
            DISPLAY_ID,
        )
        handle.name = "ga-stylus"
        handle.token = channel.token
        handle.layoutParamsType = TYPE_SECURE_SYSTEM_OVERLAY
        handle.dispatchingTimeoutMillis = DISPATCH_TIMEOUT_MS
        handle.ownerPid = Process.myPid()
        handle.ownerUid = Process.myUid()
        handle.scaleFactor = 1f
        stylusHandle = handle
        applyStylusWindow()
        stylusReceiver = object : InputEventReceiver(channel, Looper.getMainLooper()) {
            override fun onInputEvent(event: InputEvent) {
                var handled = false
                try {
                    if (event is MotionEvent) {
                        handled = true
                        if (intercept && isStylus(event)) sendMotion(HelperProtocol.FRAME_STYLUS, event)
                    }
                } finally {
                    finishInputEvent(event, handled)
                }
            }
        }
    }

    /**
     * Idle: not touchable at all. Intercepting: NOT_TOUCHABLE + INTERCEPTS_STYLUS,
     * so only the pen lands here and it no longer reaches the app underneath.
     */
    private fun applyStylusWindow() {
        val monitor = stylusMonitor ?: return
        val handle = stylusHandle ?: return
        val surface = monitor.surface
        handle.inputConfig = if (intercept) {
            CONFIG_NOT_FOCUSABLE or CONFIG_NOT_TOUCHABLE or CONFIG_INTERCEPTS_STYLUS
        } else {
            CONFIG_NOT_FOCUSABLE or CONFIG_NOT_TOUCHABLE
        }
        handle.replaceTouchableRegionWithCrop = false
        val region = Region(screen)
        region.op(excluded, Region.Op.DIFFERENCE)
        handle.touchableRegion.set(region)
        val t = SurfaceControl.Transaction()
        // A buffer-less layer has no input bounds of its own; without a crop
        // SurfaceFlinger clears the touchable region.
        t.setCrop(surface, screen)
        handle.setTrustedOverlay(t, surface, true)
        setInputWindowInfo.invoke(t, surface, handle)
        t.apply()
        log("stylus region=${region.bounds} excluded=${excluded.bounds} intercept=$intercept")
    }

    private val setInputWindowInfo by lazy {
        SurfaceControl.Transaction::class.java.getMethod(
            "setInputWindowInfo", SurfaceControl::class.java, InputWindowHandle::class.java,
        )
    }

    private fun setupFingerSpy() {
        val monitor = InputManagerGlobal.getInstance().monitorGestureInput("ga-finger", DISPLAY_ID)
        fingerMonitor = monitor
        fingerReceiver = object : InputEventReceiver(monitor.inputChannel, Looper.getMainLooper()) {
            override fun onInputEvent(event: InputEvent) {
                try {
                    if (event is MotionEvent && !isStylus(event) &&
                        event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)
                    ) sendMotion(HelperProtocol.FRAME_FINGER, event)
                } finally {
                    finishInputEvent(event, false)
                }
            }
        }
    }

    private fun isStylus(event: MotionEvent): Boolean {
        if (event.pointerCount == 0) return false
        val tool = event.getToolType(0)
        return tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER
    }

    private fun sendMotion(type: Int, event: MotionEvent) {
        if (textMode) {
            val who = if (type == HelperProtocol.FRAME_STYLUS) "STYLUS " else "FINGER "
            log(
                who + MotionEvent.actionToString(event.action) +
                    " x=" + event.x + " y=" + event.y + " p=" + event.pressure +
                    " tool=" + event.getToolType(0) + " hist=" + event.historySize,
            )
            return
        }
        val parcel = Parcel.obtain()
        try {
            event.writeToParcel(parcel, 0)
            val bytes = parcel.marshall()
            synchronized(outLock) {
                out.writeByte(type)
                out.writeInt(bytes.size)
                out.write(bytes)
                out.flush()
            }
        } finally {
            parcel.recycle()
        }
    }

    // ── pen keyboard ────────────────────────────────────────────────────────

    private fun findPenKeyNodes(): List<String> {
        val nodes = ArrayList<String>()
        val text = runCatching { File("/proc/bus/input/devices").readText() }.getOrDefault("")
        for (block in text.split("\n\n")) {
            val name = Regex("N: Name=\"(.*)\"").find(block)?.groupValues?.get(1) ?: continue
            if (!name.contains("Focus Pen", ignoreCase = true)) continue
            if (name.contains("Mouse", ignoreCase = true)) continue
            val event = Regex("H: Handlers=.*?(event\\d+)").find(block)?.groupValues?.get(1) ?: continue
            nodes.add("/dev/input/$event")
        }
        return nodes
    }

    private fun startPenKeyReader() {
        val active = HashSet<String>()
        Thread({
            while (true) {
                for (node in findPenKeyNodes()) {
                    val fresh = synchronized(active) { active.add(node) }
                    if (!fresh) continue
                    Thread({
                        runCatching { readKeys(node) }
                        synchronized(active) { active.remove(node) }
                    }, "ga-keys").start()
                }
                Thread.sleep(3000)
            }
        }, "ga-key-scan").apply { isDaemon = true }.start()
    }

    /** struct input_event on arm64: timeval(16) + type(2) + code(2) + value(4). */
    private fun readKeys(node: String) {
        FileInputStream(node).use { stream ->
            log("pen keys: reading $node")
            val buffer = ByteArray(24)
            val view = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
            while (true) {
                var read = 0
                while (read < 24) {
                    val n = stream.read(buffer, read, 24 - read)
                    if (n < 0) return
                    read += n
                }
                val type = view.getShort(16).toInt() and 0xFFFF
                val code = view.getShort(18).toInt() and 0xFFFF
                val value = view.getInt(20)
                if (type != 1) continue
                if (textMode) {
                    log("PENKEY scan=$code value=$value")
                } else {
                    val now = SystemClock.uptimeMillis()
                    frame(HelperProtocol.FRAME_PEN_KEY) {
                        it.putInt(code); it.putInt(value); it.putLong(now)
                    }
                }
            }
        }
    }

    // ── commands ────────────────────────────────────────────────────────────

    private fun readCommands(handler: Handler) {
        val reader = BufferedReader(InputStreamReader(System.`in`))
        while (true) {
            val line = reader.readLine() ?: break
            val space = line.indexOf(' ')
            val name = if (space < 0) line else line.substring(0, space)
            val arg = if (space < 0) "" else line.substring(space + 1).trim()
            when (name) {
                HelperProtocol.CMD_QUIT -> break
                HelperProtocol.CMD_INTERCEPT -> handler.post {
                    intercept = arg == "1"
                    runCatching { applyStylusWindow() }.onFailure { log("apply failed: $it") }
                    log("intercept=$intercept")
                }
                HelperProtocol.CMD_REGION -> handler.post {
                    excluded.setEmpty()
                    for (part in arg.split(';')) {
                        val n = part.split(',').mapNotNull { it.trim().toIntOrNull() }
                        if (n.size == 4) excluded.op(Rect(n[0], n[1], n[2], n[3]), Region.Op.UNION)
                    }
                    runCatching { applyStylusWindow() }.onFailure { log("apply failed: $it") }
                }
                HelperProtocol.CMD_OPACITY -> setOpacityLifted(arg == "1")
                HelperProtocol.CMD_INJECT -> {
                    val n = arg.split(',').mapNotNull { it.trim().toIntOrNull() }
                    if (n.size == 5) {
                        Thread({
                            shell("input swipe ${n[0]} ${n[1]} ${n[2]} ${n[3]} ${n[4]}")
                        }, "ga-inject").start()
                    }
                }
            }
        }
        // stdin closed: the app is gone.
        cleanup()
        exitProcess(0)
    }

    private fun cleanup() {
        runCatching { setOpacityLifted(false) }
        runCatching { stylusReceiver?.dispose() }
        runCatching { fingerReceiver?.dispose() }
        runCatching { stylusMonitor?.dispose() }
        runCatching { fingerMonitor?.dispose() }
    }

    /**
     * A full-screen overlay that lets touches through is treated as an
     * "untrusted obscuring window" above 80 % opacity, which would force the
     * ink to be drawn translucent. While annotating the limit is raised to
     * 100 %, and the previous value is put back afterwards.
     */
    @Synchronized
    private fun setOpacityLifted(lift: Boolean) {
        if (lift == opacityLifted) return
        if (lift) {
            savedOpacity = shell("settings get global $OPACITY_KEY").trim()
            shell("settings put global $OPACITY_KEY 1.0")
        } else {
            val previous = savedOpacity
            if (previous.isNullOrEmpty() || previous == "null") {
                shell("settings delete global $OPACITY_KEY")
            } else {
                shell("settings put global $OPACITY_KEY $previous")
            }
        }
        opacityLifted = lift
        log("opacity lifted=$lift (previous=$savedOpacity)")
    }

    private fun shell(command: String): String = runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        val text = process.inputStream.bufferedReader().readText()
        process.waitFor()
        text
    }.getOrDefault("")

    private fun queryScreen(): Rect {
        val size = Regex("(\\d+)x(\\d+)").findAll(shell("wm size")).lastOrNull()
        val w = size?.groupValues?.get(1)?.toIntOrNull() ?: 4096
        val h = size?.groupValues?.get(2)?.toIntOrNull() ?: 4096
        // A square of the longer side covers both orientations.
        val side = maxOf(w, h)
        return Rect(0, 0, side, side)
    }

    private fun frame(type: Int, fill: (ByteBuffer) -> Unit) {
        val buffer = ByteBuffer.allocate(64)
        fill(buffer)
        synchronized(outLock) {
            if (textMode) {
                out.write("FRAME $type len=${buffer.position()}".toByteArray())
                out.write(NEWLINE)
            } else {
                out.writeByte(type)
                out.writeInt(buffer.position())
                out.write(buffer.array(), 0, buffer.position())
            }
            out.flush()
        }
    }

    private fun log(message: String) {
        val bytes = message.toByteArray()
        synchronized(outLock) {
            if (textMode) {
                out.write(bytes)
                out.write(NEWLINE)
            } else {
                out.writeByte(HelperProtocol.FRAME_LOG)
                out.writeInt(bytes.size)
                out.write(bytes)
            }
            out.flush()
        }
    }
}
