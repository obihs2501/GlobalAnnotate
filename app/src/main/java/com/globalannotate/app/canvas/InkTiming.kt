package com.globalannotate.app.canvas

import android.util.Log

/**
 * Switchable latency instrumentation for the ink path.
 *
 * Off by default: no logs and no timing work beyond two volatile reads. Turn it
 * on from adb with
 * `am broadcast -a com.globalannotate.action.LATENCY --ez enabled true`
 * and read `GA.Latency` every stroke end (or force a dump with `--ez dump true`).
 *
 * Stages: HelperClient hand-off -> AnnotationInputService.onTransact ->
 * canvas.post queue -> injectStylusEvent -> one coalesced redraw per frame.
 */
object InkTiming {

    private const val TAG = "GA.Latency"

    @Volatile
    var enabled = false

    private var samples = 0
    private var redraws = 0
    private var strokeCount = 0
    private var ipcEvents = 0
    private var ipcTotalNs = 0L
    private var queueTotalNs = 0L
    private var redrawTotalNs = 0L
    private var injectTotalNs = 0L
    private var redrawMaxNs = 0L
    private var strokeStartMs = 0L
    private val lock = Any()

    fun onIpc(eventTimeMs: Long, receiveUptimeMs: Long) {
        if (!enabled) return
        synchronized(lock) {
            ipcEvents++
            ipcTotalNs += ((receiveUptimeMs - eventTimeMs).coerceAtLeast(0L)) * 1_000_000L
        }
    }

    fun onQueued(postedAtNs: Long) {
        if (!enabled) return
        synchronized(lock) { queueTotalNs += System.nanoTime() - postedAtNs }
    }

    fun onInject(nanos: Long) {
        if (!enabled) return
        synchronized(lock) { injectTotalNs += nanos }
    }

    fun onSample() {
        if (!enabled) return
        synchronized(lock) {
            if (samples == 0) strokeStartMs = System.currentTimeMillis()
            samples++
        }
    }

    fun onRedraw(nanos: Long) {
        if (!enabled) return
        synchronized(lock) {
            redraws++
            redrawTotalNs += nanos
            if (nanos > redrawMaxNs) redrawMaxNs = nanos
        }
    }

    fun onStrokeEnd() {
        if (!enabled) return
        synchronized(lock) {
            strokeCount++
            if (strokeCount % 3 == 0) dumpLocked()
        }
    }

    fun dump() {
        if (!enabled) return
        synchronized(lock) { dumpLocked() }
    }

    private fun dumpLocked() {
        val ms = { ns: Long -> "%.2f".format(ns / 1_000_000.0) }
        Log.i(
            TAG,
            "strokes=$strokeCount samples=$samples redraws=$redraws " +
                "ipc=$ipcEvents ipcTotal=${ms(ipcTotalNs)}ms " +
                "queueTotal=${ms(queueTotalNs)}ms injectTotal=${ms(injectTotalNs)}ms " +
                "redrawTotal=${ms(redrawTotalNs)}ms redrawMax=${ms(redrawMaxNs)}ms " +
                "avgRedraw=${if (redraws > 0) ms(redrawTotalNs / redraws) else "0"}ms " +
                "redrawPerSample=${if (samples > 0) "%.2f".format(redraws.toDouble() / samples) else "0"} " +
                "elapsed=${System.currentTimeMillis() - strokeStartMs}ms",
        )
    }

    fun reset() {
        synchronized(lock) {
            samples = 0; redraws = 0; strokeCount = 0; ipcEvents = 0
            ipcTotalNs = 0; queueTotalNs = 0; redrawTotalNs = 0
            injectTotalNs = 0; redrawMaxNs = 0; strokeStartMs = System.currentTimeMillis()
        }
    }
}
