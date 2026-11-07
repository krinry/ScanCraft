package com.krinry.scancraft.ui.home.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.krinry.scancraft.data.model.FolderWithCount
import com.krinry.scancraft.ui.theme.ScannerGreen
import com.krinry.scancraft.ui.theme.ScannerTheme

enum class MoveCopyAction {
    MOVE,
    COPY
}

@Composable
fun MoveCopyDialog(
    selectedDocCount: Int,
    foldersWithCount: List<FolderWithCount>,
    initialAction: MoveCopyAction = MoveCopyAction.MOVE,
    onDismiss: () -> Unit,
    onCreateFolder: (name: String, parentFolderId: Long?) -> Unit,
    onConfirm: (action: MoveCopyAction, targetFolderId: Long, folderName: String) -> Unit
) {
    val colors = ScannerTheme.colors
    var currentAction by remember { mutableStateOf(initialAction) }
    var currentViewingFolderId by remember { mutableStateOf(-1L) } // -1 = Root
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    // Compute breadcrumb path: Root -> Parent -> ... -> Current
    val breadcrumbs = remember(currentViewingFolderId, foldersWithCount) {
        val list = mutableListOf<Pair<Long, String>>() // (id, name)
        var currId = currentViewingFolderId
        val visited = mutableSetOf<Long>()
        while (currId > 0L && visited.add(currId)) {
            val f = foldersWithCount.find { it.folder.id == currId }?.folder
            if (f != null) {
                list.add(0, f.id to f.name)
                currId = f.parentFolderId
            } else {
                break
            }
        }
        list.add(0, -1L to "Root")
        list
    }

    val currentFolderName = remember(currentViewingFolderId, foldersWithCount) {
        if (currentViewingFolderId <= 0L) "Root (All docs)"
        else foldersWithCount.find { it.folder.id == currentViewingFolderId }?.folder?.name ?: "Folder"
    }

    // Subfolders at current level
    val currentSubfolders = remember(currentViewingFolderId, foldersWithCount) {
        foldersWithCount.filter { it.folder.parentFolderId == currentViewingFolderId }
    }

    val onNavigateUp = {
        if (currentViewingFolderId > 0L) {
            val current = foldersWithCount.find { it.folder.id == currentViewingFolderId }?.folder
            val parentId = current?.parentFolderId ?: -1L
            currentViewingFolderId = if (parentId > 0L) parentId else -1L
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.82f),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (currentAction == MoveCopyAction.MOVE) "Move to Folder" else "Copy to Folder",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "$selectedDocCount document(s) selected",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Switcher: [ Move ]  |  [ Copy ]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2A2A2A))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { currentAction = MoveCopyAction.MOVE },
                        shape = RoundedCornerShape(6.dp),
                        color = if (currentAction == MoveCopyAction.MOVE) ScannerGreen else Color.Transparent
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.DriveFileMove,
                                contentDescription = null,
                                tint = if (currentAction == MoveCopyAction.MOVE) Color.White else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Move",
                                color = if (currentAction == MoveCopyAction.MOVE) Color.White else Color.Gray,
                                fontWeight = if (currentAction == MoveCopyAction.MOVE) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { currentAction = MoveCopyAction.COPY },
                        shape = RoundedCornerShape(6.dp),
                        color = if (currentAction == MoveCopyAction.COPY) ScannerGreen else Color.Transparent
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                tint = if (currentAction == MoveCopyAction.COPY) Color.White else Color.Gray,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Copy",
                                color = if (currentAction == MoveCopyAction.COPY) Color.White else Color.Gray,
                                fontWeight = if (currentAction == MoveCopyAction.COPY) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Navigation Breadcrumb Bar & Back Button
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF262626),
                    border = BorderStroke(0.5.dp, Color(0xFF383838))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentViewingFolderId > 0L) {
                            IconButton(
                                onClick = onNavigateUp,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Up",
                                    tint = ScannerGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        LazyRow(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            itemsIndexed(breadcrumbs) { index, crumb ->
                                val isLast = index == breadcrumbs.size - 1
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        currentViewingFolderId = crumb.first
                                    }
                                ) {
                                    if (crumb.first == -1L) {
                                        Icon(
                                            Icons.Default.Home,
                                            contentDescription = null,
                                            tint = if (isLast) Color.White else ScannerGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = crumb.second,
                                        color = if (isLast) Color.White else ScannerGreen,
                                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!isLast) {
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Section Title + "+ New Folder" Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (currentViewingFolderId <= 0L) "FOLDERS IN ROOT" else "SUBFOLDERS IN '${currentFolderName.uppercase()}'",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    TextButton(
                        onClick = { showCreateFolderDialog = true },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.CreateNewFolder,
                            contentDescription = null,
                            tint = ScannerGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "New Folder",
                            color = ScannerGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Scrollable Subfolders List or Empty State
                if (currentSubfolders.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF242424))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No subfolders here",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap 'New Folder' to create one,\nor tap '${if (currentAction == MoveCopyAction.MOVE) "Move Here" else "Copy Here"}' below to put items in '$currentFolderName'.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(currentSubfolders, key = { it.folder.id }) { fwc ->
                            val childCount = foldersWithCount.count { it.folder.parentFolderId == fwc.folder.id }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { currentViewingFolderId = fwc.folder.id },
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF262626),
                                border = BorderStroke(0.5.dp, Color(0xFF333333))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF333333),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = colors.folderYellow,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = fwc.folder.name,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                        val subtitle = buildString {
                                            append("${fwc.docCount} docs")
                                            if (childCount > 0) {
                                                append(" • $childCount subfolders")
                                            }
                                        }
                                        Text(
                                            text = subtitle,
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Open",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action Button: "Move Here (currentFolderName)"
                Button(
                    onClick = {
                        onConfirm(currentAction, currentViewingFolderId, currentFolderName)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = if (currentAction == MoveCopyAction.MOVE) {
                            Icons.AutoMirrored.Outlined.DriveFileMove
                        } else {
                            Icons.Outlined.ContentCopy
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (currentAction == MoveCopyAction.MOVE) {
                            "Move Here ($currentFolderName)"
                        } else {
                            "Copy Here ($currentFolderName)"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    // Inline Create New Folder Dialog (creates under currentViewingFolderId)
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { folderName ->
                onCreateFolder(folderName, if (currentViewingFolderId > 0L) currentViewingFolderId else -1L)
                showCreateFolderDialog = false
            }
        )
    }
}
