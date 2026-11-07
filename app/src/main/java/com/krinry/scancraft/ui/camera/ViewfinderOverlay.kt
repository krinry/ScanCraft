package com.krinry.scancraft.ui.camera

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krinry.scancraft.camera.CaptureMode
import com.krinry.scancraft.cv.DocumentQuadrilateral
import com.krinry.scancraft.cv.PointF
import com.krinry.scancraft.cv.IdCardCropper
import com.krinry.scancraft.ui.theme.ScannerGreen

@Composable
fun ViewfinderOverlay(
    mode: CaptureMode,
    quad: DocumentQuadrilateral?,
    previewWidth: Int,
    previewHeight: Int,
    showGrid: Boolean = false,
    idCardStep: Int = 1,
    stabilityState: StabilityState = StabilityState.SEARCHING,
    autoCaptureMode: Boolean = false,
    levelerState: com.krinry.scancraft.camera.LevelerState = com.krinry.scancraft.camera.LevelerState(),
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Horizon Leveler Reticle
            if (levelerState.isVisible) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val reticleRadius = 24.dp.toPx()
                val innerTargetRadius = 8.dp.toPx()
                val bubbleRadius = 5.dp.toPx()

                val isLvl = levelerState.isLevel
                val reticleColor = if (isLvl) Color(0xFF00E676) else Color.White.copy(alpha = 0.35f)
                val reticleStroke = Stroke(width = if (isLvl) 2.dp.toPx() else 1.2.dp.toPx())

                // Outer guide circle
                drawCircle(color = reticleColor, radius = reticleRadius, center = center, style = reticleStroke)
                // Inner lock ring
                drawCircle(color = reticleColor, radius = innerTargetRadius, center = center, style = Stroke(width = 1.dp.toPx()))

                // 4 Crosshair tick marks
                val tickLen = 6.dp.toPx()
                drawLine(reticleColor, Offset(center.x - reticleRadius - tickLen, center.y), Offset(center.x - reticleRadius, center.y), strokeWidth = reticleStroke.width)
                drawLine(reticleColor, Offset(center.x + reticleRadius, center.y), Offset(center.x + reticleRadius + tickLen, center.y), strokeWidth = reticleStroke.width)
                drawLine(reticleColor, Offset(center.x, center.y - reticleRadius - tickLen), Offset(center.x, center.y - reticleRadius), strokeWidth = reticleStroke.width)
                drawLine(reticleColor, Offset(center.x, center.y + reticleRadius), Offset(center.x, center.y + reticleRadius + tickLen), strokeWidth = reticleStroke.width)

                // Moving level bubble
                val maxBubbleDisplacement = reticleRadius - bubbleRadius - 1f
                val bubbleOffset = if (isLvl) {
                    center // Snap to dead center when level!
                } else {
                    val px = (levelerState.roll * 1.8f).coerceIn(-maxBubbleDisplacement, maxBubbleDisplacement)
                    val py = (levelerState.pitch * 1.8f).coerceIn(-maxBubbleDisplacement, maxBubbleDisplacement)
                    Offset(center.x + px, center.y + py)
                }

                drawCircle(
                    color = if (isLvl) Color(0xFF00E676) else Color.White.copy(alpha = 0.85f),
                    radius = bubbleRadius,
                    center = bubbleOffset
                )
            }

            // Draw 3x3 Rule of Thirds Grid if enabled
            if (showGrid) {
                val gridColor = Color.White.copy(alpha = 0.25f)
                val strokeW = 1.dp.toPx()
                drawLine(gridColor, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), strokeW)
                drawLine(gridColor, Offset(size.width * 2 / 3f, 0f), Offset(size.width * 2 / 3f, size.height), strokeW)
                drawLine(gridColor, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), strokeW)
                drawLine(gridColor, Offset(0f, size.height * 2 / 3f), Offset(size.width, size.height * 2 / 3f), strokeW)
            }

            // Mode specific guides
            when (mode) {
                CaptureMode.ID_CARD -> {
                    // Exact ISO/IEC 7810 ID-1 aspect ratio: 1.586
                    val cardW = size.width * IdCardCropper.CARD_WIDTH_RATIO
                    val cardH = cardW / IdCardCropper.ID1_ASPECT_RATIO
                    val cardX = (size.width - cardW) / 2f
                    val cardY = (size.height - cardH) / 2f

                    val cornerRadiusPx = 16.dp.toPx()
                    val bracketLen = 28.dp.toPx()
                    val bracketStrokeW = 3.5.dp.toPx()

                    val cardRect = RoundRect(
                        rect = Rect(cardX, cardY, cardX + cardW, cardY + cardH),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                    )

                    // Semi-transparent dark scrim (#73000000) outside card frame
                    val scrimPath = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(0f, 0f, size.width, size.height))
                        addRoundRect(cardRect)
                    }
                    drawPath(scrimPath, color = Color(0x73000000))

                    // Subtle guide border around card frame
                    drawRoundRect(
                        color = ScannerGreen.copy(alpha = 0.5f),
                        topLeft = Offset(cardX, cardY),
                        size = Size(cardW, cardH),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    // Rounded corner brackets (16dp radius, 28dp length, 3.5dp stroke)
                    val bracketStroke = Stroke(width = bracketStrokeW, cap = StrokeCap.Round)

                    // 1. Top-Left Bracket
                    val pTL = Path().apply {
                        moveTo(cardX + bracketLen, cardY)
                        lineTo(cardX + cornerRadiusPx, cardY)
                        arcTo(
                            rect = Rect(cardX, cardY, cardX + 2 * cornerRadiusPx, cardY + 2 * cornerRadiusPx),
                            startAngleDegrees = 270f,
                            sweepAngleDegrees = -90f,
                            forceMoveTo = false
                        )
                        lineTo(cardX, cardY + bracketLen)
                    }
                    drawPath(pTL, color = Color.White, style = bracketStroke)

                    // 2. Top-Right Bracket
                    val pTR = Path().apply {
                        moveTo(cardX + cardW - bracketLen, cardY)
                        lineTo(cardX + cardW - cornerRadiusPx, cardY)
                        arcTo(
                            rect = Rect(cardX + cardW - 2 * cornerRadiusPx, cardY, cardX + cardW, cardY + 2 * cornerRadiusPx),
                            startAngleDegrees = 270f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false
                        )
                        lineTo(cardX + cardW, cardY + bracketLen)
                    }
                    drawPath(pTR, color = Color.White, style = bracketStroke)

                    // 3. Bottom-Right Bracket
                    val pBR = Path().apply {
                        moveTo(cardX + cardW, cardY + cardH - bracketLen)
                        lineTo(cardX + cardW, cardY + cardH - cornerRadiusPx)
                        arcTo(
                            rect = Rect(cardX + cardW - 2 * cornerRadiusPx, cardY + cardH - 2 * cornerRadiusPx, cardX + cardW, cardY + cardH),
                            startAngleDegrees = 0f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false
                        )
                        lineTo(cardX + cardW - bracketLen, cardY + cardH)
                    }
                    drawPath(pBR, color = Color.White, style = bracketStroke)

                    // 4. Bottom-Left Bracket
                    val pBL = Path().apply {
                        moveTo(cardX, cardY + cardH - bracketLen)
                        lineTo(cardX, cardY + cardH - cornerRadiusPx)
                        arcTo(
                            rect = Rect(cardX, cardY + cardH - 2 * cornerRadiusPx, cardX + 2 * cornerRadiusPx, cardY + cardH),
                            startAngleDegrees = 180f,
                            sweepAngleDegrees = -90f,
                            forceMoveTo = false
                        )
                        lineTo(cardX + bracketLen, cardY + cardH)
                    }
                    drawPath(pBL, color = Color.White, style = bracketStroke)
                }

                CaptureMode.BOOK -> {
                    // Book Curvature Mode Overlay
                    val centerX = size.width / 2f
                    val dashIntervalPx = 10.dp.toPx()

                    // Vertical dashed center spine guide (10dp on, 10dp off)
                    drawLine(
                        color = ScannerGreen,
                        start = Offset(centerX, 0f),
                        end = Offset(centerX, size.height),
                        strokeWidth = 2.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(dashIntervalPx, dashIntervalPx),
                            0f
                        )
                    )

                    // Watermark page badges: "1" (Left Page) at x = 25% and "2" (Right Page) at x = 75%
                    val badgeRadius = 26.dp.toPx()
                    val centerY = size.height / 2f - 30.dp.toPx()
                    val badgeBgColor = Color(0x33000000)

                    val textPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 28.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        isAntiAlias = true
                        alpha = 180
                    }

                    // Left Page badge ("1") at x = 25%
                    val leftBadgeX = size.width * 0.25f
                    drawCircle(color = badgeBgColor, radius = badgeRadius, center = Offset(leftBadgeX, centerY))
                    drawCircle(
                        color = Color.White.copy(alpha = 0.25f),
                        radius = badgeRadius,
                        center = Offset(leftBadgeX, centerY),
                        style = Stroke(1.5.dp.toPx())
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        "1",
                        leftBadgeX,
                        centerY - (textPaint.descent() + textPaint.ascent()) / 2,
                        textPaint
                    )

                    // Right Page badge ("2") at x = 75%
                    val rightBadgeX = size.width * 0.75f
                    drawCircle(color = badgeBgColor, radius = badgeRadius, center = Offset(rightBadgeX, centerY))
                    drawCircle(
                        color = Color.White.copy(alpha = 0.25f),
                        radius = badgeRadius,
                        center = Offset(rightBadgeX, centerY),
                        style = Stroke(1.5.dp.toPx())
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        "2",
                        rightBadgeX,
                        centerY - (textPaint.descent() + textPaint.ascent()) / 2,
                        textPaint
                    )

                    // Retain document boundary quad detection if document detected
                    if (quad != null && previewWidth > 0 && previewHeight > 0) {
                        val scale = maxOf(size.width / previewWidth.toFloat(), size.height / previewHeight.toFloat())
                        val previewRenderW = previewWidth * scale
                        val previewRenderH = previewHeight * scale
                        val offsetX = (size.width - previewRenderW) / 2f
                        val offsetY = (size.height - previewRenderH) / 2f

                        fun mapToScreen(p: PointF): Offset = Offset(offsetX + p.x * scale, offsetY + p.y * scale)

                        val p1 = mapToScreen(quad.topLeft)
                        val p2 = mapToScreen(quad.topRight)
                        val p3 = mapToScreen(quad.bottomRight)
                        val p4 = mapToScreen(quad.bottomLeft)

                        val polyPath = Path().apply {
                            moveTo(p1.x, p1.y)
                            lineTo(p2.x, p2.y)
                            lineTo(p3.x, p3.y)
                            lineTo(p4.x, p4.y)
                            close()
                        }
                        drawPath(polyPath, color = ScannerGreen.copy(alpha = 0.12f))
                        drawPath(polyPath, color = ScannerGreen, style = Stroke(width = 2.dp.toPx()))
                    }
                }

                CaptureMode.QR_CODE -> {
                    val boxSize = size.width * 0.70f
                    val boxX = (size.width - boxSize) / 2f
                    val boxY = (size.height - boxSize) / 2f - 40.dp.toPx()

                    val p = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(boxX, boxY, boxX + boxSize, boxY + boxSize),
                                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                            )
                        )
                    }
                    drawPath(p, color = ScannerGreen.copy(alpha = 0.10f))
                    drawPath(p, color = ScannerGreen, style = Stroke(width = 2.5.dp.toPx()))
                }

                else -> {
                    // Dynamic Document Detection for Document mode
                    if (quad != null && previewWidth > 0 && previewHeight > 0) {
                        val scale = maxOf(size.width / previewWidth.toFloat(), size.height / previewHeight.toFloat())
                        val previewRenderW = previewWidth * scale
                        val previewRenderH = previewHeight * scale
                        val offsetX = (size.width - previewRenderW) / 2f
                        val offsetY = (size.height - previewRenderH) / 2f

                        fun mapToScreen(p: PointF): Offset = Offset(offsetX + p.x * scale, offsetY + p.y * scale)

                        val p1 = mapToScreen(quad.topLeft)
                        val p2 = mapToScreen(quad.topRight)
                        val p3 = mapToScreen(quad.bottomRight)
                        val p4 = mapToScreen(quad.bottomLeft)

                        val polyPath = Path().apply {
                            moveTo(p1.x, p1.y)
                            lineTo(p2.x, p2.y)
                            lineTo(p3.x, p3.y)
                            lineTo(p4.x, p4.y)
                            close()
                        }

                        val isSteady = (stabilityState == StabilityState.STEADY_LOCKED)
                        val activeLaserColor = if (isSteady) Color(0xFF00E676) else ScannerGreen

                        // Translucent green tint over detected document
                        drawPath(polyPath, color = activeLaserColor.copy(alpha = if (isSteady) 0.24f else 0.16f))
                        // Green boundary outline
                        drawPath(polyPath, color = activeLaserColor, style = Stroke(width = if (isSteady) 3.2.dp.toPx() else 2.2.dp.toPx()))

                        // High-contrast L-shaped corner brackets
                        val bracketLen = if (isSteady) 26.dp.toPx() else 22.dp.toPx()
                        val bracketStroke = Stroke(width = if (isSteady) 4.dp.toPx() else 3.5.dp.toPx())

                        fun drawCornerBracket(corner: Offset, dirX: Float, dirY: Float) {
                            val path = Path().apply {
                                moveTo(corner.x + dirX * bracketLen, corner.y)
                                lineTo(corner.x, corner.y)
                                lineTo(corner.x, corner.y + dirY * bracketLen)
                            }
                            drawPath(path, color = Color.White, style = bracketStroke)
                            drawPath(path, color = activeLaserColor, style = Stroke(width = 2.dp.toPx()))
                        }

                        drawCornerBracket(p1, 1f, 1f)   // Top-Left
                        drawCornerBracket(p2, -1f, 1f)  // Top-Right
                        drawCornerBracket(p3, -1f, -1f) // Bottom-Right
                        drawCornerBracket(p4, 1f, -1f)  // Bottom-Left
                    }
                }
            }
        }

        // Dynamic prompt pill: Step 1 vs Step 2, QR, Book
        if (mode == CaptureMode.ID_CARD) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp),
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, ScannerGreen)
            ) {
                Text(
                    text = if (idCardStep == 1) "Step 1 of 2: Align FRONT of ID Card" else "Step 2 of 2: Align BACK of ID Card",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                )
            }
        } else if (mode == CaptureMode.QR_CODE) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp),
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, ScannerGreen)
            ) {
                Text(
                    text = "Align QR code within the frame",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                )
            }
        } else if (mode == CaptureMode.BOOK) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp),
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, ScannerGreen)
            ) {
                Text(
                    text = "Align book center crease with dashed spine guide",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                )
            }
        } else if (mode == CaptureMode.DOCUMENT && quad != null) {
            val pillText = when (stabilityState) {
                StabilityState.STEADY_LOCKED -> if (autoCaptureMode) "● AUTO CAPTURING..." else "READY • HOLD STEADY"
                StabilityState.MOVE_CLOSER -> "MOVE CLOSER TO DOCUMENT"
                StabilityState.STABILIZING -> "HOLD STEADY"
                StabilityState.SEARCHING -> "DOCUMENT DETECTED"
            }
            val pillBorder = when (stabilityState) {
                StabilityState.STEADY_LOCKED -> Color(0xFF00E676)
                StabilityState.MOVE_CLOSER -> Color(0xFFFFB300)
                else -> ScannerGreen
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp),
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.2.dp, pillBorder)
            ) {
                Text(
                    text = pillText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                )
            }
        }
    }
}
