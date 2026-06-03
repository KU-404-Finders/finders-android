package com.ku.lostandfound.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.media.ExifInterface
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.ku.lostandfound.data.BoardPost
import com.ku.lostandfound.data.FoundLocationSelection
import com.ku.lostandfound.data.GeoPoint
import com.ku.lostandfound.data.IndoorPlace
import com.ku.lostandfound.data.LostLocationSelection
import com.ku.lostandfound.data.OutdoorPin
import com.ku.lostandfound.data.PostStatus
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.network.FoundItemCreateRequest
import com.ku.lostandfound.network.FoundItemDetailData
import com.ku.lostandfound.network.FoundItemErrorResponse
import com.ku.lostandfound.network.FoundItemLocationRequest
import com.ku.lostandfound.network.FoundItemLocationType
import com.ku.lostandfound.network.ItemStatus
import com.ku.lostandfound.network.LostItemCreateRequest
import com.ku.lostandfound.network.LostItemData
import com.ku.lostandfound.network.LostItemErrorResponse
import com.ku.lostandfound.network.LostItemIndoorRequest
import com.ku.lostandfound.network.LostItemOutdoorRequest
import com.ku.lostandfound.network.NetworkLog
import com.ku.lostandfound.network.RetrofitClient
import com.ku.lostandfound.network.TokenManager
import com.ku.lostandfound.network.httpErrorMessage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

sealed class PostWriteUiState {
    object Idle : PostWriteUiState()
    object Loading : PostWriteUiState()
    data class Error(val message: String) : PostWriteUiState()
}

class PostWriteViewModel {
    var postType by mutableStateOf(PostType.FOUND)
        private set
    var title by mutableStateOf("")
    var category by mutableStateOf("")
    var content by mutableStateOf("")
    var imageUri by mutableStateOf<String?>(null)
    var uiState by mutableStateOf<PostWriteUiState>(PostWriteUiState.Idle)
        private set

    var lostLocation by mutableStateOf(LostLocationSelection())
        private set
    var foundLocation by mutableStateOf(FoundLocationSelection())
        private set

    fun changePostType(type: PostType) {
        postType = type
    }

    fun setLostOutdoorPins(points: List<GeoPoint>) {
        lostLocation = lostLocation.copy(
            outdoorPins = points.take(10).mapIndexed { index, point ->
                OutdoorPin(order = index + 1, point = point)
            }
        )
    }

    fun setLostIndoorPlaces(places: List<IndoorPlace>) {
        lostLocation = lostLocation.copy(
            indoorPlaces = places
                .distinctBy { it.buildingId to it.floor }
                .take(3)
        )
    }

    fun setFoundOutdoorPin(point: GeoPoint?) {
        foundLocation = foundLocation.copy(
            outdoorPin = point?.let { OutdoorPin(order = 1, point = it) },
            indoorPlace = null,
        )
    }

    fun setFoundIndoorPlace(place: IndoorPlace?) {
        foundLocation = foundLocation.copy(
            outdoorPin = null,
            indoorPlace = place,
        )
    }

    fun hasLocation(): Boolean = when (postType) {
        PostType.LOST -> lostLocation.outdoorPins.isNotEmpty() || lostLocation.indoorPlaces.isNotEmpty()
        PostType.FOUND -> foundLocation.outdoorPin != null || foundLocation.indoorPlace != null
    }

    fun canSubmit(): Boolean {
        val hasRequiredImage = postType != PostType.FOUND || imageUri != null
        return title.isNotBlank() && category.isNotBlank() && content.isNotBlank() && hasLocation() && hasRequiredImage
    }

    suspend fun createLostItem(context: Context): Result<BoardPost> {
        uiState = PostWriteUiState.Loading
        return try {
            val response = RetrofitClient.lostItemApi.createLostItem(
                authorization = bearerTokenOrThrow(),
                request = createJsonPart(toLostItemCreateRequest()),
                image = imageUri?.let { createImagePart(context, it).part },
            )

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    uiState = PostWriteUiState.Idle
                    Result.success(body.data.toBoardPost())
                } else {
                    Result.failure(IllegalStateException(body?.message ?: "분실물 등록에 실패했습니다."))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                NetworkLog.httpError("createLostItem", response.code(), errorBody)
                Result.failure(IllegalStateException(httpErrorMessage(response.code(), parseLostItemError(errorBody))))
            }
        } catch (e: Exception) {
            NetworkLog.exception("createLostItem", e)
            Result.failure(e)
        }.also { result ->
            result.exceptionOrNull()?.let { error ->
                uiState = PostWriteUiState.Error(error.message ?: "서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    suspend fun createFoundItem(context: Context): Result<BoardPost> {
        uiState = PostWriteUiState.Loading
        return try {
            val token = TokenManager.accessToken ?: throw IllegalStateException("로그인이 필요합니다.")
            val requestJson = Gson().toJson(toFoundItemCreateRequest())
            val requestBody = createJsonPart(requestJson)
            val uploadImage = imageUri?.let { createImagePart(context, it) }
                ?: throw IllegalStateException("습득물 사진은 필수입니다.")

            Log.d(FOUND_ITEM_UPLOAD_TAG, "requestJson=$requestJson")
            Log.d(FOUND_ITEM_UPLOAD_TAG, "imageUri=${uploadImage.uri}")
            Log.d(FOUND_ITEM_UPLOAD_TAG, "mimeType=${uploadImage.mimeType}")
            Log.d(FOUND_ITEM_UPLOAD_TAG, "fileName=${uploadImage.fileName}")
            Log.d(FOUND_ITEM_UPLOAD_TAG, "byteSize=${uploadImage.byteSize}")
            Log.d(FOUND_ITEM_UPLOAD_TAG, "requestPartName=request")
            Log.d(FOUND_ITEM_UPLOAD_TAG, "imagePartName=image")
            Log.d(FOUND_ITEM_UPLOAD_TAG, "hasAuthorization=${token.isNotBlank()}")

            val response = RetrofitClient.foundItemApi.createFoundItem(
                authorization = "Bearer $token",
                request = requestBody,
                image = uploadImage.part,
            )
            Log.d(FOUND_ITEM_UPLOAD_TAG, "responseCode=${response.code()}")

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    uiState = PostWriteUiState.Idle
                    Result.success(body.data.toBoardPost())
                } else {
                    Result.failure(IllegalStateException(body?.message ?: "습득물 등록에 실패했습니다."))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(FOUND_ITEM_UPLOAD_TAG, "errorBody=$errorBody")
                NetworkLog.httpError("createFoundItem", response.code(), errorBody)
                Result.failure(IllegalStateException(httpErrorMessage(response.code(), parseFoundItemError(errorBody))))
            }
        } catch (e: Exception) {
            NetworkLog.exception("createFoundItem", e)
            Result.failure(e)
        }.also { result ->
            result.exceptionOrNull()?.let { error ->
                uiState = PostWriteUiState.Error(error.message ?: "서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    fun resetUiState() {
        uiState = PostWriteUiState.Idle
    }

    fun loadForEdit(post: BoardPost) {
        postType = post.type
        title = post.title
        category = post.category
        content = post.content
        imageUri = post.imageUri
        lostLocation = post.lostLocation ?: LostLocationSelection()
        foundLocation = post.foundLocation ?: FoundLocationSelection()
        uiState = PostWriteUiState.Idle
    }

    suspend fun updatePost(context: Context, id: Long): Result<BoardPost> {
        uiState = PostWriteUiState.Loading
        return if (postType == PostType.LOST) {
            updateLostItem(context, id)
        } else {
            updateFoundItem(context, id)
        }.also { result ->
            result.exceptionOrNull()?.let { error ->
                uiState = PostWriteUiState.Error(error.message ?: "서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    fun reset() {
        postType = PostType.FOUND
        title = ""
        category = ""
        content = ""
        imageUri = null
        uiState = PostWriteUiState.Idle
        lostLocation = LostLocationSelection()
        foundLocation = FoundLocationSelection()
    }

    private fun toLostItemCreateRequest(): LostItemCreateRequest {
        return LostItemCreateRequest(
            title = title.trim(),
            kind = category.trim(),
            content = content.trim(),
            indoorSpots = lostLocation.indoorPlaces.map {
                LostItemIndoorRequest(
                    buildingName = it.buildingName,
                    floor = it.floor,
                )
            },
            outdoorPoints = lostLocation.outdoorPins.map {
                LostItemOutdoorRequest(
                    latitude = it.point.latitude,
                    longitude = it.point.longitude,
                )
            },
        )
    }

    private fun toFoundItemCreateRequest(): FoundItemCreateRequest {
        return FoundItemCreateRequest(
            title = title.trim(),
            kind = category.trim(),
            content = content.trim(),
            location = toFoundItemLocationRequest(),
        )
    }

    private fun toFoundItemLocationRequest(): FoundItemLocationRequest {
        foundLocation.indoorPlace?.let {
            return FoundItemLocationRequest(
                type = FoundItemLocationType.INDOOR,
                buildingName = it.buildingName,
                floor = it.floor,
            )
        }

        val outdoorPin = foundLocation.outdoorPin
            ?: throw IllegalStateException("습득 위치를 선택해주세요.")
        return FoundItemLocationRequest(
            type = FoundItemLocationType.OUTDOOR,
            latitude = outdoorPin.point.latitude,
            longitude = outdoorPin.point.longitude,
        )
    }

    private fun createImagePart(context: Context, uriString: String): UploadImage {
        val uri = Uri.parse(uriString)
        val contentResolver = context.contentResolver
        val sourceBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("이미지를 읽을 수 없습니다.")

        require(sourceBytes.isNotEmpty()) {
            "이미지를 읽을 수 없습니다."
        }

        val sourceMimeType = contentResolver.getType(uri)
        val canUploadOriginal = sourceMimeType in ALLOWED_IMAGE_MIME_TYPES &&
            sourceBytes.size <= MAX_IMAGE_BYTES &&
            !sourceBytes.hasExifRotation()
        val uploadMimeType: String
        val uploadBytes: ByteArray
        val extension: String

        if (canUploadOriginal) {
            uploadMimeType = sourceMimeType ?: UPLOAD_IMAGE_MIME_TYPE
            uploadBytes = sourceBytes
            extension = uploadMimeType.fileExtension()
        } else {
            uploadMimeType = UPLOAD_IMAGE_MIME_TYPE
            uploadBytes = sourceBytes.toUploadJpegBytes()
            extension = "jpg"
        }

        require(uploadBytes.size <= MAX_IMAGE_BYTES) {
            "이미지는 최대 10MB까지 등록할 수 있습니다."
        }
        require(uploadMimeType in ALLOWED_IMAGE_MIME_TYPES) {
            "JPEG, PNG, WEBP 이미지만 등록할 수 있습니다."
        }

        val requestBody = uploadBytes.toRequestBody(uploadMimeType.toMediaTypeOrNull())
        val fileLabel = if (postType == PostType.FOUND) "found-item" else "lost-item"
        val fileName = "${fileLabel}_${System.currentTimeMillis()}.$extension"
        return UploadImage(
            uri = uri,
            mimeType = uploadMimeType,
            fileName = fileName,
            byteSize = uploadBytes.size,
            part = MultipartBody.Part.createFormData(
                name = "image",
                filename = fileName,
                body = requestBody,
            ),
        )
    }

    private suspend fun updateLostItem(context: Context, id: Long): Result<BoardPost> {
        return try {
            val response = RetrofitClient.lostItemApi.updateLostItem(
                authorization = bearerTokenOrThrow(),
                id = id,
                request = createJsonPart(toLostItemCreateRequest()),
                image = imageUri?.takeIf { it.isLocalImageUri() }?.let { createImagePart(context, it).part },
            )
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    uiState = PostWriteUiState.Idle
                    Result.success(body.data.toBoardPost())
                } else {
                    Result.failure(IllegalStateException(body?.message ?: "분실물 수정에 실패했습니다."))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                NetworkLog.httpError("updateLostItem", response.code(), errorBody)
                Result.failure(IllegalStateException(httpErrorMessage(response.code(), parseLostItemError(errorBody))))
            }
        } catch (e: Exception) {
            NetworkLog.exception("updateLostItem", e)
            Result.failure(e)
        }
    }

    private suspend fun updateFoundItem(context: Context, id: Long): Result<BoardPost> {
        return try {
            val response = RetrofitClient.foundItemApi.updateFoundItem(
                authorization = bearerTokenOrThrow(),
                id = id,
                request = createJsonPart(toFoundItemCreateRequest()),
                image = imageUri?.takeIf { it.isLocalImageUri() }?.let { createImagePart(context, it).part },
            )
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    uiState = PostWriteUiState.Idle
                    Result.success(body.data.toBoardPost())
                } else {
                    Result.failure(IllegalStateException(body?.message ?: "습득물 수정에 실패했습니다."))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                NetworkLog.httpError("updateFoundItem", response.code(), errorBody)
                Result.failure(IllegalStateException(httpErrorMessage(response.code(), parseFoundItemError(errorBody))))
            }
        } catch (e: Exception) {
            NetworkLog.exception("updateFoundItem", e)
            Result.failure(e)
        }
    }

    private fun createJsonPart(request: Any): RequestBody {
        return createJsonPart(Gson().toJson(request))
    }

    private fun createJsonPart(jsonString: String): RequestBody {
        return jsonString.toRequestBody("application/json; charset=utf-8".toMediaType())
    }

    private fun String.isLocalImageUri(): Boolean =
        !startsWith("http://") && !startsWith("https://")

    private fun String.fileExtension(): String {
        return when (this) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
    }

    private fun ByteArray.toUploadJpegBytes(): ByteArray {
        val decoded = BitmapFactory.decodeByteArray(this, 0, size)
            ?: throw IllegalStateException("JPEG, PNG, WEBP 이미지만 등록할 수 있습니다.")
        val oriented = decoded.applyExifOrientation(this)

        val qualities = listOf(90, 80, 70, 60)
        var compressed = ByteArray(0)
        for (quality in qualities) {
            compressed = oriented.compressJpeg(quality)
            if (compressed.size <= MAX_IMAGE_BYTES) break
        }

        if (oriented !== decoded) oriented.recycle()
        decoded.recycle()
        return compressed
    }

    private fun ByteArray.hasExifRotation(): Boolean {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(this)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_180 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270
    }

    private fun Bitmap.compressJpeg(quality: Int): ByteArray {
        return ByteArrayOutputStream().use { output ->
            compress(Bitmap.CompressFormat.JPEG, quality, output)
            output.toByteArray()
        }
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

    private fun LostItemData.toBoardPost(): BoardPost {
        return BoardPost(
            id = id.toString(),
            type = PostType.LOST,
            status = if (itemStatus == "RETURNED") PostStatus.RESOLVED else PostStatus.OPEN,
            authorUserId = userId,
            authorName = displayUserName(),
            title = title,
            category = kind,
            content = content,
            imageUri = imageUrl,
            createdAtText = createdAt.take(10),
            lostLocation = lostLocation,
        )
    }

    private fun FoundItemDetailData.toBoardPost(): BoardPost {
        return BoardPost(
            id = id.toString(),
            type = PostType.FOUND,
            status = if (itemStatus == ItemStatus.RETURNED) {
                com.ku.lostandfound.data.PostStatus.RESOLVED
            } else {
                com.ku.lostandfound.data.PostStatus.OPEN
            },
            authorUserId = userId,
            authorName = displayUserName(),
            title = title,
            category = kind,
            content = content,
            imageUri = imageUrl,
            createdAtText = createdAt.take(10),
            foundLocation = toFoundLocationSelection(),
        )
    }

    private fun FoundItemDetailData.toFoundLocationSelection(): FoundLocationSelection {
        return when (locationType) {
            FoundItemLocationType.INDOOR -> FoundLocationSelection(
                indoorPlace = buildingName?.let {
                    IndoorPlace(
                        buildingId = it,
                        buildingName = it,
                        floor = floor ?: 1,
                    )
                }
            )
            FoundItemLocationType.OUTDOOR -> FoundLocationSelection(
                outdoorPin = if (latitude != null && longitude != null) {
                    OutdoorPin(
                        order = 1,
                        point = GeoPoint(longitude = longitude, latitude = latitude),
                    )
                } else {
                    null
                }
            )
        }
    }

    private fun parseLostItemError(errorBody: String?): String {
        if (errorBody == null) return "오류가 발생했습니다."
        return try {
            val errorResponse = Gson().fromJson(errorBody, LostItemErrorResponse::class.java)
            errorResponse.errors?.values?.firstOrNull() ?: errorResponse.message ?: "요청을 처리할 수 없습니다."
        } catch (e: Exception) {
            "오류가 발생했습니다."
        }
    }

    private fun parseFoundItemError(errorBody: String?): String {
        if (errorBody == null) return "오류가 발생했습니다."
        return try {
            val errorResponse = Gson().fromJson(errorBody, FoundItemErrorResponse::class.java)
            errorResponse.errors?.values?.firstOrNull() ?: errorResponse.message ?: "요청을 처리할 수 없습니다."
        } catch (e: Exception) {
            "오류가 발생했습니다."
        }
    }

    private fun bearerTokenOrThrow(): String {
        val token = TokenManager.accessToken ?: throw IllegalStateException("로그인이 필요합니다.")
        return "Bearer $token"
    }

    private fun LostItemData.displayUserName(): String =
        userName?.takeIf { it.isNotBlank() } ?: authorName?.takeIf { it.isNotBlank() } ?: "익명"

    private fun FoundItemDetailData.displayUserName(): String =
        userName?.takeIf { it.isNotBlank() } ?: authorName?.takeIf { it.isNotBlank() } ?: "익명"

    private data class UploadImage(
        val uri: Uri,
        val mimeType: String,
        val fileName: String,
        val byteSize: Int,
        val part: MultipartBody.Part,
    )

    private companion object {
        const val FOUND_ITEM_UPLOAD_TAG = "FoundItemUpload"
        const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
        const val UPLOAD_IMAGE_MIME_TYPE = "image/jpeg"
        val ALLOWED_IMAGE_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
