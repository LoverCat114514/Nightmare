/*
 * [LOCAL-NEWUI 2026-11-05] The fourth view (the web workbench), the WebView host.
 * A user-added local addition, not upstream: it rides the canvas slot — the top-left
 * toggle, Settings → Interface mode, the view memory — like AGENT/NODES/GRAPH do.
 *
 * One fullscreen WebView over `file:///android_asset/newui/nm-workbench.html`, with a
 * three-method bridge ("Nightmare" object):
 *   • boot   — onPageFinished injects the API address (127.0.0.1:8820) and the token, so
 *              the page needs no manual configuration inside the app.
 *   • appPng(base64) — `a.download` does nothing in a WebView, so the page hands finished
 *              PNGs across the bridge instead; saved to Pictures/Nightmare via MediaStore,
 *              a toast confirms.
 *   • openMoss(w,h) — the page's "打开布局画布" launches the SAME MossCanvasActivity the
 *              Compose UI uses, and the compiled draft returns through the ActivityResult
 *              into __nmOnMossDraft(w,h,draftJsonB64) in the page.
 *
 * The factory returns the view; `MainActivity`'s `newUiContent` slot puts it into the
 * canvas Box (AndroidView), so the Compose shell keeps owning the screen — switching views
 * away simply stops composing it, and the WebView goes with the composition. The launcher
 * is registered in composition (`rememberLauncherForActivityResult`), matching how every
 * other picker in this app declares its own per-screen launcher.
 */
package com.abrah.nightmare.newui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.abrah.nightmare.R
import com.abrah.nightmare.api.ApiSettings
import com.abrah.nightmare.canvas.MossCanvasActivity

/**
 * ⭐ The NEWUI page, as a composable. Recreated whenever its composition returns — the same
 * trade the fullscreen viewer makes — and the boot values are read fresh on every mount, so
 * a regenerated token applies on the next entry with no restart.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NewUiScreen() {
    val context = LocalContext.current

    // The MOSS canvas returns its compiled draft (+ size) here; the bridge hands it into
    // the page through __nmOnMossDraft. Registered in composition, per the app's pattern.
    var webRef by remember { androidx.compose.runtime.mutableStateOf<WebView?>(null) }
    val mossLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { r ->
        if (r.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val w = r.data?.getIntExtra(MossCanvasActivity.EXTRA_WIDTH, -1) ?: -1
        val h = r.data?.getIntExtra(MossCanvasActivity.EXTRA_HEIGHT, -1) ?: -1
        val draft = r.data?.getStringExtra(MossCanvasActivity.EXTRA_DRAFT).orEmpty()
        if (draft.isNotEmpty()) {
            val b64 = Base64.encodeToString(draft.toByteArray(), Base64.NO_WRAP)
            val js = "try{__nmOnMossDraft(" + (if (w > 0) w.toString() else "null") + "," +
                (if (h > 0) h.toString() else "null") + ",\"" + b64 + "\")}catch(e){}"
            webRef?.evaluateJavascript(js, null)
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                addJavascriptInterface(NewUiBridge(ctx), "Nightmare")
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        // ⭐ Boot injection: fresh address + token on every mount.
                        val addr = "http://127.0.0.1:" + ApiSettings.PORT
                        val tok = ApiSettings.token(ctx)
                        view.evaluateJavascript(
                            "try{__nmBoot(" +
                                "\"" + addr.replace("\"", "\\\"") + "\"," +
                                "\"" + tok.replace("\"", "\\\"") + "\"" +
                                ")}catch(e){}",
                            null,
                        )
                    }
                }
                webRef = this
                loadUrl("file:///android_asset/newui/nm-workbench.html")
            }
        },
        onRelease = { it.destroy() },
    )
    // The launcher must reach the bridge created inside `factory`; hand it over here so
    // both see the same slot.
    NewUiBridge.mossLauncher = mossLauncher
}

/**
 * The bridge object. Every method runs on a WebView thread; UI work marshals through the
 * main handler. The Moss launcher lives in a companion slot because the bridge is created
 * inside the AndroidView factory while the launcher registers in composition.
 */
private class NewUiBridge(private val context: android.content.Context) {
    private val main = Handler(Looper.getMainLooper())

    /** The page's save (manual button, autosave): base64 PNG bytes → Pictures/Nightmare. */
    @JavascriptInterface
    fun appPng(b64: String) {
        main.post {
            try {
                val bytes = Base64.decode(b64, Base64.NO_WRAP)
                val name = "nm-" + System.currentTimeMillis() + ".png"
                val cv = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Nightmare")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
                    ?: throw IllegalStateException("MediaStore insert failed")
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                Toast.makeText(
                    context,
                    context.getString(R.string.newui_saved, "Pictures/Nightmare/$name"),
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, "save: " + e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** The page's "打开布局画布" — the SAME MossCanvasActivity the Compose UI opens. */
    @JavascriptInterface
    fun openMoss(width: Int, height: Int) {
        main.post {
            val act = context as? Activity ?: return@post
            val launcher = NewUiBridge.mossLauncher ?: return@post
            val i = Intent(act, MossCanvasActivity::class.java)
                .putExtra(MossCanvasActivity.EXTRA_WIDTH, if (width > 0) width else 832)
                .putExtra(MossCanvasActivity.EXTRA_HEIGHT, if (height > 0) height else 1216)
            launcher.launch(i)
        }
    }

    companion object {
        /** The Moss canvas launcher, handed over by [NewUiScreen] once it registers. */
        @Volatile var mossLauncher: androidx.activity.result.ActivityResultLauncher<Intent>? = null
    }
}