package com.ku.lostandfound.ui.post.screen

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

private val DeepGreen = Color(0xFF1B6425)
private val FieldGray = Color(0xFFF4F4F4)
private val TextGray = Color(0xFF8A8A8A)
private val LightGreen = Color(0xFFE4F6E5)

@Composable
fun PostDetailScreen(
    post: BoardPost,
    boundary: CampusBoundary,
    buildings: List<CampusBuilding>,
    referencePaths: List<CampusPath> = emptyList(),
    onBackClick: () -> Unit,
    showOwnerActions: Boolean = false,
    onToggleResolvedClick: (BoardPost) -> Unit = {},
    comments: List<BoardComment> = emptyList(),
    currentUserName: String = "김건국",
    currentUserEmail: String = "konkuk26@konkuk.ac.kr",
    onAddComment: (String) -> Unit = {},
    matchCandidateTexts: List<String> = emptyList(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
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

            if (matchCandidateTexts.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                MatchCandidateSection(matchCandidateTexts)
            }

            if (showOwnerActions) {
                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { onToggleResolvedClick(post) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (post.status == PostStatus.OPEN) DeepGreen else Color(0xFF777777),
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
                            "미해결로 다시 변경"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            CommentSection(
                comments = comments,
                postAuthorEmail = post.authorEmail
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
private fun MatchCandidateSection(candidates: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "위치 매칭 후보",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            candidates.forEach { candidate ->
                Text(
                    text = candidate,
                    color = Color(0xFF444444),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FieldGray, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PostImageBox(imageUri: String?) {
    val context = LocalContext.current
    val bitmap = remember(imageUri) {
        imageUri?.let { uriString ->
            runCatching {
                context.contentResolver.openInputStream(android.net.Uri.parse(uriString)).use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(FieldGray),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "게시글 사진",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = if (imageUri == null) "사진 영역" else "등록된 사진",
                color = Color.Gray
            )
        }
    }
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
    postAuthorEmail: String,
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

        if (comments.isEmpty()) {
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
                        isPostAuthor = comment.authorEmail == postAuthorEmail
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
        }
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
