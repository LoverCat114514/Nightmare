package com.abrah.nightmare.scramble

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ⭐⭐⭐ Image Scramble Engine — High-performance, 100% mathematically lossless image
 * scrambler based on the Generalized Hilbert ('Gilbert') space-filling curve.
 *
 * Fully reversible: descramble(scramble(img)) == img with zero loss of quality.
 * Uses Math.floorDiv to maintain 100% exact cross-platform compatibility with JS/Python.
 */
object ImageScrambleEngine {

    /**
     * Generates the discrete 2D Hilbert space-filling curve coordinates for an arbitrary-sized
     * grid of dimensions [width] x [height].
     *
     * Packaged into a single flat [IntArray] where each value is `y * width + x` to eliminate
     * 1 million Point/Pair object allocations and avoid GC pauses on mobile devices.
     */
    fun gilbert2d(width: Int, height: Int): IntArray {
        val total = width * height
        val coords = IntArray(total)
        var idx = 0

        fun sign(n: Int): Int = when {
            n > 0 -> 1
            n < 0 -> -1
            else -> 0
        }

        fun generate2d(x0: Int, y0: Int, ax: Int, ay: Int, bx: Int, by: Int) {
            var x = x0
            var y = y0
            val w = Math.abs(ax + ay)
            val h = Math.abs(bx + by)

            val dax = sign(ax)
            val day = sign(ay)
            val dbx = sign(bx)
            val dby = sign(by)

            if (h == 1) {
                for (i in 0 until w) {
                    coords[idx++] = y * width + x
                    x += dax
                    y += day
                }
                return
            }

            if (w == 1) {
                for (i in 0 until h) {
                    coords[idx++] = y * width + x
                    x += dbx
                    y += dby
                }
                return
            }

            var ax2 = Math.floorDiv(ax, 2)
            var ay2 = Math.floorDiv(ay, 2)
            var bx2 = Math.floorDiv(bx, 2)
            var by2 = Math.floorDiv(by, 2)

            val w2 = Math.abs(ax2 + ay2)
            val h2 = Math.abs(bx2 + by2)

            if (2 * w > 3 * h) {
                if ((w2 % 2 != 0) && (w > 2)) {
                    ax2 += dax
                    ay2 += day
                }
                generate2d(x, y, ax2, ay2, bx, by)
                generate2d(x + ax2, y + ay2, ax - ax2, ay - ay2, bx, by)
            } else {
                if ((h2 % 2 != 0) && (h > 2)) {
                    bx2 += dbx
                    by2 += dby
                }
                generate2d(x, y, bx2, by2, ax2, ay2)
                generate2d(x + bx2, y + by2, ax, ay, bx - bx2, by - by2)
                generate2d(
                    x + (ax - dax) + (bx2 - dbx),
                    y + (ay - day) + (by2 - dby),
                    -bx2,
                    -by2,
                    -(ax - ax2),
                    -(ay - ay2),
                )
            }
        }

        if (width >= height) {
            generate2d(0, 0, width, 0, 0, height)
        } else {
            generate2d(0, 0, 0, height, width, 0)
        }

        return coords
    }

    /**
     * Scrambles (encrypts) the input [bitmap] using Gilbert space curve permutation.
     * Guaranteed 100% reversible by [descramble].
     */
    suspend fun scramble(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val width = bitmap.width
        val height = bitmap.height
        val total = width * height

        val inPixels = IntArray(total)
        bitmap.getPixels(inPixels, 0, width, 0, 0, width, height)

        val curve = gilbert2d(width, height)
        val offset = Math.round(((Math.sqrt(5.0) - 1.0) / 2.0) * total).toInt()

        val outPixels = IntArray(total)
        for (i in 0 until total) {
            val oldPos = curve[i]
            val newPos = curve[(i + offset) % total]
            outPixels[newPos] = inPixels[oldPos]
        }

        val config = bitmap.config ?: Bitmap.Config.ARGB_8888
        Bitmap.createBitmap(outPixels, width, height, config)
    }

    /**
     * Descrambles (decrypts) the scrambled [bitmap] back into its pristine original form.
     */
    suspend fun descramble(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val width = bitmap.width
        val height = bitmap.height
        val total = width * height

        val inPixels = IntArray(total)
        bitmap.getPixels(inPixels, 0, width, 0, 0, width, height)

        val curve = gilbert2d(width, height)
        val offset = Math.round(((Math.sqrt(5.0) - 1.0) / 2.0) * total).toInt()

        val outPixels = IntArray(total)
        for (i in 0 until total) {
            val oldPos = curve[i]
            val newPos = curve[(i + offset) % total]
            outPixels[oldPos] = inPixels[newPos]
        }

        val config = bitmap.config ?: Bitmap.Config.ARGB_8888
        Bitmap.createBitmap(outPixels, width, height, config)
    }
}
