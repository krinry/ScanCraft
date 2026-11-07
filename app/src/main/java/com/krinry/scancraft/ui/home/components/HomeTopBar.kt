package com.krinry.scancraft.ui.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krinry.scancraft.R
import com.krinry.scancraft.data.model.Folder
import com.krinry.scancraft.data.model.FolderWithCount
import com.krinry.scancraft.ui.home.DocSortOrder
import com.krinry.scancraft.ui.theme.ScannerTheme
import com.krinry.scancraft.ui.theme.ThemeManager

@Composable
fun HomeTopBar(
    isSelectMode: Boolean,
    selectedDocCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    isSearchExpanded: Boolean,
    onToggleSearch: () -> Unit,
    selectedFolder: Folder?,
    totalDocCount: Int,
    foldersWithCount: List<FolderWithCount>,
    onSelectFolder: (Folder?) -> Unit,
    sortOrder: DocSortOrder,
    onSortOrderChanged: (DocSortOrder) -> Unit,
    isGridView: Boolean,
    onToggleGridView: () -> Unit,
    onEnterSelectMode: () -> Unit,
    onCreateFolderClick: () -> Unit,
    onNavigateUp: () -> Unit = { onSelectFolder(null) },
    onProfileClick: () -> Unit = {},
    showSubHeader: Boolean = true
) {
    val context = LocalContext.current
    val colors = ScannerTheme.colors
    var showFolderDropdown by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        if (isSelectMode) {
            // Batch Selection Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
                Text(
                    text = "$selectedDocCount selected",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                TextButton(onClick = onSelectAll) {
                    Text("Select All", color = colors.primary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        } else {
            // ScanCraft Clean Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // User Profile Avatar
                Surface(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(onClick = onProfileClick),
                    shape = CircleShape,
                    color = colors.primaryContainer,
                    border = BorderStroke(1.5.dp, colors.primary)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = colors.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // ScanCraft Brand Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.scancraft_logo),
                        contentDescription = "ScanCraft Logo",
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ScanCraft",
                        color = colors.primary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 21.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                // Actions: Theme Switcher + Search
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Theme Switch Button (Sun / Moon)
                    IconButton(
                        onClick = { ThemeManager.toggleTheme(context) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (colors.isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (colors.isDark) "Switch to Light Theme" else "Switch to Dark Theme",
                            tint = if (colors.isDark) Color(0xFFFFD54F) else colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Search Icon
                    IconButton(
                        onClick = onToggleSearch,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Collapsible Search Input
            AnimatedVisibility(visible = isSearchExpanded) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text("Search document or OCR text...", color = colors.textSecondary, fontSize = 14.sp) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colors.textSecondary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = colors.textSecondary)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.cardBorder,
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface
                    ),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            if (showSubHeader) {
                // Sub-header Filter & Actions Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // "All docs(count) ▾"
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
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            val currentLabel = selectedFolder?.name ?: "All docs($totalDocCount)"

                            Text(
                                text = currentLabel,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 200.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Select Folder",
                                tint = colors.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Dropdown of folders
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
                                        Icon(Icons.Default.Folder, contentDescription = null, tint = colors.folderYellow)
                                    }
                                )
                            }
                        }
                    }

                    // Right action icons: New Folder [+], Sort [⇅], View Grid/List [⊞], Multi-select [☑]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // New Folder [+]
                        IconButton(
                            onClick = onCreateFolderClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Outlined.CreateNewFolder,
                                contentDescription = "New Folder",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Sort Menu [⇅]
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Outlined.SwapVert, contentDescription = "Sort", tint = colors.textSecondary)
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

                        // Grid / List Toggle [⊞]
                        IconButton(
                            onClick = onToggleGridView,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isGridView) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                                contentDescription = "Toggle View",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Select Mode trigger [☑]
                        IconButton(
                            onClick = onEnterSelectMode,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Outlined.Checklist, contentDescription = "Select", tint = colors.textSecondary, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }
}
