package com.krinry.scancraft.ui.viewer.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ripple
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krinry.scancraft.ui.theme.ScannerGreen
import com.krinry.scancraft.ui.theme.ScannerTheme

@Composable
fun ViewerBottomBar(
    onCropClick: () -> Unit,
    onEditClick: () -> Unit,
    onShareClick: () -> Unit,
    onSignatureWatermarkClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ScannerTheme.colors

    Surface(
        color = colors.surface,
        border = BorderStroke(0.5.dp, colors.cardBorder),
        tonalElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Crop & Post-scan filters
            ViewerBottomActionItem(
                icon = Icons.Outlined.Crop,
                label = "Crop",
                onClick = onCropClick,
                modifier = Modifier.weight(1f)
            )

            // 2. Edit (Image editing tools)
            ViewerBottomActionItem(
                icon = Icons.Outlined.Edit,
                label = "Edit",
                onClick = onEditClick,
                modifier = Modifier.weight(1f)
            )

            // 3. Center: Share (Current open image) - Prominently highlighted
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, color = ScannerGreen),
                            onClick = onShareClick
                        )
                        .padding(vertical = 4.dp, horizontal = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(ScannerGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Share",
                        color = colors.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 4. Signature & Watermark
            ViewerBottomActionItem(
                icon = Icons.Outlined.Draw,
                label = "Sign / Mark",
                onClick = onSignatureWatermarkClick,
                modifier = Modifier.weight(1f)
            )

            // 5. 3-dot More
            ViewerBottomActionItem(
                icon = Icons.Default.MoreVert,
                label = "More",
                onClick = onMoreClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ViewerBottomActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ScannerTheme.colors

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = colors.primary.copy(alpha = 0.2f)),
                onClick = onClick
            )
            .padding(vertical = 6.dp, horizontal = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = colors.textPrimary,
            modifier = Modifier.size(23.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
