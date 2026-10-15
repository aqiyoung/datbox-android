package com.datbox.app.data.api

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 「记住我」密码的本地加密（Android Keystore，AES-256/GCM，密钥由系统保管、不可导出）。
 *
 * 背景: 密码之前明文写进 DataStore(openlist_prefs / last_password)，Root / 备份导出 / 取证都能直接读。
 * 现在落盘格式为 `enc:<base64(iv)>:<base64(密文)>`；读取时解密。
 * 旧版明文(无 `enc:` 前缀)一律返回 null —— 调用方读到空串，下次保存后被密文覆盖，实现平滑升级。
 */
internal object CredentialCipher {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "openlist_credential_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX = "enc:"
    private const val GCM_TAG_BITS = 128

    /** 加密；失败(设备无 Keystore 等)返回 null，调用方据此不落盘 —— 绝不退回明文。 */
    fun encrypt(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, obtainKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val ciphertext = Base64.encodeToString(
            cipher.doFinal(plain.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP,
        )
        "$PREFIX$iv:$ciphertext"
    }.getOrNull()

    /** 解密；非 `enc:` 格式(旧版明文/空)与解密失败(换设备/密钥失效)都返回 null。 */
    fun decrypt(payload: String): String? = runCatching {
        if (!payload.startsWith(PREFIX)) return@runCatching null
        val body = payload.removePrefix(PREFIX)
        val iv = Base64.decode(body.substringBefore(':'), Base64.NO_WRAP)
        val ciphertext = Base64.decode(body.substringAfter(':'), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, obtainKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    private fun obtainKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }
}
