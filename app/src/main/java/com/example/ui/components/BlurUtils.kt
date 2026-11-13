package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Generates a genuinely blurred, opaque black-backed ImageBitmap from a Compose ImageBitmap
 * for Android versions below API 31 where Modifier.blur() is unsupported.
 *
 * Uses downscaling paired with a multi-pass separable sliding-window box blur
 * (approximating a ~28dp Gaussian blur at ~80% intensity) in O(W*H) time.
 */
fun createBlurredBackdrop(
    source: ImageBitmap,
    downscaleFactor: Int = 4,
    radius: Int = 12
): ImageBitmap {
    val srcBitmap = source.asAndroidBitmap()
    val safeFactor = downscaleFactor.coerceAtLeast(1)
    val scaledWidth = (srcBitmap.width / safeFactor).coerceAtLeast(16)
    val scaledHeight = (srcBitmap.height / safeFactor).coerceAtLeast(16)

    var copiedSoftware: Bitmap? = null
    val readable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        srcBitmap.config == Bitmap.Config.HARDWARE
    ) {
        srcBitmap.copy(Bitmap.Config.ARGB_8888, false).also { copiedSoftware = it }
    } else {
        srcBitmap
    }

    val smallBitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(smallBitmap)
        canvas.drawColor(android.graphics.Color.BLACK)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        canvas.drawBitmap(readable, null, Rect(0, 0, scaledWidth, scaledHeight), paint)
    } finally {
        copiedSoftware?.recycle()
    }

    if (radius > 0) {
        val pixels = IntArray(scaledWidth * scaledHeight)
        smallBitmap.getPixels(pixels, 0, scaledWidth, 0, 0, scaledWidth, scaledHeight)
        applySeparableBoxBlur(pixels, scaledWidth, scaledHeight, radius, passes = 3)
        smallBitmap.setPixels(pixels, 0, scaledWidth, 0, 0, scaledWidth, scaledHeight)
    }

    return smallBitmap.asImageBitmap()
}

/**
 * Blurs an already-downscaled ARGB_8888 Bitmap (such as one captured via PixelCopy) in place.
 * Returns null if the captured bitmap contains no visible non-black content so the UI never
 * covers the live dashboard with a solid black rectangle.
 */
fun blurBitmapInPlace(
    bitmap: Bitmap,
    radius: Int = 12
): ImageBitmap? {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return null

    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    var hasVisibleContent = false
    for (i in pixels.indices) {
        val c = pixels[i]
        val r = (c ushr 16) and 0xFF
        val g = (c ushr 8) and 0xFF
        val b = c and 0xFF
        if (r > 8 || g > 8 || b > 8) {
            hasVisibleContent = true
            break
        }
    }
    if (!hasVisibleContent) return null

    if (radius > 0) {
        applySeparableBoxBlur(pixels, width, height, radius, passes = 3)
    } else {
        for (i in pixels.indices) {
            pixels[i] = (0xFF shl 24) or (pixels[i] and 0x00FFFFFF)
        }
    }

    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap.asImageBitmap()
}

internal fun applySeparableBoxBlur(
    pixels: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    passes: Int = 3
) {
    if (width <= 0 || height <= 0 || radius <= 0 || passes <= 0) return
    val temp = IntArray(width * height)
    val div = 2 * radius + 1

    repeat(passes) {
        // Horizontal pass: pixels -> temp
        for (y in 0 until height) {
            val rowOffset = y * width
            var rSum = 0
            var gSum = 0
            var bSum = 0

            for (k in -radius..radius) {
                val sampleX = k.coerceIn(0, width - 1)
                val c = pixels[rowOffset + sampleX]
                rSum += (c ushr 16) and 0xFF
                gSum += (c ushr 8) and 0xFF
                bSum += c and 0xFF
            }

            for (x in 0 until width) {
                temp[rowOffset + x] =
                    (0xFF shl 24) or
                    ((rSum / div) shl 16) or
                    ((gSum / div) shl 8) or
                    (bSum / div)

                if (x + 1 < width) {
                    val removeX = (x - radius).coerceIn(0, width - 1)
                    val addX = (x + radius + 1).coerceIn(0, width - 1)
                    val removeColor = pixels[rowOffset + removeX]
                    val addColor = pixels[rowOffset + addX]

                    rSum += ((addColor ushr 16) and 0xFF) - ((removeColor ushr 16) and 0xFF)
                    gSum += ((addColor ushr 8) and 0xFF) - ((removeColor ushr 8) and 0xFF)
                    bSum += (addColor and 0xFF) - (removeColor and 0xFF)
                }
            }
        }

        // Vertical pass: temp -> pixels
        for (x in 0 until width) {
            var rSum = 0
            var gSum = 0
            var bSum = 0

            for (k in -radius..radius) {
                val sampleY = k.coerceIn(0, height - 1)
                val c = temp[sampleY * width + x]
                rSum += (c ushr 16) and 0xFF
                gSum += (c ushr 8) and 0xFF
                bSum += c and 0xFF
            }

            for (y in 0 until height) {
                pixels[y * width + x] =
                    (0xFF shl 24) or
                    ((rSum / div) shl 16) or
                    ((gSum / div) shl 8) or
                    (bSum / div)

                if (y + 1 < height) {
                    val removeY = (y - radius).coerceIn(0, height - 1)
                    val addY = (y + radius + 1).coerceIn(0, height - 1)
                    val removeColor = temp[removeY * width + x]
                    val addColor = temp[addY * width + x]

                    rSum += ((addColor ushr 16) and 0xFF) - ((removeColor ushr 16) and 0xFF)
                    gSum += ((addColor ushr 8) and 0xFF) - ((removeColor ushr 8) and 0xFF)
                    bSum += (addColor and 0xFF) - (removeColor and 0xFF)
                }
            }
        }
    }
}
