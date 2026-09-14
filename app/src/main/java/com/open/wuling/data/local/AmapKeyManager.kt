package com.open.wuling.data.local

import android.content.Context

/**
 * 高德地图 Key 管理器
 * 注意：高德 SDK 需要通过 Manifest 配置 Key
 * 这里的 Key 仅用于显示/记录，不影响 SDK 初始化
 */
object AmapKeyManager {
    private var currentKey: String = ""

    fun setKey(key: String) {
        currentKey = key.trim()
    }

    fun getKey(): String = currentKey

    fun hasKey(): Boolean = currentKey.isNotEmpty()

    /**
     * 从 SharedPreferences 加载 Key
     */
    fun loadFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences("wuling_config", Context.MODE_PRIVATE)
        currentKey = prefs.getString("amap_key", "") ?: ""
    }

    /**
     * 保存 Key 到 SharedPreferences
     */
    fun saveToPrefs(context: Context, key: String) {
        val prefs = context.getSharedPreferences("wuling_config", Context.MODE_PRIVATE)
        prefs.edit().putString("amap_key", key.trim()).apply()
        currentKey = key.trim()
    }

    /**
     * 清除保存的 Key
     */
    fun clearPrefs(context: Context) {
        val prefs = context.getSharedPreferences("wuling_config", Context.MODE_PRIVATE)
        prefs.edit().remove("amap_key").apply()
        currentKey = ""
    }
}
