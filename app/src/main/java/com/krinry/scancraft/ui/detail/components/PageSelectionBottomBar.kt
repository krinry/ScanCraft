package com.krinry.scancraft.ui.detail.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
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
fun PageSelectionBottomBar(
    selectedCount: Int,
    onShare: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onSetPosition: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    val colors = ScannerTheme.colors
    val hasSelection = selectedCount > 0

    Surface(
        color = Color(0xFF121212),
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        shadowElevation = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Share
            PageActionButton(
                icon = Icons.Outlined.Share,
                label = "Share",
                enabled = hasSelection,
                tint = if (hasSelection) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f),
                onClick = onShare
            )

            // 2. Move
            PageActionButton(
                icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                label = "Move",
                enabled = hasSelection,
                tint = if (hasSelection) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f),
                onClick = onMove
            )

            // 3. Copy
            PageActionButton(
                icon = Icons.Outlined.ContentCopy,
                label = "Copy",
                enabled = hasSelection,
                tint = if (hasSelection) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f),
                onClick = onCopy
            )

            // 4. Set Position #
            if (onSetPosition != null) {
                PageActionButton(
                    icon = Icons.Default.FormatListNumbered,
                    label = "Position",
                    enabled = hasSelection,
                    tint = if (hasSelection) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f),
                    onClick = onSetPosition
                )
            }

            // 5. Delete (Red)
            PageActionButton(
                icon = Icons.Outlined.Delete,
                label = "Delete",
                enabled = hasSelection,
                tint = if (hasSelection) Color(0xFFFF5252) else Color(0xFFFF5252).copy(alpha = 0.35f),
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun PageActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = tint,
            fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
