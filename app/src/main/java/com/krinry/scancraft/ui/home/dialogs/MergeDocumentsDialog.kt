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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MergeDocumentsDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onConfirmMerge: (mergedTitle: String, deleteOriginals: Boolean) -> Unit
) {
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
                    color = Color.White,
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
                    color = Color.LightGray,
                    fontSize = 13.sp
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Merged Document Name", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = ScannerGreen,
                        unfocusedBorderColor = Color(0xFF444444),
                        focusedLabelColor = ScannerGreen,
                        unfocusedLabelColor = Color.Gray
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
                            uncheckedColor = Color.Gray
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete original documents after merging",
                        color = Color.LightGray,
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
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(16.dp)
    )
}
