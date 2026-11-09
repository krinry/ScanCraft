package com.krinry.scancraft.ui.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krinry.scancraft.ui.theme.ScannerGreen
import com.krinry.scancraft.ui.theme.ScannerTheme

@Composable
fun HomeToolsTab(
    onScanIdCard: () -> Unit,
    onScanDocs: () -> Unit,
    onScanBook: () -> Unit,
    onScanQr: () -> Unit,
    onToText: () -> Unit,
    onImportFiles: () -> Unit,
    onCollageImages: () -> Unit,
    onPdfWatermark: () -> Unit,
    onPdfMerge: () -> Unit,
    onPdfCompression: () -> Unit,
    onPdfSignature: () -> Unit,
    onPdfPassword: () -> Unit,
    onPageReorder: () -> Unit
) {
    val colors = ScannerTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // --- 1. SECTION: SCAN ---
        SectionHeader("Scan")
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShortcutTile(
                modifier = Modifier.weight(1f),
                title = "ID Card",
                onClick = onScanIdCard
            ) { IdCardIllustration() }

            ShortcutTile(
                modifier = Modifier.weight(1f),
                title = "Scan Docs",
                onClick = onScanDocs
            ) { ScanDocsIllustration() }

            ShortcutTile(
                modifier = Modifier.weight(1f),
                title = "Book",
                onClick = onScanBook
            ) { BookIllustration() }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShortcutTile(
                modifier = Modifier.weight(1f),
                title = "QR Code",
                onClick = onScanQr
            ) { QrCodeIllustration() }

            ShortcutTile(
                modifier = Modifier.weight(1f),
                title = "To Text",
                onClick = onToText
            ) { ToTextIllustration() }

            // Empty spacer for 3-column balance
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 2. SECTION: PROCESS FILES ---
        SectionHeader("Process Files")
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShortcutTile(
                modifier = Modifier.weight(1f),
                title = "Import Files",
                onClick = onImportFiles
            ) { ImportFilesIllustration() }

            ShortcutTile(
                modifier = Modifier.weight(1f),
                title = "Collage Images",
                onClick = onCollageImages
            ) { CollageIllustration() }

            ShortcutTile(
                modifier = Modifier.weight(1f),
                title = "PDF Watermark",
                onClick = onPdfWatermark
            ) { WatermarkIllustration() }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShortcutTile(
                modifier = Modifier.weight(1f),
                title = "PDF Merge",
                onClick = onPdfMerge
            ) { PdfMergeIllustration() }

            ShortcutTile(
                modifier = Modifier.weight(1f),
                title = "Compression",
                onClick = onPdfCompression
            ) { CompressionIllustration() }

            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 3. SECTION: PDF TOOLS ---
        SectionHeader("PDF Tools")
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShortcutTile(
                modifier = Modifier.weight(1f),
                title = "PDF Signature",
                onClick = onPdfSignature
            ) { SignatureIllustration() }

            ShortcutTile(
                modifier = Modifier.weight(1f),
                title = "PDF Password",
                onClick = onPdfPassword
            ) { PasswordIllustration() }

            ShortcutTile(
                modifier = Modifier.weight(1f),
                title = "Page Reorder",
                onClick = onPageReorder
            ) { PageReorderIllustration() }
        }

        Spacer(modifier = Modifier.height(80.dp)) // Padding for bottom bar
    }
}

@Composable
fun SectionHeader(title: String) {
    val colors = ScannerTheme.colors
    Text(
        text = title,
        color = colors.textPrimary,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp
    )
}

@Composable
fun ShortcutTile(
    modifier: Modifier = Modifier,
    title: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val colors = ScannerTheme.colors
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)),
            color = colors.surface,
            border = BorderStroke(0.5.dp, colors.cardBorder)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ----------------------------------------------------
// DYNAMIC CLEAN-ROOM CANVAS ILLUSTRATIONS (RULE 1.1)
// ----------------------------------------------------

@Composable
fun IdCardIllustration() {
    Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        val w = size.width
        val h = size.height

        // White ID Card base
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.08f, h * 0.12f),
            size = Size(w * 0.84f, h * 0.76f),
            cornerRadius = CornerRadius(6.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Fill
        )
        drawRoundRect(
            color = Color(0xFFDCDCDC),
            topLeft = Offset(w * 0.08f, h * 0.12f),
            size = Size(w * 0.84f, h * 0.76f),
            cornerRadius = CornerRadius(6.dp.toPx()),
            style = Stroke(1.dp.toPx())
        )

        // Photo Box
        drawRoundRect(
            color = Color(0xFFE8F5E9),
            topLeft = Offset(w * 0.16f, h * 0.24f),
            size = Size(w * 0.28f, h * 0.38f),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
        // Person head/shoulders in photo
        drawCircle(color = Color(0xFF81C784), radius = w * 0.07f, center = Offset(w * 0.30f, h * 0.37f))

        // Info Lines
        val linePaint = Color(0xFFB0BEC5)
        drawLine(linePaint, Offset(w * 0.50f, h * 0.28f), Offset(w * 0.82f, h * 0.28f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        drawLine(linePaint, Offset(w * 0.50f, h * 0.38f), Offset(w * 0.78f, h * 0.38f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(linePaint, Offset(w * 0.50f, h * 0.48f), Offset(w * 0.72f, h * 0.48f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)

        // Stamp badge on bottom
        drawCircle(color = ScannerGreen.copy(alpha = 0.2f), radius = w * 0.12f, center = Offset(w * 0.70f, h * 0.68f))
        drawCircle(color = ScannerGreen, radius = w * 0.12f, center = Offset(w * 0.70f, h * 0.68f), style = Stroke(1.5.dp.toPx()))
    }
}

@Composable
fun ScanDocsIllustration() {
    Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        val w = size.width
        val h = size.height

        // Invoice Sheet
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.14f, h * 0.08f),
            size = Size(w * 0.72f, h * 0.84f),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFFDCDCDC),
            topLeft = Offset(w * 0.14f, h * 0.08f),
            size = Size(w * 0.72f, h * 0.84f),
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = Stroke(1.dp.toPx())
        )

        // Header "INVOICE"
        drawLine(Color(0xFF37474F), Offset(w * 0.24f, h * 0.20f), Offset(w * 0.52f, h * 0.20f), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)

        // Text Rows
        for (i in 0..4) {
            val y = h * (0.32f + i * 0.09f)
            drawLine(Color(0xFFCFD8DC), Offset(w * 0.24f, y), Offset(w * 0.76f, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }

        // Green Laser Scan Line across center
        val scanY = h * 0.48f
        drawLine(
            color = ScannerGreen,
            start = Offset(w * 0.08f, scanY),
            end = Offset(w * 0.92f, scanY),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun BookIllustration() {
    Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        val w = size.width
        val h = size.height

        // Open Book Base
        val leftPage = Path().apply {
            moveTo(w * 0.50f, h * 0.22f)
            lineTo(w * 0.14f, h * 0.24f)
            lineTo(w * 0.14f, h * 0.76f)
            lineTo(w * 0.50f, h * 0.74f)
            close()
        }
        val rightPage = Path().apply {
            moveTo(w * 0.50f, h * 0.22f)
            lineTo(w * 0.86f, h * 0.24f)
            lineTo(w * 0.86f, h * 0.76f)
            lineTo(w * 0.50f, h * 0.74f)
            close()
        }

        drawPath(leftPage, color = Color.White)
        drawPath(leftPage, color = Color(0xFFDCDCDC), style = Stroke(1.dp.toPx()))
        drawPath(rightPage, color = Color.White)
        drawPath(rightPage, color = Color(0xFFDCDCDC), style = Stroke(1.dp.toPx()))

        // Center spine line
        drawLine(Color(0xFF90A4AE), Offset(w * 0.50f, h * 0.20f), Offset(w * 0.50f, h * 0.78f), strokeWidth = 2.dp.toPx())

        // Laser scan line across pages
        drawLine(ScannerGreen, Offset(w * 0.18f, h * 0.48f), Offset(w * 0.82f, h * 0.48f), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)

        // Corner guides
        val bLen = 8.dp.toPx()
        val gColor = ScannerGreen
        drawLine(gColor, Offset(w * 0.14f, h * 0.24f), Offset(w * 0.14f + bLen, h * 0.24f), strokeWidth = 2.dp.toPx())
        drawLine(gColor, Offset(w * 0.14f, h * 0.24f), Offset(w * 0.14f, h * 0.24f + bLen), strokeWidth = 2.dp.toPx())
        drawLine(gColor, Offset(w * 0.86f, h * 0.76f), Offset(w * 0.86f - bLen, h * 0.76f), strokeWidth = 2.dp.toPx())
        drawLine(gColor, Offset(w * 0.86f, h * 0.76f), Offset(w * 0.86f, h * 0.76f - bLen), strokeWidth = 2.dp.toPx())
    }
}

@Composable
fun QrCodeIllustration() {
    Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        val w = size.width
        val h = size.height

        // Phone outline
        drawRoundRect(
            color = Color(0xFFF1F8E9),
            topLeft = Offset(w * 0.18f, h * 0.10f),
            size = Size(w * 0.64f, h * 0.80f),
            cornerRadius = CornerRadius(10.dp.toPx())
        )
        drawRoundRect(
            color = ScannerGreen,
            topLeft = Offset(w * 0.18f, h * 0.10f),
            size = Size(w * 0.64f, h * 0.80f),
            cornerRadius = CornerRadius(10.dp.toPx()),
            style = Stroke(2.dp.toPx())
        )

        // QR Pattern inside
        val qrX = w * 0.32f
        val qrY = h * 0.26f
        val qrSize = w * 0.36f

        // 3 Corner finder boxes
        val s = qrSize * 0.32f
        drawRect(Color(0xFF2E7D32), Offset(qrX, qrY), Size(s, s))
        drawRect(Color.White, Offset(qrX + s * 0.25f, qrY + s * 0.25f), Size(s * 0.5f, s * 0.5f))

        drawRect(Color(0xFF2E7D32), Offset(qrX + qrSize - s, qrY), Size(s, s))
        drawRect(Color.White, Offset(qrX + qrSize - s + s * 0.25f, qrY + s * 0.25f), Size(s * 0.5f, s * 0.5f))

        drawRect(Color(0xFF2E7D32), Offset(qrX, qrY + qrSize - s), Size(s, s))
        drawRect(Color.White, Offset(qrX + s * 0.25f, qrY + qrSize - s + s * 0.25f), Size(s * 0.5f, s * 0.5f))
    }
}

@Composable
fun ToTextIllustration() {
    Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        val w = size.width
        val h = size.height

        // Document sheet
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.14f, h * 0.10f),
            size = Size(w * 0.72f, h * 0.80f),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFFDCDCDC),
            topLeft = Offset(w * 0.14f, h * 0.10f),
            size = Size(w * 0.72f, h * 0.80f),
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = Stroke(1.dp.toPx())
        )

        // Highlighted OCR Green text rows
        drawRoundRect(
            color = ScannerGreen.copy(alpha = 0.20f),
            topLeft = Offset(w * 0.22f, h * 0.22f),
            size = Size(w * 0.56f, h * 0.08f),
            cornerRadius = CornerRadius(2.dp.toPx())
        )
        drawLine(Color(0xFF37474F), Offset(w * 0.24f, h * 0.26f), Offset(w * 0.72f, h * 0.26f), strokeWidth = 2.dp.toPx())

        for (i in 1..4) {
            val y = h * (0.28f + i * 0.09f)
            drawLine(Color(0xFF90A4AE), Offset(w * 0.24f, y), Offset(w * 0.65f, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }

        // Green Circle 'T' badge
        drawCircle(color = ScannerGreen, radius = w * 0.15f, center = Offset(w * 0.72f, h * 0.66f))
        // Draw letter 'T'
        val tPaint = Color.White
        drawLine(tPaint, Offset(w * 0.65f, h * 0.60f), Offset(w * 0.79f, h * 0.60f), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Square)
        drawLine(tPaint, Offset(w * 0.72f, h * 0.60f), Offset(w * 0.72f, h * 0.73f), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Square)
    }
}

@Composable
fun ImportFilesIllustration() {
    Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        val w = size.width
        val h = size.height

        // Document list card
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.12f, h * 0.12f),
            size = Size(w * 0.76f, h * 0.76f),
            cornerRadius = CornerRadius(6.dp.toPx())
        )

        // File rows (PDF, DOCX, IMG)
        val colors = listOf(Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047))
        for (i in 0..2) {
            val y = h * (0.22f + i * 0.18f)
            // Color icon box
            drawRoundRect(colors[i], Offset(w * 0.18f, y), Size(w * 0.12f, h * 0.11f), CornerRadius(2.dp.toPx()))
            // Title line
            drawLine(Color(0xFF37474F), Offset(w * 0.34f, y + h * 0.04f), Offset(w * 0.72f, y + h * 0.04f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }

        // Circular import arrow badge
        drawCircle(color = Color(0xFF1E88E5), radius = w * 0.14f, center = Offset(w * 0.74f, h * 0.72f))
        val arrowP = Path().apply {
            moveTo(w * 0.74f, h * 0.64f)
            lineTo(w * 0.74f, h * 0.78f)
            moveTo(w * 0.68f, h * 0.72f)
            lineTo(w * 0.74f, h * 0.78f)
            lineTo(w * 0.80f, h * 0.72f)
        }
        drawPath(arrowP, color = Color.White, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun CollageIllustration() {
    Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        val w = size.width
        val h = size.height

        // Background mat
        drawRoundRect(
            color = Color(0xFFE0F2F1),
            topLeft = Offset(w * 0.10f, h * 0.10f),
            size = Size(w * 0.80f, h * 0.80f),
            cornerRadius = CornerRadius(6.dp.toPx())
        )

        // Top landscape photo
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.16f, h * 0.16f),
            size = Size(w * 0.68f, h * 0.32f),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
        drawRect(Color(0xFF80CBC4), Offset(w * 0.20f, h * 0.20f), Size(w * 0.60f, h * 0.24f))

        // 4 grid slots below
        val gw = w * 0.14f
        val gh = h * 0.24f
        for (i in 0..3) {
            val gx = w * (0.16f + i * 0.18f)
            drawRoundRect(
                color = ScannerGreen.copy(alpha = 0.5f),
                topLeft = Offset(gx, h * 0.56f),
                size = Size(gw, gh),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }
    }
}

@Composable
fun WatermarkIllustration() {
    Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        val w = size.width
        val h = size.height

        // Sheet with table
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.12f, h * 0.10f),
            size = Size(w * 0.76f, h * 0.80f),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
        // Table lines
        for (i in 0..4) {
            val y = h * (0.20f + i * 0.12f)
            drawLine(Color(0xFFECEFF1), Offset(w * 0.16f, y), Offset(w * 0.84f, y), strokeWidth = 1.dp.toPx())
        }

        // Diagonal Watermark lines
        drawLine(
            color = Color.Gray.copy(alpha = 0.35f),
            start = Offset(w * 0.15f, h * 0.75f),
            end = Offset(w * 0.85f, h * 0.25f),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Stamp badge on right
        drawCircle(color = ScannerGreen, radius = w * 0.14f, center = Offset(w * 0.74f, h * 0.68f))
        // Stamp handle icon
        val p = Path().apply {
            moveTo(w * 0.70f, h * 0.72f)
            lineTo(w * 0.78f, h * 0.72f)
            moveTo(w * 0.74f, h * 0.72f)
            lineTo(w * 0.74f, h * 0.62f)
        }
        drawPath(p, color = Color.White, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun PdfMergeIllustration() {
    Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        val w = size.width
        val h = size.height

        // Two source docs on top
        drawRoundRect(Color.White, Offset(w * 0.12f, h * 0.14f), Size(w * 0.32f, h * 0.28f), CornerRadius(3.dp.toPx()))
        drawRoundRect(Color(0xFFCFD8DC), Offset(w * 0.12f, h * 0.14f), Size(w * 0.32f, h * 0.28f), CornerRadius(3.dp.toPx()), style = Stroke(1.dp.toPx()))

        drawRoundRect(Color.White, Offset(w * 0.56f, h * 0.14f), Size(w * 0.32f, h * 0.28f), CornerRadius(3.dp.toPx()))
        drawRoundRect(Color(0xFFCFD8DC), Offset(w * 0.56f, h * 0.14f), Size(w * 0.32f, h * 0.28f), CornerRadius(3.dp.toPx()), style = Stroke(1.dp.toPx()))

        // Merge arrows pointing down
        val arrowP = Path().apply {
            moveTo(w * 0.50f, h * 0.44f)
            lineTo(w * 0.50f, h * 0.54f)
            moveTo(w * 0.44f, h * 0.50f)
            lineTo(w * 0.50f, h * 0.54f)
            lineTo(w * 0.56f, h * 0.50f)
        }
        drawPath(arrowP, color = ScannerGreen, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))

        // Large merged doc at bottom
        drawRoundRect(Color.White, Offset(w * 0.16f, h * 0.58f), Size(w * 0.68f, h * 0.32f), CornerRadius(4.dp.toPx()))
        drawRoundRect(ScannerGreen, Offset(w * 0.16f, h * 0.58f), Size(w * 0.68f, h * 0.32f), CornerRadius(4.dp.toPx()), style = Stroke(1.5.dp.toPx()))
    }
}

@Composable
fun CompressionIllustration() {
    Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        val w = size.width
        val h = size.height

        // Card container
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.10f, h * 0.12f),
            size = Size(w * 0.80f, h * 0.76f),
            cornerRadius = CornerRadius(6.dp.toPx())
        )

        // Compression Badge
        drawCircle(color = Color(0xFFE1F5FE), radius = w * 0.15f, center = Offset(w * 0.70f, h * 0.28f))
        drawCircle(color = Color(0xFF0288D1), radius = w * 0.15f, center = Offset(w * 0.70f, h * 0.28f), style = Stroke(1.5.dp.toPx()))

        // Original size line
        drawLine(Color(0xFF78909C), Offset(w * 0.20f, h * 0.46f), Offset(w * 0.68f, h * 0.46f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)

        // Green Compressed Pill Box
        drawRoundRect(
            color = Color(0xFFE8F5E9),
            topLeft = Offset(w * 0.18f, h * 0.60f),
            size = Size(w * 0.64f, h * 0.18f),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
        drawRoundRect(
            color = ScannerGreen,
            topLeft = Offset(w * 0.18f, h * 0.60f),
            size = Size(w * 0.64f, h * 0.18f),
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = Stroke(1.dp.toPx())
        )
        // Check line in pill
        drawLine(ScannerGreen, Offset(w * 0.26f, h * 0.69f), Offset(w * 0.62f, h * 0.69f), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
fun SignatureIllustration() {
    Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        val w = size.width
        val h = size.height

        // Document sheet
        drawRoundRect(Color.White, Offset(w * 0.12f, h * 0.10f), Size(w * 0.76f, h * 0.80f), CornerRadius(4.dp.toPx()))
        drawRoundRect(Color(0xFFDCDCDC), Offset(w * 0.12f, h * 0.10f), Size(w * 0.76f, h * 0.80f), CornerRadius(4.dp.toPx()), style = Stroke(1.dp.toPx()))

        // Text lines
        for (i in 0..2) {
            val y = h * (0.22f + i * 0.09f)
            drawLine(Color(0xFFCFD8DC), Offset(w * 0.20f, y), Offset(w * 0.72f, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }

        // Green signature box
        drawRoundRect(
            color = ScannerGreen.copy(alpha = 0.12f),
            topLeft = Offset(w * 0.24f, h * 0.54f),
            size = Size(w * 0.52f, h * 0.26f),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
        drawRoundRect(
            color = ScannerGreen,
            topLeft = Offset(w * 0.24f, h * 0.54f),
            size = Size(w * 0.52f, h * 0.26f),
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = Stroke(1.dp.toPx())
        )

        // Signature curve inside
        val sigPath = Path().apply {
            moveTo(w * 0.28f, h * 0.70f)
            quadraticBezierTo(w * 0.36f, h * 0.58f, w * 0.44f, h * 0.68f)
            quadraticBezierTo(w * 0.54f, h * 0.56f, w * 0.68f, h * 0.66f)
        }
        drawPath(sigPath, color = Color(0xFF1B5E20), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun PasswordIllustration() {
    Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        val w = size.width
        val h = size.height

        // Protected document sheet
        drawRoundRect(Color.White, Offset(w * 0.14f, h * 0.10f), Size(w * 0.72f, h * 0.80f), CornerRadius(4.dp.toPx()))
        drawRoundRect(Color(0xFFDCDCDC), Offset(w * 0.14f, h * 0.10f), Size(w * 0.72f, h * 0.80f), CornerRadius(4.dp.toPx()), style = Stroke(1.dp.toPx()))

        // Text lines
        for (i in 0..3) {
            val y = h * (0.24f + i * 0.09f)
            drawLine(Color(0xFFCFD8DC), Offset(w * 0.22f, y), Offset(w * 0.68f, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }

        // Green Circular Lock Badge
        drawCircle(color = ScannerGreen, radius = w * 0.16f, center = Offset(w * 0.70f, h * 0.68f))

        // Padlock icon
        val lockX = w * 0.64f
        val lockY = h * 0.65f
        drawRoundRect(Color.White, Offset(lockX, lockY), Size(w * 0.12f, h * 0.09f), CornerRadius(2.dp.toPx()))
        drawArc(
            color = Color.White,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(lockX + w * 0.02f, lockY - h * 0.06f),
            size = Size(w * 0.08f, h * 0.08f),
            style = Stroke(2.dp.toPx())
        )
    }
}

@Composable
fun PageReorderIllustration() {
    Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        val w = size.width
        val h = size.height

        // Page 1
        drawRoundRect(Color.White, Offset(w * 0.12f, h * 0.18f), Size(w * 0.34f, h * 0.54f), CornerRadius(3.dp.toPx()))
        drawRoundRect(Color(0xFFCFD8DC), Offset(w * 0.12f, h * 0.18f), Size(w * 0.34f, h * 0.54f), CornerRadius(3.dp.toPx()), style = Stroke(1.dp.toPx()))
        // Page 1 text lines
        for (i in 0..2) {
            val y = h * (0.28f + i * 0.09f)
            drawLine(Color(0xFFCFD8DC), Offset(w * 0.18f, y), Offset(w * 0.38f, y), strokeWidth = 2.dp.toPx())
        }
        // Badge [1]
        drawRoundRect(ScannerGreen, Offset(w * 0.22f, h * 0.62f), Size(w * 0.14f, h * 0.07f), CornerRadius(2.dp.toPx()))

        // Page 2
        drawRoundRect(Color.White, Offset(w * 0.54f, h * 0.18f), Size(w * 0.34f, h * 0.54f), CornerRadius(3.dp.toPx()))
        drawRoundRect(Color(0xFFCFD8DC), Offset(w * 0.54f, h * 0.18f), Size(w * 0.34f, h * 0.54f), CornerRadius(3.dp.toPx()), style = Stroke(1.dp.toPx()))
        // Page 2 text lines
        for (i in 0..2) {
            val y = h * (0.28f + i * 0.09f)
            drawLine(Color(0xFFCFD8DC), Offset(w * 0.60f, y), Offset(w * 0.80f, y), strokeWidth = 2.dp.toPx())
        }
        // Badge [2]
        drawRoundRect(ScannerGreen, Offset(w * 0.64f, h * 0.62f), Size(w * 0.14f, h * 0.07f), CornerRadius(2.dp.toPx()))
    }
}
