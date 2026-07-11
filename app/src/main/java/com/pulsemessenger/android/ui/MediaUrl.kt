package com.pulsemessenger.android.ui

import com.pulsemessenger.android.BuildConfig

fun resolveBackendMediaUrl(path: String): String {
    if (path.startsWith("http://") || path.startsWith("https://")) {
        return path
    }
    val base = BuildConfig.BASE_URL.trimEnd('/')
    val suffix = if (path.startsWith('/')) path else "/$path"
    return base + suffix
}
