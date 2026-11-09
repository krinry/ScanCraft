package com.krinry.scancraft.data.repository

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageCalculatorTest {
    @get:Rule val temp = TemporaryFolder()

    private fun context(internal: File, external: File, cache: File): Context = mockk<Context>().also {
        every { it.filesDir } returns internal
        every { it.getExternalFilesDir(null) } returns external
        every { it.cacheDir } returns cache
    }

    private fun file(root: File, path: String, size: Int) {
        File(root, path).apply { parentFile.mkdirs(); writeBytes(ByteArray(size)) }
    }

    @Test
    fun countsVaultAndLegacyStorageIncludingNestedFileSizes() = runTest {
        val internal = temp.newFolder("internal")
        val external = temp.newFolder("external")
        val cache = temp.newFolder("cache")
        file(external, "scans/page.jpg", 10)
        file(external, "scans/nested/page.jpg", 20)
        file(internal, "scans/legacy.jpg", 30)
        file(external, "thumbs/thumb.jpg", 4)
        file(internal, "thumbs/thumb.jpg", 5)
        file(cache, "nested/preview.jpg", 6)
        file(external, "meta/ignored.json", 100)

        val stats = StorageCalculator.calculate(context(internal, external, cache))

        assertEquals(60L, stats.scansBytes)
        assertEquals(9L, stats.thumbsBytes)
        assertEquals(6L, stats.cacheBytes)
        assertEquals(75L, stats.totalAppBytes)
    }

    @Test
    fun sharedVaultAndInternalDirectoryIsCountedOnlyOnce() = runTest {
        val root = temp.newFolder("storage")
        val cache = temp.newFolder("cache")
        file(root, "scans/a.jpg", 10)
        file(root, "scans/b.jpg", 20)
        file(root, "thumbs/a.jpg", 5)

        val stats = StorageCalculator.calculate(context(root, root, cache))

        assertEquals(30L, stats.scansBytes)
        assertEquals(5L, stats.thumbsBytes)
        assertEquals(35L, stats.totalAppBytes)
        assertEquals(2, stats.totalFilesCount)
    }

    @Test
    fun emptyStorageHasZeroUsageAndFileCount() = runTest {
        val stats = StorageCalculator.calculate(context(
            temp.newFolder("internal"), temp.newFolder("external"), temp.newFolder("cache")))
        assertEquals(0L, stats.totalAppBytes)
        assertEquals(0L, stats.scansBytes)
        assertEquals(0L, stats.thumbsBytes)
        assertEquals(0L, stats.cacheBytes)
        assertEquals(0, stats.totalFilesCount)
    }
}
