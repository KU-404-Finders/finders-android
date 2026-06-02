package com.ku.lostandfound.ui.post.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.data.BoardComment
import com.ku.lostandfound.data.BoardPost
import com.ku.lostandfound.data.CampusBoundary
import com.ku.lostandfound.data.CampusBuilding
import com.ku.lostandfound.data.CampusPath
import com.ku.lostandfound.data.FoundLocationSelection
import com.ku.lostandfound.data.LostLocationSelection
import com.ku.lostandfound.data.PostStatus
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.ui.component.BackTitleBar
import com.ku.lostandfound.ui.component.CampusMapCanvas
import com.ku.lostandfound.ui.component.PostStatusBadge
import com.ku.lostandfound.ui.component.PostTypeBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.net.URL

private val DeepGreen = Color(0xFF1B6425)
private val FieldGray = Color(0xFFF4F4F4)
private val TextGray = Color(0xFF8A8A8A)
private val LightGreen = Color(0xFFE4F6E5)

data class MatchCandidateUiModel(
    val id: String,
    val type: PostType,
    val title: String,
    val category: String,
    val status: PostStatus,
    val createdAtText: String,
    val imageUrl: String?,
    val locationScore: Double,
    val associatedBuildingNames: List<String> = emptyList(),
)

@Composable
fun PostDetailScreen(
    post: BoardPost,
    boundary: CampusBoundary,
    buildings: List<CampusBuilding>,
    referencePaths: List<CampusPath> = emptyList(),
    onBackClick: () -> Unit,
    showOwnerActions: Boolean = false,
    onToggleResolvedClick: (BoardPost) -> Unit = {},
    onDeleteClick: (BoardPost) -> Unit = {},
    comments: List<BoardComment> = emptyList(),
    currentUserName: String = "김건국",
    currentUserEmail: String = "konkuk26@konkuk.ac.kr",
    currentUserId: Long? = null,
    onAddComment: (String) -> Unit = {},
    matchCandidates: List<MatchCandidateUiModel> = emptyList(),
    isDetailLoading: Boolean = false,
    isMatchLoading: Boolean = false,
    isCommentsLoading: Boolean = false,
    onDeleteComment: (BoardComment) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    var showResolveConfirmDialog by remember { mutableStateOf(false) }

    if (showResolveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResolveConfirmDialog = false },
            title = {
                Text(
                    text = "해결 완료로 변경할까요?",
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )
            },
            text = {
                Text(
                    text = "해결 완료로 변경하면 미해결 상태로 되돌릴 수 없습니다.",
                    color = Color(0xFF555555),
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResolveConfirmDialog = false
                        onToggleResolvedClick(post)
                    }
                ) {
                    Text(
                        text = "해결 완료",
                        color = DeepGreen,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResolveConfirmDialog = false }) {
                    Text(text = "취소", color = TextGray)
                }
            },
            containerColor = Color.White,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
    ) {
        BackTitleBar(
            title = if (post.type == PostType.LOST) "분실물 상세" else "습득물 상세",
            onBackClick = onBackClick,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            if (isDetailLoading) {
                LoadingRow("게시글을 불러오는 중입니다.")
                Spacer(Modifier.height(14.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                PostTypeBadge(post.type)
                Spacer(Modifier.size(8.dp))
                PostStatusBadge(post.status)
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = post.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "${post.authorName} · ${post.createdAtText}",
                color = Color.Gray,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(18.dp))

            PostImageBox(imageUri = post.imageUri)

            Spacer(Modifier.height(12.dp))

            Text(
                text = post.content,
                color = Color(0xFF333333),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(18.dp))

            LocationPreviewCard(
                post = post,
                boundary = boundary,
                buildings = buildings,
                referencePaths = referencePaths,
            )

            if (isMatchLoading) {
                Spacer(Modifier.height(18.dp))
                LoadingRow("관련 게시물을 찾는 중입니다.")
            } else if (matchCandidates.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                MatchCandidateSection(matchCandidates.take(10))
            }

            if (showOwnerActions) {
                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { showResolveConfirmDialog = true },
                    enabled = post.status == PostStatus.OPEN,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (post.status == PostStatus.OPEN) DeepGreen else Color(0xFF777777),
                        disabledContainerColor = Color(0xFFD8D8D8),
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(
                        text = if (post.status == PostStatus.OPEN) {
                            "해결 완료로 변경"
                        } else {
                            "해결 완료됨"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = { onDeleteClick(post) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F),
                        disabledContainerColor = Color(0xFFD8D8D8),
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(
                        text = "게시글 삭제",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            CommentSection(
                comments = comments,
                postAuthorUserId = post.authorUserId,
                currentUserId = currentUserId,
                isLoading = isCommentsLoading,
                onDeleteComment = onDeleteComment,
            )

            Spacer(Modifier.height(20.dp))
        }

        CommentInputBar(
            currentUserName = currentUserName,
            onSubmit = onAddComment
        )
    }
}

@Composable
private fun MatchCandidateSection(candidates: List<MatchCandidateUiModel>) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "이 게시물인가요?",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.Black,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "위치가 비슷한 게시물을 확인해보세요.",
            color = TextGray,
            fontSize = 12.sp,
        )

        Spacer(Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(candidates, key = { it.id }) { candidate ->
                MatchCandidateCard(candidate)
            }
        }
    }
}

@Composable
private fun MatchCandidateCard(candidate: MatchCandidateUiModel) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.width(220.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PostTypeBadge(candidate.type)
                Spacer(Modifier.size(6.dp))
                PostStatusBadge(candidate.status)
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.Top) {
                CandidateThumbnail(hasImage = candidate.imageUrl != null)

                Spacer(Modifier.size(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = candidate.title,
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 18.sp,
                        maxLines = 2,
                    )

                    Spacer(Modifier.height(5.dp))

                    Text(
                        text = candidate.category,
                        color = TextGray,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "위치 유사도 ${"%.2f".format(candidate.locationScore)}",
                color = DeepGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(LightGreen, RoundedCornerShape(999.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )

            val locationText = candidate.associatedBuildingNames
                .take(2)
                .joinToString(", ")
                .ifBlank { "위치 정보 확인 필요" }

            Spacer(Modifier.height(8.dp))

            Text(
                text = locationText,
                color = Color(0xFF555555),
                fontSize = 12.sp,
                maxLines = 1,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = candidate.createdAtText,
                color = TextGray,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun CandidateThumbnail(hasImage: Boolean) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (hasImage) Color(0xFFE7E7E7) else FieldGray),
        contentAlignment = Alignment.Center,
    ) {
        if (hasImage) {
            Text(
                text = "IMG",
                color = TextGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun PostImageBox(imageUri: String?) {
    val imageBitmap = rememberPostImageBitmap(imageUri)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(FieldGray),
        contentAlignment = Alignment.Center,
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "게시글 사진",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = if (imageUri == null) "사진 영역" else "사진을 불러오는 중입니다.",
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun rememberPostImageBitmap(imageUri: String?): ImageBitmap? {
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
private fun LocationPreviewCard(
    post: BoardPost,
    boundary: CampusBoundary,
    buildings: List<CampusBuilding>,
    referencePaths: List<CampusPath>,
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "위치 정보",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Spacer(Modifier.height(10.dp))

            when (post.type) {
                PostType.LOST -> LostLocationView(
                    location = post.lostLocation,
                    boundary = boundary,
                    buildings = buildings,
                    referencePaths = referencePaths
                )

                PostType.FOUND -> FoundLocationView(
                    location = post.foundLocation,
                    boundary = boundary,
                    buildings = buildings,
                    referencePaths = referencePaths
                )
            }
        }
    }
}

@Composable
private fun LostLocationView(
    location: LostLocationSelection?,
    boundary: CampusBoundary,
    buildings: List<CampusBuilding>,
    referencePaths: List<CampusPath>,
) {
    val pins = location?.outdoorPins.orEmpty()

    if (pins.isNotEmpty()) {
        CampusMapCanvas(
            boundary = boundary,
            buildings = buildings,
            referencePaths = referencePaths,
            outdoorPins = pins,
            showRoute = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(8.dp)),
        )

        Spacer(Modifier.height(10.dp))
    }

    val places = location?.indoorPlaces.orEmpty()

    if (places.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            places.forEach { place ->
                LocationChip("${place.buildingName} ${place.floor}층")
            }
        }
    }

    if (pins.isEmpty() && places.isEmpty()) {
        Text(
            text = "등록된 위치가 없습니다.",
            color = Color.Gray,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun FoundLocationView(
    location: FoundLocationSelection?,
    boundary: CampusBoundary,
    buildings: List<CampusBuilding>,
    referencePaths: List<CampusPath>,
) {
    val pin = location?.outdoorPin
    val place = location?.indoorPlace

    if (pin != null) {
        CampusMapCanvas(
            boundary = boundary,
            buildings = buildings,
            referencePaths = referencePaths,
            outdoorPins = listOf(pin),
            showRoute = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
    }

    if (place != null) {
        if (pin != null) Spacer(Modifier.height(10.dp))
        LocationChip("${place.buildingName} ${place.floor}층")
    }

    if (pin == null && place == null) {
        Text(
            text = "등록된 위치가 없습니다.",
            color = Color.Gray,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun LocationChip(text: String) {
    Text(
        text = "📍 $text",
        fontSize = 12.sp,
        color = Color(0xFF666666),
        modifier = Modifier
            .background(FieldGray, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

@Composable
private fun CommentSection(
    comments: List<BoardComment>,
    postAuthorUserId: Long?,
    currentUserId: Long?,
    isLoading: Boolean,
    onDeleteComment: (BoardComment) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "댓글 ${comments.size}",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Spacer(Modifier.height(14.dp))

        if (isLoading) {
            LoadingRow("댓글을 불러오는 중입니다.")
        } else if (comments.isEmpty()) {
            Text(
                text = "아직 댓글이 없습니다.",
                color = TextGray,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                comments.forEach { comment ->
                    CommentItem(
                        comment = comment,
                        isPostAuthor = postAuthorUserId != null && comment.authorUserId == postAuthorUserId,
                        canDelete = currentUserId != null && comment.authorUserId == currentUserId,
                        onDeleteClick = { onDeleteComment(comment) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentItem(
    comment: BoardComment,
    isPostAuthor: Boolean,
    canDelete: Boolean,
    onDeleteClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFFEDEDED)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "♡",
                color = Color(0xFF9A9A9A),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.size(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = comment.authorName,
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                if (isPostAuthor) {
                    Spacer(Modifier.size(6.dp))

                    Text(
                        text = "작성자",
                        color = DeepGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(LightGreen, RoundedCornerShape(999.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(Modifier.size(8.dp))

                Text(
                    text = comment.createdAtText,
                    color = TextGray,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(7.dp))

            Text(
                text = comment.content,
                color = Color(0xFF444444),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            if (canDelete) {
                Spacer(Modifier.height(6.dp))

                Text(
                    text = "삭제",
                    color = TextGray,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable(onClick = onDeleteClick)
                )
            }
        }
    }
}

@Composable
private fun LoadingRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = DeepGreen,
        )
        Spacer(Modifier.size(10.dp))
        Text(text = text, color = TextGray, fontSize = 13.sp)
    }
}

@Composable
private fun CommentInputBar(
    currentUserName: String,
    onSubmit: (String) -> Unit,
) {
    var commentText by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = commentText,
            onValueChange = { commentText = it },
            placeholder = {
                Text(
                    text = "댓글을 입력해보세요.",
                    color = TextGray,
                    fontSize = 12.sp
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = FieldGray,
                unfocusedContainerColor = FieldGray,
                disabledContainerColor = FieldGray,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = DeepGreen,
                unfocusedTextColor = Color.Black,
                focusedTextColor = Color.Black
            ),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        )

        Spacer(Modifier.size(10.dp))

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (commentText.isBlank()) Color(0xFFB7CBB9) else DeepGreen
                )
                .clickable {
                    val text = commentText.trim()
                    if (text.isNotBlank()) {
                        onSubmit(text)
                        commentText = ""
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "➤",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
