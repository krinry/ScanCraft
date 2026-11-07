package com.krinry.scancraft.ui.detail.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.krinry.scancraft.ui.theme.ScannerGreen
import com.krinry.scancraft.ui.theme.ScannerTheme

@Composable
fun MoveToPositionDialog(
    currentPosition1Based: Int,
    totalPages: Int,
    selectedPageCount: Int = 1,
    onDismiss: () -> Unit,
    onConfirm: (targetPosition1Based: Int) -> Unit
) {
    val colors = ScannerTheme.colors
    var targetText by remember { mutableStateOf(currentPosition1Based.toString()) }
    val targetInt = targetText.toIntOrNull() ?: currentPosition1Based
    val isValid = targetInt in 1..totalPages

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.surface,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = if (selectedPageCount > 1) "Move $selectedPageCount Pages" else "Move Page",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (selectedPageCount > 1) {
                        "Set target position for selected pages (1 to $totalPages)"
                    } else {
                        "Move Page $currentPosition1Based to a new position (1 to $totalPages)"
                    },
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Number Stepper Input ( - [ Number ] + )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Decrement Button
                    IconButton(
                        onClick = {
                            val next = (targetInt - 1).coerceAtLeast(1)
                            targetText = next.toString()
                        },
                        enabled = targetInt > 1,
                        modifier = Modifier
                            .size(44.dp)
                            .background(colors.surfaceSecondary, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Decrease",
                            tint = if (targetInt > 1) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Direct Number Text Field
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() }.take(4)
                            targetText = filtered
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(88.dp),
                        textStyle = LocalTextStyle.current.copy(
                            textAlign = TextAlign.Center,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isValid) ScannerGreen else Color(0xFFFF5252),
                            unfocusedBorderColor = colors.cardBorder,
                            focusedContainerColor = colors.surfaceSecondary,
                            unfocusedContainerColor = colors.surfaceSecondary
                        )
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    // Increment Button
                    IconButton(
                        onClick = {
                            val next = (targetInt + 1).coerceAtMost(totalPages)
                            targetText = next.toString()
                        },
                        enabled = targetInt < totalPages,
                        modifier = Modifier
                            .size(44.dp)
                            .background(colors.surfaceSecondary, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Increase",
                            tint = if (targetInt < totalPages) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Jump Pills (Move to First, Move to Last)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    FilterChip(
                        selected = targetInt == 1,
                        onClick = { targetText = "1" },
                        label = { Text("Move to First (1)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ScannerGreen.copy(alpha = 0.2f),
                            selectedLabelColor = ScannerGreen
                        )
                    )

                    FilterChip(
                        selected = targetInt == totalPages,
                        onClick = { targetText = totalPages.toString() },
                        label = { Text("Move to Last ($totalPages)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ScannerGreen.copy(alpha = 0.2f),
                            selectedLabelColor = ScannerGreen
                        )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions: Cancel & Move
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, colors.cardBorder)
                    ) {
                        Text("Cancel", color = colors.textSecondary)
                    }

                    Button(
                        onClick = {
                            if (isValid) {
                                onConfirm(targetInt)
                            }
                        },
                        enabled = isValid,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Move to $targetInt", color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
