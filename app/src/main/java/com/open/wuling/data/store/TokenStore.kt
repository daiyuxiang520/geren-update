package com.open.wuling.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore(name = "wuling_token")

/**
 * Token 安全持久化存储
 * 使用 DataStore Preferences 保存 accessToken
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
    }

    /**
     * 观察 Token 变化的 Flow
     */
    val tokenFlow: Flow<String> = context.tokenDataStore.data.map { preferences ->
        preferences[Keys.ACCESS_TOKEN] ?: ""
    }

    /**
     * 获取当前保存的 Token（挂起函数）
     */
    suspend fun getToken(): String {
        return tokenFlow.first()
    }

    /**
     * 保存 Token
     */
    suspend fun saveToken(token: String) {
        context.tokenDataStore.edit { preferences ->
            preferences[Keys.ACCESS_TOKEN] = token
        }
    }

    /**
     * 清除 Token（退出登录时调用）
     */
    suspend fun clearToken() {
        context.tokenDataStore.edit { preferences ->
            preferences.remove(Keys.ACCESS_TOKEN)
        }
    }
}
