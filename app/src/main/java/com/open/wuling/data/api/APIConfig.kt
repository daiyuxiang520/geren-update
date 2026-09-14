package com.open.wuling.data.api

import android.util.Log
import com.open.wuling.BuildConfig

/**
 * API 配置 - 敏感信息通过 BuildConfig 注入，不在源码中硬编码
 */
object APIConfig {
    private const val TAG = "APIConfig"

    // 运行时设置（从持久化存储读取）
    @Volatile
    var accessToken: String = ""
        private set

    /**
     * 设置 Access Token
     * 注意：此方法仅供内部使用，Token 存储由 TokenStore 管理
     */
    fun setAccessToken(token: String) {
        accessToken = token
    }

    // 以下常量从 BuildConfig 读取（值来源于 local.properties，不提交到 VCS）
    val clientId: String get() = BuildConfig.CLIENT_ID
    val clientSecret: String get() = BuildConfig.CLIENT_SECRET
    val appCode: String get() = BuildConfig.APP_CODE
    val appVersion: String get() = BuildConfig.APP_VERSION
    val baseURL: String get() = BuildConfig.BASE_URL
    val deviceImei: String get() = BuildConfig.DEVICE_IMEI
    val deviceModel: String get() = BuildConfig.DEVICE_MODEL
    val deviceBrand: String get() = BuildConfig.DEVICE_BRAND
    val apiVersion: String get() = BuildConfig.API_VERSION
    val apiVersionCode: String get() = BuildConfig.API_VERSION_CODE

    const val system: String = "android"
    const val systemVersion: String = "10"

    val isConfigured: Boolean
        get() = accessToken.isNotEmpty() && baseURL.isNotEmpty()

    init {
        // 仅在 Debug 模式下打印非敏感信息
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "APIConfig initialized")
            Log.d(TAG, "Base URL: $baseURL")
            Log.d(TAG, "App Version: $appVersion")
            Log.d(TAG, "API Version: $apiVersion")
            Log.d(TAG, "Device: $deviceBrand $deviceModel")
            Log.d(TAG, "isConfigured: $isConfigured")
        }
    }
}
