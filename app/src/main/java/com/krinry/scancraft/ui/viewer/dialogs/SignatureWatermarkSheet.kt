package com.krinry.scancraft.ui.viewer.dialogs

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.krinry.scancraft.ScanCraftApp
import com.krinry.scancraft.data.model.Page
import com.krinry.scancraft.data.repository.ScannerRepository
import com.krinry.scancraft.ui.signature.SignatureDialog
import com.krinry.scancraft.ui.signature.SignaturePlacementDialog
import com.krinry.scancraft.ui.theme.ScannerGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureWatermarkSheet(
    page: Page,
    onDismiss: () -> Unit,
    onPageUpdated: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showSignatureDrawDialog by remember { mutableStateOf(false) }
    var drawnSignatureBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showWatermarkCustomizer by remember { mutableStateOf(false) }

    // Watermark state
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var isTiledWatermark by remember { mutableStateOf(true) }
    var watermarkAngle by remember { mutableFloatStateOf(-45f) }
    var watermarkOpacity by remember { mutableFloatStateOf(0.35f) }
    var watermarkColor by remember { mutableStateOf(Color(0xFF888888)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 38.dp, height = 4.dp),
                shape = CircleShape,
                color = Color(0xFF555555)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Sign & Watermark",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Add authentic signature or anti-theft security watermark",
                color = Color(0xFFAAAAAA),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
            )

            // 1. Signature
            ActionTile(
                icon = Icons.Outlined.Draw,
                title = "Add Signature",
                description = "Draw signature and stamp freely on the page",
                onClick = { showSignatureDrawDialog = true }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Watermark
            ActionTile(
                icon = Icons.Outlined.Security,
                title = "Add Security Watermark",
                description = "Overlay diagonal or centered anti-copy text",
                onClick = { showWatermarkCustomizer = true }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Drawing Signature Dialog
    if (showSignatureDrawDialog) {
        SignatureDialog(
            onDismiss = { showSignatureDrawDialog = false },
            onSignatureCaptured = { bmp ->
                showSignatureDrawDialog = false
                drawnSignatureBitmap = bmp
            }
        )
    }

    // Placing Signature on Page Dialog
    drawnSignatureBitmap?.let { sigBmp ->
        SignaturePlacementDialog(
            page = page,
            signatureBitmap = sigBmp,
            onDismiss = { drawnSignatureBitmap = null },
            onStamp = { relX, relY, relW, relH ->
                coroutineScope.launch(Dispatchers.IO) {
                    val app = context.applicationContext as ScanCraftApp
                    val repo = ScannerRepository(
                        context = context,
                        documentDao = app.database.documentDao(),
                        pageDao = app.database.pageDao(),
                        folderDao = app.database.folderDao()
                    )
                    repo.stampSignatureOnPage(page.id, sigBmp, relX, relY, relW, relH)
                    withContext(Dispatchers.Main) {
                        drawnSignatureBitmap = null
                        Toast.makeText(context, "Signature stamped successfully!", Toast.LENGTH_SHORT).show()
                        onPageUpdated()
                        onDismiss()
                    }
                }
            }
        )
    }

    // Watermark Customizer Dialog
    if (showWatermarkCustomizer) {
        val presetWatermarks = listOf("CONFIDENTIAL", "DO NOT COPY", "OFFICIAL", "SAMPLE", "PAID")
        AlertDialog(
            onDismissRequest = { showWatermarkCustomizer = false },
            title = { Text("Watermark Settings", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = watermarkText,
                        onValueChange = { watermarkText = it },
                        label = { Text("Watermark Text") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presetWatermarks.take(3).forEach { chip ->
                            FilterChip(
                                selected = watermarkText == chip,
                                onClick = { watermarkText = chip },
                                label = { Text(chip, fontSize = 10.sp) }
                            )
                        }
                    }

                    // Tiled vs Center Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tiled Repeated Pattern", fontSize = 13.sp)
                        Switch(
                            checked = isTiledWatermark,
                            onCheckedChange = { isTiledWatermark = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = ScannerGreen)
                        )
                    }

                    // Opacity Slider
                    Column {
                        Text("Opacity: ${(watermarkOpacity * 100).toInt()}%", fontSize = 12.sp, color = Color.Gray)
                        Slider(
                            value = watermarkOpacity,
                            onValueChange = { watermarkOpacity = it },
                            valueRange = 0.10f..0.70f,
                            colors = SliderDefaults.colors(thumbColor = ScannerGreen, activeTrackColor = ScannerGreen)
                        )
                    }

                    // Color options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Color:", fontSize = 12.sp, color = Color.Gray)
                        listOf(
                            Color(0xFF888888),
                            Color(0xFFE53935),
                            Color(0xFF1E88E5),
                            Color(0xFF000000)
                        ).forEach { col ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .clickable { watermarkColor = col }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            val app = context.applicationContext as ScanCraftApp
                            val repo = ScannerRepository(
                                context = context,
                                documentDao = app.database.documentDao(),
                                pageDao = app.database.pageDao(),
                                folderDao = app.database.folderDao()
                            )
                            repo.applyWatermarkToPage(
                                pageId = page.id,
                                watermarkText = watermarkText.trim(),
                                color = watermarkColor.toArgb(),
                                opacity = watermarkOpacity,
                                isTiled = isTiledWatermark,
                                angle = watermarkAngle
                            )
                            withContext(Dispatchers.Main) {
                                showWatermarkCustomizer = false
                                Toast.makeText(context, "Watermark applied to page!", Toast.LENGTH_SHORT).show()
                                onPageUpdated()
                                onDismiss()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                ) {
                    Text("Apply", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWatermarkCustomizer = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        color = Color(0xFF282828),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ScannerGreen.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ScannerGreen,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    color = Color(0xFFAAAAAA),
                    fontSize = 11.sp
                )
            }
        }
    }
}
