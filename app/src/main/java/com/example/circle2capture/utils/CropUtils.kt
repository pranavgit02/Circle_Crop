package com.example.circle2capture.utils

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect as AndroidRect
import android.graphics.RectF as AndroidRectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.applyCanvas
import kotlin.math.roundToInt

/** Where the src (srcW x srcH) is drawn inside a box of size 'box' with ContentScale.Fit. */
fun computeFitBounds(box: IntSize, srcW: Int, srcH: Int): Rect {
    if (box.width == 0 || box.height == 0) return Rect.Zero
    val boxRatio = box.width.toFloat() / box.height
    val srcRatio = srcW.toFloat() / srcH
    val (drawW, drawH) = if (srcRatio >= boxRatio) {
        val w = box.width.toFloat(); val h = w / srcRatio; w to h
    } else {
        val h = box.height.toFloat(); val w = h * srcRatio; w to h
    }
    val left = (box.width - drawW) / 2f
    val top = (box.height - drawH) / 2f
    return Rect(left, top, left + drawW, top + drawH)
}

private fun viewToBitmap(
    x: Float, y: Float,
    imageBoundsInView: Rect,
    srcW: Int, srcH: Int
): Pair<Float, Float> {
    val nx = ((x - imageBoundsInView.left) / imageBoundsInView.width).coerceIn(0f, 1f)
    val ny = ((y - imageBoundsInView.top)  / imageBoundsInView.height).coerceIn(0f, 1f)
    return nx * srcW to ny * srcH
}

private fun ensureSoftwareBitmap(src: Bitmap): Bitmap =
    if (src.config == Bitmap.Config.HARDWARE)
        src.copy(Bitmap.Config.ARGB_8888, /* mutable = */ false)
    else src

/** Circular PNG containing only the area inside the user’s circle (transparent outside). */
fun cropCircleFromBitmap(
    source: Bitmap,
    imageBoundsInView: Rect,
    circleCenterInView: Offset,
    circleRadiusInView: Float
): Bitmap {
    // Guarantee a software-backed bitmap for Canvas drawing
    val safeSource = ensureSoftwareBitmap(source)

    // Guard: avoid divide-by-zero / invalid bounds
    if (imageBoundsInView.width <= 0f || imageBoundsInView.height <= 0f) {
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    val (cxBmp, cyBmp) = viewToBitmap(
        circleCenterInView.x, circleCenterInView.y,
        imageBoundsInView, safeSource.width, safeSource.height
    )

    val scaleX = safeSource.width / imageBoundsInView.width
    val scaleY = safeSource.height / imageBoundsInView.height
    val rBmp = (((circleRadiusInView * scaleX) + (circleRadiusInView * scaleY)) / 2f)
        .coerceAtLeast(1f)

    // Tight square around the circle, clamped to bitmap
    var left   = (cxBmp - rBmp).coerceIn(0f, safeSource.width  - 1f)
    var top    = (cyBmp - rBmp).coerceIn(0f, safeSource.height - 1f)
    var right  = (cxBmp + rBmp).coerceIn(left + 1f,  safeSource.width.toFloat())
    var bottom = (cyBmp + rBmp).coerceIn(top + 1f,   safeSource.height.toFloat())

    // Snap to ints and ensure at least 1×1
    val outW = (right - left).roundToInt().coerceAtLeast(1)
    val outH = (bottom - top).roundToInt().coerceAtLeast(1)

    // Rebuild using snapped size to avoid off-by-one invalid rects
    left = left.roundToInt().toFloat()
    top  = top.roundToInt().toFloat()
    right = (left + outW).toFloat().coerceAtMost(safeSource.width.toFloat())
    bottom = (top + outH).toFloat().coerceAtMost(safeSource.height.toFloat())

    val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)

    out.applyCanvas {
        val srcRect = AndroidRect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
        val dstRect = AndroidRect(0, 0, outW, outH)
        drawBitmap(safeSource, srcRect, dstRect, null)

        // Mask outside the circle to transparent
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        val path = Path().apply {
            addOval(AndroidRectF(0f, 0f, outW.toFloat(), outH.toFloat()), Path.Direction.CW)
        }
        drawPath(path, paint)
    }
    return out
}
