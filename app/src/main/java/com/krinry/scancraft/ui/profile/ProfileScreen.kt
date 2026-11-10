package com.krinry.scancraft.ui.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.krinry.scancraft.BuildConfig
import com.krinry.scancraft.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.krinry.scancraft.cloud.*
import com.krinry.scancraft.data.repository.SettingsManager
import com.krinry.scancraft.data.repository.StorageCalculator
import com.krinry.scancraft.data.repository.StorageStats
import com.krinry.scancraft.ui.theme.ScannerGreen
import com.krinry.scancraft.ui.theme.ScannerGreenContainer
import com.krinry.scancraft.ui.theme.ScannerTheme
import com.krinry.scancraft.ui.theme.ThemeManager
import com.krinry.scancraft.ui.theme.AppThemeMode
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val cloudSyncManager = remember { CloudSyncManager(context) }

    // Reactive State from SettingsManager
    val userName by settingsManager.userName.collectAsState()
    val userTier by settingsManager.userTier.collectAsState()
    val avatarPath by settingsManager.avatarPath.collectAsState()

    // Real Live Storage Stats
    var storageStats by remember { mutableStateOf<StorageStats?>(null) }
    fun refreshStorage() {
        coroutineScope.launch {
            storageStats = StorageCalculator.calculate(context)
        }
    }
    LaunchedEffect(Unit) {
        refreshStorage()
    }

    // Avatar Picker Launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val avatarFile = File(context.filesDir, "profile_avatar.jpg")
                    context.contentResolver.openInputStream(uri)?.use { inStream ->
                        avatarFile.outputStream().use { outStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    settingsManager.setAvatarPath(avatarFile.absolutePath)
                } catch (e: Exception) {
                    coroutineScope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to save avatar: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val colors = ScannerTheme.colors
    val themeMode by ThemeManager.themeMode.collectAsState()

    // Dialog states
    var showAvatarOptionsDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var showScanSettingsDialog by remember { mutableStateOf(false) }
    var showDocManagementDialog by remember { mutableStateOf(false) }
    var showDocExportDialog by remember { mutableStateOf(false) }
    var showOcrLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth().padding(end = 48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Me",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
            )
        },
        containerColor = colors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 1. User Profile & Live Storage Header Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                color = colors.surface,
                border = BorderStroke(0.5.dp, colors.cardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // User Avatar (Custom photo or dynamic initials badge)
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(colors.primaryContainer)
                                .clickable { showAvatarOptionsDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            val avatarFile = avatarPath?.let { File(it) }
                            if (avatarFile != null && avatarFile.exists()) {
                                AsyncImage(
                                    model = avatarFile,
                                    contentDescription = "Profile Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                // Initials Fallback
                                val initials = userName.split(" ")
                                    .filter { it.isNotBlank() }
                                    .take(2)
                                    .map { it.first().uppercaseChar() }
                                    .joinToString("")
                                    .ifEmpty { "OS" }

                                Text(
                                    text = initials,
                                    color = ScannerGreen,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Camera edit icon indicator
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(ScannerGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = "Change Avatar",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showEditNameDialog = true }
                            ) {
                                Text(
                                    text = userName,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit Name",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Tier Badge
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colors.surfaceSecondary,
                                border = BorderStroke(0.5.dp, ScannerGreen.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .clickable { showEditNameDialog = true }
                                    .padding(vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        tint = ScannerGreen,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = userTier,
                                        color = colors.textPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Real Storage Calculation Row
                    val stats = storageStats
                    val appUsed = stats?.appUsedFormatted ?: "Calculating..."
                    val freeDev = stats?.freeDeviceFormatted ?: "..."

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Storage Used",
                            color = colors.textSecondary,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "$appUsed / $freeDev Free",
                            color = ScannerGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Real Live Progress Indicator
                    val progressVal = stats?.usageFraction ?: 0.05f
                    LinearProgressIndicator(
                        progress = { progressVal },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = ScannerGreen,
                        trackColor = colors.cardBorder
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Breakdown row
                    val filesCount = stats?.totalFilesCount ?: 0
                    val cacheSize = stats?.cacheFormatted ?: "0 B"
                    Text(
                        text = "$filesCount Scan files • Cache: $cacheSize",
                        color = colors.textMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Action Section 1 (Sync, Scan Settings, Doc Management, Doc Export, OCR Language, Theme)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                color = colors.surface,
                border = BorderStroke(0.5.dp, colors.cardBorder)
            ) {
                Column {
                    ProfileMenuRow(
                        icon = Icons.Outlined.Sync,
                        title = "Sync & Cloud Storage",
                        subtitle = "Google Drive, OneDrive, Dropbox, WebDAV, Local",
                        onClick = { showSyncDialog = true }
                    )
                    ProfileDivider()
                    ProfileMenuRow(
                        icon = Icons.Outlined.CropFree,
                        title = "Scan Settings",
                        subtitle = "Grid, Shutter sound, Auto-crop",
                        onClick = { showScanSettingsDialog = true }
                    )
                    ProfileDivider()
                    ProfileMenuRow(
                        icon = Icons.Outlined.Description,
                        title = "Doc Management",
                        subtitle = "Naming pattern, Auto-OCR",
                        onClick = { showDocManagementDialog = true }
                    )
                    ProfileDivider()
                    ProfileMenuRow(
                        icon = Icons.Outlined.FileUpload,
                        title = "Doc Export",
                        subtitle = "Default PDF page format",
                        onClick = { showDocExportDialog = true }
                    )
                    ProfileDivider()
                    ProfileMenuRow(
                        icon = Icons.Outlined.Translate,
                        title = "OCR Language",
                        subtitle = settingsManager.ocrLanguage.collectAsState().value,
                        onClick = { showOcrLanguageDialog = true }
                    )
                    ProfileDivider()
                    ProfileMenuRow(
                        icon = Icons.Outlined.DarkMode,
                        title = "App Theme",
                        subtitle = when (themeMode) {
                            AppThemeMode.LIGHT -> "Light Theme"
                            AppThemeMode.DARK -> "Dark Theme"
                            AppThemeMode.SYSTEM -> "System Default"
                        },
                        onClick = { showThemeDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Action Section 2 (Tell Friends, Free Up Memory, Feedback, About ScanCraft)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                color = colors.surface,
                border = BorderStroke(0.5.dp, colors.cardBorder)
            ) {
                Column {
                    ProfileMenuRow(
                        icon = Icons.Outlined.ThumbUp,
                        title = "Tell Friends",
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Try ScanCraft: Fast, privacy-first, offline document scanner with HD scan & multi-cloud sync! https://github.com/krinry/ScanCraft"
                                )
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share ScanCraft with friends"))
                        }
                    )
                    ProfileDivider()
                    ProfileMenuRow(
                        icon = Icons.Outlined.CleaningServices,
                        title = "Free Up Memory",
                        subtitle = "Clean temporary cache (${storageStats?.cacheFormatted ?: "0 B"})",
                        onClick = {
                            coroutineScope.launch {
                                var freedBytes = 0L
                                try {
                                    val cacheDir = context.cacheDir
                                    cacheDir.listFiles()?.forEach { file ->
                                        freedBytes += file.length()
                                        file.deleteRecursively()
                                    }
                                } catch (e: Exception) { /* Ignore */ }
                                refreshStorage()
                                val mb = String.format("%.1f", freedBytes / (1024f * 1024f).coerceAtLeast(0.1f))
                                Toast.makeText(context, "Memory Cleaned! Freed $mb MB cache.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    ProfileDivider()
                    ProfileMenuRow(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        title = "Feedback",
                        subtitle = "Submit issue or feature request on GitHub",
                        onClick = { showFeedbackDialog = true }
                    )
                    ProfileDivider()
                    ProfileMenuRow(
                        icon = Icons.Outlined.Info,
                        title = "About ScanCraft",
                        onClick = { showAboutDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 4. Footer
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ScanCraft v${BuildConfig.VERSION_NAME}",
                    color = colors.textMuted,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Open Source Document Vault • Clean-Room Native Vision",
                    color = colors.textMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "github.com/krinry/ScanCraft",
                    color = ScannerGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/krinry/ScanCraft"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "https://github.com/krinry/ScanCraft", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // --- Dialogs ---

    // Avatar Selection Dialog
    if (showAvatarOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarOptionsDialog = false },
            title = { Text("Profile Photo", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("Choose from Gallery", color = colors.textPrimary) },
                        leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = ScannerGreen) },
                        modifier = Modifier.clickable {
                            showAvatarOptionsDialog = false
                            photoPickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    if (avatarPath != null) {
                        ListItem(
                            headlineContent = { Text("Remove Photo", color = Color(0xFFFF5252)) },
                            leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252)) },
                            modifier = Modifier.clickable {
                                settingsManager.setAvatarPath(null)
                                try {
                                    File(context.filesDir, "profile_avatar.jpg").delete()
                                } catch (e: Exception) {}
                                showAvatarOptionsDialog = false
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAvatarOptionsDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface
        )
    }

    // 1. Edit Name & Tier Dialog
    if (showEditNameDialog) {
        var tempName by remember { mutableStateOf(userName) }
        var tempTier by remember { mutableStateOf(userTier) }

        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Edit Profile", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text("Your Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = ScannerGreen,
                            unfocusedBorderColor = colors.cardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempTier,
                        onValueChange = { tempTier = it },
                        label = { Text("Status / Title (e.g. Pro, Offline Vault)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = ScannerGreen,
                            unfocusedBorderColor = colors.cardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        settingsManager.setUserName(tempName)
                        settingsManager.setUserTier(tempTier.trim().ifEmpty { "Basic" })
                        showEditNameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface
        )
    }

    // App Theme Dialog
    if (showThemeDialog) {
        val currentThemeMode = themeMode
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("App Theme", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(
                        AppThemeMode.SYSTEM to "System Default",
                        AppThemeMode.DARK to "Dark Theme",
                        AppThemeMode.LIGHT to "Light Theme"
                    ).forEach { (mode, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ThemeManager.setThemeMode(context, mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = currentThemeMode == mode,
                                onClick = {
                                    ThemeManager.setThemeMode(context, mode)
                                    showThemeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = ScannerGreen)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, color = colors.textPrimary, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface
        )
    }

    // 2. Real Cloud Storage & Auto-Sync Dialog
    if (showSyncDialog) {
        RealCloudSyncDialog(
            cloudSyncManager = cloudSyncManager,
            onDismiss = {
                showSyncDialog = false
                refreshStorage()
            }
        )
    }

    // 3. Scan Settings Dialog
    if (showScanSettingsDialog) {
        RealScanSettingsDialog(
            settingsManager = settingsManager,
            onDismiss = { showScanSettingsDialog = false }
        )
    }

    // 4. Document Management Dialog
    if (showDocManagementDialog) {
        RealDocManagementDialog(
            settingsManager = settingsManager,
            onDismiss = { showDocManagementDialog = false }
        )
    }

    // 5. Document Export Dialog
    if (showDocExportDialog) {
        RealDocExportDialog(
            settingsManager = settingsManager,
            onDismiss = { showDocExportDialog = false }
        )
    }

    // 6. OCR Language Dialog
    if (showOcrLanguageDialog) {
        RealOcrLanguageDialog(
            settingsManager = settingsManager,
            onDismiss = { showOcrLanguageDialog = false }
        )
    }

    // 7. Real GitHub Feedback Dialog
    if (showFeedbackDialog) {
        RealFeedbackDialog(
            context = context,
            onDismiss = { showFeedbackDialog = false }
        )
    }

    // 8. About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = ScannerGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("About ScanCraft", color = colors.textPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.scancraft_logo),
                        contentDescription = "ScanCraft Logo",
                        modifier = Modifier.size(72.dp).clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("ScanCraft v${BuildConfig.VERSION_NAME}", color = colors.textPrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "An offline-first, privacy-focused open source document scanner powered by high-performance clean-room native computer vision.",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("• 100% Offline & Private (Zero tracking)", color = colors.textPrimary, fontSize = 12.sp)
                        Text("• Native C++ Document Quad Detection & Warp", color = colors.textPrimary, fontSize = 12.sp)
                        Text("• Multi-cloud & SAF local archive synchronization", color = colors.textPrimary, fontSize = 12.sp)
                        Text("• Multi-script ML Kit OCR (Latin, Devanagari, Chinese, Japanese)", color = colors.textPrimary, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Licensed under the MIT License.", color = colors.textMuted, fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAboutDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                ) {
                    Text("Close", color = Color.White)
                }
            },
            containerColor = colors.surface
        )
    }
}

@Composable
fun ProfileMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    val colors = ScannerTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = colors.textPrimary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun ProfileDivider() {
    val colors = ScannerTheme.colors
    HorizontalDivider(
        modifier = Modifier.padding(start = 54.dp),
        thickness = 0.5.dp,
        color = colors.cardBorder
    )
}

/**
 * Real Multi-Cloud Storage & Auto-Sync Dialog with genuine SDK clients.
 */
@Composable
fun RealCloudSyncDialog(
    cloudSyncManager: CloudSyncManager,
    onDismiss: () -> Unit
) {
    val colors = ScannerTheme.colors
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var autoSyncEnabled by remember { mutableStateOf(cloudSyncManager.isAutoSyncEnabled()) }
    var wifiOnly by remember { mutableStateOf(cloudSyncManager.isWifiOnly()) }
    var syncPdfCopies by remember { mutableStateOf(cloudSyncManager.isSyncPdfCopies()) }

    var isGdriveConnected by remember { mutableStateOf(false) }
    var gdriveAccount by remember { mutableStateOf("Not connected") }

    var isDropboxConnected by remember { mutableStateOf(false) }
    var dropboxAccount by remember { mutableStateOf("Not connected") }

    var isOneDriveConnected by remember { mutableStateOf(false) }
    var oneDriveAccount by remember { mutableStateOf("Not connected") }

    var isWebDavConnected by remember { mutableStateOf(false) }
    var webDavAccount by remember { mutableStateOf("Not connected") }

    var isLocalConnected by remember { mutableStateOf(false) }
    var localFolderName by remember { mutableStateOf("Not selected") }

    // Sub-dialogs for account credentials
    var showGoogleDriveOptionsDialog by remember { mutableStateOf(false) }
    var showGoogleDeveloperErrorDialog by remember { mutableStateOf(false) }
    var showDropboxDialog by remember { mutableStateOf(false) }
    var showOneDriveDialog by remember { mutableStateOf(false) }
    var showWebDavDialog by remember { mutableStateOf(false) }

    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    fun refreshProviderStatuses() {
        coroutineScope.launch {
            isGdriveConnected = cloudSyncManager.googleDriveProvider.isConnected()
            gdriveAccount = if (isGdriveConnected) cloudSyncManager.googleDriveProvider.getAccountIdentifier() else "Not connected"

            isDropboxConnected = cloudSyncManager.dropboxProvider.isConnected()
            dropboxAccount = if (isDropboxConnected) cloudSyncManager.dropboxProvider.getAccountIdentifier() else "Not connected"

            isOneDriveConnected = cloudSyncManager.oneDriveProvider.isConnected()
            oneDriveAccount = if (isOneDriveConnected) cloudSyncManager.oneDriveProvider.getAccountIdentifier() else "Not connected"

            isWebDavConnected = cloudSyncManager.webDavProvider.isConnected()
            webDavAccount = if (isWebDavConnected) cloudSyncManager.webDavProvider.getAccountIdentifier() else "Not connected"

            isLocalConnected = cloudSyncManager.localSafProvider.isConnected()
            localFolderName = if (isLocalConnected) cloudSyncManager.localSafProvider.getAccountIdentifier() else "Not selected"
        }
    }

    LaunchedEffect(Unit) {
        refreshProviderStatuses()
    }

    // Google Drive SAF Directory Picker Launcher
    val gdriveSafFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                val folderName = docFile?.name ?: "Google Drive Folder"
                coroutineScope.launch {
                    cloudSyncManager.googleDriveProvider.saveSafDriveFolder(uri, folderName)
                    refreshProviderStatuses()
                    Toast.makeText(context, "Google Drive connected: $folderName", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Google Drive folder error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Google Sign-In Activity Result Launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            if (account != null) {
                coroutineScope.launch {
                    cloudSyncManager.googleDriveProvider.onAccountSignedIn(account)
                    refreshProviderStatuses()
                    Toast.makeText(context, "Google Drive connected: ${account.email}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            if (e.statusCode == 10 || e.statusCode == com.google.android.gms.common.api.CommonStatusCodes.DEVELOPER_ERROR) {
                showGoogleDeveloperErrorDialog = true
            } else {
                Toast.makeText(context, "Google Sign-In error (${e.statusCode}): ${e.message}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Google Drive sign-in: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // SAF Local Directory Picker Launcher
    val safFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                val folderName = docFile?.name ?: "Selected Folder"
                coroutineScope.launch {
                    cloudSyncManager.localSafProvider.saveFolderUri(uri, folderName)
                    refreshProviderStatuses()
                    Toast.makeText(context, "Backup folder selected: $folderName", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "SAF permission error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val syncState by cloudSyncManager.syncState.collectAsState()
    val lastSyncTime by cloudSyncManager.lastSyncTime.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Sync, contentDescription = null, tint = ScannerGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cloud Storage & Multi-Sync", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Connect genuine cloud accounts & local folders to automatically back up scans and databases.",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("GENUINE CLOUD PROVIDERS", color = ScannerGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(8.dp))

                // 1. Google Drive (Official Play Services Auth & Drive v3 SDK)
                RealCloudProviderItem(
                    name = "Google Drive",
                    accountDesc = gdriveAccount,
                    isConnected = isGdriveConnected,
                    onConnectClick = {
                        if (isGdriveConnected) {
                            coroutineScope.launch {
                                cloudSyncManager.googleDriveProvider.disconnect()
                                refreshProviderStatuses()
                            }
                        } else {
                            showGoogleDriveOptionsDialog = true
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Microsoft OneDrive (Microsoft Graph REST Client)
                RealCloudProviderItem(
                    name = "Microsoft OneDrive",
                    accountDesc = oneDriveAccount,
                    isConnected = isOneDriveConnected,
                    onConnectClick = {
                        if (isOneDriveConnected) {
                            coroutineScope.launch {
                                cloudSyncManager.oneDriveProvider.disconnect()
                                refreshProviderStatuses()
                            }
                        } else {
                            showOneDriveDialog = true
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Dropbox (Official Dropbox Core SDK)
                RealCloudProviderItem(
                    name = "Dropbox",
                    accountDesc = dropboxAccount,
                    isConnected = isDropboxConnected,
                    onConnectClick = {
                        if (isDropboxConnected) {
                            coroutineScope.launch {
                                cloudSyncManager.dropboxProvider.disconnect()
                                refreshProviderStatuses()
                            }
                        } else {
                            showDropboxDialog = true
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 4. WebDAV / Nextcloud Client
                RealCloudProviderItem(
                    name = "WebDAV / Nextcloud",
                    accountDesc = webDavAccount,
                    isConnected = isWebDavConnected,
                    onConnectClick = {
                        if (isWebDavConnected) {
                            coroutineScope.launch {
                                cloudSyncManager.webDavProvider.disconnect()
                                refreshProviderStatuses()
                            }
                        } else {
                            showWebDavDialog = true
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 5. Local / SD Card (Storage Access Framework)
                RealCloudProviderItem(
                    name = "Local / SD Card Backup",
                    accountDesc = localFolderName,
                    isConnected = isLocalConnected,
                    onConnectClick = {
                        if (isLocalConnected) {
                            coroutineScope.launch {
                                cloudSyncManager.localSafProvider.disconnect()
                                refreshProviderStatuses()
                            }
                        } else {
                            safFolderLauncher.launch(null)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text("AUTOMATIC SYNC PREFERENCES", color = ScannerGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(8.dp))

                // Auto-sync switch
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Sync New Scans", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Upload automatically after scanning", color = colors.textSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = autoSyncEnabled,
                        onCheckedChange = {
                            autoSyncEnabled = it
                            cloudSyncManager.setAutoSyncEnabled(it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ScannerGreen)
                    )
                }

                // Wi-Fi Only Switch
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sync on Wi-Fi Only", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Conserve mobile data when uploading HD scans", color = colors.textSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = wifiOnly,
                        onCheckedChange = {
                            wifiOnly = it
                            cloudSyncManager.setWifiOnly(it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ScannerGreen)
                    )
                }

                // Upload PDF Copies Switch
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Generate & Sync PDFs", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Sync multi-page PDF & ZIP backup package", color = colors.textSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = syncPdfCopies,
                        onCheckedChange = {
                            syncPdfCopies = it
                            cloudSyncManager.setSyncPdfCopies(it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ScannerGreen)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Live Sync Status
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.surfaceSecondary,
                    border = BorderStroke(0.5.dp, colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Sync Status", color = colors.textSecondary, fontSize = 11.sp)
                            val statusText = when (val s = syncState) {
                                is SyncState.Syncing -> s.progressMessage
                                is SyncState.Completed -> s.message
                                is SyncState.Failed -> s.error
                                else -> "Last synced: $lastSyncTime"
                            }
                            Text(
                                text = statusText,
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2
                            )
                        }

                        if (syncState is SyncState.Syncing) {
                            CircularProgressIndicator(
                                color = ScannerGreen,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val res = cloudSyncManager.syncNow()
                                        when (res) {
                                            is SyncResult.Success -> Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                            is SyncResult.Error -> Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Sync Now", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
            ) {
                Text("Done", color = Color.White)
            }
        },
        containerColor = colors.surface
    )

    // Sub-Dialog 1: Google Drive Connection Method Selector
    if (showGoogleDriveOptionsDialog) {
        var showManualInput by remember { mutableStateOf(false) }
        var webClientIdInput by remember { mutableStateOf("") }
        var tokenInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showGoogleDriveOptionsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudQueue, contentDescription = null, tint = ScannerGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect Google Drive", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Choose your preferred way to sync scans with Google Drive:",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Option A: Android System Drive Picker (Recommended - Zero Setup)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.primaryContainer,
                        border = BorderStroke(1.dp, ScannerGreen.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showGoogleDriveOptionsDialog = false
                                gdriveSafFolderLauncher.launch(null)
                            }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FolderShared, contentDescription = null, tint = ScannerGreen, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Select Drive Folder", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(shape = RoundedCornerShape(4.dp), color = ScannerGreen) {
                                        Text("INSTANT", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                    }
                                }
                                Text("No developer account or SHA-1 setup needed. Uses phone's Google Drive app directly.", color = colors.textSecondary, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option B: 1-Tap Google Play Services Sign-In
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.surfaceSecondary,
                        border = BorderStroke(0.5.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showGoogleDriveOptionsDialog = false
                                try {
                                    googleSignInLauncher.launch(cloudSyncManager.googleDriveProvider.getSignInIntent())
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Sign-In error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Google Play Sign-In (1-Tap)", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Requires registered SHA-1 in Google Cloud Console.", color = colors.textSecondary, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option C: Manual Token or Web Client ID
                    if (!showManualInput) {
                        TextButton(
                            onClick = { showManualInput = true },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Advanced: Enter Client ID or OAuth Token", color = ScannerGreen, fontSize = 12.sp)
                        }
                    } else {
                        OutlinedTextField(
                            value = webClientIdInput,
                            onValueChange = { webClientIdInput = it },
                            label = { Text("Web Client ID (optional)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedBorderColor = ScannerGreen,
                                unfocusedBorderColor = colors.cardBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            label = { Text("OAuth Bearer Access Token (optional)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedBorderColor = ScannerGreen,
                                unfocusedBorderColor = colors.cardBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    if (tokenInput.isNotBlank()) {
                                        cloudSyncManager.googleDriveProvider.saveAuthToken(tokenInput)
                                    }
                                    if (webClientIdInput.isNotBlank()) {
                                        cloudSyncManager.googleDriveProvider.saveWebClientId(webClientIdInput)
                                    }
                                    refreshProviderStatuses()
                                    showGoogleDriveOptionsDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Advanced Configuration", color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showGoogleDriveOptionsDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface
        )
    }

    // Sub-Dialog 2: Google Sign-In Developer Error (Error 10) Resolution Assistant
    if (showGoogleDeveloperErrorDialog) {
        AlertDialog(
            onDismissRequest = { showGoogleDeveloperErrorDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFFFB300))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Google Sign-In: Error 10", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Google Play Services returned error 10 (DEVELOPER_ERROR).",
                        color = Color(0xFFFFB300),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "This occurs because Google Cloud Console requires registering this build's SHA-1 certificate before allowing 1-tap Google Play sign-in.",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("INSTANT FIX (NO SETUP REQUIRED)", color = ScannerGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            showGoogleDeveloperErrorDialog = false
                            gdriveSafFolderLauncher.launch(null)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderShared, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connect Drive via System Picker", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("DEVELOPER CREDENTIALS FOR GOOGLE CLOUD", color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    // Package Name Card
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.surfaceSecondary,
                        border = BorderStroke(0.5.dp, colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Package Name", color = colors.textMuted, fontSize = 10.sp)
                                Text(GoogleDriveProvider.APP_PACKAGE_NAME, color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            IconButton(onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(GoogleDriveProvider.APP_PACKAGE_NAME))
                                Toast.makeText(context, "Package Name copied!", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = ScannerGreen, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // SHA-1 Card
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.surfaceSecondary,
                        border = BorderStroke(0.5.dp, colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Debug SHA-1 Fingerprint", color = colors.textMuted, fontSize = 10.sp)
                                Text(GoogleDriveProvider.DEBUG_SHA1, color = colors.textPrimary, fontSize = 11.sp, maxLines = 1)
                            }
                            IconButton(onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(GoogleDriveProvider.DEBUG_SHA1))
                                Toast.makeText(context, "SHA-1 copied to clipboard!", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = ScannerGreen, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showGoogleDeveloperErrorDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceSecondary)
                ) {
                    Text("Close", color = colors.textPrimary)
                }
            },
            containerColor = colors.surface
        )
    }

    // Sub-Dialog: Dropbox Access Token
    if (showDropboxDialog) {
        var tokenInput by remember { mutableStateOf("") }
        var emailInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showDropboxDialog = false },
            title = { Text("Connect Dropbox", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter your Dropbox OAuth Access Token:", color = colors.textSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("Access Token") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = ScannerGreen,
                            unfocusedBorderColor = colors.cardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Account Name / Email (optional)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = ScannerGreen,
                            unfocusedBorderColor = colors.cardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tokenInput.isNotBlank()) {
                            coroutineScope.launch {
                                cloudSyncManager.dropboxProvider.saveAccessToken(
                                    token = tokenInput,
                                    accountName = emailInput.ifBlank { "Dropbox User" }
                                )
                                refreshProviderStatuses()
                                showDropboxDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                ) {
                    Text("Connect", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDropboxDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface
        )
    }

    // Sub-Dialog: OneDrive Token
    if (showOneDriveDialog) {
        var tokenInput by remember { mutableStateOf("") }
        var accountInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showOneDriveDialog = false },
            title = { Text("Connect Microsoft OneDrive", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter Microsoft Graph OAuth Token / App Token:", color = colors.textSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("OAuth Bearer Token") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = ScannerGreen,
                            unfocusedBorderColor = colors.cardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = accountInput,
                        onValueChange = { accountInput = it },
                        label = { Text("Account Name (optional)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = ScannerGreen,
                            unfocusedBorderColor = colors.cardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tokenInput.isNotBlank()) {
                            coroutineScope.launch {
                                cloudSyncManager.oneDriveProvider.saveAuth(tokenInput, accountInput)
                                refreshProviderStatuses()
                                showOneDriveDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                ) {
                    Text("Connect", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOneDriveDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface
        )
    }

    // Sub-Dialog: WebDAV / Nextcloud Configuration
    if (showWebDavDialog) {
        var urlInput by remember { mutableStateOf("https://cloud.example.com/remote.php/dav/files/user/") }
        var userInput by remember { mutableStateOf("") }
        var passInput by remember { mutableStateOf("") }
        var testResultMsg by remember { mutableStateOf<String?>(null) }
        var isTesting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showWebDavDialog = false },
            title = { Text("WebDAV / Nextcloud Setup", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter your private cloud WebDAV endpoint credentials:", color = colors.textSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Server WebDAV URL") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = ScannerGreen,
                            unfocusedBorderColor = colors.cardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = userInput,
                        onValueChange = { userInput = it },
                        label = { Text("Username") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = ScannerGreen,
                            unfocusedBorderColor = colors.cardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passInput,
                        onValueChange = { passInput = it },
                        label = { Text("Password or App Token") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = ScannerGreen,
                            unfocusedBorderColor = colors.cardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                isTesting = true
                                testResultMsg = null
                                coroutineScope.launch {
                                    val res = cloudSyncManager.webDavProvider.testConnection(urlInput, userInput, passInput)
                                    isTesting = false
                                    testResultMsg = if (res.isSuccess) "Success!" else "Failed: ${res.exceptionOrNull()?.localizedMessage}"
                                }
                            },
                            enabled = !isTesting && urlInput.isNotBlank() && userInput.isNotBlank()
                        ) {
                            Text(if (isTesting) "Testing..." else "Test Connection", fontSize = 12.sp)
                        }

                        testResultMsg?.let { msg ->
                            Text(
                                text = msg,
                                color = if (msg.startsWith("Success")) ScannerGreen else Color(0xFFFF5252),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (urlInput.isNotBlank() && userInput.isNotBlank()) {
                            coroutineScope.launch {
                                cloudSyncManager.webDavProvider.saveCredentials(urlInput, userInput, passInput)
                                refreshProviderStatuses()
                                showWebDavDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                ) {
                    Text("Save & Connect", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWebDavDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface
        )
    }
}

@Composable
fun RealCloudProviderItem(
    name: String,
    accountDesc: String,
    isConnected: Boolean,
    onConnectClick: () -> Unit
) {
    val colors = ScannerTheme.colors
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.surfaceSecondary,
        border = BorderStroke(0.5.dp, if (isConnected) ScannerGreen.copy(alpha = 0.5f) else colors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = accountDesc,
                    color = if (isConnected) ScannerGreen else colors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
            Button(
                onClick = onConnectClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) Color(0xFF263228) else ScannerGreen
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                border = if (isConnected) BorderStroke(1.dp, ScannerGreen) else null
            ) {
                Text(
                    text = if (isConnected) "Connected" else "Connect",
                    color = if (isConnected) ScannerGreen else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Real Scan Settings Dialog wired to SettingsManager.
 */
@Composable
fun RealScanSettingsDialog(
    settingsManager: SettingsManager,
    onDismiss: () -> Unit
) {
    val colors = ScannerTheme.colors
    val autoCrop by settingsManager.autoCropEnabled.collectAsState()
    val gridEnabled by settingsManager.gridEnabled.collectAsState()
    val shutterSound by settingsManager.shutterSoundEnabled.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan Preferences", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Crop Bounds by Default", color = colors.textPrimary, fontSize = 14.sp)
                        Text("Detect edges and perspective warp automatically", color = colors.textSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = autoCrop,
                        onCheckedChange = { settingsManager.setAutoCropEnabled(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = ScannerGreen)
                    )
                }

                HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Viewfinder Rule-of-Thirds Grid", color = colors.textPrimary, fontSize = 14.sp)
                        Text("Show 3x3 alignment guidelines in camera", color = colors.textSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = gridEnabled,
                        onCheckedChange = { settingsManager.setGridEnabled(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = ScannerGreen)
                    )
                }

                HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Camera Shutter Sound", color = colors.textPrimary, fontSize = 14.sp)
                        Text("Play physical camera shutter audio click", color = colors.textSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = shutterSound,
                        onCheckedChange = { settingsManager.setShutterSoundEnabled(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = ScannerGreen)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)) {
                Text("Done", color = Color.White)
            }
        },
        containerColor = colors.surface
    )
}

/**
 * Real Document Management Dialog wired to SettingsManager with naming patterns & auto-OCR.
 */
@Composable
fun RealDocManagementDialog(
    settingsManager: SettingsManager,
    onDismiss: () -> Unit
) {
    val colors = ScannerTheme.colors
    val namingPattern by settingsManager.namingPattern.collectAsState()
    val autoOcr by settingsManager.autoOcrEnabled.collectAsState()
    var tempPattern by remember { mutableStateOf(namingPattern) }

    val presetPatterns = listOf(
        "ScanCraft_yyyyMMdd_HHmmss",
        "Scan_{DATE}_{TIME}",
        "Doc_{DATE}",
        "Receipt_{DATE}"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Document Management", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Default Document Naming Pattern:", color = colors.textSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = tempPattern,
                    onValueChange = {
                        tempPattern = it
                        settingsManager.setNamingPattern(it)
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = ScannerGreen,
                        unfocusedBorderColor = colors.cardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Pattern helper chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetPatterns.take(2).forEach { pat ->
                        SuggestionChip(
                            onClick = {
                                tempPattern = pat
                                settingsManager.setNamingPattern(pat)
                            },
                            label = { Text(pat, fontSize = 10.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Live preview of generated title
                Text(
                    text = "Preview: ${settingsManager.generateDocumentTitle()}",
                    color = ScannerGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Run OCR After Scan", color = colors.textPrimary, fontSize = 14.sp)
                        Text("Extract text automatically in background", color = colors.textSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = autoOcr,
                        onCheckedChange = { settingsManager.setAutoOcrEnabled(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = ScannerGreen)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)) {
                Text("Done", color = Color.White)
            }
        },
        containerColor = colors.surface
    )
}

/**
 * Real Document Export Dialog wired to SettingsManager.
 */
@Composable
fun RealDocExportDialog(
    settingsManager: SettingsManager,
    onDismiss: () -> Unit
) {
    val colors = ScannerTheme.colors
    val currentSize by settingsManager.defaultPdfSize.collectAsState()
    val options = listOf("A4", "US Letter", "Fit to Document")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Preferences", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Default PDF Page Format:", color = colors.textSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                options.forEach { opt ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { settingsManager.setDefaultPdfSize(opt) }
                            .padding(vertical = 6.dp)
                    ) {
                        RadioButton(
                            selected = currentSize == opt,
                            onClick = { settingsManager.setDefaultPdfSize(opt) },
                            colors = RadioButtonDefaults.colors(selectedColor = ScannerGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(opt, color = colors.textPrimary, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)) {
                Text("Done", color = Color.White)
            }
        },
        containerColor = colors.surface
    )
}

/**
 * Real OCR Language Dialog supporting Multi-Script ML Kit (Latin, Devanagari, Chinese, Japanese).
 */
@Composable
fun RealOcrLanguageDialog(
    settingsManager: SettingsManager,
    onDismiss: () -> Unit
) {
    val colors = ScannerTheme.colors
    val selectedLang by settingsManager.ocrLanguage.collectAsState()
    val languages = listOf("English", "Hindi", "Spanish", "French", "German", "Japanese", "Chinese")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("OCR Language Selection", color = colors.textPrimary, fontWeight = FontWeight.Bold)
                Text("Powered by Google ML Kit Offline Models", color = ScannerGreen, fontSize = 11.sp)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                languages.forEach { lang ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { settingsManager.setOcrLanguage(lang) }
                            .padding(vertical = 6.dp)
                    ) {
                        RadioButton(
                            selected = selectedLang.equals(lang, ignoreCase = true),
                            onClick = { settingsManager.setOcrLanguage(lang) },
                            colors = RadioButtonDefaults.colors(selectedColor = ScannerGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(lang, color = colors.textPrimary, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)) {
                Text("Done", color = Color.White)
            }
        },
        containerColor = colors.surface
    )
}

/**
 * Real Feedback Dialog opening genuine GitHub Issues on krinry/ScanCraft with system diagnostic metadata.
 */
@Composable
fun RealFeedbackDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    val colors = ScannerTheme.colors
    var feedbackText by remember { mutableStateOf("") }
    var issueTitle by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Submit Feedback & Issue", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Submit directly to ScanCraft GitHub Issues (krinry/ScanCraft) with auto-attached system details:",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = issueTitle,
                    onValueChange = { issueTitle = it },
                    label = { Text("Issue Title / Summary") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = ScannerGreen,
                        unfocusedBorderColor = colors.cardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = feedbackText,
                    onValueChange = { feedbackText = it },
                    placeholder = { Text("Describe the bug or feature request in detail...") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = ScannerGreen,
                        unfocusedBorderColor = colors.cardBorder
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val title = Uri.encode(issueTitle.ifBlank { "User Feedback: ScanCraft" })
                    val bodyContent = buildString {
                        appendLine("### Description")
                        appendLine(feedbackText.ifBlank { "General feedback." })
                        appendLine()
                        appendLine("### Environment Details")
                        appendLine("- App: ScanCraft v${BuildConfig.VERSION_NAME}")
                        appendLine("- Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        appendLine("- Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                        appendLine("- Device Brand: ${Build.BRAND}")
                    }
                    val body = Uri.encode(bodyContent)
                    val githubUrl = "https://github.com/krinry/ScanCraft/issues/new?title=$title&body=$body"

                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Fallback to mailto
                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:support@scancraft.org")
                            putExtra(Intent.EXTRA_SUBJECT, issueTitle)
                            putExtra(Intent.EXTRA_TEXT, bodyContent)
                        }
                        context.startActivity(emailIntent)
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
            ) {
                Text("Submit on GitHub", color = Color.White)
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
