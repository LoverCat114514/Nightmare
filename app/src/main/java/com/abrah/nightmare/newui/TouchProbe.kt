package com.abrah.nightmare.newui

import android.content.Context
import android.os.Environment
import android.view.Choreographer
import android.view.MotionEvent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * ⭐ TouchProbe (2026-11-06, twenty-ninth round): the black box for problem 4
 * ("the canvas intermittently refuses to scroll"). Synthetic gestures never
 * reproduced it (6/6 swipes landed, janky 2.44%), so the probe rides inside
 * the app and records the three layers that decide whether a scroll can
 * happen:
 *
 *   DISPATCH — Activity.dispatchTouchEvent saw DOWN/UP/CANCEL, with how long
 *              the whole dispatch took (`took=`): kernel → framework → Compose.
 *   STALL    — Choreographer frame gaps while frames ARE being produced; a
 *              main-thread stall only shows when something is animating
 *              (idle screens produce no frames, so no false positives).
 *   SCROLL   — the workbench scroll position actually changed: Compose
 *              applied the gesture to the one verticalScroll in NEWUI.
 *
 * Reading a frozen moment (grab the file, look around the wall-clock time):
 *   1. DISPATCH present + STALL present            → main thread saturated
 *      (compute on the UI thread); `took=` points at the culprit dispatch.
 *   2. DISPATCH present + NO STALL + SCROLL frozen → the gesture is being
 *      consumed by something else (ownership conflict): events flow, the
 *      thread is alive, yet the scroll state never changes.
 *   3. No DISPATCH lines + a STALL storm           → the app never even saw
 *      the finger (window-level freeze, ANR territory).
 *
 * The log lives in Download/NightmareProbe/touch.log (the app already holds
 * MANAGE_EXTERNAL_STORAGE for its model imports), falling back to the app's
 * own external files dir. Appended from ONE background thread; the hot path
 * is a volatile read and two long ops per event — never touches main. The
 * file rotates at 4 MB (one generation kept) so it cannot grow unbounded.
 */
object TouchProbe {
    private val lock = Any()
    private var writer: FileWriter? = null
    private var file: File? = null
    private val pool = Executors.newSingleThreadExecutor { r -> Thread(r, "touch-probe") }
    private val ts = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val lastFrameNs = AtomicLong(0L)
    private var lastScrollLog = 0L

    /** The current workbench scroll value, for correlating a freeze report. */
    @Volatile
    var scrollValue: Float = Float.NaN
        private set

    /** Called from MainActivity.onCreate, once per process. */
    fun install(ctx: Context) {
        synchronized(lock) {
            if (writer != null) return
            hostActivity = ctx as? android.app.Activity
            val pub: File? = try {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "NightmareProbe")
            } catch (t: Throwable) {
                null
            }
            var target: File? = null
            if (pub != null) {
                try {
                    pub.mkdirs()
                    if (pub.isDirectory && pub.canWrite()) target = File(pub, "touch.log")
                } catch (t: Throwable) {
                    // fall through to the private dir
                }
            }
            if (target == null) {
                val priv = File(ctx.getExternalFilesDir(null), "probe")
                priv.mkdirs()
                target = File(priv, "touch.log")
            }
            try {
                file = target
                writer = FileWriter(target, true)
                writer!!.write("=== session start ${ts.format(Date())} (log=${target!!.path}) ===\n")
                writer!!.flush()
            } catch (t: Throwable) {
                // The probe must never take the app down; a missing log is
                // reported by the absence of the file, not by a crash.
                writer = null
                file = null
            }
        }
    }

    /** The one log sink; the write happens on the probe thread. */
    fun log(line: String) {
        val w = writer ?: return
        val stamp = ts.format(Date())
        pool.execute {
            synchronized(lock) {
                try {
                    w.write("$stamp $line\n")
                    w.flush()
                    val f = file ?: return@synchronized
                    if (f.length() > 4_000_000L) {
                        w.close()
                        val old = File(f.parentFile, "touch.old.log")
                        old.delete()
                        f.renameTo(old)
                        writer = FileWriter(f, false)
                    }
                } catch (t: Throwable) {
                    // swallow: probing must never crash the app
                }
            }
        }
    }

    /**
     * Layer 1, from Activity.dispatchTouchEvent: DOWN/UP/CANCEL only (MOVEs
     * would flood the file), plus the whole dispatch cost in microseconds —
     * a large `took=` on a frozen moment means the main thread was busy
     * somewhere inside event dispatch.
     */
    fun dispatch(ev: MotionEvent, tookUs: Long) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Window height on DOWN: the IME eats 30-40% of it when it
                // rises — a height drop between two DOWNs is the keyboard
                // transition that swallows gestures in flight.
                val h = try {
                    windowMetricsHeight()
                } catch (t: Throwable) {
                    -1
                }
                log("DISPATCH a=DOWN took=${tookUs}us x=${(ev.x * 10).toInt() / 10f} y=${(ev.y * 10).toInt() / 10f} winH=$h")
            }
            MotionEvent.ACTION_UP ->
                log("DISPATCH a=UP took=${tookUs}us dur=${ev.eventTime - ev.downTime}ms")
            MotionEvent.ACTION_CANCEL ->
                log("DISPATCH a=CANCEL took=${tookUs}us dur=${ev.eventTime - ev.downTime}ms")
            MotionEvent.ACTION_POINTER_DOWN ->
                log("DISPATCH a=PTR_DOWN n=${ev.pointerCount} idx=${ev.actionIndex}")
            MotionEvent.ACTION_POINTER_UP ->
                log("DISPATCH a=PTR_UP n=${ev.pointerCount} idx=${ev.actionIndex}")
        }
    }

    /** The dispatch-time window height; needs the activity, set on install. */
    private var hostActivity: android.app.Activity? = null

    private fun windowMetricsHeight(): Int {
        val act = hostActivity ?: return -1
        return act.windowManager.currentWindowMetrics.bounds.height()
    }

    /** Layer 3, from the workbench scroll state (throttled to ~7 lines/s). */
    fun markScroll(v: Float) {
        scrollValue = v
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastScrollLog < 150) return
        lastScrollLog = now
        log("SCROLL v=$v")
    }

    /**
     * Layer 3b: the scroll RANGE. A collapsed measure (maxValue == 0 while the
     * screen shows full content) makes every drag a no-op with the value glued
     * at 0 — the exact dead-swipe signature. Logged only on CHANGE.
     */
    fun markScrollRange(max: Int) {
        if (max == lastScrollMax) return
        lastScrollMax = max
        log("SCROLLRANGE max=$max")
    }

    private var lastScrollMax = -1

    /**
     * Layer 2, installed from MainActivity.onCreate: re-posts itself every
     * frame and logs only gaps > 700 ms — an idle screen produces no frames
     * at all, so silence here is normal and a gap is always a real stall.
     */
    val frameProbe: Choreographer.FrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameNs: Long) {
            val prev = lastFrameNs.getAndSet(frameNs)
            if (prev > 0L) {
                val gapMs = (frameNs - prev) / 1_000_000L
                if (gapMs > 700L) log("STALL gap=${gapMs}ms")
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
}

/**
 * Layer 4 (probe v3): a Compose-tree pass observer, attached as
 * `Modifier.pointerInput(Unit) { TouchProbe.passObserver("tag") }` on a node.
 * Top-level EXTENSION on [PointerInputScope] — `awaitPointerEvent` only exists
 * inside a pointer-input scope, and an extension declared INSIDE the object
 * could not be called with the `TouchProbe.` qualifier anyway.
 *
 * What it answers, per gesture:
 *   DOWN        — the hit path reached this subtree at all (awaited in the
 *                 INITIAL pass, before any descendant can react).
 *   MOVE c=true — a descendant IS consuming the drag (ownership conflict
 *                 caught red-handed, at this layer of the tree).
 *   UP          — the gesture ended (a system CANCEL lands here too: this
 *                 Compose version has no Cancel event type, cancellation just
 *                 drops every pointer's `pressed`); `consumed=` says whether
 *                 anything ate the drag.
 *
 * Reading a frozen moment across two tags (host = the NEWUI host Box,
 * wb = the workbench Column):
 *   host logs, wb silent          → the event died BETWEEN the two layers
 *                                   (something invisible eats it).
 *   both log, SCROLL frozen,
 *   MOVE consumed=true            → a descendant consumes the drag and the
 *                                   scroll never applies it.
 *   both log, SCROLL frozen,
 *   MOVE consumed=false           → the scrollable saw the whole drag and
 *                                   refused — measurement collapse territory
 *                                   (check SCROLLRANGE).
 */
suspend fun PointerInputScope.passObserver(tag: String) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        TouchProbe.log("PASS tag=$tag DOWN")
        var moves = 0
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Main)
            val consumed = ev.changes.any { it.isConsumed }
            if (ev.changes.none { it.pressed }) {
                TouchProbe.log("PASS tag=$tag UP consumed=$consumed")
                break
            }
            // Throttle: one line per ~8 move events (~8/s at 60 Hz), and only
            // while something consumes — silence means the drag is flowing
            // through unconsumed.
            if (consumed && moves++ % 8 == 0) TouchProbe.log("PASS tag=$tag MOVE consumed=true")
        }
    }
}
