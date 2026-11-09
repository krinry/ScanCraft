package com.krinry.scancraft.ui.viewer.dialogs

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.krinry.scancraft.ScanCraftApp
import com.krinry.scancraft.data.model.Page
import com.krinry.scancraft.data.repository.ScannerRepository
import com.krinry.scancraft.ui.theme.ScannerGreen
import com.krinry.scancraft.ui.theme.ScannerTheme
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerMoreMenuSheet(
    page: Page,
    docTitle: String,
    onDismiss: () -> Unit,
    onPageUpdated: () -> Unit,
    onPageDeleted: () -> Unit,
    onTitleUpdated: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showRetakeSourceDialog by remember { mutableStateOf(false) }

    // Retake Camera File & Launcher
    var tempCameraFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                tempCameraFile?.let { file ->
                    coroutineScope.launch(Dispatchers.IO) {
                        val app = context.applicationContext as ScanCraftApp
                        val repo = ScannerRepository(
                            context = context,
                            documentDao = app.database.documentDao(),
                            pageDao = app.database.pageDao(),
                            folderDao = app.database.folderDao()
                        )
                        repo.replacePageWithPhoto(page.id, file.absolutePath)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Page retaken successfully!", Toast.LENGTH_SHORT).show()
                            onPageUpdated()
                            onDismiss()
                        }
                    }
                }
            }
        }
    )

    // Gallery Picker Launcher for Retake
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val target = File(context.cacheDir, "gallery_replace_${System.currentTimeMillis()}.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(target).use { output ->
                                input.copyTo(output)
                            }
                        }
                        val app = context.applicationContext as ScanCraftApp
                        val repo = ScannerRepository(
                            context = context,
                            documentDao = app.database.documentDao(),
                            pageDao = app.database.pageDao(),
                            folderDao = app.database.folderDao()
                        )
                        repo.replacePageWithPhoto(page.id, target.absolutePath)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Page replaced from gallery!", Toast.LENGTH_SHORT).show()
                            onPageUpdated()
                            onDismiss()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    )

    val colors = ScannerTheme.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 38.dp, height = 4.dp),
                shape = CircleShape,
                color = colors.textMuted
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
                text = "Page Options",
                color = colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Manage Page ${page.pageIndex + 1} of '$docTitle'",
                color = colors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
            )

            // 1. Save to Gallery
            MoreMenuRow(
                icon = Icons.Outlined.FileDownload,
                title = "Save to Gallery",
                subtitle = "Save image to device Photos / Pictures folder",
                iconTint = ScannerGreen,
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        val app = context.applicationContext as ScanCraftApp
                        val repo = ScannerRepository(
                            context = context,
                            documentDao = app.database.documentDao(),
                            pageDao = app.database.pageDao(),
                            folderDao = app.database.folderDao()
                        )
                        val ok = repo.savePageToGallery(page)
                        withContext(Dispatchers.Main) {
                            if (ok) {
                                Toast.makeText(context, "Saved page to Gallery!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to save to Gallery", Toast.LENGTH_SHORT).show()
                            }
                            onDismiss()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Retake Page
            MoreMenuRow(
                icon = Icons.Outlined.CameraAlt,
                title = "Retake Page",
                subtitle = "Take a fresh photo to replace this page",
                iconTint = Color(0xFF42A5F5),
                onClick = { showRetakeSourceDialog = true }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Edit Page / Document Name
            MoreMenuRow(
                icon = Icons.Outlined.DriveFileRenameOutline,
                title = "Edit Name",
                subtitle = "Rename the document title",
                iconTint = Color(0xFFFFB74D),
                onClick = { showRenameDialog = true }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Delete Page
            MoreMenuRow(
                icon = Icons.Outlined.Delete,
                title = "Delete Page",
                subtitle = "Permanently remove this page from the document",
                iconTint = Color(0xFFEF5350),
                textColor = Color(0xFFEF5350),
                onClick = { showDeleteConfirmDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Retake Source Chooser Dialog
    if (showRetakeSourceDialog) {
        AlertDialog(
            onDismissRequest = { showRetakeSourceDialog = false },
            title = { Text("Retake Page ${page.pageIndex + 1}", fontWeight = FontWeight.Bold) },
            text = { Text("Select capture source to replace this page:", fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        showRetakeSourceDialog = false
                        val file = File(context.cacheDir, "retake_cam_${System.currentTimeMillis()}.jpg")
                        tempCameraFile = file
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        cameraLauncher.launch(uri)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                ) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Camera")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showRetakeSourceDialog = false
                        galleryLauncher.launch("image/*")
                    }
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gallery")
                }
            }
        )
    }

    // Rename Dialog
    if (showRenameDialog) {
        var newTitle by remember { mutableStateOf(docTitle) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Edit Document Name", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Document Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            coroutineScope.launch(Dispatchers.IO) {
                                val app = context.applicationContext as ScanCraftApp
                                val repo = ScannerRepository(
                                    context = context,
                                    documentDao = app.database.documentDao(),
                                    pageDao = app.database.pageDao(),
                                    folderDao = app.database.folderDao()
                                )
                                repo.updateDocumentTitle(page.documentId, newTitle.trim())
                                withContext(Dispatchers.Main) {
                                    showRenameDialog = false
                                    onTitleUpdated(newTitle.trim())
                                    Toast.makeText(context, "Name updated", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Page ${page.pageIndex + 1}?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This action cannot be undone. Are you sure you want to permanently delete this page?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        coroutineScope.launch(Dispatchers.IO) {
                            val app = context.applicationContext as ScanCraftApp
                            val repo = ScannerRepository(
                                context = context,
                                documentDao = app.database.documentDao(),
                                pageDao = app.database.pageDao(),
                                folderDao = app.database.folderDao()
                            )
                            repo.deletePage(page)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Page deleted", Toast.LENGTH_SHORT).show()
                                onPageDeleted()
                                onDismiss()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MoreMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    textColor: Color? = null,
    onClick: () -> Unit
) {
    val colors = ScannerTheme.colors
    val effectiveTextColor = textColor ?: colors.textPrimary

    Surface(
        color = colors.surfaceSecondary,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.cardBorder),
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
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = effectiveTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = colors.textSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}
