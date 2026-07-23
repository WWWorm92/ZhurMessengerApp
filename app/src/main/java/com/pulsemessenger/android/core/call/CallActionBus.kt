package com.pulsemessenger.android.core.call

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class CallNotificationAction {
    data class Incoming(
        val callId: String,
        val peerUserId: Long,
        val peerName: String,
        val peerAvatarUrl: String = "",
    ) : CallNotificationAction()

    data class Accept(
        val callId: String,
        val peerUserId: Long,
        val peerName: String,
        val peerAvatarUrl: String = "",
    ) : CallNotificationAction()

    data class Reject(
        val callId: String,
        val peerUserId: Long,
    ) : CallNotificationAction()

    data object EndCurrent : CallNotificationAction()

    data object OpenCurrent : CallNotificationAction()
}

object CallActionBus {
    private val _actions = MutableSharedFlow<CallNotificationAction>(
        replay = 1,
        extraBufferCapacity = 8,
    )

    val actions = _actions.asSharedFlow()

    fun publish(action: CallNotificationAction) {
        _actions.tryEmit(action)
    }
}
