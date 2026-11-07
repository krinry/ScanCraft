package com.krinry.scancraft.ui.home.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krinry.scancraft.data.model.FolderWithCount
import com.krinry.scancraft.ui.theme.ScannerTheme

@Composable
fun MoveToFolderDialog(
    foldersWithCount: List<FolderWithCount>,
    onDismiss: () -> Unit,
    onSelectFolderId: (Long) -> Unit
) {
    val colors = ScannerTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to Folder", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = colors.surfaceSecondary,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectFolderId(-1L)
                            onDismiss()
                        }
                ) {
                    Text("Root (All docs)", color = colors.textPrimary, modifier = Modifier.padding(14.dp))
                }

                foldersWithCount.forEach { fwc ->
                    Surface(
                        color = colors.surfaceSecondary,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectFolderId(fwc.folder.id)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = colors.folderYellow)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(fwc.folder.name, color = colors.textPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        },
        containerColor = colors.surface
    )
}
