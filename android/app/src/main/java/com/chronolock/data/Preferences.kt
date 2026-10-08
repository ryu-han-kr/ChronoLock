package com.chronolock.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore("preferences")

@Singleton
class Preferences @Inject constructor(@ApplicationContext private val context: Context) {
    private val disclosure = booleanPreferencesKey("permission_disclosure_acknowledged")
    val acknowledged = context.preferences.data.map { it[disclosure] ?: false }
    suspend fun acknowledge() { context.preferences.edit { it[disclosure] = true } }
}
