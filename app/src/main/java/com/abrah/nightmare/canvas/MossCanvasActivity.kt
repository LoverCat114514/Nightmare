package com.abrah.nightmare.canvas

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.abrah.nightmare.R

/**
 * The MOSS layout canvas, INSIDE the app (user ask 2026-11-04): a fullscreen
 * WebView over the single-file editor, with a one-method JS bridge. "写回提示词"
 * in the page hands the COMPILED prompt through [Bridge.saveDraft]; the result
 * goes back to the inspector's ActivityResultLauncher, which writes it into the
 * prompt node wired to the sampler that opened this screen. The
 * browser-then-copy-paste loop is gone.
 *
 * ⚠ The editor persists its draft in its own localStorage (the WebView's
 * per-app disk storage). Since fix35x the Activity clears it ONCE per process
 * at startup: a cold start opens an EMPTY canvas (user ask 2026-11-10 — old
 * words kept coming back after the app was killed), while re-entering the
 * canvas within the same process still keeps the draft for continued editing.
 * The bridge adds a return path, it does not change the editor.
 *
 * ⚠ No draft is parsed here. The page compiles; this Activity only carries the
 * compiled string back. Size hints come in as extras and are injected into the
 * page, which knows how to apply them (`__nmSetSize`).
 */
class MossCanvasActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SAMPLER = "sampler_id"
        const val EXTRA_WIDTH = "sampler_width"
        const val EXTRA_HEIGHT = "sampler_height"
        const val EXTRA_DRAFT = "compiled_draft"

        // ⭐ fix35x (user ask 2026-11-10): the page keeps its draft in localStorage, which the WebView
        // persists on disk — a cold start (app killed from recents) replayed yesterday's words into a
        // canvas the user meant to start fresh. Cleared ONCE per process, below, so within one process
        // re-entering the canvas keeps the draft (a backgrounded app is NOT killed, so flipping back
        // and forth keeps editing where it left off); only a fresh process starts empty.
        private var draftClearedThisProcess = false
    }

    private lateinit var web: WebView
    // Typed size reported live by the page (Nightmare.setSize). -1 = no report
    // yet; saveDraft then falls back to the size the page passes with the draft.
    private var sizeW = -1
    private var sizeH = -1

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_moss_canvas)
        hideSystemBars()

        web = findViewById(R.id.moss_canvas_web)
        web.setBackgroundColor(android.graphics.Color.parseColor("#F2F0F7"))
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(Bridge(), "Nightmare")
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // Size hint: the sampler's current width/height widgets, so the
                // canvas opens already matched to what will be generated. The
                // page's own __nmSetSize guards its types.
                val w = intent.getStringExtra(EXTRA_WIDTH)?.filter { it.isDigit() }.orEmpty()
                val h = intent.getStringExtra(EXTRA_HEIGHT)?.filter { it.isDigit() }.orEmpty()
                val js = "try{__nmSetSize(" +
                    (if (w.isEmpty()) "null" else w) + "," +
                    (if (h.isEmpty()) "null" else h) + ")}catch(e){}"
                view.evaluateJavascript(js, null)
            }
        }
        // ⭐ fix35x: once per process, before the page can read its draft back. The editor's whole
        // persistence is `localStorage["mossDraft"]` on the `file://` origin, so deleting that origin
        // clears it without touching cookies or anything else (this is the app's only DOM-storage WebView).
        if (!draftClearedThisProcess) {
            draftClearedThisProcess = true
            android.webkit.WebStorage.getInstance().deleteOrigin("file://")
        }
        web.loadUrl("file:///android_asset/moss_canvas/index.html")
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    /**
     * The one bridge method. Runs on a WebView thread — setResult/finish are
     * marshalled to the UI thread. A cancel (back gesture) finishes with no
     * result and nothing is written.
     *
     * ⭐ (2026-11-04 fix) The draft's canvas size rides home beside the compiled
     * prompt: the inspector writes it back to the sampler's own width/height, so
     * the canvas and the sampler's widgets no longer drift apart.
     */
    private inner class Bridge {
        @JavascriptInterface
        fun close() = runOnUiThread { finish() }

        @JavascriptInterface
        fun setSize(w: Int, h: Int) = runOnUiThread { sizeW = w; sizeH = h }

        @JavascriptInterface
        fun saveDraft(compiled: String, width: Int, height: Int) {
            runOnUiThread {
                // Prefer the live-typed size (kept current by setSize) over the
                // value captured when the draft was compiled, so edits made
                // after compiling still land back on the sampler widgets.
                val w = if (sizeW > 0) sizeW else width
                val h = if (sizeH > 0) sizeH else height
                val data = Intent()
                    .putExtra(EXTRA_DRAFT, compiled)
                    .putExtra(EXTRA_WIDTH, w)
                    .putExtra(EXTRA_HEIGHT, h)
                setResult(RESULT_OK, data)
                finish()
            }
        }
    }
}
