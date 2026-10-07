package com.ku.lostandfound.ui.chat.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ku.lostandfound.network.ChatMessageResponse
import com.ku.lostandfound.network.ChatRoomResponse
import com.ku.lostandfound.network.CreateChatRoomRequest
import com.ku.lostandfound.network.NetworkLog
import com.ku.lostandfound.network.RetrofitClient
import com.ku.lostandfound.network.httpErrorMessage
import kotlinx.coroutines.launch
import com.ku.lostandfound.network.ChatStompClient
import com.ku.lostandfound.network.TokenManager
import kotlinx.coroutines.Dispatchers

sealed class ChatUiState {
    object Idle : ChatUiState()
    object Loading : ChatUiState()
    data class Error(val message: String) : ChatUiState()
}

class ChatViewModel : ViewModel() {

    var uiState by mutableStateOf<ChatUiState>(ChatUiState.Idle)
        private set

    var rooms by mutableStateOf<List<ChatRoomResponse>>(emptyList())
        private set

    var messages by mutableStateOf<List<ChatMessageResponse>>(emptyList())
        private set

    var messageUiState by mutableStateOf<ChatUiState>(ChatUiState.Idle)
        private set

    private var messageLoadRequestId = 0

    fun loadRooms() {
        viewModelScope.launch {
            uiState = ChatUiState.Loading

            try {
                val response = RetrofitClient.chatApi.getRooms()

                if (response.isSuccessful) {
                    val body = response.body()

                    if (body?.success == true) {
                        rooms = body.data.orEmpty()
                        uiState = ChatUiState.Idle
                    } else {
                        uiState = ChatUiState.Error(
                            body?.message ?: "채팅방 목록을 불러오지 못했습니다."
                        )
                    }
                } else {
                    val errorBody = response.errorBody()?.string()

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
            } catch (e: Exception) {
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

        viewModelScope.launch {
            messageUiState = ChatUiState.Loading

            try {
                val response = RetrofitClient.chatApi.getMessages(
                    roomId = roomId,
                    size = size,
                )

                // 이전 채팅방의 응답이 뒤늦게 도착하면 무시
                if (
                    activeRoomId != roomId ||
                    session != connectionGeneration ||
                    requestId != messageLoadRequestId
                ) {
                    return@launch
                }

                if (response.isSuccessful) {
                    val body = response.body()

                    if (body?.success == true) {
                        messages = (messages + body.data.orEmpty())
                            .distinctBy { it.id }
                            .sortedBy { it.id }

                        messageUiState = ChatUiState.Idle
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

    fun createRoom(
        recipientId: Long,
        onSuccess: (ChatRoomResponse) -> Unit,
    ) {
        viewModelScope.launch {
            uiState = ChatUiState.Loading

            try {
                val response = RetrofitClient.chatApi.createRoom(
                    CreateChatRoomRequest(
                        recipientId = recipientId
                    )
                )

                if (response.isSuccessful) {
                    val body = response.body()

                    if (
                        body?.success == true &&
                        body.data != null
                    ) {
                        uiState = ChatUiState.Idle
                        onSuccess(body.data)
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

                    // 구독 완료 이후 다시 조회해 놓친 메시지 복구
                    loadMessages(roomId)
                }
            },

            onMessage = { incoming ->
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    if (generation != connectionGeneration) return@launch

                    messages = (messages + incoming)
                        .distinctBy { it.id }
                        .sortedBy { it.id }
                    messageUiState = ChatUiState.Idle
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
    }

    override fun onCleared() {
        disconnectRoom()
        super.onCleared()
    }

}