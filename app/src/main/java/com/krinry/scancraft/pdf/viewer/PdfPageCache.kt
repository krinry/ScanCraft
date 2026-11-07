package com.krinry.scancraft.pdf.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.krinry.scancraft.data.model.Page
import java.io.File

class PdfPageCache(
    private val context: Context,
    private val scope: CoroutineScope,
    maxPagesInMemory: Int = 4
) {
    // High-resolution bitmap LRU Cache
    private val lruCache = object : LruCache<Long, Bitmap>(maxPagesInMemory) {
        override fun entryRemoved(evicted: Boolean, key: Long, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && oldValue != newValue && !oldValue.isRecycled) {
                oldValue.recycle()
            }
        }
    }

    // Active loading jobs to prevent duplicate disk reads
    private val loadingJobs = mutableMapOf<Long, Job>()

    fun getCachedBitmap(pageId: Long): Bitmap? = lruCache.get(pageId)

    /**
     * Loads high-res bitmap for [page]. If cached, immediately invokes [onLoaded].
     * Otherwise decodes on Dispatchers.IO and delivers on Dispatchers.Main.
     */
    fun loadPageBitmap(
        page: Page,
        onLoaded: (Bitmap) -> Unit
    ) {
        val cached = lruCache.get(page.id)
        if (cached != null && !cached.isRecycled) {
            onLoaded(cached)
            return
        }

        // Avoid duplicate concurrent decode jobs
        if (loadingJobs[page.id]?.isActive == true) return

        loadingJobs[page.id] = scope.launch(Dispatchers.IO) {
            try {
                val file = listOf(File(page.processedImagePath), File(page.originalImagePath))
                    .firstOrNull { it.exists() && it.length() > 0 } ?: return@launch

                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bmp = BitmapFactory.decodeFile(file.absolutePath, options) ?: return@launch

                synchronized(lruCache) {
                    lruCache.put(page.id, bmp)
                }

                withContext(Dispatchers.Main) {
                    onLoaded(bmp)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                loadingJobs.remove(page.id)
            }
        }
    }

    /**
     * Prefetches adjacent pages into LRU cache to ensure instantaneous rendering
     * when the user scrolls up or down.
     */
    fun prefetchAdjacentPages(pages: List<Page>, currentIndex: Int) {
        val nextIndices = listOf(currentIndex - 1, currentIndex + 1, currentIndex + 2)
            .filter { it in pages.indices }

        for (idx in nextIndices) {
            val p = pages[idx]
            if (lruCache.get(p.id) == null && loadingJobs[p.id]?.isActive != true) {
                loadingJobs[p.id] = scope.launch(Dispatchers.IO) {
                    try {
                        val file = listOf(File(p.processedImagePath), File(p.originalImagePath))
                            .firstOrNull { it.exists() && it.length() > 0 } ?: return@launch
                        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return@launch
                        synchronized(lruCache) {
                            lruCache.put(p.id, bmp)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        loadingJobs.remove(p.id)
                    }
                }
            }
        }
    }

    fun clear() {
        loadingJobs.values.forEach { it.cancel() }
        loadingJobs.clear()
        lruCache.evictAll()
    }
}
