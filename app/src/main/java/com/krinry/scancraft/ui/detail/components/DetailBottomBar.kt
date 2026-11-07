package com.krinry.scancraft.ui.detail.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krinry.scancraft.ui.theme.ScannerTheme

@Composable
fun DetailBottomBar(
    onAddClick: () -> Unit,
    onCollageClick: () -> Unit,
    onShareClick: () -> Unit,
    onEditPdfClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val colors = ScannerTheme.colors

    Surface(
        color = colors.surface,
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DetailActionButton(
                icon = Icons.Default.AddAPhoto,
                label = "Add",
                onClick = onAddClick
            )
            DetailActionButton(
                icon = Icons.Outlined.DashboardCustomize,
                label = "Collage",
                onClick = onCollageClick
            )
            DetailActionButton(
                icon = Icons.Outlined.Share,
                label = "Share",
                onClick = onShareClick
            )
            DetailActionButton(
                icon = Icons.Outlined.PictureAsPdf,
                label = "Edit PDF",
                onClick = onEditPdfClick
            )
            DetailActionButton(
                icon = Icons.Default.MoreVert,
                label = "More",
                onClick = onMoreClick
            )
        }
    }
}

@Composable
fun DetailActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val colors = ScannerTheme.colors

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = colors.textPrimary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = colors.textPrimary
        )
    }
}
