package com.krinry.scancraft

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.krinry.scancraft.data.local.AppDatabase
import com.krinry.scancraft.data.repository.DocumentRecoveryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScanCraftApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Initialize PDFBox resource loader
        PDFBoxResourceLoader.init(applicationContext)

        // Automatically restore documents from public StorageVault if Room DB is empty (e.g. after reinstall)
        appScope.launch {
            try {
                DocumentRecoveryManager.autoRestoreIfDatabaseEmpty(applicationContext, database)
            } catch (_: Throwable) {}
        }
    }

    companion object {
        lateinit var instance: ScanCraftApp
            private set
    }
}
