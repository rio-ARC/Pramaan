package app.pramaan.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.pramaan.api.dto.AccountProfileDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "pramaan_auth")

class AuthStore(private val context: Context) {

    companion object {
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_ROLE = stringPreferencesKey("role")
        val KEY_ACCOUNT_ID = stringPreferencesKey("account_id")
        val KEY_TOKEN = stringPreferencesKey("token")
        val KEY_PROFILE_JSON = stringPreferencesKey("profile_json")

        const val DEFAULT_SERVER_URL = "http://10.0.2.2:8080"
    }

    private val json = Json { ignoreUnknownKeys = true }

    val serverUrlFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_SERVER_URL]
    }

    val roleFlow: Flow<Role?> = context.dataStore.data.map { prefs ->
        prefs[KEY_ROLE]?.let {
            try { Role.valueOf(it) } catch (_: Exception) { null }
        }
    }

    val accountIdFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACCOUNT_ID]
    }

    val tokenFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_TOKEN]
    }

    val profileFlow: Flow<AccountProfileDto?> = context.dataStore.data.map { prefs ->
        prefs[KEY_PROFILE_JSON]?.let {
            try { json.decodeFromString<AccountProfileDto>(it) } catch (_: Exception) { null }
        }
    }

    suspend fun getServerUrl(): String = serverUrlFlow.first() ?: ""

    suspend fun getAccountId(): String? = accountIdFlow.first()

    suspend fun getToken(): String? = tokenFlow.first()

    suspend fun getRole(): Role? = roleFlow.first()

    suspend fun getProfile(): AccountProfileDto? = profileFlow.first()

    suspend fun setServerUrl(url: String) {
        val cleanUrl = url.trim().trimEnd('/')
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = cleanUrl
        }
    }

    suspend fun saveAuth(
        accountId: String,
        role: Role,
        token: String,
        profile: AccountProfileDto
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCOUNT_ID] = accountId
            prefs[KEY_ROLE] = role.name
            prefs[KEY_TOKEN] = token
            prefs[KEY_PROFILE_JSON] = json.encodeToString(AccountProfileDto.serializer(), profile)
        }
    }

    suspend fun clearAuth() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_ROLE)
            prefs.remove(KEY_ACCOUNT_ID)
            prefs.remove(KEY_TOKEN)
            prefs.remove(KEY_PROFILE_JSON)
        }
    }

    suspend fun isLoggedIn(): Boolean {
        val token = getToken()
        return !token.isNullOrBlank()
    }
}
