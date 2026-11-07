package com.krinry.scancraft.ui.detail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.krinry.scancraft.data.model.Page
import com.krinry.scancraft.ui.theme.ScannerGreen
import com.krinry.scancraft.ui.theme.ScannerTheme
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageGridCard(
    page: Page,
    isSelected: Boolean = false,
    isSelectMode: Boolean = false,
    isReorderMode: Boolean = false,
    canMoveLeft: Boolean = false,
    canMoveRight: Boolean = false,
    onMoveLeft: () -> Unit = {},
    onMoveRight: () -> Unit = {},
    onPageNumberClick: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val colors = ScannerTheme.colors
    val formattedPageNum = String.format("%02d", page.pageIndex + 1)
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(10.dp))
            .pointerInput(page.id, isReorderMode) {
                if (isReorderMode) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragAccumulator > 70f && canMoveRight) {
                                onMoveRight()
                            } else if (dragAccumulator < -70f && canMoveLeft) {
                                onMoveLeft()
                            }
                            dragAccumulator = 0f
                        },
                        onDragCancel = { dragAccumulator = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            dragAccumulator += dragAmount
                        }
                    )
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(
            width = if (isSelected) 2.5.dp else if (isReorderMode) 1.5.dp else 0.5.dp,
            color = if (isSelected) ScannerGreen else if (isReorderMode) colors.primary.copy(alpha = 0.6f) else colors.cardBorder
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val pageFile = File(page.processedImagePath)
            if (pageFile.exists()) {
                AsyncImage(
                    model = pageFile,
                    contentDescription = "Page ${page.pageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Selection mode translucent overlay
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ScannerGreen.copy(alpha = 0.15f))
                )
            }

            // Top-Left Page Number Badge (Clickable to jump to specific position)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onPageNumberClick() },
                shape = RoundedCornerShape(6.dp),
                color = if (isReorderMode) colors.primary else Color.Black.copy(alpha = 0.70f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isReorderMode) {
                        Icon(
                            Icons.Default.DragIndicator,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                    }
                    Text(
                        text = formattedPageNum,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Top-Right Checkbox in Selection Mode
            if (isSelectMode) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp),
                    shape = CircleShape,
                    color = if (isSelected) ScannerGreen else Color.Black.copy(alpha = 0.55f),
                    border = if (!isSelected) BorderStroke(1.5.dp, Color.White.copy(alpha = 0.85f)) else null
                ) {
                    if (isSelected) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Reorder Controls on Bottom of Card in Reorder Mode
            if (isReorderMode) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    color = Color.Black.copy(alpha = 0.78f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Move left/up
                        IconButton(
                            onClick = onMoveLeft,
                            enabled = canMoveLeft,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Move Left",
                                tint = if (canMoveLeft) Color.White else Color.Gray.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onPageNumberClick() }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Pos #$formattedPageNum",
                                color = ScannerGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Move right/down
                        IconButton(
                            onClick = onMoveRight,
                            enabled = canMoveRight,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Move Right",
                                tint = if (canMoveRight) Color.White else Color.Gray.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            } else if (!isSelectMode) {
                // Bottom strip with page number in regular mode
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    color = Color.Black.copy(alpha = 0.55f)
                ) {
                    Text(
                        text = "Page $formattedPageNum",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
