package com.krinry.scancraft.ui.home.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.krinry.scancraft.data.model.Document
import com.krinry.scancraft.ui.theme.ScannerTheme

@Composable
fun RenameDocumentDialog(
    document: Document,
    onDismiss: () -> Unit,
    onConfirm: (newTitle: String) -> Unit
) {
    val colors = ScannerTheme.colors
    var updatedTitle by remember { mutableStateOf(document.title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Document", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = updatedTitle,
                onValueChange = { updatedTitle = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.cardBorder,
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (updatedTitle.isNotBlank()) {
                        onConfirm(updatedTitle.trim())
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        },
        containerColor = colors.surface
    )
}
