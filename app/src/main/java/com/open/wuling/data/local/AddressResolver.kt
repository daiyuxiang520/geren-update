package com.open.wuling.data.local

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * 逆地理编码：坐标 → 文字地址
 *
 * 服务端只下发经纬度（address 字段一直为空），这里用 locate.ms.tn 免费接口补齐
 * （与 iOS「菱宝 Widget」脚本同源接口，长期稳定）：
 *
 * GET https://locate.ms.tn/get_location?lng=<经度>&lat=<纬度>
 * 响应: {"success": true, "address": "广西壮族自治区柳州市..."}
 */
object AddressResolver {

    private const val TAG = "AddressResolver"
    private const val GEO_API_URL = "https://locate.ms.tn/get_location"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000

    /**
     * 内存缓存：坐标（4 位小数，约 11 米精度）→ 地址。
     * 车静止时每 30 秒自动刷新不会重复请求；车移动时短距离位移也命中缓存。
     */
    private val cache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean =
            size > 32
    }

    /**
     * 同步请求（调用方需在 IO 线程执行）。
     * 成功返回地址文本；失败（网络异常/无效响应/境外无结果）返回 null，由调用方决定降级展示。
     */
    fun resolve(lat: Double, lon: Double): String? {
        val key = String.format(Locale.US, "%.4f,%.4f", lat, lon)
        synchronized(cache) { cache[key]?.let { return it } }
        return try {
            val url = "$GEO_API_URL?lng=$lon&lat=$lat"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "wuling-app")
            val code = conn.responseCode
            val body = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }
            conn.disconnect()

            val json = JSONObject(body)
            val address = if (json.optBoolean("success", false)) {
                json.optString("address", "").trim()
            } else ""

            if (address.isNotEmpty()) {
                synchronized(cache) { cache[key] = address }
                address
            } else {
                Log.d(TAG, "resolveAddress: empty address (http=$code)")
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "resolveAddress failed: ${e.message}")
            null
        }
    }
}
