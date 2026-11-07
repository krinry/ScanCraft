package com.krinry.scancraft.ui.signature

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.krinry.scancraft.ui.theme.ScannerGreen

data class SignaturePath(
    val path: Path,
    val color: Color,
    val strokeWidth: Float,
    val points: List<Offset>
)

@Composable
fun SignatureDialog(
    onDismiss: () -> Unit,
    onSignatureCaptured: (Bitmap) -> Unit
) {
    val paths = remember { mutableStateListOf<SignaturePath>() }
    var currentPoints = remember { mutableStateListOf<Offset>() }
    var selectedColor by remember { mutableStateOf(Color.Black) }
    val strokeWidth = 8f

    val inkColors = listOf(
        Color(0xFF111111), // Classic Black
        Color(0xFF0D47A1), // Official Blue
        Color(0xFFB71C1C)  // Stamp Red
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add Digital Signature",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF1A1A1A)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Sign with your finger inside the box below",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Signature Drawing Canvas Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF9F9F9))
                        .border(1.5.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(selectedColor) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentPoints.clear()
                                        currentPoints.add(offset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentPoints.add(change.position)
                                    },
                                    onDragEnd = {
                                        if (currentPoints.size > 1) {
                                            val p = Path().apply {
                                                moveTo(currentPoints.first().x, currentPoints.first().y)
                                                for (i in 1 until currentPoints.size) {
                                                    lineTo(currentPoints[i].x, currentPoints[i].y)
                                                }
                                            }
                                            paths.add(
                                                SignaturePath(
                                                    path = p,
                                                    color = selectedColor,
                                                    strokeWidth = strokeWidth,
                                                    points = currentPoints.toList()
                                                )
                                            )
                                        }
                                        currentPoints = mutableStateListOf()
                                    }
                                )
                            }
                    ) {
                        // Baseline guide line
                        drawLine(
                            color = Color(0xFFDDDDDD),
                            start = Offset(20f, size.height * 0.75f),
                            end = Offset(size.width - 20f, size.height * 0.75f),
                            strokeWidth = 1.5f
                        )

                        // Draw completed paths
                        for (sp in paths) {
                            drawPath(
                                path = sp.path,
                                color = sp.color,
                                style = Stroke(
                                    width = sp.strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }

                        // Draw live path
                        if (currentPoints.size > 1) {
                            val livePath = Path().apply {
                                moveTo(currentPoints.first().x, currentPoints.first().y)
                                for (i in 1 until currentPoints.size) {
                                    lineTo(currentPoints[i].x, currentPoints[i].y)
                                }
                            }
                            drawPath(
                                path = livePath,
                                color = selectedColor,
                                style = Stroke(
                                    width = strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    if (paths.isEmpty() && currentPoints.isEmpty()) {
                        Text(
                            text = "Sign here",
                            color = Color(0xFFCCCCCC),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Controls: Color Pickers + Clear + Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Ink Colors
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        inkColors.forEach { inkColor ->
                            val isSelected = inkColor == selectedColor
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(inkColor)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) ScannerGreen else Color.LightGray,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = inkColor },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Clear button
                    OutlinedButton(
                        onClick = {
                            paths.clear()
                            currentPoints.clear()
                        },
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear", color = Color.Gray, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Confirm & Apply Button
                Button(
                    onClick = {
                        if (paths.isEmpty()) return@Button
                        // Render paths to transparent bitmap
                        val bmpW = 600
                        val bmpH = 300
                        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                        val canvas = AndroidCanvas(bitmap)

                        for (sp in paths) {
                            val paint = AndroidPaint().apply {
                                color = sp.color.toArgb()
                                this.strokeWidth = sp.strokeWidth * 1.5f
                                style = AndroidPaint.Style.STROKE
                                strokeCap = AndroidPaint.Cap.ROUND
                                strokeJoin = AndroidPaint.Join.ROUND
                                isAntiAlias = true
                            }
                            val androidPath = android.graphics.Path()
                            val pts = sp.points
                            if (pts.isNotEmpty()) {
                                androidPath.moveTo(pts[0].x * (bmpW / 400f), pts[0].y * (bmpH / 200f))
                                for (i in 1 until pts.size) {
                                    androidPath.lineTo(pts[i].x * (bmpW / 400f), pts[i].y * (bmpH / 200f))
                                }
                                canvas.drawPath(androidPath, paint)
                            }
                        }
                        onSignatureCaptured(bitmap)
                    },
                    enabled = paths.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text("Apply Signature", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                }
            }
        }
    }
}
