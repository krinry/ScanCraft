package com.krinry.scancraft.ui.signature

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.krinry.scancraft.data.model.Page
import com.krinry.scancraft.ui.theme.ScannerGreen
import java.io.File

@Composable
fun SignaturePlacementDialog(
    page: Page,
    signatureBitmap: Bitmap,
    onDismiss: () -> Unit,
    onStamp: (relX: Float, relY: Float, relWidth: Float, relHeight: Float) -> Unit
) {
    val density = LocalDensity.current
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    var containerHeightPx by remember { mutableFloatStateOf(0f) }

    // Relative placement states (0.0 to 1.0)
    var relX by remember { mutableFloatStateOf(0.50f) }
    var relY by remember { mutableFloatStateOf(0.70f) }
    var relWidth by remember { mutableFloatStateOf(0.40f) }

    val aspect = remember(signatureBitmap) {
        if (signatureBitmap.width > 0) signatureBitmap.height.toFloat() / signatureBitmap.width.toFloat() else 0.5f
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF121212)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                    }
                    Text(
                        text = "Position Signature",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Button(
                        onClick = {
                            val relHeight = relWidth * aspect
                            onStamp(relX, relY, relWidth, relHeight)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Apply", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Page Canvas with Moveable & Scalable Signature Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Document Page Image
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coords ->
                                containerWidthPx = coords.size.width.toFloat()
                                containerHeightPx = coords.size.height.toFloat()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = File(page.processedImagePath),
                            contentDescription = "Page Preview",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, Color(0xFF333333), RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Fit
                        )

                        // Draggable & Scalable Signature Overlay
                        if (containerWidthPx > 0f && containerHeightPx > 0f) {
                            val sigWidthPx = containerWidthPx * relWidth
                            val sigHeightPx = sigWidthPx * aspect

                            val sigPosX = (containerWidthPx * relX).coerceIn(0f, containerWidthPx - sigWidthPx)
                            val sigPosY = (containerHeightPx * relY).coerceIn(0f, containerHeightPx - sigHeightPx)

                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(sigPosX.toInt(), sigPosY.toInt()) }
                                    .size(
                                        width = with(density) { sigWidthPx.toDp() },
                                        height = with(density) { sigHeightPx.toDp() }
                                    )
                                    .border(1.5.dp, ScannerGreen, RoundedCornerShape(4.dp))
                                    .background(ScannerGreen.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val newX = (containerWidthPx * relX + dragAmount.x)
                                                .coerceIn(0f, containerWidthPx - sigWidthPx)
                                            val newY = (containerHeightPx * relY + dragAmount.y)
                                                .coerceIn(0f, containerHeightPx - sigHeightPx)
                                            relX = newX / containerWidthPx
                                            relY = newY / containerHeightPx
                                        }
                                    }
                            ) {
                                androidx.compose.foundation.Image(
                                    bitmap = signatureBitmap.asImageBitmap(),
                                    contentDescription = "Signature",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )

                                // Corner Resize Handle
                                Surface(
                                    shape = CircleShape,
                                    color = ScannerGreen,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .align(Alignment.BottomEnd)
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val addedWidth = dragAmount.x
                                                val newWidthPx = (sigWidthPx + addedWidth)
                                                    .coerceIn(containerWidthPx * 0.15f, containerWidthPx * 0.85f)
                                                relWidth = newWidthPx / containerWidthPx
                                            }
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Drag to position signature • Drag green corner to resize",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
