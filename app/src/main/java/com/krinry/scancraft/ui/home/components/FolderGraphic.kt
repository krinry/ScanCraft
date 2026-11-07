package com.krinry.scancraft.ui.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.krinry.scancraft.ui.theme.ScannerFolderYellow
import com.krinry.scancraft.ui.theme.ScannerTheme

/**
 * Standard Manila folder vector drawn using Jetpack Compose Canvas.
 * Adapts dynamically to light and dark theme backgrounds.
 */
@Composable
fun ScannerFolderGraphic(
    modifier: Modifier = Modifier,
    color: Color = ScannerFolderYellow
) {
    val isDark = ScannerTheme.colors.isDark
    val sheetColor = if (isDark) Color(0xFFF8F9FA) else Color(0xFFFFFFFF)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val corner = 6.dp.toPx()

        // 1. Back Folder Body with Tab
        val tabPath = Path().apply {
            moveTo(corner, 0f)
            lineTo(w * 0.44f, 0f)
            quadraticBezierTo(w * 0.50f, 0f, w * 0.54f, corner)
            lineTo(w - corner, corner)
            quadraticBezierTo(w, corner, w, corner * 2)
            lineTo(w, h - corner)
            quadraticBezierTo(w, h, w - corner, h)
            lineTo(corner, h)
            quadraticBezierTo(0f, h, 0f, h - corner)
            lineTo(0f, corner)
            quadraticBezierTo(0f, 0f, corner, 0f)
            close()
        }
        drawPath(tabPath, color = color.copy(alpha = 0.90f))

        // 2. Paper Sheet Peeking inside
        drawRoundRect(
            color = sheetColor,
            topLeft = Offset(w * 0.12f, h * 0.14f),
            size = Size(w * 0.76f, h * 0.48f),
            cornerRadius = CornerRadius(3.dp.toPx())
        )

        // 3. Front Flap
        val frontPath = Path().apply {
            moveTo(0f, h * 0.34f + corner)
            quadraticBezierTo(0f, h * 0.34f, corner, h * 0.34f)
            lineTo(w - corner, h * 0.34f)
            quadraticBezierTo(w, h * 0.34f, w, h * 0.34f + corner)
            lineTo(w, h - corner)
            quadraticBezierTo(w, h, w - corner, h)
            lineTo(corner, h)
            quadraticBezierTo(0f, h, 0f, h - corner)
            close()
        }
        drawPath(frontPath, color = color)
    }
}
