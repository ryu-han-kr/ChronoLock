package com.chronolock.account

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chronolock.domain.AuthSession
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Separate file so clearing rules never signs the user out. Backups exclude all app data.
private val Context.account by preferencesDataStore("account")

data class AccountState(val configured: Boolean = false, val signedIn: Boolean = false, val email: String? = null)

@Singleton
class AccountRepository @Inject constructor(@ApplicationContext private val context: Context, private val auth: SupabaseAuth) {
    private val userId = stringPreferencesKey("user_id")
    private val email = stringPreferencesKey("email")
    private val access = stringPreferencesKey("access_token")
    private val refresh = stringPreferencesKey("refresh_token")
    private val expires = longPreferencesKey("expires_at")
    private val mutex = Mutex()

    val state: Flow<AccountState> = context.account.data.map {
        AccountState(auth.configured, it[userId] != null, it[email])
    }

    suspend fun signInWithGoogle(idToken: String, rawNonce: String) = mutex.withLock { save(auth.signInWithGoogle(idToken, rawNonce)) }

    /** Ends the session locally even when the server cannot be reached; the refresh token is discarded. */
    suspend fun signOut() = mutex.withLock {
        val session = stored()
        try { if (session != null) auth.signOut(validAccessToken(session)) } finally { clearLocal() }
    }

    /** Deletes the Supabase user, then the local session. Local rules and history stay on the device. */
    suspend fun deleteAccount() = mutex.withLock {
        val session = checkNotNull(stored()) { "Not signed in" }
        auth.deleteAccount(validAccessToken(session))
        clearLocal()
    }

    /** Drops the stored session and Credential Manager's remembered account, so the next sign-in shows the chooser again. */
    private suspend fun clearLocal() {
        context.account.edit { it.clear() }
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    private suspend fun validAccessToken(session: AuthSession): String =
        if (session.needsRefresh(System.currentTimeMillis() / 1000)) auth.refresh(session.refreshToken).also { save(it) }.accessToken
        else session.accessToken

    private suspend fun stored(): AuthSession? = context.account.data.first().let {
        val id = it[userId] ?: return null
        AuthSession(id, it[email], it[access].orEmpty(), it[refresh].orEmpty(), it[expires] ?: 0)
    }

    private suspend fun save(session: AuthSession) {
        context.account.edit {
            it[userId] = session.userId
            if (session.email != null) it[email] = session.email!! else it.remove(email)
            it[access] = session.accessToken
            it[refresh] = session.refreshToken
            it[expires] = session.expiresAtEpochSeconds
        }
    }
}
