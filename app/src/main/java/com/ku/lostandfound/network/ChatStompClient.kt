package com.ku.lostandfound.network

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class ChatStompClient(
    private val onConnected: () -> Unit,
    private val onMessage: (ChatMessageResponse) -> Unit,
    private val onError: (String) -> Unit,
    private val onDisconnected: () -> Unit,
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val buffer = StringBuilder()
    @Volatile
    private var socket: WebSocket? = null

    fun connect(roomId: Long, accessToken: String) {
        disconnect()

        val request = Request.Builder()
            .url("wss://finders.hyungyu.dev/ws/chat")
            .build()

        socket = client.newWebSocket(
            request,
            object : WebSocketListener() {

                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response,
                ) {
                    if (socket !== webSocket) return

                    webSocket.send(
                        "CONNECT\n" +
                                "accept-version:1.2\n" +
                                "host:finders.hyungyu.dev\n" +
                                "Authorization:Bearer $accessToken\n" +
                                "heart-beat:0,0\n\n\u0000"
                    )
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    text: String,
                ) {
                    if (socket !== webSocket) return

                    val frames = mutableListOf<String>()

                    synchronized(buffer) {
                        buffer.append(text)

                        while (true) {
                            val end = buffer.indexOf("\u0000")
                            if (end < 0) break

                            frames.add(buffer.substring(0, end))
                            buffer.delete(0, end + 1)
                        }
                    }

                    frames.forEach { frame ->
                        handleFrame(webSocket, roomId, frame)
                    }
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: Response?,
                ) {
                    if (socket !== webSocket) return

                    socket = null
                    onError("실시간 채팅 연결에 실패했습니다.")
                    onDisconnected()
                }

                override fun onClosing(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String,
                ) {
                    if (socket !== webSocket) return

                    webSocket.close(1000, null)
                }

                override fun onClosed(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String,
                ) {
                    if (socket !== webSocket) return

                    socket = null
                    onDisconnected()
                }
            }
        )
    }

    private fun handleFrame(
        webSocket: WebSocket,
        roomId: Long,
        rawFrame: String,
    ) {
        val frame = rawFrame
            .replace("\r\n", "\n")
            .trimStart('\n', '\r')

        val command = frame.substringBefore("\n").trim()
        val header = frame.substringBefore("\n\n")
        val body = frame.substringAfter("\n\n", "")

        when (command) {
            "CONNECTED" -> {
                val roomSubscribed = webSocket.send(
                    "SUBSCRIBE\n" +
                            "id:room-$roomId\n" +
                            "destination:/topic/chat/rooms/$roomId\n\n\u0000"
                )

                val errorsSubscribed = webSocket.send(
                    "SUBSCRIBE\n" +
                            "id:chat-errors\n" +
                            "destination:/user/queue/chat-errors\n\n\u0000"
                )

                if (roomSubscribed && errorsSubscribed) {
                    onConnected()
                } else {
                    onError("채팅방 구독에 실패했습니다.")
                }
            }

            "MESSAGE" -> {
                val destination = header
                    .lineSequence()
                    .firstOrNull {
                        it.startsWith("destination:")
                    }
                    ?.substringAfter("destination:")

                if (destination == "/topic/chat/rooms/$roomId") {
                    try {
                        val message = gson.fromJson(
                            body,
                            ChatMessageResponse::class.java
                        )

                        if (message != null && message.roomId == roomId) {
                            onMessage(message)
                        }
                    } catch (_: Exception) {
                        onError("메시지를 해석하지 못했습니다.")
                    }
                } else if (destination == "/user/queue/chat-errors") {
                    onError("메시지 전송이 거부되었습니다.")
                }
            }

            "ERROR" -> {
                onError("채팅 인증 또는 권한 오류가 발생했습니다.")
                webSocket.close(1000, "STOMP error")
            }
        }
    }

    fun sendMessage(roomId: Long, content: String): Boolean {
        val text = content.trim()

        if (text.isEmpty() || text.length > 2000) {
            return false
        }

        val json = gson.toJson(mapOf("content" to text))

        return socket?.send(
            "SEND\n" +
                    "destination:/app/chat/rooms/$roomId/messages\n" +
                    "content-type:application/json\n\n" +
                    json +
                    "\u0000"
        ) ?: false
    }

    fun disconnect() {
        val oldSocket = socket
        socket = null

        synchronized(buffer) {
            buffer.clear()
        }

        oldSocket?.close(1000, "Leaving chat")
    }
}
