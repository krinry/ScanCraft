package com.krinry.scancraft.ui.home.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MergeDocumentsDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onConfirmMerge: (mergedTitle: String, deleteOriginals: Boolean) -> Unit
) {
    val colors = ScannerTheme.colors
    val defaultTitle = remember {
        "Merged Doc " + SimpleDateFormat("MM-dd-yyyy HH.mm", Locale.getDefault()).format(Date())
    }
    var title by remember { mutableStateOf(defaultTitle) }
    var deleteOriginals by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.CallMerge,
                    contentDescription = null,
                    tint = ScannerGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Merge Documents",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Combine $selectedCount selected documents into a single multipage document.",
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Merged Document Name", color = colors.textSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = ScannerGreen,
                        unfocusedBorderColor = colors.cardBorder,
                        focusedLabelColor = ScannerGreen,
                        unfocusedLabelColor = colors.textSecondary,
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { deleteOriginals = !deleteOriginals },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = deleteOriginals,
                        onCheckedChange = { deleteOriginals = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = ScannerGreen,
                            uncheckedColor = colors.textMuted
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete original documents after merging",
                        color = colors.textPrimary,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTitle = title.trim().ifEmpty { defaultTitle }
                    onConfirmMerge(finalTitle, deleteOriginals)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Merge", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        },
        containerColor = colors.surface,
        shape = RoundedCornerShape(16.dp)
    )
}
