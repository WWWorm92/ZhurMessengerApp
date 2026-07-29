package com.pulsemessenger.android.core.notification

/**
 * Stores only the small piece of UI state needed by push notifications:
 * whether MainActivity is resumed and which conversation is currently open.
 *
 * FirebaseMessagingService may call this object from a background thread,
 * therefore all state operations are synchronized.
 */
object ActiveChatTracker {
    private var appVisible = false
    private var activeChatKey: String? = null

    @Synchronized
    fun setAppVisible(visible: Boolean) {
        appVisible = visible
    }

    @Synchronized
    fun enterChat(scope: String, targetId: Long) {
        chatKey(scope, targetId)?.let { activeChatKey = it }
    }

    @Synchronized
    fun leaveChat(scope: String, targetId: Long) {
        val key = chatKey(scope, targetId) ?: return
        if (activeChatKey == key) {
            activeChatKey = null
        }
    }

    @Synchronized
    fun shouldSuppressNotification(scope: String, targetId: Long): Boolean {
        val key = chatKey(scope, targetId) ?: return false
        return appVisible && activeChatKey == key
    }

    private fun chatKey(scope: String, targetId: Long): String? {
        if (targetId <= 0L) return null
        val normalizedScope = scope.trim()
        if (normalizedScope != "dm" && normalizedScope != "room") return null
        return "$normalizedScope:$targetId"
    }
}
