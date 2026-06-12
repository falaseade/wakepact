package app.wakepact.data.identity

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class Identity(
    val uid: String,
    val displayName: String,
    val pactId: String?,
)

@Singleton
class IdentityRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val UID = stringPreferencesKey("uid")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val PACT_ID = stringPreferencesKey("pact_id")
    }

    val identity: Flow<Identity> = dataStore.data.map { p ->
        Identity(
            uid = p[Keys.UID].orEmpty(),
            displayName = p[Keys.DISPLAY_NAME].orEmpty(),
            pactId = p[Keys.PACT_ID],
        )
    }

    /** Snapshot with uid and display name guaranteed non-blank. */
    suspend fun current(): Identity {
        val uid = ensureUid()
        val name = ensureDisplayName()
        val pactId = dataStore.data.first()[Keys.PACT_ID]
        return Identity(uid, name, pactId)
    }

    /** Stable local uid, created on first call. */
    suspend fun ensureUid(): String {
        dataStore.data.first()[Keys.UID]?.let { return it }
        val fresh = UUID.randomUUID().toString()
        dataStore.edit { it[Keys.UID] = fresh }
        return fresh
    }

    /** Display name, defaulting to a friendly handle derived from the uid. */
    suspend fun ensureDisplayName(): String {
        dataStore.data.first()[Keys.DISPLAY_NAME]?.takeIf { it.isNotBlank() }?.let { return it }
        val handle = "Sleeper-" + ensureUid().filter { it.isLetterOrDigit() }.takeLast(4).uppercase()
        dataStore.edit { it[Keys.DISPLAY_NAME] = handle }
        return handle
    }

    suspend fun setDisplayName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        dataStore.edit { it[Keys.DISPLAY_NAME] = trimmed.take(MAX_NAME_LENGTH) }
    }

    suspend fun setPactId(pactId: String?) {
        dataStore.edit { prefs ->
            if (pactId == null) prefs.remove(Keys.PACT_ID) else prefs[Keys.PACT_ID] = pactId
        }
    }

    private companion object {
        const val MAX_NAME_LENGTH = 24
    }
}
