package com.krinry.scancraft.ui.pdf

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krinry.scancraft.pdf.PdfExportOptions
import com.krinry.scancraft.pdf.PdfPageSize
import com.krinry.scancraft.pdf.PdfQuality
import com.krinry.scancraft.ui.theme.ScannerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfExportDialog(
    onDismiss: () -> Unit,
    onSaveToDownloads: (PdfExportOptions) -> Unit,
    onShare: (PdfExportOptions) -> Unit
) {
    val colors = ScannerTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsManager = remember { com.krinry.scancraft.data.repository.SettingsManager.getInstance(context) }
    val initialSize = remember {
        when (settingsManager.defaultPdfSize.value) {
            "US Letter" -> PdfPageSize.LETTER
            "Fit to Document" -> PdfPageSize.FIT_IMAGE
            else -> PdfPageSize.A4
        }
    }
    var selectedPageSize by remember { mutableStateOf(initialSize) }
    var selectedQuality by remember { mutableStateOf(PdfQuality.MEDIUM) }
    var watermarkText by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    fun currentOptions() = PdfExportOptions(
        pageSize = selectedPageSize,
        quality = selectedQuality,
        watermarkText = watermarkText.ifBlank { null },
        password = password.ifBlank { null }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = colors.primary)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Export PDF Settings", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Page size selection
                Text("Page Size", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfPageSize.entries.forEach { size ->
                        val isSelected = size == selectedPageSize
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedPageSize = size },
                            label = { Text(if (size == PdfPageSize.A4) "A4" else if (size == PdfPageSize.LETTER) "Letter" else "Fit") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primary,
                                selectedLabelColor = Color.White,
                                containerColor = colors.surfaceSecondary,
                                labelColor = colors.textPrimary
                            )
                        )
                    }
                }

                // File Quality
                Text("File Quality", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfQuality.entries.forEach { quality ->
                        val isSelected = quality == selectedQuality
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedQuality = quality },
                            label = { Text(quality.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primary,
                                selectedLabelColor = Color.White,
                                containerColor = colors.surfaceSecondary,
                                labelColor = colors.textPrimary
                            )
                        )
                    }
                }

                // Security Watermark
                Text("Custom Watermark (Optional)", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                OutlinedTextField(
                    value = watermarkText,
                    onValueChange = { watermarkText = it },
                    placeholder = { Text("e.g. CONFIDENTIAL / DO NOT COPY", color = colors.textMuted, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.cardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // AES Password Protection
                Text("Password Protection (Optional)", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("Leave empty for no password", color = colors.textMuted, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.cardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onSaveToDownloads(currentOptions()) },
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, colors.cardBorder)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.textPrimary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save", color = colors.textPrimary)
                }
                Button(
                    onClick = { onShare(currentOptions()) },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        },
        containerColor = colors.surface,
        shape = RoundedCornerShape(16.dp)
    )
}
