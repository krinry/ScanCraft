package com.krinry.scancraft.ui.filter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.krinry.scancraft.data.model.EnhanceMode
import com.krinry.scancraft.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterScreen(
    imagePath: String,
    docId: Long,
    borderStr: String,
    onSaved: (docId: Long) -> Unit,
    onClose: () -> Unit,
    viewModel: FilterViewModel = viewModel()
) {
    LaunchedEffect(imagePath, borderStr) {
        viewModel.init(imagePath, borderStr)
    }

    val colors = ScannerTheme.colors
    val previewBitmap by viewModel.previewBitmap.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()
    val brightness by viewModel.brightness.collectAsState()
    val contrast by viewModel.contrast.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    var showAdjustDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enhance", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.saveDocument(docId, borderStr) { savedDocId ->
                                onSaved(savedDocId)
                            }
                        },
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = ScannerGreen, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = ScannerGreen,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Check, contentDescription = "Save", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.surface,
                border = BorderStroke(0.5.dp, colors.cardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 12.dp)
                ) {
                    // Filter selection carousel with document enhancement thumbnails
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp)
                    ) {
                        items(EnhanceMode.entries) { mode ->
                            val isSelected = mode == selectedMode
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { viewModel.setFilterMode(mode) }
                            ) {
                                Surface(
                                    modifier = Modifier.size(52.dp),
                                    shape = CircleShape,
                                    color = if (isSelected) colors.primaryContainer else colors.surfaceSecondary,
                                    border = if (isSelected) BorderStroke(2.5.dp, ScannerGreen) else BorderStroke(1.dp, colors.cardBorder)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = mode.shortCode,
                                            color = if (isSelected) ScannerGreen else colors.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = mode.displayName,
                                    color = if (isSelected) ScannerGreen else colors.textSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Adjust Sliders button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colors.surfaceSecondary,
                            border = BorderStroke(0.5.dp, colors.cardBorder),
                            modifier = Modifier.clickable { showAdjustDialog = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tone Adjustments", color = colors.textPrimary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colors.background),
            contentAlignment = Alignment.Center
        ) {
            previewBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Enhanced Document",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            } ?: CircularProgressIndicator(color = ScannerGreen)
        }
    }

    if (showAdjustDialog) {
        ModalBottomSheet(
            onDismissRequest = { showAdjustDialog = false },
            containerColor = colors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text("Fine-Tune Paper Tones", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))

                Text("Brightness: $brightness", color = colors.textSecondary, fontSize = 14.sp)
                Slider(
                    value = brightness.toFloat(),
                    onValueChange = { viewModel.setBrightness(it.toInt()) },
                    valueRange = -80f..80f,
                    colors = SliderDefaults.colors(
                        thumbColor = ScannerGreen,
                        activeTrackColor = ScannerGreen,
                        inactiveTrackColor = colors.cardBorder
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("Contrast: $contrast", color = colors.textSecondary, fontSize = 14.sp)
                Slider(
                    value = contrast.toFloat(),
                    onValueChange = { viewModel.setContrast(it.toInt()) },
                    valueRange = -80f..80f,
                    colors = SliderDefaults.colors(
                        thumbColor = ScannerGreen,
                        activeTrackColor = ScannerGreen,
                        inactiveTrackColor = colors.cardBorder
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { showAdjustDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply", color = Color.White)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
