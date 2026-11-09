package com.krinry.scancraft.ui.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun ScannerDocumentListItem(
    document: Document,
    isSelected: Boolean,
    isSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onSelectToggle: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onMoveToFolder: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = ScannerTheme.colors
    val dateFormat = remember { SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()) }

    val rowBg = if (isSelected) colors.primaryContainer.copy(alpha = 0.5f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .combinedClickable(
                onClick = {
                    if (isSelectMode) onSelectToggle() else onClick()
                },
                onLongClick = {
                    onLongClick()
                }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail with Green Check Badge
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(92.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceSecondary)
                .border(0.5.dp, colors.cardBorder, RoundedCornerShape(8.dp))
        ) {
            val thumbFile = remember(document.id, DocumentThumbnailLoader.revision.intValue) {
                DocumentThumbnailLoader.findThumbnailFile(context, document.id)
            }

            if (thumbFile != null && thumbFile.canRead()) {
                AsyncImage(
                    model = thumbFile,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val placeholderColor = if (colors.isDark) Color(0xFF333333) else Color(0xFFDEE2E6)
                    val placeholderSecondary = if (colors.isDark) Color(0xFF2A2A2A) else Color(0xFFE9ECEF)
                    Box(modifier = Modifier.fillMaxWidth(0.8f).height(5.dp).background(placeholderColor, RoundedCornerShape(2.dp)))
                    Box(modifier = Modifier.fillMaxWidth(0.95f).height(4.dp).background(placeholderSecondary, RoundedCornerShape(2.dp)))
                    Box(modifier = Modifier.fillMaxWidth(0.7f).height(4.dp).background(placeholderSecondary, RoundedCornerShape(2.dp)))
                    Box(modifier = Modifier.fillMaxWidth(0.85f).height(4.dp).background(placeholderSecondary, RoundedCornerShape(2.dp)))
                }
            }

            // Green Verified Badge in bottom right of thumbnail
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(15.dp),
                shape = CircleShape,
                color = colors.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Scanned",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Center Column: Title, Date, Page count
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = document.title,
                color = colors.textPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                Text(
                    text = dateFormat.format(Date(document.modifiedTime)),
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "${document.pageCount}",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Right side: Selection Checkbox in Select Mode or 3-dots menu in Regular Mode
        if (isSelectMode) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable { onSelectToggle() },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Surface(
                        shape = CircleShape,
                        color = colors.primary,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(1.5.dp, Color.White),
                        modifier = Modifier.size(22.dp)
                    ) {}
                }
            }
        } else {
            var docMenuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { docMenuExpanded = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = colors.textSecondary)
                }
                DropdownMenu(
                    expanded = docMenuExpanded,
                    onDismissRequest = { docMenuExpanded = false },
                    modifier = Modifier.background(colors.surface)
                ) {
                    DropdownMenuItem(
                        text = { Text("Move to Folder", color = colors.textPrimary) },
                        onClick = {
                            docMenuExpanded = false
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
                            docMenuExpanded = false
                            onRename()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = colors.textSecondary) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color(0xFFEE3F3F)) },
                        onClick = {
                            docMenuExpanded = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEE3F3F)) }
                    )
                }
            }
        }
    }
}
