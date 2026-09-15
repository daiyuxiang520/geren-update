package com.open.wuling.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.privacyDataStore: DataStore<Preferences> by preferencesDataStore(name = "privacy_settings")

/**
 * 隐私合规偏好持久化。
 *
 * 工信部《APP 用户权益保护测评规范》要求：**用户同意隐私政策前，App 不得采集
 * 任何个人信息、不得初始化任何采集 SDK**。因此友盟等统计 SDK 的 `init` 必须
 * 延后到用户在本弹窗点「同意」之后。
 *
 * 这里只存「是否已同意」一个布尔值；首次启动为 false，弹窗展示，用户同意后置 true
 * 并持久化，后续启动不再弹。
 */
@Singleton
class PrivacyPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        /** 用户是否已同意隐私政策（默认 false，即需要弹窗） */
        val PRIVACY_AGREED = booleanPreferencesKey("privacy_agreed")
    }

    /** 同意状态流（供 Compose 收集，决定是否展示弹窗与是否 init 友盟） */
    val privacyAgreedFlow: Flow<Boolean> = context.privacyDataStore.data.map { prefs ->
        prefs[PRIVACY_AGREED] ?: false
    }

    /** 同步读取一次（Application.onCreate 等非协程场景用） */
    suspend fun isAgreed(): Boolean = context.privacyDataStore.data.map { it[PRIVACY_AGREED] ?: false }.first()

    /** 记录用户已同意隐私政策 */
    suspend fun setAgreed(agreed: Boolean) {
        context.privacyDataStore.edit { prefs ->
            prefs[PRIVACY_AGREED] = agreed
        }
    }
}
