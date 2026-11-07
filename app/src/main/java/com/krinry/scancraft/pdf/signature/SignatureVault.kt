package com.krinry.scancraft.pdf.signature

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import com.krinry.scancraft.data.security.CryptoHelper
import com.krinry.scancraft.pdf.model.OffsetNormalized
import com.krinry.scancraft.ui.theme.ScannerGreen
import java.io.File
import java.util.UUID

data class SavedSignature(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val inkColorArgb: Int,
    val strokes: List<List<OffsetNormalized>>,
    val createdTime: Long = System.currentTimeMillis()
)

object SignatureVaultManager {

    private fun getVaultDir(context: Context): File {
        val dir = File(context.filesDir, "signatures")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun loadAllSignatures(context: Context): List<SavedSignature> {
        val dir = getVaultDir(context)
        val file = File(dir, "vault.json")
        if (!file.exists()) return emptyList()

        val list = mutableListOf<SavedSignature>()
        try {
            val rawContent = file.readText()
            val jsonStr = try {
                CryptoHelper.decrypt(rawContent)
            } catch (e: Exception) {
                // Graceful fallback for migration of existing plaintext vaults
                rawContent
            }
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id", UUID.randomUUID().toString())
                val title = obj.optString("title", "Signature")
                val color = obj.optInt("color", Color(0xFF0D47A1).toArgb())
                val time = obj.optLong("time", System.currentTimeMillis())

                val strokesArr = obj.optJSONArray("strokes") ?: JSONArray()
                val strokesList = mutableListOf<List<OffsetNormalized>>()
                for (sIdx in 0 until strokesArr.length()) {
                    val sArr = strokesArr.getJSONArray(sIdx)
                    val pts = mutableListOf<OffsetNormalized>()
                    for (pIdx in 0 until sArr.length()) {
                        val pObj = sArr.getJSONObject(pIdx)
                        pts.add(
                            OffsetNormalized(
                                pObj.optDouble("x", 0.0).toFloat(),
                                pObj.optDouble("y", 0.0).toFloat()
                            )
                        )
                    }
                    strokesList.add(pts)
                }
                list.add(SavedSignature(id, title, color, strokesList, time))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveSignature(
        context: Context,
        rawStrokes: List<List<Offset>>,
        colorArgb: Int,
        title: String = "My Signature"
    ): SavedSignature? {
        if (rawStrokes.isEmpty()) return null

        val allPoints = rawStrokes.flatten()
        if (allPoints.isEmpty()) return null

        val minX = allPoints.minOf { it.x }
        val maxX = allPoints.maxOf { it.x }
        val minY = allPoints.minOf { it.y }
        val maxY = allPoints.maxOf { it.y }

        val origW = (maxX - minX).coerceAtLeast(1f)
        val origH = (maxY - minY).coerceAtLeast(1f)

        // Normalize points into 0..1 bounding box of the signature itself
        val normalizedStrokes = rawStrokes.map { stroke ->
            stroke.map { pt ->
                OffsetNormalized(
                    x = (pt.x - minX) / origW,
                    y = (pt.y - minY) / origH
                )
            }
        }

        val newSig = SavedSignature(
            title = title,
            inkColorArgb = colorArgb,
            strokes = normalizedStrokes
        )

        val existing = loadAllSignatures(context).toMutableList()
        existing.add(0, newSig)
        persistAll(context, existing)
        return newSig
    }

    fun deleteSignature(context: Context, id: String) {
        val existing = loadAllSignatures(context).filter { it.id != id }
        persistAll(context, existing)
    }

    private fun persistAll(context: Context, list: List<SavedSignature>) {
        try {
            val arr = JSONArray()
            for (s in list) {
                val obj = JSONObject()
                obj.put("id", s.id)
                obj.put("title", s.title)
                obj.put("color", s.inkColorArgb)
                obj.put("time", s.createdTime)

                val strokesArr = JSONArray()
                for (st in s.strokes) {
                    val sArr = JSONArray()
                    for (pt in st) {
                        val pObj = JSONObject()
                        pObj.put("x", pt.x.toDouble())
                        pObj.put("y", pt.y.toDouble())
                        sArr.put(pObj)
                    }
                    strokesArr.put(sArr)
                }
                obj.put("strokes", strokesArr)
                arr.put(obj)
            }
            val jsonString = arr.toString()
            val payload = try {
                CryptoHelper.encrypt(jsonString)
            } catch (e: Exception) {
                e.printStackTrace()
                jsonString
            }
            File(getVaultDir(context), "vault.json").writeText(payload)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun SignatureVaultDialog(
    context: Context,
    onDismissRequest: () -> Unit,
    onSignatureSelected: (SavedSignature) -> Unit
) {
    var savedList by remember { mutableStateOf(SignatureVaultManager.loadAllSignatures(context)) }
    var isCreatingNew by remember { mutableStateOf(savedList.isEmpty()) }

    // New signature state
    val liveStrokes = remember { mutableStateListOf<List<Offset>>() }
    var activeStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var padSize by remember { mutableStateOf(Offset(400f, 200f)) }
    var selectedInkColor by remember { mutableStateOf(Color(0xFF0D47A1)) } // Default Royal Blue

    val inkColors = listOf(
        Color(0xFF0D47A1) to "Royal Blue",
        Color(0xFF141414) to "Executive Black",
        Color(0xFF1B5E20) to "Forest Green",
        Color(0xFFB71C1C) to "Crimson Red"
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF1A1A1A),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Gesture, contentDescription = null, tint = ScannerGreen, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Signature Vault", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                if (!isCreatingNew) {
                    IconButton(onClick = { isCreatingNew = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add New", tint = ScannerGreen)
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (isCreatingNew) {
                    // Ink Color Chooser
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Ink:", color = Color.Gray, fontSize = 12.sp)
                        inkColors.forEach { (c, _) ->
                            val isSel = selectedInkColor == c
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(if (isSel) 2.5.dp else 1.dp, if (isSel) ScannerGreen else Color.DarkGray, CircleShape)
                                    .clickable { selectedInkColor = c }
                            )
                        }
                    }

                    // Pad
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF9F9FB))
                            .border(1.dp, Color(0xFF444444), RoundedCornerShape(10.dp))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        padSize = Offset(size.width.toFloat(), size.height.toFloat())
                                        activeStroke = listOf(offset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        activeStroke = activeStroke + change.position
                                    },
                                    onDragEnd = {
                                        if (activeStroke.isNotEmpty()) {
                                            liveStrokes.add(activeStroke)
                                            activeStroke = emptyList()
                                        }
                                    },
                                    onDragCancel = { activeStroke = emptyList() }
                                )
                            }
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            // Baseline guide
                            val baselineY = size.height * 0.72f
                            drawLine(
                                color = Color.LightGray,
                                start = Offset(20f, baselineY),
                                end = Offset(size.width - 20f, baselineY),
                                strokeWidth = 1.5f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )

                            // Committed strokes
                            for (stroke in liveStrokes) {
                                if (stroke.size > 1) {
                                    val p = Path()
                                    p.moveTo(stroke[0].x, stroke[0].y)
                                    for (i in 1 until stroke.size) {
                                        val prev = stroke[i - 1]
                                        val curr = stroke[i]
                                        p.quadraticBezierTo(prev.x, prev.y, (prev.x + curr.x) / 2f, (prev.y + curr.y) / 2f)
                                    }
                                    p.lineTo(stroke.last().x, stroke.last().y)
                                    drawPath(p, color = selectedInkColor, style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                }
                            }

                            // Active stroke
                            if (activeStroke.size > 1) {
                                val p = Path()
                                p.moveTo(activeStroke[0].x, activeStroke[0].y)
                                for (i in 1 until activeStroke.size) {
                                    val prev = activeStroke[i - 1]
                                    val curr = activeStroke[i]
                                    p.quadraticBezierTo(prev.x, prev.y, (prev.x + curr.x) / 2f, (prev.y + curr.y) / 2f)
                                }
                                p.lineTo(activeStroke.last().x, activeStroke.last().y)
                                drawPath(p, color = selectedInkColor, style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                        }

                        if (liveStrokes.isEmpty() && activeStroke.isEmpty()) {
                            Text(
                                "Sign here with finger or stylus",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                liveStrokes.clear()
                                activeStroke = emptyList()
                            }
                        ) {
                            Text("Clear", color = Color(0xFFFF5252), fontSize = 12.sp)
                        }

                        Row {
                            if (savedList.isNotEmpty()) {
                                TextButton(onClick = { isCreatingNew = false }) {
                                    Text("Saved Signatures", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                            Button(
                                onClick = {
                                    val created = SignatureVaultManager.saveSignature(
                                        context = context,
                                        rawStrokes = liveStrokes.toList(),
                                        colorArgb = selectedInkColor.toArgb(),
                                        title = "Signature ${savedList.size + 1}"
                                    )
                                    if (created != null) {
                                        onSignatureSelected(created)
                                    }
                                },
                                enabled = liveStrokes.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                            ) {
                                Text("Save & Place", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    // List of saved signatures
                    Text("Select a saved signature to place on the page:", color = Color.LightGray, fontSize = 12.sp)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (sig in savedList) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF242424),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSignatureSelected(sig) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Mini preview
                                    Box(
                                        modifier = Modifier
                                            .size(width = 100.dp, height = 44.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.White)
                                    ) {
                                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                                            val pw = size.width
                                            val ph = size.height
                                            val col = Color(sig.inkColorArgb)
                                            for (stroke in sig.strokes) {
                                                if (stroke.size > 1) {
                                                    val p = Path()
                                                    p.moveTo(stroke[0].x * pw, stroke[0].y * ph)
                                                    for (i in 1 until stroke.size) {
                                                        val prev = stroke[i - 1]
                                                        val curr = stroke[i]
                                                        p.quadraticBezierTo(prev.x * pw, prev.y * ph, (prev.x + curr.x) * pw / 2f, (prev.y + curr.y) * ph / 2f)
                                                    }
                                                    p.lineTo(stroke.last().x * pw, stroke.last().y * ph)
                                                    drawPath(p, color = col, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                                }
                                            }
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            SignatureVaultManager.deleteSignature(context, sig.id)
                                            savedList = SignatureVaultManager.loadAllSignatures(context)
                                            if (savedList.isEmpty()) isCreatingNew = true
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close", color = Color.LightGray)
            }
        }
    )
}
