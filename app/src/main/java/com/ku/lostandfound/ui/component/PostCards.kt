package com.ku.lostandfound.ui.component

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.data.BoardPost
import com.ku.lostandfound.data.PostStatus
import com.ku.lostandfound.data.PostType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.net.URL

private val DeepGreen = Color(0xFF1B6425)
private val LightGreen = Color(0xFFE4F6E5)
private val Danger = Color(0xFFFF5D7A)
private val LightDanger = Color(0xFFFFECEF)
private val TextGray = Color(0xFF8A8A8A)
private val FieldGray = Color(0xFFF4F4F4)

@Composable
fun PostTypeBadge(type: PostType, modifier: Modifier = Modifier) {
    val isFound = type == PostType.FOUND
    Text(
        text = if (isFound) "습득" else "분실",
        color = if (isFound) DeepGreen else Danger,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = modifier
            .background(if (isFound) LightGreen else LightDanger, RoundedCornerShape(5.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

@Composable
fun PostStatusBadge(status: PostStatus, modifier: Modifier = Modifier) {
    val solved = status == PostStatus.RESOLVED
    Text(
        text = if (solved) "해결됨" else "미해결",
        color = if (solved) Color(0xFF777777) else DeepGreen,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = modifier
            .background(if (solved) Color(0xFFEDEDED) else LightGreen, RoundedCornerShape(5.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

@Composable
fun CompactPostCard(
    post: BoardPost,
    modifier: Modifier = Modifier,
    showThumbnail: Boolean = false,
    showStatus: Boolean = false,
    showResolveButton: Boolean = false,
    onClick: () -> Unit,
    onToggleResolved: (() -> Unit)? = null,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .noRippleClickable(onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PostTypeBadge(post.type)
                    if (showStatus) {
                        Spacer(Modifier.size(6.dp))
                        PostStatusBadge(post.status)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(post.createdAtText, color = TextGray, fontSize = 12.sp)
                }

                Spacer(Modifier.height(10.dp))
                Text(post.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "◇ ${post.category}",
                    color = TextGray,
                    fontSize = 12.sp,
                )

                if (showResolveButton && onToggleResolved != null) {
                    val canResolve = post.status == PostStatus.OPEN
                    val buttonModifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (canResolve) LightGreen else Color(0xFFEDEDED))
                        .let { modifier ->
                            if (canResolve) modifier.noRippleClickable { onToggleResolved() } else modifier
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (canResolve) "해결 처리" else "해결 완료",
                            color = if (canResolve) DeepGreen else TextGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = buttonModifier,
                        )
                    }
                }
            }

            if (showThumbnail) {
                Spacer(Modifier.size(12.dp))
                ThumbnailBox(imageUri = post.imageUri)
            }
        }
    }
}

@Composable
private fun ThumbnailBox(imageUri: String?) {
    val imageBitmap = rememberThumbnailBitmap(imageUri)
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (imageUri != null) Color(0xFFE7E7E7) else FieldGray),
        contentAlignment = Alignment.Center,
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "게시글 사진",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(58.dp),
            )
        } else if (imageUri != null) {
            Text("IMG", color = TextGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun rememberThumbnailBitmap(imageUri: String?): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(imageUri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(imageUri) {
        bitmap = null
        if (imageUri.isNullOrBlank()) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val sourceBytes = if (imageUri.startsWith("http://") || imageUri.startsWith("https://")) {
                    URL(imageUri).openStream().use { stream -> stream.readBytes() }
                } else {
                    context.contentResolver.openInputStream(android.net.Uri.parse(imageUri)).use { stream ->
                        stream?.readBytes()
                    }
                }
                sourceBytes?.toOrientedImageBitmap()
            }.getOrNull()
        }
    }

    return bitmap
}

private fun ByteArray.toOrientedImageBitmap(): ImageBitmap? {
    val decoded = BitmapFactory.decodeByteArray(this, 0, size) ?: return null
    val oriented = decoded.applyExifOrientation(this)
    val imageBitmap = oriented.asImageBitmap()
    if (oriented !== decoded) decoded.recycle()
    return imageBitmap
}

private fun Bitmap.applyExifOrientation(sourceBytes: ByteArray): Bitmap {
    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(sourceBytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        else -> return this
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

@Composable
fun PostListCard(
    post: BoardPost,
    modifier: Modifier = Modifier,
    showStatus: Boolean = true,
    onClick: () -> Unit,
) {
    CompactPostCard(
        post = post,
        modifier = modifier,
        showThumbnail = true,
        showStatus = showStatus,
        onClick = onClick,
    )
}
