package com.open.wuling.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.updateChannelDataStore: DataStore<Preferences> by preferencesDataStore(name = "update_channel_settings")

/**
 * 「App 更新通道」偏好持久化（v75）。
 *
 * 两条互不干扰的发布轨道：
 *  - **稳定版（stable）**：读 `update.json`，只推经过验证的正式发布，
 *    面向全部用户，是默认通道。
 *  - **测试版（beta）**：读 `update-beta.json`，用于灰度验证 / 问题排查
 *    （如 MQTT 连接诊断包）。版本号可能带 `-betaN` 后缀，用户自愿切换。
 *
 * 两条轨道的版本清单相互独立，各自维护自己的 versionCode 与 apkUrl：
 *  - 稳定版用户不会被测试版打扰；
 *  - 测试版用户也不会因稳定版清单版本号更低而被提示「降级」。
 *
 * 默认 stable —— 绝大多数用户不应被测试版波及。
 */
@Singleton
class UpdateChannelPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val KEY_CHANNEL = stringPreferencesKey("update_channel")

        /** 稳定版：正式发布轨道 */
        const val CHANNEL_STABLE = "stable"

        /** 测试版：灰度 / 问题排查轨道 */
        const val CHANNEL_BETA = "beta"

        /** 全部可选通道（UI 展示顺序即此顺序） */
        val CHANNELS = listOf(CHANNEL_STABLE, CHANNEL_BETA)

        /** 回落到稳定版：任何异常值都按 stable 处理，避免脏数据把用户锁在未知通道 */
        fun normalize(raw: String?): String =
            if (raw == CHANNEL_BETA) CHANNEL_BETA else CHANNEL_STABLE

        /** 写盘用独立 scope（设置项低频写入，fire-and-forget 足够） */
        private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private val store get() = context.updateChannelDataStore

    /** 当前更新通道（默认 stable） */
    val channelFlow: Flow<String> = store.data.map { normalize(it[KEY_CHANNEL]) }

    fun setChannel(v: String) = writeScope.launch {
        store.edit { it[KEY_CHANNEL] = normalize(v) }
    }
}
