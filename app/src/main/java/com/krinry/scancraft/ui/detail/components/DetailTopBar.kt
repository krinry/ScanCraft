package com.krinry.scancraft.ui.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krinry.scancraft.ui.theme.ScannerGreen
import com.krinry.scancraft.ui.theme.ScannerTheme

@Composable
fun DetailTopBar(
    title: String,
    onNavigateBack: () -> Unit,
    onRenameClick: () -> Unit,
    onExportPdfClick: () -> Unit,
    isSelectMode: Boolean = false,
    selectedCount: Int = 0,
    totalCount: Int = 0,
    onClearSelection: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    isReorderMode: Boolean = false,
    onToggleReorderMode: () -> Unit = {},
    onEnterSelectMode: () -> Unit = {}
) {
    val colors = ScannerTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectMode) {
            // Selection Mode Header
            IconButton(onClick = onClearSelection) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Cancel Selection",
                    tint = colors.textPrimary
                )
            }

            Text(
                text = "$selectedCount selected",
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )

            TextButton(onClick = onSelectAll) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.SelectAll,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (selectedCount == totalCount && totalCount > 0) "Deselect" else "Select All",
                        color = colors.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        } else if (isReorderMode) {
            // Reorder Mode Header
            IconButton(onClick = onToggleReorderMode) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Cancel Reorder",
                    tint = colors.textPrimary
                )
            }

            Text(
                text = "Reorder Pages",
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )

            Button(
                onClick = onToggleReorderMode,
                colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Done",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Done", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        } else {
            // Normal Mode Header
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onRenameClick() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Rename",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Reorder button
            IconButton(onClick = onToggleReorderMode) {
                Icon(
                    Icons.Default.SwapVert,
                    contentDescription = "Reorder Pages",
                    tint = colors.textSecondary
                )
            }

            // Select mode toggle button
            IconButton(onClick = onEnterSelectMode) {
                Icon(
                    Icons.Outlined.Checklist,
                    contentDescription = "Select Pages",
                    tint = colors.textSecondary
                )
            }

            // PDF Export button
            IconButton(onClick = onExportPdfClick) {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = "Export to PDF",
                    tint = colors.primary
                )
            }
        }
    }
}
