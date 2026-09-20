package com.open.wuling.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v70：登录凭据安全存储（手机号 + 密码），用于 token 失效时自动静默重登。
 *
 * 背景：服务端是单点登录 —— 同一账号在官方 App / 本 App / 任何一端重新登录，
 * 都会把其它端的 token 顶失效。本 App 没有凭据就"死了"，用户必须手动重登。
 * 保存凭据后即可自动恢复，谁顶了都能自愈。
 *
 * 加密方案：AndroidKeyStore 硬件级 AES-256-GCM，随机 IV，密文落 SharedPreferences。
 * 密钥不出 Keystore，root/备份文件都拿不到明文。不引入 security-crypto 新依赖。
 */
@Singleton
class CredentialStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CredentialStore"
        private const val PREFS = "wuling_credentials"
        private const val KEY_BLOB = "cred_blob"
        private const val KEY_ENABLED = "auto_relogin_enabled"
        private const val KEYSTORE_ALIAS = "wuling_cred_master_key"
        private const val GCM_TAG_BITS = 128
        private const val SEPARATOR = '\n'
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 自动重登总开关（默认开；用户可在设置页关闭或清除凭据） */
    var autoReloginEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_ENABLED, value).apply() }

    fun hasCredentials(): Boolean = prefs.getString(KEY_BLOB, null) != null

    /** 保存凭据（加密后落盘） */
    fun save(mobile: String, password: String) {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val ciphertext = cipher.doFinal("$mobile$SEPARATOR$password".toByteArray(Charsets.UTF_8))
            prefs.edit()
                .putString(KEY_BLOB, Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP))
                .putBoolean(KEY_ENABLED, true)
                .apply()
            Log.d(TAG, "凭据已加密保存")
        } catch (e: Exception) {
            Log.e(TAG, "凭据保存失败: ${e.message}")
        }
    }

    /** 读取凭据，解密失败/未保存返回 null */
    fun load(): Pair<String, String>? {
        val blob = prefs.getString(KEY_BLOB, null) ?: return null
        return try {
            val data = Base64.decode(blob, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            // 标准 GCM 布局：前 12 字节 IV，其余为密文+tag
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, data, 0, 12))
            val plain = String(cipher.doFinal(data, 12, data.size - 12), Charsets.UTF_8)
            val idx = plain.indexOf(SEPARATOR)
            if (idx <= 0) null else Pair(plain.substring(0, idx), plain.substring(idx + 1))
        } catch (e: Exception) {
            // Keystore 密钥失效（如清除凭据/换机恢复）——清掉废数据
            Log.e(TAG, "凭据解密失败，已清除: ${e.message}")
            clear()
            null
        }
    }

    /** 清除凭据（退出登录时调用） */
    fun clear() {
        prefs.edit().clear().apply()
        Log.d(TAG, "凭据已清除")
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }
}
