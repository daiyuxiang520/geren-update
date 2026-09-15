package com.open.wuling.data.local

import android.content.Context

/**
 * 高德地图 Key 管理器
 * 注意：高德 SDK 需要通过 Manifest 配置 Key
 * 这里的 Key 仅用于显示/记录，不影响 SDK 初始化
 */
object AmapKeyManager {
    private var currentKey: String = ""

    /** 天气查询专用 Key（高德「Web 服务」类型，与地图的「Web JS API」Key 不通用） */
    private var currentWeatherKey: String = ""

    private const val KEY_MAP = "amap_key"
    private const val KEY_WEATHER = "amap_weather_key"
    private const val PREFS = "wuling_config"

    fun setKey(key: String) {
        currentKey = key.trim()
    }

    fun getKey(): String = currentKey

    fun hasKey(): Boolean = currentKey.isNotEmpty()

    // ============== 天气 Key（独立存取，互不干扰） ==============

    fun setWeatherKey(key: String) {
        currentWeatherKey = key.trim()
    }

    /** 天气 Key（优先内存值；未加载时读 prefs） */
    fun getWeatherKey(): String = currentWeatherKey

    fun hasWeatherKey(): Boolean = currentWeatherKey.isNotEmpty()

    fun loadWeatherKeyFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        currentWeatherKey = prefs.getString(KEY_WEATHER, "") ?: ""
    }

    fun saveWeatherKeyToPrefs(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_WEATHER, key.trim()).apply()
        currentWeatherKey = key.trim()
    }

    fun clearWeatherKeyPrefs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_WEATHER).apply()
        currentWeatherKey = ""
    }

    /**
     * 从 SharedPreferences 加载 Key（地图 + 天气）
     */
    fun loadFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        currentKey = prefs.getString(KEY_MAP, "") ?: ""
        currentWeatherKey = prefs.getString(KEY_WEATHER, "") ?: ""
    }

    /**
     * 保存 Key 到 SharedPreferences
     */
    fun saveToPrefs(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_MAP, key.trim()).apply()
        currentKey = key.trim()
    }

    /**
     * 清除保存的 Key
     */
    fun clearPrefs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_MAP).apply()
        currentKey = ""
    }
}
