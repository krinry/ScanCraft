package com.krinry.scancraft.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krinry.scancraft.data.model.Folder
import com.krinry.scancraft.data.model.FolderWithCount
import com.krinry.scancraft.ui.home.DocSortOrder
import com.krinry.scancraft.ui.theme.ScannerTheme

@Composable
fun RecentDocsHeader(
    selectedFolder: Folder?,
    totalDocCount: Int,
    foldersWithCount: List<FolderWithCount>,
    onSelectFolder: (Folder?) -> Unit,
    onNavigateUp: () -> Unit,
    sortOrder: DocSortOrder,
    onSortOrderChanged: (DocSortOrder) -> Unit,
    isGridView: Boolean,
    onToggleGridView: () -> Unit,
    onEnterSelectMode: () -> Unit,
    onCreateFolderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ScannerTheme.colors
    var showFolderDropdown by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Folder / "Recent Docs" selector
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { showFolderDropdown = true }
                        .padding(vertical = 4.dp)
                ) {
                    if (selectedFolder != null) {
                        IconButton(
                            onClick = onNavigateUp,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Column {
                        val headerTitle = if (selectedFolder != null) selectedFolder.name else "Recent Docs"
                        Text(
                            text = headerTitle,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 190.dp)
                        )

                        if (selectedFolder == null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "All docs ($totalDocCount)",
                                    color = colors.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Folder",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Folder Dropdown Menu
                DropdownMenu(
                    expanded = showFolderDropdown,
                    onDismissRequest = { showFolderDropdown = false },
                    modifier = Modifier.background(colors.surface)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "All docs ($totalDocCount)",
                                color = if (selectedFolder == null) colors.primary else colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        onClick = {
                            onSelectFolder(null)
                            showFolderDropdown = false
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (selectedFolder == null) colors.primary else colors.folderYellow
                            )
                        }
                    )

                    foldersWithCount.forEach { fc ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${fc.folder.name} (${fc.docCount})",
                                    color = if (selectedFolder?.id == fc.folder.id) colors.primary else colors.textPrimary
                                )
                            },
                            onClick = {
                                onSelectFolder(fc.folder)
                                showFolderDropdown = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = colors.folderYellow
                                )
                            }
                        )
                    }
                }
            }

            // Right: 4 Shortcut Action Icons with subtle dividers
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 1. New Folder [+]
                IconButton(
                    onClick = onCreateFolderClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.CreateNewFolder,
                        contentDescription = "New Folder",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                HeaderDivider(color = colors.cardBorder)

                // 2. Sort Menu [⇅]
                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Outlined.SwapVert,
                            contentDescription = "Sort",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(colors.surface)
                    ) {
                        DocSortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        order.title,
                                        color = if (sortOrder == order) colors.primary else colors.textPrimary
                                    )
                                },
                                onClick = {
                                    onSortOrderChanged(order)
                                    showSortMenu = false
                                },
                                trailingIcon = {
                                    if (sortOrder == order) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = colors.primary)
                                    }
                                }
                            )
                        }
                    }
                }

                HeaderDivider(color = colors.cardBorder)

                // 3. Grid / List View Toggle [⊞]
                IconButton(
                    onClick = onToggleGridView,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isGridView) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                        contentDescription = "Toggle View",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                HeaderDivider(color = colors.cardBorder)

                // 4. Multi-Select Mode [☑]
                IconButton(
                    onClick = onEnterSelectMode,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.Checklist,
                        contentDescription = "Select Mode",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderDivider(color: Color) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(14.dp)
            .background(color)
    )
}
