package com.ku.lostandfound.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ku.lostandfound.network.TokenManager
import com.ku.lostandfound.ui.component.ThumbnailLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ThumbnailLoaderTest {
    @Test fun repeatedLoadsReuseDecodedThumbnailAndClearInvalidatesIt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("thumbnail-regression", ".png", context.cacheDir)
        val source = Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888)
        try {
            ThumbnailLoader.clear()
            file.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val session = TokenManager.sessionGeneration()
            val uri = file.toURI().toString()
            val first = ThumbnailLoader.load(context, uri, session)!!
            assertEquals(512, first.width)
            assertEquals(256, first.height)
            println("PERF thumbnail source_bytes=${source.allocationByteCount} decoded_bytes=${first.asAndroidBitmap().allocationByteCount}")
            assertTrue(file.delete())
            // With the source gone, only a cache hit can succeed.
            repeat(10) {
                assertSame(first.asAndroidBitmap(), ThumbnailLoader.load(context, uri, session)!!.asAndroidBitmap())
            }
            assertNull(ThumbnailLoader.load(context, uri, session - 1))
            ThumbnailLoader.clear()
            assertNull(runCatching { ThumbnailLoader.load(context, uri, session) }.getOrNull())
        } finally {
            source.recycle()
            file.delete()
            ThumbnailLoader.clear()
        }
    }
}
