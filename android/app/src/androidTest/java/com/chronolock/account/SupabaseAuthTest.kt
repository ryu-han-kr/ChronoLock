package com.chronolock.account

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SupabaseAuthTest {
    private val config = AuthConfig("https://example.supabase.co", "anon", "client.apps.googleusercontent.com")
    private val calls = mutableListOf<Triple<String, Map<String, String>, String>>()
    private fun auth(status: Int = 200, body: String = SESSION) =
        SupabaseAuth(config, { url, headers, request -> calls += Triple(url, headers, request); status to body }, { 1_000 })

    @Test fun googleIdToken_isExchangedWithRawNonce() = runBlocking {
        val session = auth().signInWithGoogle("google-id-token", "raw-nonce")
        val (url, headers, body) = calls.single()
        assertEquals("https://example.supabase.co/auth/v1/token?grant_type=id_token", url)
        assertEquals("anon", headers["apikey"])
        val json = JSONObject(body)
        assertEquals("google", json.getString("provider"))
        assertEquals("google-id-token", json.getString("id_token"))
        assertEquals("raw-nonce", json.getString("nonce"))
        assertEquals("user-1", session.userId)
        assertEquals("a@example.com", session.email)
        assertEquals(4_600L, session.expiresAtEpochSeconds) // Falls back to now + expires_in.
    }

    @Test fun accountDeletion_usesUserToken() = runBlocking {
        auth(204, "").deleteAccount("user-access")
        val (url, headers, _) = calls.single()
        assertEquals("https://example.supabase.co/rest/v1/rpc/delete_my_account", url)
        assertEquals("Bearer user-access", headers["Authorization"])
    }

    @Test fun httpErrors_areNotTreatedAsSuccess() = runBlocking {
        val error = runCatching { auth(400, """{"error":"invalid_grant"}""").refresh("old") }.exceptionOrNull()
        assertEquals(400, (error as AuthHttpException).status)
    }

    @Test fun missingConfiguration_hidesLogin() {
        assertFalse(AuthConfig("", "", "").configured)
        assertFalse(AuthConfig("https://example.supabase.co", "anon", "").configured)
        assertTrue(config.configured)
    }

    private companion object {
        const val SESSION = """{"access_token":"access","refresh_token":"refresh","expires_in":3600,
            "user":{"id":"user-1","email":"a@example.com"}}"""
    }
}
