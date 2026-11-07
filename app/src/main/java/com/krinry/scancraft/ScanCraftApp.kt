package com.krinry.scancraft

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.krinry.scancraft.data.local.AppDatabase

class ScanCraftApp : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Initialize PDFBox resource loader
        PDFBoxResourceLoader.init(applicationContext)
    }

    companion object {
        lateinit var instance: ScanCraftApp
            private set
    }
}
