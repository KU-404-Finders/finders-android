package com.ku.lostandfound.viewmodel

import android.content.Context
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ku.lostandfound.network.RequestSession
import com.ku.lostandfound.network.RefreshSessionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.exifinterface.media.ExifInterface
import com.google.gson.Gson
import com.ku.lostandfound.data.BoardPost
import com.ku.lostandfound.data.FoundLocationSelection
import com.ku.lostandfound.data.GeoPoint
import com.ku.lostandfound.data.IndoorPlace
import com.ku.lostandfound.data.LostLocationSelection
import com.ku.lostandfound.data.OutdoorPin
import com.ku.lostandfound.data.PostStatus
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.network.FoundItemApiService
import com.ku.lostandfound.network.LostItemApiService
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
import androidx.core.net.toUri

sealed class PostWriteUiState {
    object Idle : PostWriteUiState()
    object Loading : PostWriteUiState()
    data class Error(val message: String) : PostWriteUiState()
    data object Unconfirmed : PostWriteUiState()
    data class Saved(val post: BoardPost, val session: Long, val isEdit: Boolean) : PostWriteUiState()
}

class PostWriteViewModel internal constructor(
    private val foundItemApi: FoundItemApiService,
    private val lostItemApi: LostItemApiService,
    private val imageDispatcher: CoroutineDispatcher,
) : ViewModel() {
    constructor() : this(RetrofitClient.foundItemApi, RetrofitClient.lostItemApi, Dispatchers.IO)

    private var writeJob: Job? = null
    private var attempt = 0L
    private var journalContext: Context? = null
    private var displayedCreation: PostCreationJournal.Entry? = null
    var unconfirmedType by mutableStateOf<PostType?>(null)
        private set

    fun restoreCreationStatus(context: Context) {
        journalContext = context.applicationContext
        if (isWriting || verifiedEdit != null) return
        val pending = PostCreationJournal.account()?.let { PostCreationJournal.pending(context, it) }
        displayedCreation = pending
        unconfirmedType = pending?.type
        if (pending != null) uiState = PostWriteUiState.Unconfirmed
        else if (uiState == PostWriteUiState.Unconfirmed) uiState = PostWriteUiState.Idle
    }
    private val editLoadLock = Mutex()
    private var verifiedEdit by mutableStateOf<Triple<PostType, Long, Long>?>(null)

    fun isEditReady(type: PostType, id: Long): Boolean =
        verifiedEdit == Triple(type, id, TokenManager.sessionGeneration())

    internal suspend fun restoreEdit(type: PostType, id: Long): Boolean = editLoadLock.withLock {
        if (id <= 0) return@withLock false
        if (isEditReady(type, id)) return@withLock true
        val session = TokenManager.sessionGeneration()
        val post = try {
            when (type) {
                PostType.FOUND -> foundItemApi.getFoundItemDetail(id, RequestSession(session)).body()
                    ?.takeIf { it.success }?.data?.toBoardPost()
                PostType.LOST -> lostItemApi.getLostItemDetail(id, RequestSession(session)).body()
                    ?.takeIf { it.success }?.data?.toBoardPost()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (post == null || post.type != type || post.id.toLongOrNull() != id ||
            !TokenManager.isCurrentSession(session)
        ) return@withLock false
        loadForEdit(post)
        true
    }

    val isWriting: Boolean
        get() = uiState is PostWriteUiState.Loading || uiState is PostWriteUiState.Saved

    @MainThread
    fun save(
        context: Context,
        editedId: Long? = null,
        resolveAuthor: suspend () -> Pair<String, String> = { "" to "" },
    ) {
        if (isWriting) return
        if (editedId == null) restoreCreationStatus(context)
        if (uiState == PostWriteUiState.Unconfirmed || !canSubmit()) return
        val session = TokenManager.sessionGeneration()
        if (editedId != null && !isEditReady(postType, editedId)) return
        if (editedId == null && verifiedEdit != null) return
        val requestSession = RequestSession(session)
        val operation = ++attempt
        val type = postType
        uiState = PostWriteUiState.Loading
        writeJob = viewModelScope.launch {
            try {
                val author = if (editedId == null) resolveAuthor() else null
                if (!TokenManager.isCurrentSession(session)) return@launch
                if (TokenManager.isAccessTokenExpired() &&
                    RetrofitClient.refreshSession() != RefreshSessionResult.Success
                ) {
                    error("로그인 상태를 확인할 수 없습니다. 잠시 후 다시 시도해주세요.")
                }
                if (!TokenManager.isCurrentSession(session)) return@launch
                val authorization = bearerTokenOrThrow()
                val appContext = context.applicationContext
                val account = if (editedId == null) PostCreationJournal.account(session)
                    ?: error("계정 정보를 확인할 수 없습니다. 다시 로그인한 뒤 등록해 주세요.")
                else null
                var pending: PostCreationJournal.Entry? = null
                val beginCreation = {
                    if (!TokenManager.isCurrentSession(session)) throw CancellationException()
                    pending = PostCreationJournal.begin(appContext, checkNotNull(account), type)
                        ?: throw UnconfirmedPostCreation()
                }
                val result = when {
                    editedId != null && type == PostType.LOST -> updateLostItem(appContext, editedId, requestSession, authorization)
                    editedId != null -> updateFoundItem(appContext, editedId, requestSession, authorization)
                    type == PostType.LOST -> createLostItem(appContext, requestSession, authorization, beginCreation)
                    else -> createFoundItem(appContext, requestSession, authorization, beginCreation)
                }
                if (result.exceptionOrNull() !is UnconfirmedPostCreation) {
                    pending?.let { PostCreationJournal.clear(appContext, it) }
                }
                if (operation != attempt || !TokenManager.isCurrentSession(session)) return@launch
                val post = result.getOrThrow()
                if (editedId != null && (post.type != type || post.id.toLongOrNull() != editedId)) {
                    error("수정 결과의 게시글 정보를 확인할 수 없습니다.")
                }
                uiState = PostWriteUiState.Saved(
                    if (author == null) post else post.copy(
                        authorName = author.first,
                        authorEmail = author.second,
                    ),
                    session,
                    isEdit = editedId != null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (operation == attempt && TokenManager.isCurrentSession(session)) {
                    if (e is UnconfirmedPostCreation) {
                        uiState = PostWriteUiState.Idle
                        restoreCreationStatus(context)
                    } else {
                        uiState = PostWriteUiState.Error(e.message ?: "서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
                    }
                }
            } finally {
                if (operation == attempt && uiState == PostWriteUiState.Loading) uiState = PostWriteUiState.Idle
            }
        }
    }

    fun acknowledgeUnconfirmed() {
        if (uiState != PostWriteUiState.Unconfirmed) return
        val context = journalContext ?: return
        val entry = displayedCreation?.takeIf { it.key == PostCreationJournal.account() } ?: return
        if (PostCreationJournal.clear(context, entry)) restoreCreationStatus(context)
    }

    private class UnconfirmedPostCreation : IllegalStateException()

    private suspend fun <T> confirmCreation(send: suspend () -> retrofit2.Response<T>): retrofit2.Response<T> {
        val response = try {
            send()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Once dispatched, a transport or decoding failure cannot establish whether the server saved it.
            throw UnconfirmedPostCreation()
        }
        if (response.code() in 500..599 || response.code() == 408) {
            response.errorBody()?.close()
            throw UnconfirmedPostCreation()
        }
        return response
    }

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

    private suspend fun createLostItem(
        context: Context, session: RequestSession, authorization: String, beginCreation: () -> Unit,
    ): Result<BoardPost> {
        return try {
            val request = createJsonPart(toLostItemCreateRequest())
            val uploadImage = imageUri?.let { prepareImage(context, it) }
            beginCreation()
            val response = confirmCreation {
                lostItemApi.createLostItem(authorization, request, uploadImage, session)
            }

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    if (body.data.id <= 0) throw UnconfirmedPostCreation()
                    Result.success(runCatching { body.data.toBoardPost() }.getOrElse {
                        throw UnconfirmedPostCreation()
                    })
                } else {
                    if (body == null || body.success == true) throw UnconfirmedPostCreation()
                    Result.failure(IllegalStateException(body.message ?: "분실물 등록에 실패했습니다."))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                NetworkLog.httpError("createLostItem", response.code())
                Result.failure(
                    IllegalStateException(
                        httpErrorMessage(
                            response.code(),
                            parseLostItemError(errorBody)
                        )
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkLog.exception("createLostItem", e)
            Result.failure(e)
        }
    }

    private suspend fun createFoundItem(
        context: Context, session: RequestSession, authorization: String, beginCreation: () -> Unit,
    ): Result<BoardPost> {
        return try {
            val request = createJsonPart(toFoundItemCreateRequest())
            val uploadImage = imageUri?.let { prepareImage(context, it) }
                ?: throw IllegalStateException("습득물 사진은 필수입니다.")
            beginCreation()
            val response = confirmCreation {
                foundItemApi.createFoundItem(authorization, request, uploadImage, session)
            }

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    if (body.data.id <= 0) throw UnconfirmedPostCreation()
                    Result.success(runCatching { body.data.toBoardPost() }.getOrElse {
                        throw UnconfirmedPostCreation()
                    })
                } else {
                    if (body == null || body.success == true) throw UnconfirmedPostCreation()
                    Result.failure(IllegalStateException(body.message ?: "습득물 등록에 실패했습니다."))
                }
            } else {
                val errorBody = response.errorBody()?.string()

                NetworkLog.httpError("createFoundItem", response.code())
                Result.failure(
                    IllegalStateException(
                        httpErrorMessage(
                            response.code(),
                            parseFoundItemError(errorBody)
                        )
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkLog.exception("createFoundItem", e)
            Result.failure(e)
        }
    }

    fun resetUiState() {
        if (uiState is PostWriteUiState.Error) uiState = PostWriteUiState.Idle
    }

    internal fun loadForEdit(post: BoardPost) {
        verifiedEdit = post.id.toLongOrNull()?.takeIf { it > 0 }?.let {
            Triple(post.type, it, TokenManager.sessionGeneration())
        }
        postType = post.type
        title = post.title
        category = post.category
        content = post.content
        imageUri = post.imageUri
        lostLocation = post.lostLocation ?: LostLocationSelection()
        foundLocation = post.foundLocation ?: FoundLocationSelection()
        uiState = PostWriteUiState.Idle
    }

    fun reset() {
        verifiedEdit = null
        unconfirmedType = null
        displayedCreation = null
        ++attempt
        writeJob?.cancel()
        writeJob = null
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

    private suspend fun prepareImage(context: Context, uriString: String): MultipartBody.Part {
        val type = postType
        return withContext(imageDispatcher) { createImagePart(context, uriString, type) }
    }

    private fun createImagePart(context: Context, uriString: String, type: PostType): MultipartBody.Part {
        val uri = uriString.toUri()
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
        val fileLabel = if (type == PostType.FOUND) "found-item" else "lost-item"
        val fileName = "${fileLabel}_${System.currentTimeMillis()}.$extension"
        return MultipartBody.Part.createFormData(name = "image", filename = fileName, body = requestBody)
    }

    private suspend fun updateLostItem(context: Context, id: Long, session: RequestSession, authorization: String): Result<BoardPost> {
        return try {
            val response = lostItemApi.updateLostItem(
                authorization = authorization,
                session = session,
                id = id,
                request = createJsonPart(toLostItemCreateRequest()),
                image = imageUri?.takeIf { it.isLocalImageUri() }
                    ?.let { prepareImage(context, it) },
            )
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    Result.success(body.data.toBoardPost())
                } else {
                    Result.failure(IllegalStateException(body?.message ?: "분실물 수정에 실패했습니다."))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                NetworkLog.httpError("updateLostItem", response.code())
                Result.failure(
                    IllegalStateException(
                        httpErrorMessage(
                            response.code(),
                            parseLostItemError(errorBody)
                        )
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkLog.exception("updateLostItem", e)
            Result.failure(e)
        }
    }

    private suspend fun updateFoundItem(context: Context, id: Long, session: RequestSession, authorization: String): Result<BoardPost> {
        return try {
            val response = foundItemApi.updateFoundItem(
                authorization = authorization,
                session = session,
                id = id,
                request = createJsonPart(toFoundItemCreateRequest()),
                image = imageUri?.takeIf { it.isLocalImageUri() }
                    ?.let { prepareImage(context, it) },
            )
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    Result.success(body.data.toBoardPost())
                } else {
                    Result.failure(IllegalStateException(body?.message ?: "습득물 수정에 실패했습니다."))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                NetworkLog.httpError("updateFoundItem", response.code())
                Result.failure(
                    IllegalStateException(
                        httpErrorMessage(
                            response.code(),
                            parseFoundItemError(errorBody)
                        )
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
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
                PostStatus.RESOLVED
            } else {
                PostStatus.OPEN
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
            errorResponse.errors?.values?.firstOrNull() ?: errorResponse.message
        } catch (_: Exception) {
            "오류가 발생했습니다."
        }
    }

    private fun parseFoundItemError(errorBody: String?): String {
        if (errorBody == null) return "오류가 발생했습니다."
        return try {
            val errorResponse = Gson().fromJson(errorBody, FoundItemErrorResponse::class.java)
            errorResponse.errors?.values?.firstOrNull() ?: errorResponse.message
        } catch (_: Exception) {
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

    private companion object {
        const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
        const val UPLOAD_IMAGE_MIME_TYPE = "image/jpeg"
        val ALLOWED_IMAGE_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
