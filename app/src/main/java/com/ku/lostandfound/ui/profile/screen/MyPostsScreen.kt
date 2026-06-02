package com.ku.lostandfound.ui.profile.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.network.MyFoundItemData
import com.ku.lostandfound.network.MyLostItemData
import com.ku.lostandfound.ui.component.GetBackTopAppBar
import com.ku.lostandfound.ui.profile.viewmodel.MyPostsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.net.URL

private val ScreenGray = Color(0xFFF4F4F4)
private val TextGray = Color(0xFF777777)
private val DeepGreen = Color(0xFF1B6425)

enum class MyPostsType {
    LOST,
    FOUND,
}

@Composable
fun MyPostsScreen(
    type: MyPostsType,
    lostItems: List<MyLostItemData>,
    foundItems: List<MyFoundItemData>,
    uiState: MyPostsUiState,
    onBackClick: () -> Unit,
    onLostItemClick: (MyLostItemData) -> Unit,
    onFoundItemClick: (MyFoundItemData) -> Unit,
) {
    val isLost = type == MyPostsType.LOST
    val title = if (isLost) "나의 분실물 등록 내역" else "나의 습득물 등록 내역"
    val emptyText = if (isLost) "등록한 분실물이 없습니다." else "등록한 습득물이 없습니다."

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenGray),
    ) {
        GetBackTopAppBar(isWhite = true, title = title, onClick = onBackClick)

        when (uiState) {
            MyPostsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("불러오는 중입니다.", color = TextGray, fontSize = 14.sp)
                }
            }
            is MyPostsUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(uiState.message, color = Color(0xFFB3261E), fontSize = 14.sp)
                }
            }
            MyPostsUiState.Idle -> {
                if (isLost) {
                    MyLostItemsList(
                        items = lostItems,
                        emptyText = emptyText,
                        onItemClick = onLostItemClick,
                    )
                } else {
                    MyFoundItemsList(
                        items = foundItems,
                        emptyText = emptyText,
                        onItemClick = onFoundItemClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun MyLostItemsList(
    items: List<MyLostItemData>,
    emptyText: String,
    onItemClick: (MyLostItemData) -> Unit,
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = TextGray, fontSize = 14.sp)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items) { item ->
            MyPostListCard(
                imageUrl = item.imageUrl,
                title = item.title,
                kind = item.kind,
                itemStatus = item.itemStatus,
                createdAt = item.createdAt,
                onClick = { onItemClick(item) },
            )
        }
    }
}

@Composable
private fun MyFoundItemsList(
    items: List<MyFoundItemData>,
    emptyText: String,
    onItemClick: (MyFoundItemData) -> Unit,
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = TextGray, fontSize = 14.sp)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items) { item ->
            MyPostListCard(
                imageUrl = item.imageUrl,
                title = item.title,
                kind = item.kind,
                itemStatus = item.itemStatus,
                createdAt = item.createdAt,
                locationType = item.locationType,
                associatedBuildingNames = item.associatedBuildingNames,
                onClick = { onItemClick(item) },
            )
        }
    }
}

@Composable
private fun MyPostListCard(
    imageUrl: String?,
    title: String,
    kind: String,
    itemStatus: String,
    createdAt: String,
    locationType: String? = null,
    associatedBuildingNames: List<String> = emptyList(),
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NetworkImage(imageUrl = imageUrl)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = kind,
                        color = DeepGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color(0xFFE4F6E5), RoundedCornerShape(5.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(statusLabel(itemStatus), color = TextGray, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text(createdAt.take(10), color = TextGray, fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text(title, color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (locationType != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = listOf(locationType, associatedBuildingNames.joinToString(", "))
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        color = TextGray,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkImage(imageUrl: String?) {
    var imageBitmap by remember(imageUrl) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(imageUrl) {
        imageBitmap = null
        if (imageUrl.isNullOrBlank()) return@LaunchedEffect
        imageBitmap = withContext(Dispatchers.IO) {
            runCatching {
                URL(imageUrl).openStream().use { stream ->
                    stream.readBytes().toOrientedImageBitmap()
                }
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFEDEDED)),
        contentAlignment = Alignment.Center,
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text("IMG", color = TextGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
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

private fun statusLabel(status: String): String {
    return when (status) {
        "SEARCHING" -> "찾는 중"
        "RETURNED" -> "반환 완료"
        else -> status
    }
}
