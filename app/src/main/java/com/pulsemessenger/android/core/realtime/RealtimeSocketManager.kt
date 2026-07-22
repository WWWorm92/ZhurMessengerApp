package com.pulsemessenger.android.core.realtime

import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URISyntaxException

class RealtimeSocketManager(
    private val baseUrl: String,
) {
    private var socket: Socket? = null
    private var onDmMessageNew: ((JSONObject) -> Unit)? = null
    private var onDmMessageUpdate: ((JSONObject) -> Unit)? = null
    private var onDmRead: ((Long, String?) -> Unit)? = null
    private var onRoomMessageNew: ((Long, JSONObject) -> Unit)? = null
    private var onRoomMessageUpdate: ((Long, JSONObject) -> Unit)? = null
    private var onDialogCleared: ((Long) -> Unit)? = null
    private var onRoomsUpdate: (() -> Unit)? = null
    private var onRoomDeleted: ((Long) -> Unit)? = null
    private var onRoomMemberKicked: ((Long, String) -> Unit)? = null
    private var onPollUpdate: ((JSONObject) -> Unit)? = null
    private var onPresenceUpdate: ((Set<Long>) -> Unit)? = null
    private var onTypingUpdate: ((String, Long, Long, Boolean) -> Unit)? = null
    private var onCallIncoming: ((JSONObject) -> Unit)? = null
    private var onCallRinging: ((JSONObject) -> Unit)? = null
    private var onCallAccepted: ((JSONObject) -> Unit)? = null
    private var onCallRejected: ((JSONObject) -> Unit)? = null
    private var onCallCancelled: ((JSONObject) -> Unit)? = null
    private var onCallOffer: ((JSONObject) -> Unit)? = null
    private var onCallAnswer: ((JSONObject) -> Unit)? = null
    private var onCallIce: ((JSONObject) -> Unit)? = null
    private var onCallEnded: ((JSONObject) -> Unit)? = null
    private var onCallError: ((JSONObject) -> Unit)? = null

    private var onConnectionStateChanged: ((Boolean) -> Unit)? = null
    private var onConnectionError: ((String?) -> Unit)? = null
    fun connect(token: String, deviceKey: String) {
        if (token.isBlank()) return
        disconnect()
        try {
            val options = IO.Options.builder()
                .setAuth(
                    mapOf(
                        "token" to token,
                        "deviceKey" to deviceKey,
                        "client" to "android"
                    )
                )
                .build()
            socket = IO.socket(baseUrl, options).apply {
                on(Socket.EVENT_CONNECT) {
                    onConnectionStateChanged?.invoke(true)
                }

                on(Socket.EVENT_DISCONNECT) {
                    onConnectionStateChanged?.invoke(false)
                }

                on(Socket.EVENT_CONNECT_ERROR) {
                    onConnectionStateChanged?.invoke(false)
                    val message = it.firstOrNull()?.toString()
                    onConnectionError?.invoke(message)
                }
                on("message:new") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    onDmMessageNew?.invoke(payload)
                }
                on("message:update") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    onDmMessageUpdate?.invoke(payload)
                }
                on("dm:read") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val peerUserId = payload.optLong("peerUserId", 0L)
                    if (peerUserId > 0) {
                        onDmRead?.invoke(peerUserId, payload.optString("readAt").ifBlank { null })
                    }
                }
                on("room:message:new") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val roomId = payload.optLong("roomId", 0L)
                    if (roomId > 0) {
                        onRoomMessageNew?.invoke(roomId, payload)
                    }
                }
                on("room:message:update") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val roomId = payload.optLong("roomId", 0L)
                    if (roomId > 0) {
                        onRoomMessageUpdate?.invoke(roomId, payload)
                    }
                }
                on("dialog:cleared") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val peerUserId = payload.optLong("peerUserId", 0L)
                    if (peerUserId > 0) {
                        onDialogCleared?.invoke(peerUserId)
                    }
                }
                on("rooms:update") {
                    onRoomsUpdate?.invoke()
                }
                on("room:deleted") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val roomId = payload.optLong("roomId", 0L)
                    if (roomId > 0) {
                        onRoomDeleted?.invoke(roomId)
                    }
                }
                on("room:member:kicked") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val roomId = payload.optLong("roomId", 0L)
                    if (roomId > 0) {
                        onRoomMemberKicked?.invoke(roomId, payload.optString("roomName"))
                    }
                }
                on("poll:update") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    onPollUpdate?.invoke(payload)
                }
                on("presence:update") { args ->
                    val rawValues = when (val payload = args.firstOrNull()) {
                        is org.json.JSONArray -> List(payload.length()) { index -> payload.optLong(index, 0L) }
                        is Collection<*> -> payload.mapNotNull { (it as? Number)?.toLong() }
                        else -> emptyList()
                    }
                    onPresenceUpdate?.invoke(rawValues.filter { it > 0 }.toSet())
                }
                on("typing:update") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val scope = payload.optString("scope")
                    val targetId = payload.optLong("targetId", 0L)
                    val userId = payload.optLong("userId", 0L)
                    val isTyping = payload.optBoolean("isTyping", false)
                    if (scope.isNotBlank() && targetId > 0 && userId > 0) {
                        onTypingUpdate?.invoke(scope, targetId, userId, isTyping)
                    }
                }
                on("call:incoming") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    onCallIncoming?.invoke(payload)
                }
                on("call:ringing") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    onCallRinging?.invoke(payload)
                }
                on("call:accepted") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    onCallAccepted?.invoke(payload)
                }
                on("call:rejected") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    onCallRejected?.invoke(payload)
                }
                on("call:cancelled") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    onCallCancelled?.invoke(payload)
                }
                on("call:offer") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    onCallOffer?.invoke(payload)
                }
                on("call:answer") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    onCallAnswer?.invoke(payload)
                }
                on("call:ice") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val callId = payload.optString("callId")
                    val fromUserId = payload.optLong("fromUserId", 0L)
                    val candidate = payload.optString("candidate")

                    android.util.Log.d(
                        "WEBRTC_CALL",
                        "received ICE callId=$callId fromUserId=$fromUserId mid=${payload.optString("sdpMid")} index=${payload.optInt("sdpMLineIndex", 0)} len=${candidate.length}"
                    )

                    onCallIce?.invoke(payload)
                }
                on("call:ended") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    onCallEnded?.invoke(payload)
                }
                on("call:error") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    onCallError?.invoke(payload)
                }
                connect()
            }
        } catch (_: URISyntaxException) {
            // ignore invalid base url for now
        }
    }

    fun disconnect() {
        onConnectionStateChanged?.invoke(false)
        socket?.disconnect()
        socket?.close()
        socket = null
    }

    fun setOnRoomMessageNew(listener: ((Long, JSONObject) -> Unit)?) {
        onRoomMessageNew = listener
    }

    fun setOnRoomMessageUpdate(listener: ((Long, JSONObject) -> Unit)?) {
        onRoomMessageUpdate = listener
    }

    fun setOnDmMessageNew(listener: ((JSONObject) -> Unit)?) {
        onDmMessageNew = listener
    }

    fun setOnDmMessageUpdate(listener: ((JSONObject) -> Unit)?) {
        onDmMessageUpdate = listener
    }

    fun setOnDmRead(listener: ((Long, String?) -> Unit)?) {
        onDmRead = listener
    }

    fun setOnDialogCleared(listener: ((Long) -> Unit)?) {
        onDialogCleared = listener
    }

    fun setOnRoomsUpdate(listener: (() -> Unit)?) {
        onRoomsUpdate = listener
    }

    fun setOnRoomDeleted(listener: ((Long) -> Unit)?) {
        onRoomDeleted = listener
    }

    fun setOnRoomMemberKicked(listener: ((Long, String) -> Unit)?) {
        onRoomMemberKicked = listener
    }

    fun setOnPollUpdate(listener: ((JSONObject) -> Unit)?) {
        onPollUpdate = listener
    }

    fun setOnPresenceUpdate(listener: ((Set<Long>) -> Unit)?) {
        onPresenceUpdate = listener
    }

    fun setOnTypingUpdate(listener: ((String, Long, Long, Boolean) -> Unit)?) {
        onTypingUpdate = listener
    }

    fun setOnConnectionStateChanged(listener: ((Boolean) -> Unit)?) {
        onConnectionStateChanged = listener
    }

    fun setOnConnectionError(listener: ((String?) -> Unit)?) {
        onConnectionError = listener
    }

    fun emitTypingUpdate(scope: String, targetId: Long, isTyping: Boolean) {
        if (targetId <= 0L) {
            return
        }
        socket?.emit(
            "typing:update",
            JSONObject()
                .put("scope", if (scope == "room") "room" else "dm")
                .put("targetId", targetId)
                .put("isTyping", isTyping)
        )
    }


    fun setOnCallIncoming(listener: ((JSONObject) -> Unit)?) {
        onCallIncoming = listener
    }

    fun setOnCallRinging(listener: ((JSONObject) -> Unit)?) {
        onCallRinging = listener
    }

    fun setOnCallAccepted(listener: ((JSONObject) -> Unit)?) {
        onCallAccepted = listener
    }

    fun setOnCallRejected(listener: ((JSONObject) -> Unit)?) {
        onCallRejected = listener
    }

    fun setOnCallCancelled(listener: ((JSONObject) -> Unit)?) {
        onCallCancelled = listener
    }

    fun setOnCallOffer(listener: ((JSONObject) -> Unit)?) {
        onCallOffer = listener
    }

    fun setOnCallAnswer(listener: ((JSONObject) -> Unit)?) {
        onCallAnswer = listener
    }

    fun setOnCallIce(listener: ((JSONObject) -> Unit)?) {
        onCallIce = listener
    }

    fun setOnCallEnded(listener: ((JSONObject) -> Unit)?) {
        onCallEnded = listener
    }

    fun setOnCallError(listener: ((JSONObject) -> Unit)?) {
        onCallError = listener
    }

    fun emitCallInvite(callId: String, targetUserId: Long) {
        if (callId.isBlank() || targetUserId <= 0L) return
        socket?.emit(
            "call:invite",
            JSONObject()
                .put("callId", callId)
                .put("targetUserId", targetUserId)
                .put("callType", "audio")
        )
    }

    fun emitCallAccept(callId: String, targetUserId: Long) {
        emitCallControl("call:accept", callId, targetUserId)
    }

    fun emitCallReject(callId: String, targetUserId: Long) {
        emitCallControl("call:reject", callId, targetUserId)
    }

    fun emitCallCancel(callId: String, targetUserId: Long) {
        emitCallControl("call:cancel", callId, targetUserId)
    }

    fun emitCallEnd(callId: String, targetUserId: Long) {
        emitCallControl("call:end", callId, targetUserId)
    }

    fun emitCallOffer(callId: String, targetUserId: Long, sdp: String) {
        emitCallSdp("call:offer", callId, targetUserId, sdp)
    }

    fun emitCallAnswer(callId: String, targetUserId: Long, sdp: String) {
        emitCallSdp("call:answer", callId, targetUserId, sdp)
    }

    fun emitCallIce(
        callId: String,
        targetUserId: Long,
        sdpMid: String?,
        sdpMLineIndex: Int,
        candidate: String,
    ) {
        if (callId.isBlank() || targetUserId <= 0L || candidate.isBlank()) {
            android.util.Log.w(
                "WEBRTC_CALL",
                "emit ICE skipped callId=$callId targetUserId=$targetUserId len=${candidate.length}"
            )
            return
        }

        android.util.Log.d(
            "WEBRTC_CALL",
            "emit ICE callId=$callId targetUserId=$targetUserId mid=$sdpMid index=$sdpMLineIndex len=${candidate.length}"
        )

        socket?.emit(
            "call:ice",
            JSONObject()
                .put("callId", callId)
                .put("targetUserId", targetUserId)
                .put("sdpMid", sdpMid ?: JSONObject.NULL)
                .put("sdpMLineIndex", sdpMLineIndex)
                .put("candidate", candidate)
        )
    }

    private fun emitCallControl(event: String, callId: String, targetUserId: Long) {
        if (callId.isBlank() || targetUserId <= 0L) return
        socket?.emit(
            event,
            JSONObject()
                .put("callId", callId)
                .put("targetUserId", targetUserId)
        )
    }

    private fun emitCallSdp(event: String, callId: String, targetUserId: Long, sdp: String) {
        if (callId.isBlank() || targetUserId <= 0L || sdp.isBlank()) return
        socket?.emit(
            event,
            JSONObject()
                .put("callId", callId)
                .put("targetUserId", targetUserId)
                .put("sdp", sdp)
        )
    }

    fun isConnected(): Boolean = socket?.connected() == true
}