package com.krinry.scancraft.ui.crop

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.krinry.scancraft.ui.theme.ScannerGreen
import kotlin.math.roundToInt

/**
 * High-performance HUD Magnifier Loupe (Zoom Preview) for document crop handles.
 *
 * HUD corner evasion logic:
 * - Parked in Top-Left by default.
 * - Flips smoothly to Top-Right when the touch/vertex is within 3 * radius of Top-Left.
 * - Hand/fingers NEVER occlude the magnified view.
 * - Zero allocations on draw path for buttery smooth 120 FPS performance.
 */
@Composable
fun MagnifierLoupe(
    bitmap: Bitmap?,
    touchPoint: Offset?,
    imageScale: Float,
    fitRect: android.graphics.RectF? = null,
    handlePoint: Offset? = null,
    modifier: Modifier = Modifier
) {
    val activeTarget = handlePoint ?: touchPoint
    if (activeTarget == null || bitmap == null || imageScale <= 0f) return

    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val density = LocalDensity.current
    val config = LocalConfiguration.current

    val loupeSize = 124.dp
    val zoomFactor = 2.2f

    val loupeSizePx = with(density) { loupeSize.toPx() }
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val radiusPx = loupeSizePx / 2f
    val marginPx = with(density) { 16.dp.toPx() }

    // Evasion threshold: 3 * radius from top-left
    val evasionThresholdX = 3f * radiusPx + marginPx
    val evasionThresholdY = 3f * radiusPx + marginPx

    val anchor = touchPoint ?: activeTarget
    val isNearTopLeft = anchor.x < evasionThresholdX && anchor.y < evasionThresholdY

    val targetPosX = if (isNearTopLeft) {
        (screenWidthPx - loupeSizePx - marginPx).coerceAtLeast(marginPx)
    } else {
        marginPx
    }
    val targetPosY = marginPx

    // Fast 100ms tween for instantaneous HUD flip
    val animatedPosX by animateFloatAsState(
        targetValue = targetPosX,
        animationSpec = tween(durationMillis = 100),
        label = "loupePosX"
    )

    // Reusable circle clip path
    val lensClipPath = remember(loupeSizePx) {
        Path().apply { addOval(Rect(Offset.Zero, androidx.compose.ui.geometry.Size(loupeSizePx, loupeSizePx))) }
    }

    Box(
        modifier = modifier
            .offset { IntOffset(animatedPosX.roundToInt(), targetPosY.roundToInt()) }
            .size(loupeSize)
            .shadow(12.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.6f))
            .clip(CircleShape)
            .border(3.dp, ScannerGreen, CircleShape)
    ) {
        Canvas(modifier = Modifier.size(loupeSize)) {
            // Circular lens aperture clip
            clipPath(lensClipPath) {
                drawRect(Color(0xFF121212))

                val offX = fitRect?.left ?: 0f
                val offY = fitRect?.top ?: 0f

                // Center exactly on activeTarget vertex coordinates
                translate(
                    left = size.width / 2f - (activeTarget.x - offX) * zoomFactor,
                    top = size.height / 2f - (activeTarget.y - offY) * zoomFactor
                ) {
                    scale(
                        scaleX = zoomFactor * imageScale,
                        scaleY = zoomFactor * imageScale,
                        pivot = Offset.Zero
                    ) {
                        drawImage(image = imageBitmap)
                    }
                }
            }

            // High-precision targeting reticle exactly at center of lens
            val c = Offset(size.width / 2f, size.height / 2f)

            // Outer dark drop-shadow for crosshairs (readable on white paper & dark backgrounds)
            drawLine(Color.Black.copy(alpha = 0.65f), Offset(c.x - 24f, c.y), Offset(c.x + 24f, c.y), strokeWidth = 4f)
            drawLine(Color.Black.copy(alpha = 0.65f), Offset(c.x, c.y - 24f), Offset(c.x, c.y + 24f), strokeWidth = 4f)

            // Primary crisp white crosshairs
            drawLine(Color.White, Offset(c.x - 22f, c.y), Offset(c.x + 22f, c.y), strokeWidth = 2.5f)
            drawLine(Color.White, Offset(c.x, c.y - 22f), Offset(c.x, c.y + 22f), strokeWidth = 2.5f)

            // Accent inner crosshair lines
            drawLine(ScannerGreen, Offset(c.x - 14f, c.y), Offset(c.x + 14f, c.y), strokeWidth = 1.8f)
            drawLine(ScannerGreen, Offset(c.x, c.y - 14f), Offset(c.x, c.y + 14f), strokeWidth = 1.8f)

            // Center targeting micro-dots
            drawCircle(Color.White, radius = 3.5f, center = c)
            drawCircle(ScannerGreen, radius = 2f, center = c)
        }
    }
}
