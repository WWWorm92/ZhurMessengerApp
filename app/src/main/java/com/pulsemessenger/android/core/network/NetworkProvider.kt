package com.pulsemessenger.android.core.network

import android.content.Context
import androidx.core.content.edit
import com.google.gson.reflect.TypeToken
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.pulsemessenger.android.BuildConfig
import com.pulsemessenger.android.core.session.SessionStore
import okhttp3.Authenticator
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import android.os.Build
import android.util.Log

class NetworkProvider(context: Context, private val sessionStore: SessionStore) {
    private data class StoredCookie(
        val name: String,
        val value: String,
        val expiresAt: Long,
        val domain: String,
        val path: String,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean,
        val persistent: Boolean,
    )

    private val prefs = context.getSharedPreferences("pulse_cookies", Context.MODE_PRIVATE)
    private val gson: Gson = GsonBuilder().create()
    private val refreshLock = Any()
    private val cookieType = object : TypeToken<List<StoredCookie>>() {}.type

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val now = System.currentTimeMillis()
            val existing = readCookies().filter { it.expiresAt > now }
            val merged = existing.filterNot { stored ->
                cookies.any { it.name == stored.name && it.domain == stored.domain && it.path == stored.path }
            } + cookies.filter { it.persistent || it.name == "refresh_token" }.map {
                StoredCookie(
                    name = it.name,
                    value = it.value,
                    expiresAt = it.expiresAt,
                    domain = it.domain,
                    path = it.path,
                    secure = it.secure,
                    httpOnly = it.httpOnly,
                    hostOnly = it.hostOnly,
                    persistent = it.persistent,
                )
            }
            writeCookies(merged)
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            val valid = readCookies().filter { it.expiresAt > now }
            writeCookies(valid)
            return valid.mapNotNull { stored ->
                val builder = Cookie.Builder()
                    .name(stored.name)
                    .value(stored.value)
                    .path(stored.path)

                if (stored.hostOnly) builder.hostOnlyDomain(stored.domain) else builder.domain(stored.domain)
                if (stored.secure) builder.secure()
                if (stored.httpOnly) builder.httpOnly()
                if (stored.persistent) builder.expiresAt(stored.expiresAt)

                runCatching { builder.build() }.getOrNull()?.takeIf { it.matches(url) }
            }
        }
    }

    private val authenticator = Authenticator { _: Route?, response: Response ->
        val authHeader = response.request.header("Authorization") ?: return@Authenticator null
        if (responseCount(response) >= 2) {
            return@Authenticator null
        }

        synchronized(refreshLock) {
            val requestToken = authHeader.removePrefix("Bearer ").trim()
            val currentToken = sessionStore.currentToken().trim()

            if (currentToken.isNotBlank() && currentToken != requestToken) {
                return@Authenticator response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            val refreshedToken = refreshAccessToken() ?: return@Authenticator null
            sessionStore.saveToken(refreshedToken)
            response.request.newBuilder()
                .header("Authorization", "Bearer $refreshedToken")
                .build()
        }
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .authenticator(authenticator)
        .addInterceptor { chain ->
            val deviceKey = sessionStore.ensureDeviceKey()

            val userAgent = buildString {
                append("PulseAndroid/")
                append(BuildConfig.VERSION_NAME)
                append(" Android/")
                append(Build.VERSION.RELEASE)
                append(" ")
                append(Build.MANUFACTURER)
                append("/")
                append(Build.MODEL)
            }

            val request = chain.request()
                .newBuilder()
                .header("X-Device-Key", deviceKey)
                .header("User-Agent", userAgent)
                .build()

            chain.proceed(request)
        }
        .addInterceptor(logging)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val api: PulseApiService = retrofit.create(PulseApiService::class.java)

    suspend fun refreshSessionToken(): Boolean {
        val refreshed = synchronized(refreshLock) {
            refreshAccessToken()
        } ?: return false
        sessionStore.saveToken(refreshed)
        return true
    }

    private fun refreshAccessToken(): String? {
        return try {
            val request = Request.Builder()
                .url(BuildConfig.BASE_URL.trimEnd('/') + "/api/auth/refresh")
                .post(FormBody.Builder().build())
                .build()

            val response = client.newCall(request).execute()

            response.use {
                val body = it.body?.string().orEmpty()

                Log.d("AUTH", "refresh status=${it.code} body=$body")

                if (!it.isSuccessful) {
                    return null
                }

                gson.fromJson(body, LoginResponse::class.java)
                    ?.token
                    ?.takeIf { token -> token.isNotBlank() }
            }
        } catch (error: Exception) {
            Log.e("AUTH", "refresh failed", error)
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private fun readCookies(): List<StoredCookie> {
        val raw = prefs.getString("cookies", null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching { gson.fromJson<List<StoredCookie>>(raw, cookieType) }.getOrNull().orEmpty()
    }

    private fun writeCookies(cookies: List<StoredCookie>) {
        prefs.edit { putString("cookies", gson.toJson(cookies, cookieType)) }
    }
}
