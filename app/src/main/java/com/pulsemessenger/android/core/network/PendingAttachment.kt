package com.pulsemessenger.android.core.network

import android.net.Uri

enum class PendingAttachmentKind {
    Image,
    File
}

data class PendingAttachment(
    val localId: Long = System.nanoTime(),
    val uri: Uri,
    val kind: PendingAttachmentKind,
    val fileName: String = "",
    val fileSize: Long? = null,
)
