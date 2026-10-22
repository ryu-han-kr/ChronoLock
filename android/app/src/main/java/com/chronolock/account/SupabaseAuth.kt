package com.chronolock.account

import com.chronolock.BuildConfig
import com.chronolock.domain.AuthSession
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class AuthConfig(val supabaseUrl: String, val anonKey: String, val googleWebClientId: String) {
    val configured get() = supabaseUrl.isNotBlank() && anonKey.isNotBlank() && googleWebClientId.isNotBlank()

    companion object {
        fun fromBuild() = AuthConfig(BuildConfig.SUPABASE_URL.trimEnd('/'), BuildConfig.SUPABASE_ANON_KEY, BuildConfig.GOOGLE_WEB_CLIENT_ID)
    }
}

class AuthHttpException(val status: Int, message: String) : IOException(message)

/** Minimal HTTP seam so the Supabase calls can be tested without a network. */
fun interface AuthTransport {
    /** Returns the status code and body; never throws for HTTP error statuses. */
    fun post(url: String, headers: Map<String, String>, body: String): Pair<Int, String>
}

class UrlConnectionTransport @Inject constructor() : AuthTransport {
    override fun post(url: String, headers: Map<String, String>, body: String): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            headers.forEach(connection::setRequestProperty)
            connection.outputStream.use { it.write(body.toByteArray()) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            return status to (stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }
}

/** Supabase Auth over REST. The Kotlin SDK needs Kotlin 2, which this project does not use yet. */
@Singleton
class SupabaseAuth(private val config: AuthConfig, private val transport: AuthTransport, private val now: () -> Long) {
    @Inject constructor(transport: UrlConnectionTransport) : this(AuthConfig.fromBuild(), transport, { System.currentTimeMillis() / 1000 })

    val configured get() = config.configured

    suspend fun signInWithGoogle(idToken: String, rawNonce: String): AuthSession =
        session(call("/auth/v1/token?grant_type=id_token", null,
            JSONObject().put("provider", "google").put("id_token", idToken).put("nonce", rawNonce)))

    suspend fun refresh(refreshToken: String): AuthSession =
        session(call("/auth/v1/token?grant_type=refresh_token", null, JSONObject().put("refresh_token", refreshToken)))

    suspend fun signOut(accessToken: String) { call("/auth/v1/logout", accessToken, JSONObject()) }

    /** Requires the `delete_my_account` function from supabase/migrations. */
    suspend fun deleteAccount(accessToken: String) { call("/rest/v1/rpc/delete_my_account", accessToken, JSONObject()) }

    private suspend fun call(path: String, accessToken: String?, body: JSONObject): String = withContext(Dispatchers.IO) {
        check(config.configured) { "Supabase is not configured" }
        val headers = buildMap {
            put("apikey", config.anonKey)
            put("Content-Type", "application/json")
            put("Authorization", "Bearer ${accessToken ?: config.anonKey}")
        }
        val (status, response) = transport.post(config.supabaseUrl + path, headers, body.toString())
        if (status !in 200..299) throw AuthHttpException(status, "Supabase request failed: $status")
        response
    }

    private fun session(response: String): AuthSession {
        val json = JSONObject(response)
        val user = json.getJSONObject("user")
        val expiresAt = json.optLong("expires_at", 0).takeIf { it > 0 } ?: (now() + json.getLong("expires_in"))
        return AuthSession(user.getString("id"), user.optString("email").ifBlank { null },
            json.getString("access_token"), json.getString("refresh_token"), expiresAt)
    }
}
