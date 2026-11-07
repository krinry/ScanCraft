package com.krinry.scancraft.ui.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.krinry.scancraft.data.model.Document
import com.krinry.scancraft.ui.theme.ScannerTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScannerDocumentGridCard(
    document: Document,
    isSelected: Boolean,
    isSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onSelectToggle: () -> Unit = {},
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onMoveToFolder: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = ScannerTheme.colors
    val dateFormat = remember { SimpleDateFormat("dd/MM/yy", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isSelectMode) onSelectToggle() else onClick() },
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 0.5.dp,
            color = if (isSelected) colors.primary else colors.cardBorder
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(colors.surfaceSecondary),
                contentAlignment = Alignment.Center
            ) {
                val thumbFile = remember(document.id) {
                    DocumentThumbnailLoader.findThumbnailFile(context, document.id)
                }

                if (thumbFile != null && thumbFile.exists()) {
                    AsyncImage(
                        model = thumbFile,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(44.dp)
                    )
                }

                // Green badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(18.dp),
                    shape = CircleShape,
                    color = colors.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                    }
                }

                // Selection check indicator in grid mode
                if (isSelectMode) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp),
                        shape = CircleShape,
                        color = if (isSelected) colors.primary else Color.Black.copy(alpha = 0.5f),
                        border = if (!isSelected) BorderStroke(1.5.dp, Color.White) else null
                    ) {
                        if (isSelected) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.title,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (document.tags.contains("draft", ignoreCase = true) || document.title.startsWith("Draft", ignoreCase = true)) {
                            Surface(
                                color = Color(0xFFF59E0B),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "DRAFT",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(dateFormat.format(Date(document.modifiedTime)), color = colors.textSecondary, fontSize = 11.sp)
                        Text("${document.pageCount} P", color = colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (!isSelectMode) {
                    var gridDocMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { gridDocMenuExpanded = true }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = null, tint = colors.textSecondary)
                        }
                        DropdownMenu(
                            expanded = gridDocMenuExpanded,
                            onDismissRequest = { gridDocMenuExpanded = false },
                            modifier = Modifier.background(colors.surface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Move to Folder", color = colors.textPrimary) },
                                onClick = {
                                    gridDocMenuExpanded = false
                                    onMoveToFolder()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.DriveFileMove,
                                        contentDescription = "Move to Folder",
                                        tint = colors.textSecondary
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Rename", color = colors.textPrimary) },
                                onClick = {
                                    gridDocMenuExpanded = false
                                    onRename()
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = colors.textSecondary) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = Color(0xFFEE3F3F)) },
                                onClick = {
                                    gridDocMenuExpanded = false
                                    onDelete()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEE3F3F)) }
                            )
                        }
                    }
                }
            }
        }
    }
}
