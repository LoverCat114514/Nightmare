package com.abrah.nightmare.newui.core

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * ⭐⭐ fix34 (2026-11-06) — one finished picture of this session.
 *
 * The decoded bitmap plus the meta the strip shows. Promoted from a
 * file-private class in `NewUiPanel.kt` to a real type in `core`, because the
 * cards now take it as a **parameter** instead of reading a file-level
 * `AtomicReference` (`LAST_SHOT`) plus a hand-rolled `LAST_SHOT_VERSION`
 * counter. That pair was an observable whose correctness depended on every
 * writer remembering to bump the version — one missed bump and the frame
 * showed a stale picture forever, with no way to tell from the code.
 *
 * [key] is the strip's `LazyRow` identity. `LazyRow` keyed by position
 * re-composes every thumbnail whenever one is inserted at the head, and a new
 * shot always is; keyed by file identity, only the new one composes. `file`
 * is unique per render (the engine writes a fresh file each run), so its path
 * is the natural key — and it is stable across recompositions, unlike
 * `hashCode()` of the bitmap.
 *
 * [meta] holds the serialized JSON parameters of this generation so it can be
 * injected into the PNG tEXt metadata when saving and extracted upon import.
 */
class Shot(
    val bitmap: ImageBitmap,
    val ms: Long,
    val seed: Long,
    val file: File,
    val meta: String? = null,
    /**
     * ⭐ fix35g — the post-processing chain that produced [bitmap], as flags.
     * The on-screen bitmap is post-processed but [file] is the RAW render, so
     * the gallery save used to lose every toggle. The save path re-applies this
     * chain to the raw file; [com.abrah.nightmare.newui.core.saveShot] receives
     * it via its `post` lambda.
     */
    val postColor: Boolean = false,
    val postAdj: Boolean = false,
    val postUps4x: Boolean = false,
    val postUps2x: Boolean = false,
) {
    val key: String get() = file.absolutePath

    /**
     * ⭐ fix35h — whether ANY post tool touched [bitmap]. The workbench pager
     * then offers two pages: the RAW render and the post-processed picture.
     * [file] is always the raw render — the engine writes it once and never
     * rewrites it — so the original needs no extra storage.
     */
    val hasPost: Boolean
        get() = postColor || postAdj || postUps4x || postUps2x

    /**
     * ⭐ fix35h — the raw render, decoded lazily on first page swipe so an
     * upscaled x8 session never holds two giant bitmaps from the start.
     */
    val rawBitmap: ImageBitmap by lazy {
        val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            ?: bitmap.asAndroidBitmap()
        bmp.asImageBitmap()
    }

    /**
     * ⭐ Cached 256 px thumbnail for the 84 dp history strip.
     * Evaluated lazily once on demand, avoiding main-thread bitmap scaling during list scroll.
     */
    val thumbnail: ImageBitmap by lazy { bitmap.thumb(256) }
}

/**
 * ⭐ fix33/fix34: a downscaled copy for the 84 dp history strip.
 */
fun ImageBitmap.thumb(maxPx: Int): ImageBitmap {
    val src = asAndroidBitmap()
    val long = maxOf(src.width, src.height)
    if (long <= maxPx) return this
    val scale = maxPx.toFloat() / long
    val w = (src.width * scale).toInt().coerceAtLeast(1)
    val h = (src.height * scale).toInt().coerceAtLeast(1)
    val scaled = android.graphics.Bitmap.createScaledBitmap(src, w, h, true)
    return scaled.asImageBitmap()
}
