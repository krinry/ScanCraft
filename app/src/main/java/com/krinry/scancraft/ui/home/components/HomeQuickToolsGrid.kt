package com.krinry.scancraft.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BrandingWatermark
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krinry.scancraft.ui.theme.ScannerGreen
import com.krinry.scancraft.ui.theme.ScannerTheme

/**
 * ScanCraft Quick Action Grid displayed at the top of the Home Screen,
 * providing one-tap access to primary camera scanning and document utilities.
 */
@Composable
fun HomeQuickToolsGrid(
    onSmartScanClick: () -> Unit,
    onImportImagesClick: () -> Unit,
    onImportFilesClick: () -> Unit,
    onIdCardClick: () -> Unit,
    onOcrClick: () -> Unit,
    onPdfToolsClick: () -> Unit,
    onWatermarkClick: () -> Unit,
    onAllToolsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ScannerTheme.colors

    val items = listOf(
        QuickToolItem(
            title = "Smart Scan",
            icon = Icons.Default.CameraAlt,
            tint = ScannerGreen,
            onClick = onSmartScanClick
        ),
        QuickToolItem(
            title = "Import Photos",
            icon = Icons.Default.AddPhotoAlternate,
            tint = Color(0xFF2979FF),
            onClick = onImportImagesClick
        ),
        QuickToolItem(
            title = "Import Files",
            icon = Icons.Default.DriveFolderUpload,
            tint = Color(0xFFFF9100),
            onClick = onImportFilesClick
        ),
        QuickToolItem(
            title = "ID Card",
            icon = Icons.Default.Badge,
            tint = Color(0xFF00B0FF),
            onClick = onIdCardClick
        ),
        QuickToolItem(
            title = "To Text (OCR)",
            icon = Icons.Default.TextFields,
            tint = Color(0xFF7C4DFF),
            onClick = onOcrClick
        ),
        QuickToolItem(
            title = "PDF Tools",
            icon = Icons.Default.PictureAsPdf,
            tint = Color(0xFFFF5252),
            onClick = onPdfToolsClick
        ),
        QuickToolItem(
            title = "Watermark",
            icon = Icons.AutoMirrored.Filled.BrandingWatermark,
            tint = Color(0xFF00BFA5),
            onClick = onWatermarkClick
        ),
        QuickToolItem(
            title = "All Tools",
            icon = Icons.Default.Apps,
            tint = if (colors.isDark) Color(0xFFB0BEC5) else Color(0xFF546E7A),
            onClick = onAllToolsClick
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Row 1 (4 items)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            for (i in 0 until 4) {
                QuickToolCell(
                    item = items[i],
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Row 2 (4 items)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            for (i in 4 until 8) {
                QuickToolCell(
                    item = items[i],
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private data class QuickToolItem(
    val title: String,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit
)

@Composable
private fun QuickToolCell(
    item: QuickToolItem,
    modifier: Modifier = Modifier
) {
    val colors = ScannerTheme.colors
    val bgAlpha = if (colors.isDark) 0.16f else 0.11f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = item.onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        // Circular Icon Badge with brand tint container
        Surface(
            modifier = Modifier.size(50.dp),
            shape = CircleShape,
            color = item.tint.copy(alpha = bgAlpha)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = item.tint,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Label
        Text(
            text = item.title,
            color = colors.textPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
