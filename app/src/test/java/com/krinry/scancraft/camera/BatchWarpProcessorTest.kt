package com.krinry.scancraft.camera

import android.content.Context
import android.graphics.Bitmap
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.krinry.scancraft.data.repository.ScannerRepository
import com.krinry.scancraft.ui.camera.BatchCaptureItem
import java.util.concurrent.atomic.AtomicInteger

/**
 * Production unit tests for BatchWarpProcessor:
 * 1. Semaphore concurrency bounds (max 2 permits)
 * 2. Automatic document creation when target document ID <= 0
 * 3. Empty batch and edge-case handling
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BatchWarpProcessorTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testSemaphoreLimitsConcurrencyToTwo() = runTest {
        val semaphore = Semaphore(2)
        val currentConcurrent = AtomicInteger(0)
        val maxConcurrentObserved = AtomicInteger(0)

        coroutineScope {
            val tasks = (1..20).map {
                async {
                    semaphore.withPermit {
                        val count = currentConcurrent.incrementAndGet()
                        var max = maxConcurrentObserved.get()
                        while (count > max && !maxConcurrentObserved.compareAndSet(max, count)) {
                            max = maxConcurrentObserved.get()
                        }
                        delay(10)
                        currentConcurrent.decrementAndGet()
                    }
                }
            }
            tasks.awaitAll()
        }

        assertTrue("Max concurrent executions must not exceed 2", maxConcurrentObserved.get() <= 2)
    }

    @Test
    fun testProcessorReturnsTargetDocIdWhenEmpty() = runTest {
        val context = mockk<Context>(relaxed = true)
        val repository = mockk<ScannerRepository>(relaxed = true)
        val processor = BatchWarpProcessor(context, repository)

        val docId = processor.processBatch(targetDocId = -1L, items = emptyList())
        assertEquals(-1L, docId)
    }

    @Test
    fun testProcessorCreatesNewDocumentWhenTargetDocIdIsNegative() = runTest {
        val context = mockk<Context>(relaxed = true)
        val repository = mockk<ScannerRepository>(relaxed = true)
        coEvery { repository.createNewDocument(any(), any()) } returns 777L

        val processor = BatchWarpProcessor(context, repository)
        val dummyBitmap = mockk<Bitmap>(relaxed = true)
        val items = listOf(
            BatchCaptureItem(id = 1L, rawFilePath = "/nonexistent/test.jpg", thumbnailBitmap = dummyBitmap)
        )

        val docId = processor.processBatch(
            targetDocId = -1L,
            items = items,
            onProgress = { _, _ -> }
        )

        assertEquals(777L, docId)
        coVerify(exactly = 1) { repository.createNewDocument(any(), any()) }
    }
}
