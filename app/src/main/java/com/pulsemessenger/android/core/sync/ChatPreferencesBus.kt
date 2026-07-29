package com.pulsemessenger.android.core.sync

import com.pulsemessenger.android.core.network.ChatPreferencesDto
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ChatPreferencesBus {
    private val mutableUpdates = MutableSharedFlow<ChatPreferencesDto>(
        extraBufferCapacity = 16,
    )

    val updates = mutableUpdates.asSharedFlow()

    fun publish(preferences: ChatPreferencesDto) {
        mutableUpdates.tryEmit(preferences)
    }
}
