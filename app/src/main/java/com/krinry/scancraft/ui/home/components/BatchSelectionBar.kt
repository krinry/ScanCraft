package com.krinry.scancraft.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krinry.scancraft.ui.theme.ScannerTheme

@Composable
fun BatchSelectionBottomBar(
    selectedCount: Int,
    onShare: () -> Unit,
    onMoveCopy: () -> Unit,
    onRename: () -> Unit,
    onMerge: () -> Unit,
    onDeleteSelected: () -> Unit,
    onMore: () -> Unit
) {
    val colors = ScannerTheme.colors

    Surface(
        color = Color(0xFF0E0E0E),
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Share
            BatchActionButton(
                icon = Icons.Outlined.Share,
                label = "Share",
                enabled = selectedCount > 0,
                onClick = onShare
            )

            // 2. Move / Copy
            BatchActionButton(
                icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                label = "Move/Copy",
                enabled = selectedCount > 0,
                onClick = onMoveCopy
            )

            // 3. Rename (if 1 selected) or Merge (if > 1 selected)
            if (selectedCount > 1) {
                BatchActionButton(
                    icon = Icons.AutoMirrored.Filled.CallMerge,
                    label = "Merge",
                    enabled = true,
                    onClick = onMerge
                )
            } else {
                BatchActionButton(
                    icon = Icons.Outlined.Edit,
                    label = "Rename",
                    enabled = selectedCount == 1,
                    onClick = onRename
                )
            }

            // 4. Delete
            BatchActionButton(
                icon = Icons.Outlined.Delete,
                label = "Delete",
                enabled = selectedCount > 0,
                tint = Color(0xFFEE3F3F),
                onClick = onDeleteSelected
            )

            // 5. More
            BatchActionButton(
                icon = Icons.Default.MoreVert,
                label = "More",
                enabled = selectedCount > 0,
                onClick = onMore
            )
        }
    }
}

@Composable
private fun BatchActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color? = null,
    onClick: () -> Unit
) {
    val effectiveTint = when {
        !enabled -> Color(0xFF555555)
        tint != null -> tint
        else -> Color.White
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = effectiveTint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = effectiveTint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
