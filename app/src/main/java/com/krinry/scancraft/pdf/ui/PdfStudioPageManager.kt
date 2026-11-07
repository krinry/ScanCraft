package com.krinry.scancraft.pdf.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.krinry.scancraft.data.model.Page
import com.krinry.scancraft.data.repository.ScannerRepository
import com.krinry.scancraft.ui.theme.ScannerGreen
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfStudioPageManagerSheet(
    context: Context,
    scope: CoroutineScope,
    docId: Long,
    pages: List<Page>,
    currentPageIndex: Int,
    repository: ScannerRepository,
    onPageSelected: (Int) -> Unit,
    onPagesUpdated: (List<Page>) -> Unit,
    onDismissRequest: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    // Gallery Picker to add pages
    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            isProcessing = true
            scope.launch(Dispatchers.IO) {
                for (uri in uris) {
                    val file = File(context.cacheDir, "studio_add_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                    if (bmp != null) {
                        repository.savePage(
                            documentId = docId,
                            processedBitmap = bmp,
                            originalBitmap = bmp,
                            cropBorderJson = "",
                            enhanceModeId = 0
                        )
                        bmp.recycle()
                    }
                }
                val updated = repository.getPagesListForDocument(docId)
                withContext(Dispatchers.Main) {
                    onPagesUpdated(updated)
                    isProcessing = false
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF181818),
        scrimColor = Color.Black.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Page Manager (${pages.size} pages)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                }
            }

            // Page Filmstrip
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(pages, key = { _, item -> item.id }) { index, item ->
                    val isSelected = index == currentPageIndex
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onPageSelected(index) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 60.dp, height = 80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF282828))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) ScannerGreen else Color.DarkGray,
                                    shape = RoundedCornerShape(8.dp)
                                )
                        ) {
                            val f = File(item.processedImagePath)
                            if (f.exists()) {
                                AsyncImage(
                                    model = f,
                                    contentDescription = "Page ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${index + 1}",
                            color = if (isSelected) ScannerGreen else Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF2A2A2A))

            // Action Row for the currently selected page
            val currPage = pages.getOrNull(currentPageIndex)
            if (currPage != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Move Left
                    PageActionButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = "Move Left",
                        enabled = currentPageIndex > 0,
                        onClick = {
                            isProcessing = true
                            scope.launch(Dispatchers.IO) {
                                val prevPage = pages[currentPageIndex - 1]
                                repository.updatePage(currPage.copy(pageIndex = currentPageIndex - 1))
                                repository.updatePage(prevPage.copy(pageIndex = currentPageIndex))
                                val updated = repository.getPagesListForDocument(docId)
                                withContext(Dispatchers.Main) {
                                    onPagesUpdated(updated)
                                    onPageSelected(currentPageIndex - 1)
                                    isProcessing = false
                                }
                            }
                        }
                    )

                    // Move Right
                    PageActionButton(
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        label = "Move Right",
                        enabled = currentPageIndex < pages.size - 1,
                        onClick = {
                            isProcessing = true
                            scope.launch(Dispatchers.IO) {
                                val nextPage = pages[currentPageIndex + 1]
                                repository.updatePage(currPage.copy(pageIndex = currentPageIndex + 1))
                                repository.updatePage(nextPage.copy(pageIndex = currentPageIndex))
                                val updated = repository.getPagesListForDocument(docId)
                                withContext(Dispatchers.Main) {
                                    onPagesUpdated(updated)
                                    onPageSelected(currentPageIndex + 1)
                                    isProcessing = false
                                }
                            }
                        }
                    )

                    // Rotate 90
                    PageActionButton(
                        icon = Icons.Filled.RotateRight,
                        label = "Rotate 90°",
                        enabled = true,
                        onClick = {
                            isProcessing = true
                            scope.launch(Dispatchers.IO) {
                                val f = File(currPage.processedImagePath)
                                if (f.exists()) {
                                    val b = android.graphics.BitmapFactory.decodeFile(f.absolutePath)
                                    if (b != null) {
                                        val matrix = Matrix().apply { postRotate(90f) }
                                        val rot = Bitmap.createBitmap(b, 0, 0, b.width, b.height, matrix, true)
                                        FileOutputStream(f).use { out ->
                                            rot.compress(Bitmap.CompressFormat.JPEG, 92, out)
                                        }
                                        b.recycle()
                                        rot.recycle()
                                    }
                                }
                                val newRot = (currPage.rotationDegrees + 90) % 360
                                repository.updatePage(currPage.copy(rotationDegrees = newRot))
                                val updated = repository.getPagesListForDocument(docId)
                                withContext(Dispatchers.Main) {
                                    onPagesUpdated(updated)
                                    isProcessing = false
                                }
                            }
                        }
                    )

                    // Delete Page
                    PageActionButton(
                        icon = Icons.Outlined.Delete,
                        label = "Delete",
                        enabled = pages.size > 1,
                        tint = Color(0xFFFF5252),
                        onClick = { showDeleteConfirm = true }
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF2A2A2A))

            // Insert New Pages (Blank Page & Add from Gallery)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        isProcessing = true
                        scope.launch(Dispatchers.IO) {
                            // Create clean white A4 page (1240 x 1754 px)
                            val blank = Bitmap.createBitmap(1240, 1754, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(blank)
                            canvas.drawColor(android.graphics.Color.WHITE)

                            repository.savePage(
                                documentId = docId,
                                processedBitmap = blank,
                                originalBitmap = blank,
                                cropBorderJson = "",
                                enhanceModeId = 0
                            )
                            blank.recycle()
                            val updated = repository.getPagesListForDocument(docId)
                            withContext(Dispatchers.Main) {
                                onPagesUpdated(updated)
                                onPageSelected(updated.size - 1)
                                isProcessing = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Color(0xFF444444)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Blank Page", fontSize = 12.sp)
                }

                Button(
                    onClick = { galleryPicker.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                ) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Photo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }

    if (showDeleteConfirm && pages.size > 1) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Page ${currentPageIndex + 1}?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently remove this page?", color = Color.LightGray) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        val target = pages[currentPageIndex]
                        isProcessing = true
                        scope.launch(Dispatchers.IO) {
                            repository.deletePage(target)
                            val updated = repository.getPagesListForDocument(docId)
                            withContext(Dispatchers.Main) {
                                onPagesUpdated(updated)
                                val nextIdx = (currentPageIndex - 1).coerceAtLeast(0)
                                onPageSelected(nextIdx)
                                isProcessing = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE3F3F))
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF222222)
        )
    }
}

@Composable
private fun PageActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color = ScannerGreen,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) tint else Color.DarkGray,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label,
            color = if (enabled) Color.LightGray else Color.DarkGray,
            fontSize = 11.sp
        )
    }
}
