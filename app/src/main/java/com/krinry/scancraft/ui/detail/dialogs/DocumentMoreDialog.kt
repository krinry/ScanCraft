package com.krinry.scancraft.ui.detail.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.AlignHorizontalLeft
import androidx.compose.material.icons.automirrored.outlined.AlignHorizontalRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krinry.scancraft.ui.theme.ScannerGreen
import com.krinry.scancraft.ui.theme.ScannerTheme

enum class PageNumberPosition(val title: String) {
    NONE("No page number"),
    LOWER_LEFT("the lower-left"),
    BOTTOM_CENTER("the bottom center"),
    LOWER_RIGHT("the lower-right")
}

enum class PdfOrientation(val title: String) {
    AUTO("Auto-adjust"),
    LANDSCAPE("Landscape"),
    PORTRAIT("Portrait")
}

data class PaperSizeOption(val name: String, val dimensions: String)

val STANDARD_PAPER_SIZES = listOf(
    PaperSizeOption("Auto Fit", ""),
    PaperSizeOption("A3", "29.7cm × 42.0cm"),
    PaperSizeOption("A4", "21.0cm × 29.7cm"),
    PaperSizeOption("A5", "14.8cm × 21.0cm"),
    PaperSizeOption("B4", "25.0cm × 35.3cm"),
    PaperSizeOption("B5", "17.6cm × 25.0cm"),
    PaperSizeOption("Letter", "21.6cm × 27.9cm"),
    PaperSizeOption("Tabloid", "27.9cm × 43.2cm"),
    PaperSizeOption("Legal", "21.6cm × 35.6cm"),
    PaperSizeOption("Executive", "18.4cm × 26.7cm"),
    PaperSizeOption("Postcard", "10.0cm × 14.7cm")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentMoreDialog(
    onDismiss: () -> Unit,
    onBatchEditClick: () -> Unit,
    onSetTagClick: () -> Unit,
    onPdfPasswordClick: () -> Unit,
    onMoveToFolderClick: (() -> Unit)? = null,
    onRenameClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    onSignatureClick: (() -> Unit)? = null,
    isFirstOnTop: Boolean = true,
    onSortOrderChange: (firstOnTop: Boolean) -> Unit = {},
    selectedPageNumber: PageNumberPosition = PageNumberPosition.NONE,
    onPageNumberChange: (PageNumberPosition) -> Unit = {},
    selectedOrientation: PdfOrientation = PdfOrientation.AUTO,
    onOrientationChange: (PdfOrientation) -> Unit = {},
    selectedPaperSize: String = "A4",
    onPaperSizeChange: (String) -> Unit = {}
) {
    val colors = ScannerTheme.colors
    var currentSortFirstOnTop by remember { mutableStateOf(isFirstOnTop) }
    var currentPageNumber by remember { mutableStateOf(selectedPageNumber) }
    var currentOrientation by remember { mutableStateOf(selectedOrientation) }
    var currentPaperSize by remember { mutableStateOf(selectedPaperSize) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "More",
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Batch edit
                SettingsNavRow(
                    title = "Batch edit",
                    onClick = {
                        onDismiss()
                        onBatchEditClick()
                    }
                )

                // Set Tag
                SettingsNavRow(
                    title = "Set Tag",
                    onClick = {
                        onDismiss()
                        onSetTagClick()
                    }
                )

                if (onSignatureClick != null) {
                    SettingsNavRow(
                        title = "E-Signature",
                        onClick = {
                            onDismiss()
                            onSignatureClick()
                        }
                    )
                }

                if (onMoveToFolderClick != null) {
                    SettingsNavRow(
                        title = "Move to Folder",
                        onClick = {
                            onDismiss()
                            onMoveToFolderClick()
                        }
                    )
                }

                if (onRenameClick != null) {
                    SettingsNavRow(
                        title = "Rename",
                        onClick = {
                            onDismiss()
                            onRenameClick()
                        }
                    )
                }

                if (onDeleteClick != null) {
                    SettingsNavRow(
                        title = "Delete Document",
                        onClick = {
                            onDismiss()
                            onDeleteClick()
                        }
                    )
                }

                // View by
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("View by", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Manually Sort", color = ScannerGreen, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SelectablePillButton(
                            title = "First on top",
                            isSelected = currentSortFirstOnTop,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                currentSortFirstOnTop = true
                                onSortOrderChange(true)
                            }
                        )
                        SelectablePillButton(
                            title = "Last on top",
                            isSelected = !currentSortFirstOnTop,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                currentSortFirstOnTop = false
                                onSortOrderChange(false)
                            }
                        )
                    }
                }

                // PDF Password
                SettingsNavRow(
                    title = "PDF Password",
                    actionText = "PDF Password",
                    onClick = {
                        onDismiss()
                        onPdfPasswordClick()
                    }
                )

                // Page Number
                Column {
                    Text("Page Number", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PageNumberPosition.entries.forEach { pos ->
                            val isSelected = pos == currentPageNumber
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) colors.primaryContainer else colors.surfaceSecondary,
                                border = BorderStroke(1.dp, if (isSelected) colors.primary else Color.Transparent),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(72.dp)
                                    .clickable {
                                        currentPageNumber = pos
                                        onPageNumberChange(pos)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = when (pos) {
                                            PageNumberPosition.NONE -> Icons.Outlined.FormatLineSpacing
                                            PageNumberPosition.LOWER_LEFT -> Icons.AutoMirrored.Outlined.AlignHorizontalLeft
                                            PageNumberPosition.BOTTOM_CENTER -> Icons.Outlined.AlignHorizontalCenter
                                            PageNumberPosition.LOWER_RIGHT -> Icons.AutoMirrored.Outlined.AlignHorizontalRight
                                        },
                                        contentDescription = null,
                                        tint = if (isSelected) colors.primary else colors.textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = pos.title,
                                        color = if (isSelected) colors.primary else colors.textSecondary,
                                        fontSize = 9.sp,
                                        maxLines = 2,
                                        lineHeight = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // PDF Direction
                Column {
                    Text("PDF Direction", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PdfOrientation.entries.forEach { orient ->
                            val isSelected = orient == currentOrientation
                            SelectablePillButton(
                                title = orient.title,
                                isSelected = isSelected,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    currentOrientation = orient
                                    onOrientationChange(orient)
                                }
                            )
                        }
                    }
                }

                // PDF Page Size
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("PDF Page Size", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    val chunkedSizes = STANDARD_PAPER_SIZES.chunked(2)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        chunkedSizes.forEach { rowSizes ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowSizes.forEach { paper ->
                                    val isSelected = paper.name == currentPaperSize
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) colors.primaryContainer else colors.surfaceSecondary,
                                        border = BorderStroke(1.dp, if (isSelected) colors.primary else Color.Transparent),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(52.dp)
                                            .clickable {
                                                currentPaperSize = paper.name
                                                onPaperSizeChange(paper.name)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = paper.name,
                                                color = if (isSelected) colors.primary else colors.textPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (paper.dimensions.isNotEmpty()) {
                                                Text(
                                                    text = paper.dimensions,
                                                    color = colors.textSecondary,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }
                                if (rowSizes.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsNavRow(
    title: String,
    actionText: String? = null,
    onClick: () -> Unit
) {
    val colors = ScannerTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (actionText != null) {
                Text(actionText, color = ScannerGreen, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(4.dp))
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SelectablePillButton(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = ScannerTheme.colors
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) colors.primaryContainer else colors.surfaceSecondary,
        border = BorderStroke(1.dp, if (isSelected) colors.primary else Color.Transparent),
        modifier = modifier
            .height(44.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = title,
                color = if (isSelected) colors.primary else colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
