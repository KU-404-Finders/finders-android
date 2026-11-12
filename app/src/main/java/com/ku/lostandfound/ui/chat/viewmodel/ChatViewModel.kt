package com.ku.lostandfound.ui.chat.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ku.lostandfound.network.ChatMessageResponse
import com.ku.lostandfound.network.ChatReadReceipt
import com.ku.lostandfound.network.ChatReadRequest
import com.ku.lostandfound.network.ChatRoomResponse
import com.ku.lostandfound.network.ChatStompClient
import com.ku.lostandfound.network.CreateChatRoomRequest
import com.ku.lostandfound.network.NetworkLog
import com.ku.lostandfound.network.RetrofitClient
import com.ku.lostandfound.network.TokenManager
import com.ku.lostandfound.network.httpErrorMessage
import com.ku.lostandfound.ui.chat.ChatTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class ChatUiState {
    object Idle : ChatUiState()
    object Loading : ChatUiState()
    data class Error(val message: String) : ChatUiState()
}

class ChatViewModel : ViewModel() {
    private var cacheGeneration = 0L

    var uiState by mutableStateOf<ChatUiState>(ChatUiState.Idle)
        private set

    var rooms by mutableStateOf<List<ChatRoomResponse>>(emptyList())
        private set

    private var roomCache by mutableStateOf<
            Map<Long, ChatRoomResponse>
            >(emptyMap())

    var authorInfoByRoomId by mutableStateOf<
            Map<Long, Pair<Long, String>>
            >(emptyMap())
        private set


    private val authorRequestsInFlight = mutableSetOf<Long>()
    private val authorRequestsAttempted = mutableSetOf<Long>()
    private val pendingCreatedRoomIds = mutableSetOf<Long>()
    private var roomListRequestId = 0
    var messages by mutableStateOf<List<ChatMessageResponse>>(emptyList())
        private set

    var messageUiState by mutableStateOf<ChatUiState>(ChatUiState.Idle)
        private set

    private var lastMarkedReadMessageId: Long? = null

    // 사용자별 마지막 읽은 메시지 ID
    private var confirmedReadMessageId = 0L
    private var readRequestJob: Job? = null

    private var isRoomVisible = false
    private var visibleUserId: Long? = null
    private val lastReadByUserId = mutableMapOf<Long, Long>()

    private var messageLoadRequestId = 0
    private var roomActionRequestId = 0


    fun loadRooms() {
        val requestId = ++roomListRequestId
        val requestGeneration = cacheGeneration
        val sessionGeneration = TokenManager.sessionGeneration()

        viewModelScope.launch {
            uiState = ChatUiState.Loading

            try {
                val response = RetrofitClient.chatApi.getRooms()

                if (!isCurrentRequest(requestGeneration, sessionGeneration) || requestId != roomListRequestId) {
                    return@launch
                }

                if (response.isSuccessful) {
                    val body = response.body()

                    if (body?.success == true) {
                        val serverRooms = body.data.orEmpty()
                        // A just-created room can be absent from an in-flight list response.
                        // Preserve only those optimistic entries; every other missing room is
                        // deliberately evicted from both list and cache.
                        val serverIds = serverRooms.mapTo(mutableSetOf()) { it.id }
                        pendingCreatedRoomIds.removeAll(serverIds)
                        val pendingRooms = rooms.filter { it.id in pendingCreatedRoomIds }
                        val reconciledRooms = serverRooms + pendingRooms.filter { it.id !in serverIds }

                        roomCache = reconciledRooms.associateBy { it.id }

                        rooms = reconciledRooms.sortedWith(Comparator { first, second ->
                            ChatTimeFormatter.compareNewest(
                                second.lastMessageAt ?: second.createdAt,
                                first.lastMessageAt ?: first.createdAt,
                            )
                        })

                        uiState = ChatUiState.Idle
                    } else {
                        uiState = ChatUiState.Error(
                            body?.message
                                ?: "채팅방 목록을 불러오지 못했습니다."
                        )
                    }
                } else {
                    val errorBody =
                        response.errorBody()?.string()

                    NetworkLog.httpError(
                        "loadChatRooms",
                        response.code(),
                        errorBody
                    )

                    uiState = ChatUiState.Error(
                        httpErrorMessage(
                            response.code(),
                            "채팅방 목록을 불러오지 못했습니다."
                        )
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isCurrentRequest(requestGeneration, sessionGeneration) || requestId != roomListRequestId) {
                    return@launch
                }

                NetworkLog.exception("loadChatRooms", e)

                uiState = ChatUiState.Error(
                    "서버와 연결할 수 없습니다."
                )
            }
        }
    }


    fun loadMessages(
        roomId: Long,
        size: Int = 50,
    ) {
        if (activeRoomId != roomId) return

        val session = connectionGeneration
        val requestId = ++messageLoadRequestId
        val requestGeneration = cacheGeneration
        val sessionGeneration = TokenManager.sessionGeneration()

        viewModelScope.launch {
            messageUiState = ChatUiState.Loading

            try {
                val response = RetrofitClient.chatApi.getMessages(
                    roomId = roomId,
                    size = size,
                )

                // 이전 채팅방의 응답이 뒤늦게 도착하면 무시
                if (
                    !isCurrentRequest(requestGeneration, sessionGeneration) ||
                    activeRoomId != roomId ||
                    session != connectionGeneration ||
                    requestId != messageLoadRequestId
                ) {
                    return@launch
                }

                if (response.isSuccessful) {
                    val body = response.body()

                    if (body?.success == true) {
                        mergeMessages(body.data.orEmpty())

                        messageUiState = ChatUiState.Idle
                        markVisibleMessagesRead(roomId)
                    } else {
                        messageUiState = ChatUiState.Error(
                            body?.message ?: "채팅 내역을 불러오지 못했습니다."
                        )
                    }
                } else {
                    val errorBody = response.errorBody()?.string()

                    NetworkLog.httpError(
                        "loadChatMessages",
                        response.code(),
                        errorBody
                    )

                    messageUiState = ChatUiState.Error(
                        httpErrorMessage(
                            response.code(),
                            "채팅 내역을 불러오지 못했습니다."
                        )
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (
                    !isCurrentRequest(requestGeneration, sessionGeneration) ||
                    activeRoomId != roomId ||
                    session != connectionGeneration ||
                    requestId != messageLoadRequestId
                ) {
                    return@launch
                }

                NetworkLog.exception("loadChatMessages", e)

                messageUiState = ChatUiState.Error(
                    "서버와 연결할 수 없습니다."
                )
            }
        }
    }

    private fun mergeMessages(
        incoming: List<ChatMessageResponse>,
    ) {
        val messageMap = messages
            .associateBy { it.id }
            .toMutableMap()

        incoming.forEach { newMessage ->

            val previous = messageMap[newMessage.id]

            val readByOther = lastReadByUserId.any { (readerId, lastReadId) ->
                readerId != newMessage.senderId &&
                        newMessage.id <= lastReadId
            }

            val alreadyRead = previous?.unreadCount == 0

            messageMap[newMessage.id] = newMessage.copy(
                unreadCount = if (readByOther || alreadyRead) {
                    0
                } else {
                    newMessage.unreadCount
                }
            )
        }

        messages = messageMap.values.sortedBy { it.id }
    }

    fun createRoom(
        recipientId: Long,
        postType: String,
        postId: Long,
        onSuccess: (ChatRoomResponse) -> Unit,
    ) {
        val requestGeneration = cacheGeneration
        val sessionGeneration = TokenManager.sessionGeneration()
        viewModelScope.launch {
            uiState = ChatUiState.Loading

            try {
                android.util.Log.d(
                    "KUFindersChat",
                    "createRoom recipientId=$recipientId, " +
                            "postType=$postType, postId=$postId"
                )

                val response = RetrofitClient.chatApi.createRoom(
                    CreateChatRoomRequest(
                        recipientId = recipientId,
                        postType = postType,
                        postId = postId,
                    )
                )
                if (!isCurrentRequest(requestGeneration, sessionGeneration)) return@launch
                android.util.Log.d(
                    "KUFindersChat",
                    "createRoom HTTP=${response.code()}, " +
                            "success=${response.body()?.success}"
                )

                if (response.isSuccessful) {
                    val body = response.body()

                    if (
                        body?.success == true &&
                        body.data != null
                    ) {
                        val createdRoom = body.data

                        roomCache = roomCache +
                                (createdRoom.id to createdRoom)
                        pendingCreatedRoomIds += createdRoom.id

                        rooms = (
                                rooms.filterNot { it.id == createdRoom.id } +
                                        createdRoom
                                ).sortedWith(Comparator { first, second ->
                                ChatTimeFormatter.compareNewest(
                                    second.lastMessageAt ?: second.createdAt,
                                    first.lastMessageAt ?: first.createdAt,
                                )
                            })

                        uiState = ChatUiState.Idle
                        onSuccess(createdRoom)
                    } else {
                        uiState = ChatUiState.Error(
                            body?.message ?: "채팅방 생성에 실패했습니다."
                        )
                    }
                } else {
                    val errorBody = response.errorBody()?.string()

                    NetworkLog.httpError(
                        "createChatRoom",
                        response.code(),
                        errorBody
                    )

                    uiState = ChatUiState.Error(
                        httpErrorMessage(
                            response.code(),
                            "채팅방 생성에 실패했습니다."
                        )
                    )
                }
            } catch (e: Exception) {
                if (!isCurrentRequest(requestGeneration, sessionGeneration)) return@launch
                NetworkLog.exception("createChatRoom", e)

                uiState = ChatUiState.Error(
                    "서버와 연결할 수 없습니다."
                )
            }
        }
    }

    fun clearMessages() {
        messages = emptyList()
    }

    fun clearSession() {
        cacheGeneration++
        roomListRequestId++
        messageLoadRequestId++
        authorRequestsInFlight.clear()
        authorRequestsAttempted.clear()
        pendingCreatedRoomIds.clear()
        rooms = emptyList()
        roomCache = emptyMap()
        authorInfoByRoomId = emptyMap()
        messages = emptyList()
        uiState = ChatUiState.Idle
        messageUiState = ChatUiState.Idle
        disconnectRoom()
    }


    fun roomFor(roomId: Long): ChatRoomResponse? {
        return rooms.firstOrNull { it.id == roomId }
            ?: roomCache[roomId]
    }

    fun leaveRoom(roomId: Long, onFinished: (Boolean) -> Unit) {
        val actionId = ++roomActionRequestId
        val requestGeneration = cacheGeneration
        val sessionGeneration = TokenManager.sessionGeneration()
        viewModelScope.launch {
            val success = try {
                val response = RetrofitClient.chatApi.leaveRoom(roomId)
                response.isSuccessful && response.body()?.success == true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                NetworkLog.exception("leaveChatRoom", e)
                false
            }
            if (actionId != roomActionRequestId || !isCurrentRequest(requestGeneration, sessionGeneration)) return@launch
            if (success) {
                rooms = rooms.filterNot { it.id == roomId }
                roomCache = roomCache - roomId
                pendingCreatedRoomIds.remove(roomId)
            }
            onFinished(success)
        }
    }

    fun loadPostAuthorForRoom(room: ChatRoomResponse) {
        val postId = room.postId ?: return
        val postType = room.postType?.uppercase() ?: return

        if (authorInfoByRoomId.containsKey(room.id) || !authorRequestsAttempted.add(room.id)) return
        if (!authorRequestsInFlight.add(room.id)) return
        val requestGeneration = cacheGeneration
        val sessionGeneration = TokenManager.sessionGeneration()

        viewModelScope.launch {
            try {
                when (postType) {
                    "LOST" -> {
                        val response =
                            RetrofitClient.lostItemApi
                                .getLostItemDetail(postId)

                        val detail = response.body()
                            ?.takeIf {
                                response.isSuccessful && it.success
                            }?.data

                        if (detail != null) {
                            if (!isCurrentRequest(requestGeneration, sessionGeneration)) return@launch
                            savePostAuthor(
                                room = room,
                                authorId = detail.userId,
                                authorName = detail.userName
                                    ?.takeIf { it.isNotBlank() }
                                    ?: detail.authorName,
                                postTitle = detail.title
                            )
                        }
                    }

                    "FOUND" -> {
                        val response =
                            RetrofitClient.foundItemApi
                                .getFoundItemDetail(postId)

                        val detail = response.body()
                            ?.takeIf {
                                response.isSuccessful && it.success
                            }?.data

                        if (detail != null) {
                            if (!isCurrentRequest(requestGeneration, sessionGeneration)) return@launch
                            savePostAuthor(
                                room = room,
                                authorId = detail.userId,
                                authorName = detail.userName
                                    ?.takeIf { it.isNotBlank() }
                                    ?: detail.authorName,
                                postTitle = detail.title
                            )
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                NetworkLog.exception(
                    "loadChatPostAuthor",
                    e
                )
            } finally {
                authorRequestsInFlight.remove(room.id)
            }
        }
    }

    private fun savePostAuthor(
        room: ChatRoomResponse,
        authorId: Long,
        authorName: String?,
        postTitle: String,
    ) {
        if (!authorName.isNullOrBlank()) {
            authorInfoByRoomId = authorInfoByRoomId +
                    (room.id to (authorId to authorName))
        }

        if (room.postTitle.isNullOrBlank()) {
            roomCache = roomCache +
                    (room.id to room.copy(postTitle = postTitle))

            rooms = rooms.map { item ->
                if (
                    item.id == room.id &&
                    item.postTitle.isNullOrBlank()
                ) {
                    item.copy(postTitle = postTitle)
                } else {
                    item
                }
            }
        }
    }

    private fun isCurrentRequest(requestGeneration: Long, sessionGeneration: Long): Boolean =
        cacheGeneration == requestGeneration && TokenManager.isCurrentSession(sessionGeneration)



    private var stompClient: ChatStompClient? = null
    private var activeRoomId: Long? = null
    private var connectionGeneration = 0

    var connectedRoomId by mutableStateOf<Long?>(null)
        private set

    var connectionMessage by mutableStateOf<String?>(null)
        private set

    fun connectRoom(roomId: Long) {
        disconnectRoom()

        val generation = ++connectionGeneration
        activeRoomId = roomId
        messages = emptyList()
        lastMarkedReadMessageId = null
        connectionMessage = null

        // WebSocket 연결이 실패해도 기존 메시지는 조회
        loadMessages(roomId)

        val token = TokenManager.accessToken

        if (token.isNullOrBlank()) {
            connectionMessage = "로그인이 필요합니다."
            return
        }

        stompClient = ChatStompClient(
            onConnected = {
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    if (generation != connectionGeneration) return@launch

                    connectedRoomId = roomId
                    connectionMessage = null

                    loadMessages(roomId)
                    refreshReadState(roomId)
                }
            },

            onMessage = { incoming ->
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    if (generation != connectionGeneration) return@launch

                    mergeMessages(listOf(incoming))

                    messageUiState = ChatUiState.Idle

                    markVisibleMessagesRead(roomId)
                }
            },

            onReadReceipt = { receipt ->
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    if (generation != connectionGeneration) return@launch

                    applyReadReceipt(receipt)
                }
            },

            onRoomListChanged = {
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    if (generation == connectionGeneration) loadRooms()
                }
            },

            onError = { error ->
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    if (generation != connectionGeneration) return@launch
                    connectionMessage = error
                }
            },

            onDisconnected = {
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    if (generation != connectionGeneration) return@launch

                    connectedRoomId = null

                    if (connectionMessage == null) {
                        connectionMessage = "실시간 채팅 연결이 끊어졌습니다."
                    }
                }
            }
        ).also {
            it.connect(roomId, token)
        }
    }

    fun sendLiveMessage(content: String): Boolean {
        val roomId = activeRoomId ?: return false

        if (connectedRoomId != roomId) {
            connectionMessage = "채팅 서버 연결을 확인해주세요."
            return false
        }

        val sent = stompClient?.sendMessage(
            roomId = roomId,
            content = content
        ) ?: false

        if (!sent) {
            connectionMessage = "메시지 전송 요청에 실패했습니다."
        }

        return sent
    }

    fun disconnectRoom() {
        connectionGeneration++
        messageLoadRequestId++

        stompClient?.disconnect()
        stompClient = null

        connectedRoomId = null
        activeRoomId = null
        connectionMessage = null

        messageUiState = ChatUiState.Idle
        messages = emptyList()
        readRequestJob?.cancel()
        readRequestJob = null

        isRoomVisible = false
        visibleUserId = null

        lastMarkedReadMessageId = null
        confirmedReadMessageId = 0L
        lastReadByUserId.clear()
    }

    override fun onCleared() {
        disconnectRoom()
        super.onCleared()
    }

    private fun markVisibleMessagesRead(roomId: Long) {
        if (activeRoomId != roomId || !isRoomVisible) return

        val userId = visibleUserId ?: return

        // 내가 보낸 메시지는 제외하고
        // 실제로 받은 메시지 중 마지막 ID를 선택
        val latestMessageId = messages
            .lastOrNull { it.senderId != userId }
            ?.id ?: return

        if (latestMessageId <= confirmedReadMessageId) return

        // 같은 메시지를 이미 처리 중이면 중복 요청 방지
        if (
            lastMarkedReadMessageId == latestMessageId &&
            readRequestJob?.isActive == true
        ) {
            return
        }

        readRequestJob?.cancel()
        lastMarkedReadMessageId = latestMessageId

        val session = connectionGeneration

        readRequestJob = viewModelScope.launch {
            repeat(3) { attempt ->

                if (
                    activeRoomId != roomId ||
                    session != connectionGeneration ||
                    !isRoomVisible
                ) {
                    return@launch
                }

                try {
                    val response = RetrofitClient.chatApi.markRead(
                        roomId = roomId,
                        request = ChatReadRequest(
                            lastReadMessageId = latestMessageId
                        )
                    )

                    val body = response.body()
                    val receipt = body?.data

                    if (
                        response.isSuccessful &&
                        body?.success == true &&
                        receipt != null
                    ) {
                        confirmedReadMessageId = maxOf(
                            confirmedReadMessageId,
                            receipt.lastReadMessageId
                        )

                        applyReadReceipt(receipt)
                        return@launch
                    }

                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    NetworkLog.exception("markChatRead", e)
                }

                // 첫 실패 후 1초, 두 번째 실패 후 2초
                if (attempt < 2) {
                    delay(1000L * (1L shl attempt))
                }
            }

            // 3회 모두 실패하면 다음 화면 재진입이나
            // 새 메시지 수신 시 다시 시도할 수 있도록 초기화
            if (lastMarkedReadMessageId == latestMessageId) {
                lastMarkedReadMessageId = null
            }
        }
    }

    private fun applyReadReceipt(
        receipt: ChatReadReceipt,
    ) {
        if (activeRoomId != receipt.roomId) return

        applyReadCursor(
            readerId = receipt.readerId,
            lastReadMessageId = receipt.lastReadMessageId,
        )
    }

    private fun applyReadCursor(
        readerId: Long,
        lastReadMessageId: Long,
    ) {
        val previousReadId = lastReadByUserId[readerId] ?: 0L

        val updatedReadId = maxOf(
            previousReadId,
            lastReadMessageId,
        )

        lastReadByUserId[readerId] = updatedReadId

        messages = messages.map { message ->
            if (
                message.senderId != readerId &&
                message.id <= updatedReadId &&
                message.unreadCount != 0
            ) {
                message.copy(unreadCount = 0)
            } else {
                message
            }
        }
    }

    private fun refreshReadState(roomId: Long) {
        val session = connectionGeneration

        viewModelScope.launch {
            try {
                val response = RetrofitClient.chatApi.getReadState(roomId)

                if (
                    activeRoomId != roomId ||
                    session != connectionGeneration
                ) {
                    return@launch
                }

                val body = response.body()

                if (!response.isSuccessful || body?.success != true) {
                    return@launch
                }

                val state = body.data ?: return@launch

                state.creatorLastReadMessageId?.let { lastReadId ->
                    applyReadCursor(
                        readerId = state.creatorId,
                        lastReadMessageId = lastReadId,
                    )
                }

                state.recipientLastReadMessageId?.let { lastReadId ->
                    applyReadCursor(
                        readerId = state.recipientId,
                        lastReadMessageId = lastReadId,
                    )
                }

            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                NetworkLog.exception("refreshChatReadState", e)
            }
        }
    }

    fun setRoomVisibility(
        roomId: Long,
        currentUserId: Long?,
        visible: Boolean,
    ) {
        if (activeRoomId != roomId) return

        isRoomVisible = visible
        visibleUserId = if (visible) currentUserId else null

        if (visible) {
            markVisibleMessagesRead(roomId)
        } else {
            readRequestJob?.cancel()
            readRequestJob = null
        }
    }
}
