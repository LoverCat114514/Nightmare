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
 * ⚠ The editor keeps persisting its draft in its own localStorage (the
 * WebView's per-app storage), so drafts survive across opens with no extra
 * code — the bridge adds a return path, it does not change the editor.
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
    }

    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_moss_canvas)
        hideSystemBars()

        web = findViewById(R.id.moss_canvas_web)
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
     */
    private inner class Bridge {
        @JavascriptInterface
        fun saveDraft(compiled: String) {
            runOnUiThread {
                val data = Intent().putExtra(EXTRA_DRAFT, compiled)
                setResult(RESULT_OK, data)
                finish()
            }
        }
    }
}
