package com.ku.lostandfound.ui.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.ku.lostandfound.network.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayInputStream
import java.net.URL

@Composable
internal fun rememberThumbnailBitmap(uri: String?): ImageBitmap? {
    val context = LocalContext.current
    val session = TokenManager.sessionGeneration()
    val revision = ThumbnailLoader.revision
    var bitmap by remember(uri, session, revision) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri, session, revision) {
        if (!uri.isNullOrBlank()) {
            bitmap = try {
                ThumbnailLoader.load(context.applicationContext, uri, session)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }
    return bitmap
}

/** Small list thumbnails only. Detail/zoom images keep their full-resolution loader. */
internal object ThumbnailLoader {
    var revision by mutableIntStateOf(0)
        private set
    private data class Key(val session: Long, val uri: String, val revision: Int)
    private val cache = object : LruCache<Key, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: Key, value: Bitmap): Int = value.allocationByteCount
    }

    fun clear() {
        cache.evictAll()
        revision++
    }

    suspend fun load(context: Context, uri: String, session: Long): ImageBitmap? = withContext(Dispatchers.IO) {
        if (!TokenManager.isCurrentSession(session)) return@withContext null
        val key = Key(session, uri, revision)
        cache.get(key)?.let { return@withContext it.asImageBitmap() }
        val bytes = if (uri.startsWith("http://") || uri.startsWith("https://")) {
            URL(uri).openConnection().apply {
                connectTimeout = 10_000
                readTimeout = 10_000
            }.getInputStream().use { it.readBytes() }
        } else {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
        } ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = thumbnailSampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@withContext null
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        val bitmap = if (matrix.isIdentity) decoded else {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                if (it !== decoded) decoded.recycle()
            }
        }
        if (!TokenManager.isCurrentSession(session) || key.revision != revision) return@withContext null
        cache.put(key, bitmap)
        bitmap.asImageBitmap()
    }
}

internal fun thumbnailSampleSize(width: Int, height: Int): Int {
    var sample = 1
    while (maxOf(width, height) / sample > 512) sample *= 2
    return sample
}
