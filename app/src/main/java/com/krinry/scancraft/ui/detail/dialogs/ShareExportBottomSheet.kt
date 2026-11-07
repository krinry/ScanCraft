package com.krinry.scancraft.ui.detail.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.krinry.scancraft.data.model.Page
import com.krinry.scancraft.ui.theme.ScannerGreen
import com.krinry.scancraft.ui.theme.ScannerTheme
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareExportBottomSheet(
    pages: List<Page>,
    onDismiss: () -> Unit,
    onSharePdf: () -> Unit,
    onShareJpg: () -> Unit,
    onShareLongImage: () -> Unit,
    onExtractText: () -> Unit,
    onSplitPdf: () -> Unit,
    onSaveToGallery: () -> Unit,
    onEmailToMyself: () -> Unit
) {
    val colors = ScannerTheme.colors

    // Calculate approximate total image size
    val totalBytes = pages.sumOf { p ->
        val f = File(p.processedImagePath)
        if (f.exists()) f.length() else 500_000L
    }
    val mbSize = String.format(Locale.US, "%.2fMB", totalBytes / (1024f * 1024f).coerceAtLeast(0.01f))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${pages.size} picture(s) selected",
                    color = colors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = CircleShape,
                    color = colors.surfaceSecondary,
                    modifier = Modifier.size(32.dp)
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Thumbnail Preview Carousel
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(pages, key = { it.id }) { page ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(110.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.surfaceSecondary,
                            modifier = Modifier
                                .width(110.dp)
                                .height(150.dp)
                        ) {
                            AsyncImage(
                                model = File(page.thumbnailPath.ifEmpty { page.processedImagePath }),
                                contentDescription = "Page thumbnail",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = ScannerGreen,
                                modifier = Modifier.size(7.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ScanCraft",
                                color = colors.textSecondary,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = colors.cardBorder,
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Export Actions List
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                ExportActionRow(
                    icon = Icons.Outlined.PictureAsPdf,
                    title = "Share PDF",
                    subtitle = "($mbSize)",
                    badge = "Often use",
                    badgeColor = ScannerGreen,
                    onClick = {
                        onDismiss()
                        onSharePdf()
                    }
                )

                ExportActionRow(
                    icon = Icons.Outlined.Image,
                    title = "Share JPG",
                    subtitle = "($mbSize)",
                    onClick = {
                        onDismiss()
                        onShareJpg()
                    }
                )

                ExportActionRow(
                    icon = Icons.Outlined.ViewStream,
                    title = "Share as long image",
                    onClick = {
                        onDismiss()
                        onShareLongImage()
                    }
                )

                ExportActionRow(
                    icon = Icons.Outlined.TextFields,
                    title = "Extract Text",
                    subtitle = "(Offline OCR)",
                    onClick = {
                        onDismiss()
                        onExtractText()
                    }
                )

                ExportActionRow(
                    icon = Icons.AutoMirrored.Outlined.CallSplit,
                    title = "Split PDF into Individual Pages...",
                    subtitle = "($mbSize)",
                    onClick = {
                        onDismiss()
                        onSplitPdf()
                    }
                )

                ExportActionRow(
                    icon = Icons.Outlined.FileDownload,
                    title = "Save to Gallery",
                    onClick = {
                        onDismiss()
                        onSaveToGallery()
                    }
                )

                ExportActionRow(
                    icon = Icons.Outlined.Email,
                    title = "Email to Myself",
                    onClick = {
                        onDismiss()
                        onEmailToMyself()
                    }
                )
            }
        }
    }
}

@Composable
private fun ExportActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    badge: String? = null,
    badgeColor: Color = ScannerGreen,
    onClick: () -> Unit
) {
    val colors = ScannerTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = colors.textPrimary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = subtitle,
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }
            if (badge != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = badgeColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(vertical = 1.dp)
                ) {
                    Text(
                        text = badge,
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
