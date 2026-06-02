package com.ku.lostandfound.ui.post.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.ui.component.BackTitleBar
import com.ku.lostandfound.ui.component.GreenSegmentedSwitch
import com.ku.lostandfound.viewmodel.PostWriteUiState
import com.ku.lostandfound.viewmodel.PostWriteViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.net.URL

private val DeepGreen = Color(0xFF1B6425)
private val FieldGray = Color(0xFFF4F4F4)
private val HintGray = Color(0xFF9A9A9A)

@Composable
fun PostWriteScreen(
    viewModel: PostWriteViewModel,
    onBackClick: () -> Unit,
    onAddLocationClick: (PostType) -> Unit,
    onSubmitClick: () -> Unit,
) {
    val uiState = viewModel.uiState
    val isLoading = uiState is PostWriteUiState.Loading
    val errorMessage = (uiState as? PostWriteUiState.Error)?.message
    val focusManager = LocalFocusManager.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        viewModel.imageUri = uri?.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
    ) {
        BackTitleBar(title = "새 글 쓰기", onBackClick = onBackClick)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 26.dp)
        ) {
            GreenSegmentedSwitch(
                leftText = "분실물 등록",
                rightText = "습득물 등록",
                selectedLeft = viewModel.postType == PostType.LOST,
                onLeftClick = { viewModel.changePostType(PostType.LOST) },
                onRightClick = { viewModel.changePostType(PostType.FOUND) },
            )

            Spacer(Modifier.height(34.dp))
            SectionLabel("사진 등록")
            PhotoBox(
                imageUri = viewModel.imageUri,
                onClick = { imagePickerLauncher.launch("image/*") },
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("제목")
            SoftTextField(
                value = viewModel.title,
                onValueChange = {
                    viewModel.title = it
                    if (uiState is PostWriteUiState.Error) viewModel.resetUiState()
                },
                placeholder = "예 ) 검은색 지갑을 찾습니다",
                singleLine = true,
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("분류")
            CategoryDropdown(
                selected = viewModel.category,
                onSelected = {
                    viewModel.category = it
                    if (uiState is PostWriteUiState.Error) viewModel.resetUiState()
                },
            )

            Spacer(Modifier.height(12.dp))
            SectionLabel("위치")
            LocationAddRow(
                text = locationSummary(viewModel),
                onClick = { onAddLocationClick(viewModel.postType) },
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("상세 내용")
            SoftTextField(
                value = viewModel.content,
                onValueChange = {
                    viewModel.content = it
                    if (uiState is PostWriteUiState.Error) viewModel.resetUiState()
                },
                placeholder = "ex )\n검은색 지갑을 공학관에서 잃어버렸습니다.\n바깥에 물결무늬가 있고, 오리 캐릭터가 달려있습니다.",
                minHeight = 184.dp,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            )

            Spacer(Modifier.height(20.dp))
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = Color(0xFFD32F2F),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            Button(
                onClick = onSubmitClick,
                enabled = viewModel.canSubmit() && !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepGreen,
                    disabledContainerColor = Color(0xFFD8D8D8),
                    contentColor = Color.White,
                    disabledContentColor = Color.White,
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(45.dp),
            ) {
                Text(
                    text = if (isLoading) "등록 중..." else "등록하기",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun PhotoBox(imageUri: String?, onClick: () -> Unit) {
    val imageBitmap = rememberPostImageBitmap(imageUri)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(184.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(FieldGray)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "선택한 사진",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("+", fontSize = 42.sp, color = Color(0xFF8A8A8A), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                Text("사진을 등록해주세요", color = HintGray, fontSize = 13.sp)
            }
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
private fun SoftTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minHeight: androidx.compose.ui.unit.Dp = 52.dp,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = HintGray, fontSize = 13.sp) },
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(9.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = FieldGray,
            unfocusedContainerColor = FieldGray,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = Color.Black,
            unfocusedTextColor = Color.Black,
            disabledTextColor = Color.Black,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(minHeight),
    )
}

@Composable
private fun CategoryDropdown(selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val categories = listOf("전자기기", "지갑/카드", "의류", "가방", "문서", "기타")

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(FieldGray, RoundedCornerShape(9.dp))
                .clickable { expanded = true }
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selected.ifBlank { "선택" },
                color = if (selected.isBlank()) Color(0xFF595959) else Color.Black,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Text("▼", color = Color(0xFF8E8E8E), fontSize = 20.sp)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Color.White,
        ) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category, color = Color.Black) },
                    onClick = {
                        onSelected(category)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun LocationAddRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(FieldGray, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, color = Color(0xFF777777), fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text("+", color = Color(0xFF8C8C8C), fontSize = 32.sp, fontWeight = FontWeight.Bold)
    }
}

private fun locationSummary(viewModel: PostWriteViewModel): String {
    return when (viewModel.postType) {
        PostType.LOST -> {
            val outdoor = viewModel.lostLocation.outdoorPins.size
            val indoor = viewModel.lostLocation.indoorPlaces.size
            when {
                outdoor == 0 && indoor == 0 -> "분실 위치 추가"
                outdoor > 0 && indoor > 0 -> "외부 ${outdoor}개 · 내부 ${indoor}개"
                outdoor > 0 -> "외부 경로 ${outdoor}개"
                else -> "내부 위치 ${indoor}개"
            }
        }
        PostType.FOUND -> {
            val found = viewModel.foundLocation
            when {
                found.outdoorPin != null -> "습득 위치: 외부 핀 1개"
                found.indoorPlace != null -> "습득 위치: ${found.indoorPlace.buildingName} ${floorText(found.indoorPlace.floor)}"
                else -> "습득 위치 추가"
            }
        }
    }
}

private fun floorText(floor: Int): String = if (floor < 0) "B${-floor}층" else "${floor}층"
