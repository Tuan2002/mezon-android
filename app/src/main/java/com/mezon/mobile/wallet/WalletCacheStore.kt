package com.mezon.mobile.wallet

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.mezon.mmn.IEphemeralKeyPair
import com.mezon.mmn.IZkProof
import com.mezon.mmn.WalletDetail
import com.mezon.mobile.di.IoDispatcher
import com.mezon.mobile.session.SecretStorage
import com.mezon.mobile.session.SessionKeys
import com.mezon.mobile.session.StoredSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class WalletPersistedV1(
    val userId: String,
    val zkProof: IZkProof,
    val ephemeral: IEphemeralKeyPair,
    val walletDetail: WalletDetail? = null
)

@Singleton
class WalletCacheStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val secretStorage: SecretStorage,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = true
    }

    suspend fun loadIfValid(session: StoredSession): WalletPersistedV1? =
        withContext(ioDispatcher) {
            if (session.userId.isEmpty() || session.token.isEmpty()) return@withContext null
            val raw = dataStore.data.first()[SessionKeys.WALLET_CACHE_V1] ?: return@withContext null

            val plaintext = secretStorage.decryptFromString(raw)
                ?: tryParseLegacyAndReencrypt(raw)
                ?: run {
                    dataStore.edit { it.remove(SessionKeys.WALLET_CACHE_V1) }
                    return@withContext null
                }

            val parsed = runCatching { json.decodeFromString<WalletPersistedV1>(plaintext) }.getOrNull()
                ?: return@withContext null
            if (parsed.userId != session.userId) return@withContext null
            parsed
        }

    private suspend fun tryParseLegacyAndReencrypt(raw: String): String? {
        val parsed = runCatching { json.decodeFromString<WalletPersistedV1>(raw) }.getOrNull()
            ?: return null
        val plaintext = json.encodeToString(WalletPersistedV1.serializer(), parsed)
        val enc = secretStorage.encryptToString(plaintext) ?: return plaintext
        dataStore.edit { it[SessionKeys.WALLET_CACHE_V1] = enc }
        return plaintext
    }

    suspend fun save(
        session: StoredSession,
        zkProof: IZkProof,
        ephemeral: IEphemeralKeyPair,
        walletDetail: WalletDetail?
    ) = withContext(ioDispatcher) {
        val payload = WalletPersistedV1(
            userId = session.userId,
            zkProof = zkProof,
            ephemeral = ephemeral,
            walletDetail = walletDetail
        )
        val plaintext = json.encodeToString(WalletPersistedV1.serializer(), payload)
        val enc = secretStorage.encryptToString(plaintext) ?: return@withContext
        dataStore.edit { prefs ->
            prefs[SessionKeys.WALLET_CACHE_V1] = enc
        }
    }

    suspend fun clear() = withContext(ioDispatcher) {
        dataStore.edit { it.remove(SessionKeys.WALLET_CACHE_V1) }
        secretStorage.deleteKey()
    }
}
