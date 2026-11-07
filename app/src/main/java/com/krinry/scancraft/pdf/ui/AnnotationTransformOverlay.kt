package com.krinry.scancraft.pdf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krinry.scancraft.pdf.model.OffsetNormalized
import com.krinry.scancraft.pdf.model.PdfAnnotation
import com.krinry.scancraft.pdf.model.RectNormalized
import com.krinry.scancraft.ui.theme.ScannerGreen

enum class TransformHandle {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    BODY
}

@Composable
fun AnnotationTransformOverlay(
    annotation: PdfAnnotation,
    pageWidth: Float,
    pageHeight: Float,
    onUpdate: (PdfAnnotation) -> Unit,
    onDelete: (String) -> Unit,
    onDeselect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val boxNorm = annotation.getBoundingBox()

    var currentLeftNorm by remember(annotation) { mutableFloatStateOf(boxNorm.left) }
    var currentTopNorm by remember(annotation) { mutableFloatStateOf(boxNorm.top) }
    var currentRightNorm by remember(annotation) { mutableFloatStateOf(boxNorm.right) }
    var currentBottomNorm by remember(annotation) { mutableFloatStateOf(boxNorm.bottom) }

    val liveLeft = currentLeftNorm * pageWidth
    val liveTop = currentTopNorm * pageHeight
    val liveWidth = ((currentRightNorm - currentLeftNorm) * pageWidth).coerceAtLeast(24f)
    val liveHeight = ((currentBottomNorm - currentTopNorm) * pageHeight).coerceAtLeast(24f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(annotation.id) {
                detectTapGestures { _ ->
                    onDeselect()
                }
            }
    ) {
        // Selection bounding box
        Box(
            modifier = Modifier
                .offset(
                    x = (liveLeft).dp / (pageWidth / pageWidth), // handled in px
                    y = (liveTop).dp / (pageHeight / pageHeight)
                )
                .size(
                    width = (liveWidth / (pageWidth / pageWidth)).dp,
                    height = (liveHeight / (pageHeight / pageHeight)).dp
                )
                .border(1.5.dp, ScannerGreen, RoundedCornerShape(4.dp))
                .pointerInput(annotation.id) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dxNorm = dragAmount.x / pageWidth
                            val dyNorm = dragAmount.y / pageHeight

                            val w = currentRightNorm - currentLeftNorm
                            val h = currentBottomNorm - currentTopNorm

                            val newL = (currentLeftNorm + dxNorm).coerceIn(0f, 1f - w)
                            val newT = (currentTopNorm + dyNorm).coerceIn(0f, 1f - h)
                            currentLeftNorm = newL
                            currentTopNorm = newT
                            currentRightNorm = newL + w
                            currentBottomNorm = newT + h
                        },
                        onDragEnd = {
                            val newBounds = RectNormalized(
                                currentLeftNorm, currentTopNorm,
                                currentRightNorm, currentBottomNorm
                            )
                            applyBoundsUpdate(annotation, newBounds, onUpdate)
                        }
                    )
                }
        ) {
            // Delete button at top-right corner
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-10).dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEE3F3F))
                    .pointerInput(Unit) {
                        // handled by IconButton click
                    },
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = { onDelete(annotation.id) },
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

private fun applyBoundsUpdate(
    orig: PdfAnnotation,
    newBounds: RectNormalized,
    onUpdate: (PdfAnnotation) -> Unit
) {
    val updated = when (orig) {
        is PdfAnnotation.ShapeOverlay -> orig.copy(bounds = newBounds)
        is PdfAnnotation.SignatureStamp -> {
            val oldB = orig.bounds
            val scaleX = if (oldB.width > 0) newBounds.width / oldB.width else 1f
            val scaleY = if (oldB.height > 0) newBounds.height / oldB.height else 1f

            val scaledStrokes = orig.strokes.map { stroke ->
                stroke.map { pt ->
                    OffsetNormalized(
                        x = newBounds.left + (pt.x - oldB.left) * scaleX,
                        y = newBounds.top + (pt.y - oldB.top) * scaleY
                    )
                }
            }
            orig.copy(bounds = newBounds, strokes = scaledStrokes)
        }
        is PdfAnnotation.TextNote -> {
            orig.copy(position = OffsetNormalized(newBounds.left, newBounds.top))
        }
        is PdfAnnotation.InkStroke -> {
            val oldB = orig.getBoundingBox()
            val scaleX = if (oldB.width > 0) newBounds.width / oldB.width else 1f
            val scaleY = if (oldB.height > 0) newBounds.height / oldB.height else 1f

            val scaledPoints = orig.points.map { pt ->
                OffsetNormalized(
                    x = newBounds.left + (pt.x - oldB.left) * scaleX,
                    y = newBounds.top + (pt.y - oldB.top) * scaleY
                )
            }
            orig.copy(points = scaledPoints)
        }
    }
    onUpdate(updated)
}
