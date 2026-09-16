package com.open.wuling.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「NFC 车控」偏好持久化（v67）。
 *
 * 存三样东西：
 * 1. [enabled]     —— 功能总开关（关闭时碰标签只提示不执行）；
 * 2. [boundSecret] —— 绑定标签时写入标签体的随机密钥，触发时与本机比对；
 * 3. [lastAction]  —— 最近一次通过 NFC 执行的动作（unlock/lock），
 *    实时状态与缓存都拿不到时，作为切换方向的兜底依据。
 *
 * 用 SharedPreferences 而非 DataStore：NFC 触发链路需要无协程的同步读取，
 * 且写入频率极低（绑定一次、每次切换写一个字符串），不需要响应式存储。
 */
@Singleton
class NfcTagPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var boundSecret: String
        get() = prefs.getString(KEY_SECRET, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SECRET, value).apply()

    /** 最近一次 NFC 动作："unlock" / "lock"，null = 从未执行过 */
    var lastAction: String?
        get() = prefs.getString(KEY_LAST_ACTION, null)
        set(value) = prefs.edit().putString(KEY_LAST_ACTION, value).apply()

    val isBound: Boolean get() = boundSecret.isNotBlank()

    /** 解绑：清空密钥与最近动作记录（enabled 开关保留） */
    fun unbind() {
        prefs.edit().remove(KEY_SECRET).remove(KEY_LAST_ACTION).apply()
    }

    companion object {
        const val PREFS_NAME = "nfc_tag_control"
        const val KEY_ENABLED = "enabled"
        const val KEY_SECRET = "bound_secret"
        const val KEY_LAST_ACTION = "last_action"
    }
}
