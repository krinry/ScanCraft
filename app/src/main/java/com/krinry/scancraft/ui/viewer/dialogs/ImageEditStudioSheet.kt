package com.krinry.scancraft.ui.viewer.dialogs

import androidx.compose.runtime.Composable
import com.krinry.scancraft.data.model.Page
import com.krinry.scancraft.ui.editor.UniversalImageEditStudio

/**
 * Compatibility wrapper delegating to [UniversalImageEditStudio].
 */
@Composable
fun ImageEditStudioSheet(
    page: Page,
    onDismiss: () -> Unit,
    onPageUpdated: () -> Unit
) {
    UniversalImageEditStudio(
        page = page,
        onDismiss = onDismiss,
        onPageUpdated = onPageUpdated
    )
}
