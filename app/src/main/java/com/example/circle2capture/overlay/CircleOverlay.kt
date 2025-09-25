package com.example.circle2capture.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import kotlin.math.hypot
import kotlin.math.min

@Composable
fun CircleOverlay(
    boxSize: IntSize,
    imageBounds: Rect,                // clamp inside this rect (the drawn image area)
    center: Offset,                   // current center from parent (view px)
    radius: Float,                    // current radius from parent (view px)
    onChange: (Offset, Float) -> Unit // (newCenter, newRadius)
) {
    val density = LocalDensity.current
    val strokePx = 3f
    val handleRadiusPx = with(density) { 20f }
    val handleGrabRadiusPx = with(density) { 36f }
    val minRadiusPx = with(density) { 24f }

    // Local working copies so gesture can continue smoothly without re-keying pointerInput
    var currentCenter by remember { mutableStateOf(center) }
    var currentRadius by remember { mutableStateOf(radius) }

    // Keep local copies in sync when parent props change (e.g., after recomposition)
    val latestCenter by rememberUpdatedState(center)
    val latestRadius by rememberUpdatedState(radius)
    LaunchedEffect(latestCenter) { currentCenter = latestCenter }
    LaunchedEffect(latestRadius) { currentRadius = latestRadius }

    // Drag mode flags
    var isResizing by remember { mutableStateOf(false) }
    var isMoving by remember { mutableStateOf(false) }

    Canvas(
        modifier = Modifier
            // IMPORTANT: don't key on center/radius, or gestures will restart mid-drag
            .pointerInput(boxSize, imageBounds) {
                detectDragGestures(
                    onDragStart = { down ->
                        val handleCenter = Offset(currentCenter.x + currentRadius, currentCenter.y)
                        val distToHandle = distance(down, handleCenter)

                        if (distToHandle <= handleGrabRadiusPx) {
                            isResizing = true
                            isMoving = false
                            return@detectDragGestures
                        }

                        if (distance(down, currentCenter) <= currentRadius) {
                            isResizing = false
                            isMoving = true
                        } else {
                            isResizing = false
                            isMoving = false // ignore drags that start outside
                        }
                    },
                    onDrag = { change, drag ->
                        change.consumeAllChanges()
                        if (boxSize == IntSize.Zero) return@detectDragGestures

                        if (isResizing) {
                            val newR = distance(currentCenter, change.position)
                            val clampedR = clampRadiusToRect(newR, currentCenter, imageBounds, minRadiusPx)
                            currentRadius = clampedR
                            onChange(currentCenter, currentRadius)
                        } else if (isMoving) {
                            val newCenter = Offset(currentCenter.x + drag.x, currentCenter.y + drag.y)
                            val clampedC = clampCenterToRect(newCenter, currentRadius, imageBounds)
                            currentCenter = clampedC
                            onChange(currentCenter, currentRadius)
                        }
                    },
                    onDragEnd = {
                        isResizing = false
                        isMoving = false
                    },
                    onDragCancel = {
                        isResizing = false
                        isMoving = false
                    }
                )
            }
            .fillMaxSize()
    ) {
        // Dim whole area lightly
        drawRect(color = Color.Black.copy(alpha = 0.10f))
        // Emphasize the actual image bounds
        drawRect(color = Color.Black.copy(alpha = 0.25f), topLeft = imageBounds.topLeft, size = imageBounds.size)

        // Circle fill
        drawCircle(
            color = Color.Cyan.copy(alpha = 0.15f),
            radius = currentRadius,
            center = currentCenter
        )
        // Dashed stroke
        drawCircle(
            color = Color.Cyan,
            radius = currentRadius,
            center = currentCenter,
            style = Stroke(width = strokePx, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)))
        )
        // Resize handle
        drawCircle(
            color = Color.Cyan,
            radius = handleRadiusPx,
            center = Offset(currentCenter.x + currentRadius, currentCenter.y)
        )
    }
}

private fun distance(a: Offset, b: Offset): Float = hypot(a.x - b.x, a.y - b.y)

/** Clamp center so the full circle stays inside the image rect. */
private fun clampCenterToRect(center: Offset, r: Float, rect: Rect): Offset {
    val minX = rect.left + r
    val maxX = rect.right - r
    val minY = rect.top + r
    val maxY = rect.bottom - r
    val cx = center.x.coerceIn(minX, maxX)
    val cy = center.y.coerceIn(minY, maxY)
    return Offset(cx, cy)
}

/** Max radius so the circle remains fully inside the image rect. */
private fun clampRadiusToRect(r: Float, center: Offset, rect: Rect, minR: Float): Float {
    val maxR = min(
        min(center.x - rect.left, rect.right - center.x),
        min(center.y - rect.top, rect.bottom - center.y)
    )
    return r.coerceIn(minR, maxR)
}
