package com.krinry.scancraft.cv

import android.graphics.Bitmap
import com.google.android.gms.tasks.OnCanceledListener
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Production unit tests for OcrEngine:
 * 1. Mutex concurrency serialization to protect heap memory from OOM
 * 2. Cancellation robustness and immediate mutex release
 * 3. Graceful fallback on recycled bitmaps or ML Kit errors
 */
class OcrEngineTest {

    @Before
    fun setUp() {
        mockkStatic(InputImage::class)
        val mockImage = mockk<InputImage>()
        every { InputImage.fromBitmap(any(), any()) } returns mockImage
    }

    @After
    fun tearDown() {
        unmockkStatic(InputImage::class)
    }

    @Test
    fun testOcrEngineRecycledBitmapReturnsEmptyImmediately() = runTest {
        val recycledBitmap = mockk<Bitmap>()
        every { recycledBitmap.isRecycled } returns true

        val result = OcrEngine.recognizeText(recycledBitmap)
        assertEquals("", result)
    }

    private fun injectMockRecognizer(mockRecognizer: TextRecognizer): Any? {
        val delegateField = OcrEngine::class.java.getDeclaredField("recognizer\$delegate").apply { isAccessible = true }
        val lazyInstance = delegateField.get(null) ?: return null
        val valueField = lazyInstance::class.java.getDeclaredField("_value").apply { isAccessible = true }
        val previousValue = valueField.get(lazyInstance)
        valueField.set(lazyInstance, mockRecognizer)
        return lazyInstance to previousValue
    }

    private fun restoreMockRecognizer(state: Any?) {
        if (state == null) return
        @Suppress("UNCHECKED_CAST")
        val pair = state as Pair<Any, Any?>
        val lazyInstance = pair.first
        val previousValue = pair.second
        val valueField = lazyInstance::class.java.getDeclaredField("_value").apply { isAccessible = true }
        valueField.set(lazyInstance, previousValue)
    }

    @Test
    fun testOcrEngineSerializedConcurrencyUnderHighLoad() = runTest {
        val mockRecognizer = mockk<TextRecognizer>(relaxed = true)
        mockkStatic(TextRecognition::class)
        every { TextRecognition.getClient(any()) } returns mockRecognizer
        val realText = Text("Scanned Invoice Text", emptyList<Any>())

        val activeCallers = AtomicInteger(0)
        val peakConcurrent = AtomicInteger(0)
        val totalCompleted = AtomicInteger(0)

        class SuccessfulOcrTask(private val resultText: Text) : Task<Text>() {
            override fun isComplete(): Boolean = true
            override fun isSuccessful(): Boolean = true
            override fun isCanceled(): Boolean = false
            override fun getResult(): Text = resultText
            override fun <X : Throwable?> getResult(p0: Class<X>): Text = resultText
            override fun getException(): java.lang.Exception? = null
            override fun addOnSuccessListener(listener: OnSuccessListener<in Text>): Task<Text> {
                listener.onSuccess(resultText)
                return this
            }
            override fun addOnSuccessListener(executor: java.util.concurrent.Executor, listener: OnSuccessListener<in Text>): Task<Text> {
                listener.onSuccess(resultText)
                return this
            }
            override fun addOnSuccessListener(activity: android.app.Activity, listener: OnSuccessListener<in Text>): Task<Text> {
                listener.onSuccess(resultText)
                return this
            }
            override fun addOnFailureListener(listener: OnFailureListener): Task<Text> = this
            override fun addOnFailureListener(executor: java.util.concurrent.Executor, listener: OnFailureListener): Task<Text> = this
            override fun addOnFailureListener(activity: android.app.Activity, listener: OnFailureListener): Task<Text> = this
            override fun addOnCanceledListener(listener: OnCanceledListener): Task<Text> = this
            override fun addOnCanceledListener(executor: java.util.concurrent.Executor, listener: OnCanceledListener): Task<Text> = this
            override fun addOnCanceledListener(activity: android.app.Activity, listener: OnCanceledListener): Task<Text> = this
        }

        every { mockRecognizer.process(any<InputImage>()) } answers {
            val current = activeCallers.incrementAndGet()
            peakConcurrent.updateAndGet { prev -> maxOf(prev, current) }

            Thread.sleep(10)

            activeCallers.decrementAndGet()
            SuccessfulOcrTask(realText)
        }

        val originalDelegate = injectMockRecognizer(mockRecognizer)

        val bitmaps = (1..20).map {
            mockk<Bitmap>().apply { every { isRecycled } returns false }
        }

        try {
            val jobs = bitmaps.map { bmp ->
                async(Dispatchers.Default) {
                    val text = OcrEngine.recognizeText(bmp, 0)
                    assertEquals("Scanned Invoice Text", text)
                    totalCompleted.incrementAndGet()
                }
            }

            jobs.awaitAll()

            assertEquals(20, totalCompleted.get())
            assertEquals("Mutex must strictly serialize access to ML Kit recognizer to avoid OOM", 1, peakConcurrent.get())
        } finally {
            restoreMockRecognizer(originalDelegate)
        }
    }

    @Test
    fun testOcrEngineCancellationReleasesMutexSafely() = runTest {
        val mockRecognizer = mockk<TextRecognizer>(relaxed = true)
        val mockTask = mockk<Task<Text>>(relaxed = true)
        val mockText = mockk<Text>(relaxed = true)
        every { mockText.text } returns "Normal Flow Text"

        every { mockRecognizer.process(any<InputImage>()) } returns mockTask
        every { mockTask.addOnSuccessListener(any()) } answers {
            val listener = firstArg<OnSuccessListener<Text>>()
            listener.onSuccess(mockText)
            mockTask
        }
        every { mockTask.addOnFailureListener(any()) } returns mockTask
        every { mockTask.addOnCanceledListener(any()) } returns mockTask

        val originalDelegate = injectMockRecognizer(mockRecognizer)

        try {
            val validBitmap = mockk<Bitmap>()
            every { validBitmap.isRecycled } returns false

            val cancelledJob = launch(Dispatchers.Default) {
                OcrEngine.recognizeText(validBitmap, 0)
            }
            cancelledJob.cancelAndJoin()

            val subsequentResult = OcrEngine.recognizeText(validBitmap, 0)
            assertEquals("Normal Flow Text", subsequentResult)
        } finally {
            restoreMockRecognizer(originalDelegate)
        }
    }

    @Test
    fun testOcrEngineFailureSafelyReturnsEmptyWithoutCrash() = runTest {
        val mockRecognizer = mockk<TextRecognizer>(relaxed = true)
        val mockTask = mockk<Task<Text>>(relaxed = true)

        every { mockRecognizer.process(any<InputImage>()) } returns mockTask
        every { mockTask.addOnSuccessListener(any()) } returns mockTask
        every { mockTask.addOnFailureListener(any()) } answers {
            val listener = firstArg<OnFailureListener>()
            listener.onFailure(RuntimeException("ML Kit failure"))
            mockTask
        }
        every { mockTask.addOnCanceledListener(any()) } returns mockTask

        val originalDelegate = injectMockRecognizer(mockRecognizer)

        try {
            val validBitmap = mockk<Bitmap>()
            every { validBitmap.isRecycled } returns false

            val result = OcrEngine.recognizeText(validBitmap, 0)
            assertEquals("", result)
        } finally {
            restoreMockRecognizer(originalDelegate)
        }
    }
}
