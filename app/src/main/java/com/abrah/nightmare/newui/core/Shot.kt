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
 */
class Shot(
    val bitmap: ImageBitmap,
    val ms: Long,
    val seed: Long,
    val file: File,
) {
    val key: String get() = file.absolutePath
}

/**
 * ⭐ fix33/fix34: a downscaled copy for the 84 dp history strip.
 *
 * The strip used to draw the FULL render (`out.asImageBitmap()`, 1024 px and
 * up) into an 84 dp square. Compose uploads the whole bitmap as a texture
 * whatever size it is drawn at, so every scroll frame paid for a full-size
 * upload and sample per thumbnail. 84 dp at 3× is ≈252 px, so 256 is the right
 * ceiling.
 *
 * ⚠ The source is never mutated: `asImageBitmap()` wraps an immutable Bitmap,
 * and this returns a NEW one. Remember it at the call site — a `remember` keyed
 * on the source bitmap — so a scroll does not rescale it every frame.
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
