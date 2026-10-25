package app.tare.data.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Separate file so backup rules can exclude it; see res/xml/backup_rules.xml. */
val Context.secretsDataStore: DataStore<Preferences> by preferencesDataStore(name = "secrets")

/**
 * Stores the Claude API key (and the GitHub token for updates) encrypted with an AES-GCM key that lives in the Android
 * Keystore. The ciphertext is useless off this device, which is why it's excluded from backup.
 */
class SecretStore(private val store: DataStore<Preferences>) {

    val hasApiKey: Flow<Boolean> = store.data.map { it[API_KEY] != null }

    suspend fun apiKey(): String? = store.data.first()[API_KEY]?.let(::decrypt)

    suspend fun setApiKey(key: String?) {
        store.edit {
            val trimmed = key?.trim()
            if (trimmed.isNullOrEmpty()) it.remove(API_KEY) else it[API_KEY] = encrypt(trimmed)
        }
    }

    val hasGithubToken: Flow<Boolean> = store.data.map { it[GITHUB_TOKEN] != null }

    /** Read-only GitHub token for in-app updates from the private repository. */
    suspend fun githubToken(): String? = store.data.first()[GITHUB_TOKEN]?.let(::decrypt)

    suspend fun setGithubToken(token: String?) {
        store.edit {
            val trimmed = token?.trim()
            if (trimmed.isNullOrEmpty()) it.remove(GITHUB_TOKEN) else it[GITHUB_TOKEN] = encrypt(trimmed)
        }
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String? = runCatching {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload, 0, IV_BYTES))
        String(cipher.doFinal(payload, IV_BYTES, payload.size - IV_BYTES), Charsets.UTF_8)
    }.getOrNull()

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        val API_KEY = stringPreferencesKey("claude_api_key")
        val GITHUB_TOKEN = stringPreferencesKey("github_token")
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "tare_secrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
