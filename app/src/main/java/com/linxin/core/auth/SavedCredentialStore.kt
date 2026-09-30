package com.linxin.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** 登录页的偏好。整个进程只能有一个同名 DataStore 实例，所以集中放在这里。 */
internal val Context.loginPrefs: DataStore<Preferences> by preferencesDataStore(name = "linxin_login")

internal val KEY_SAVED_USER_CODE = stringPreferencesKey("saved_user_code")
private val KEY_REMEMBER_PASSWORD = booleanPreferencesKey("remember_password")
private val KEY_ENCRYPTED_PASSWORD = stringPreferencesKey("encrypted_password")

/**
 * 「记住密码」的本地存储。
 *
 * 明文密码绝不落盘：加密密钥由 Android Keystore 生成且不可导出，DataStore 里只存
 * AES/GCM 的 IV 和密文，所以换设备、换应用、恢复备份都解不开。
 * 密钥失效或密文被篡改时直接清空并当作没记住，不降级成明文存储。
 */
@Singleton
class SavedCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun savedUserCode(): String =
        runCatching { context.loginPrefs.data.first()[KEY_SAVED_USER_CODE] }.getOrNull().orEmpty()

    suspend fun save(userCode: String, password: String?) = withContext(Dispatchers.IO) {
        // Keystore 出任何问题都不能把登录流程带崩，最坏结果就是"这次没记住"
        val encrypted = password?.let { plain -> runCatching { encrypt(plain) }.getOrNull() }
        context.loginPrefs.edit { prefs ->
            prefs[KEY_SAVED_USER_CODE] = userCode
            if (encrypted == null) {
                prefs.remove(KEY_REMEMBER_PASSWORD)
                prefs.remove(KEY_ENCRYPTED_PASSWORD)
            } else {
                prefs[KEY_REMEMBER_PASSWORD] = true
                prefs[KEY_ENCRYPTED_PASSWORD] = encrypted
            }
        }
    }

    /** 读出已保存的学号和密码；任何一步失败都当成"没记住"，同时把脏数据清掉 */
    suspend fun load(): SavedCredential? {
        val prefs = runCatching { context.loginPrefs.data.first() }.getOrElse { return null }
        if (prefs[KEY_REMEMBER_PASSWORD] != true) return null
        val cipherText = prefs[KEY_ENCRYPTED_PASSWORD] ?: return null
        val password = runCatching { withContext(Dispatchers.IO) { decrypt(cipherText) } }.getOrNull()
        if (password == null) {
            clearPassword()
            return null
        }
        return SavedCredential(userCode = prefs[KEY_SAVED_USER_CODE].orEmpty(), password = password)
    }

    suspend fun clearPassword() = withContext(Dispatchers.IO) {
        context.loginPrefs.edit { prefs ->
            prefs.remove(KEY_REMEMBER_PASSWORD)
            prefs.remove(KEY_ENCRYPTED_PASSWORD)
        }
    }

    private fun encrypt(password: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, generateKey())
        return "${encode(cipher.iv)}:${encode(cipher.doFinal(password.toByteArray()))}"
    }

    private fun decrypt(stored: String): String {
        val (ivPart, cipherPart) = stored.split(':', limit = 2)
            .takeIf { it.size == 2 } ?: throw IllegalStateException("密文格式不对")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val key = keyStore().getKey(KEY_ALIAS, null as CharArray?) as? SecretKey ?: generateKey()
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(GCM_TAG_BITS, decode(ivPart)),
        )
        return String(cipher.doFinal(decode(cipherPart)))
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(128)
                .build(),
        )
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALGORITHM_AES = "AES"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val KEY_ALIAS = "linxin_saved_credential"
    }
}

data class SavedCredential(
    val userCode: String,
    val password: String,
)
