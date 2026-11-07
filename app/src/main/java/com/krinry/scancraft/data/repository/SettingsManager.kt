package com.krinry.scancraft.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centralized, reactive application configuration manager.
 * Acts as the single source of truth for all user preferences across ScanCraft.
 */
class SettingsManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("scancraft_profile", Context.MODE_PRIVATE)

    // --- Camera & Scan Settings ---
    private val _autoCropEnabled = MutableStateFlow(prefs.getBoolean("pref_auto_crop", true))
    val autoCropEnabled: StateFlow<Boolean> = _autoCropEnabled.asStateFlow()

    private val _gridEnabled = MutableStateFlow(prefs.getBoolean("pref_grid", false))
    val gridEnabled: StateFlow<Boolean> = _gridEnabled.asStateFlow()

    private val _shutterSoundEnabled = MutableStateFlow(prefs.getBoolean("pref_sound", true))
    val shutterSoundEnabled: StateFlow<Boolean> = _shutterSoundEnabled.asStateFlow()

    // --- Document Management Settings ---
    private val _namingPattern = MutableStateFlow(
        prefs.getString("pref_naming", "New Doc MM-dd-yyyy HH.mm") ?: "New Doc MM-dd-yyyy HH.mm"
    )
    val namingPattern: StateFlow<String> = _namingPattern.asStateFlow()

    private val _autoOcrEnabled = MutableStateFlow(prefs.getBoolean("pref_auto_ocr", true))
    val autoOcrEnabled: StateFlow<Boolean> = _autoOcrEnabled.asStateFlow()

    // --- Document Export Settings ---
    private val _defaultPdfSize = MutableStateFlow(prefs.getString("pref_pdf_size", "A4") ?: "A4")
    val defaultPdfSize: StateFlow<String> = _defaultPdfSize.asStateFlow()

    // --- OCR Settings ---
    private val _ocrLanguage = MutableStateFlow(prefs.getString("pref_ocr_lang", "English") ?: "English")
    val ocrLanguage: StateFlow<String> = _ocrLanguage.asStateFlow()

    // --- Profile & Personalization ---
    private val _userName = MutableStateFlow(prefs.getString("user_name", "ScanCraft User") ?: "ScanCraft User")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userTier = MutableStateFlow(prefs.getString("user_tier", "Basic") ?: "Basic")
    val userTier: StateFlow<String> = _userTier.asStateFlow()

    private val _avatarPath = MutableStateFlow(prefs.getString("user_avatar_path", null))
    val avatarPath: StateFlow<String?> = _avatarPath.asStateFlow()

    // --- Setters ---

    fun setAutoCropEnabled(enabled: Boolean) {
        _autoCropEnabled.value = enabled
        prefs.edit().putBoolean("pref_auto_crop", enabled).apply()
    }

    fun setGridEnabled(enabled: Boolean) {
        _gridEnabled.value = enabled
        prefs.edit().putBoolean("pref_grid", enabled).apply()
    }

    fun setShutterSoundEnabled(enabled: Boolean) {
        _shutterSoundEnabled.value = enabled
        prefs.edit().putBoolean("pref_sound", enabled).apply()
    }

    fun setNamingPattern(pattern: String) {
        val sanitized = pattern.trim().ifEmpty { "New Doc MM-dd-yyyy HH.mm" }
        _namingPattern.value = sanitized
        prefs.edit().putString("pref_naming", sanitized).apply()
    }

    fun setAutoOcrEnabled(enabled: Boolean) {
        _autoOcrEnabled.value = enabled
        prefs.edit().putBoolean("pref_auto_ocr", enabled).apply()
    }

    fun setDefaultPdfSize(size: String) {
        _defaultPdfSize.value = size
        prefs.edit().putString("pref_pdf_size", size).apply()
    }

    fun setOcrLanguage(language: String) {
        _ocrLanguage.value = language
        prefs.edit().putString("pref_ocr_lang", language).apply()
    }

    fun setUserName(name: String) {
        val sanitized = name.trim().ifEmpty { "ScanCraft User" }
        _userName.value = sanitized
        prefs.edit().putString("user_name", sanitized).apply()
    }

    fun setUserTier(tier: String) {
        _userTier.value = tier
        prefs.edit().putString("user_tier", tier).apply()
    }

    fun setAvatarPath(path: String?) {
        _avatarPath.value = path
        if (path == null) {
            prefs.edit().remove("user_avatar_path").apply()
        } else {
            prefs.edit().putString("user_avatar_path", path).apply()
        }
    }

    /**
     * Generates a document title using the user-defined naming template.
     * Supports token replacement for {DATE}, {TIME}, and standard SimpleDateFormat tokens.
     */
    fun generateDocumentTitle(): String {
        val now = Date()
        val pattern = _namingPattern.value.ifBlank { "New Doc MM-dd-yyyy HH.mm" }

        return try {
            if (pattern.contains("{DATE}") || pattern.contains("{TIME}")) {
                val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(now)
                val timeStr = SimpleDateFormat("HHmmss", Locale.getDefault()).format(now)
                pattern.replace("{DATE}", dateStr).replace("{TIME}", timeStr)
            } else {
                SimpleDateFormat(pattern, Locale.getDefault()).format(now)
            }
        } catch (e: Exception) {
            "New Doc " + SimpleDateFormat("MM-dd-yyyy HH.mm", Locale.getDefault()).format(now)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
