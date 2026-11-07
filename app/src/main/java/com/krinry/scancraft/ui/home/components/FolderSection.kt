package com.krinry.scancraft.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krinry.scancraft.data.model.Folder
import com.krinry.scancraft.data.model.FolderWithCount
import com.krinry.scancraft.ui.theme.ScannerTheme

@Composable
fun FolderSection(
    foldersWithCount: List<FolderWithCount>,
    isGridView: Boolean,
    isInsideFolder: Boolean = false,
    onSelectFolder: (Folder) -> Unit,
    onRenameFolder: (Folder) -> Unit,
    onDeleteFolder: (Folder) -> Unit,
    onCreateFolderClick: () -> Unit
) {
    val colors = ScannerTheme.colors
    if (foldersWithCount.isEmpty() && !isInsideFolder) return

    val title = if (isInsideFolder) "Subfolders (${foldersWithCount.size})" else "Folders (${foldersWithCount.size})"

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = colors.textSecondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            IconButton(
                onClick = onCreateFolderClick,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.CreateNewFolder,
                    contentDescription = "New Folder",
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (isGridView) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val chunked = foldersWithCount.chunked(2)
                chunked.forEach { rowFolders ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowFolders.forEach { fc ->
                            Box(modifier = Modifier.weight(1f)) {
                                ScannerFolderGridCard(
                                    folderWithCount = fc,
                                    onClick = { onSelectFolder(fc.folder) },
                                    onRename = { onRenameFolder(fc.folder) },
                                    onDelete = { onDeleteFolder(fc.folder) }
                                )
                            }
                        }
                        if (rowFolders.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                foldersWithCount.forEach { fc ->
                    ScannerFolderListItem(
                        folderWithCount = fc,
                        onClick = { onSelectFolder(fc.folder) },
                        onRename = { onRenameFolder(fc.folder) },
                        onDelete = { onDeleteFolder(fc.folder) }
                    )
                }
            }
        }
    }
}
