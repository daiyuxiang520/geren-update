package com.open.wuling.data.local

import com.open.wuling.util.AppLogger
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.round

/**
 * Open-Meteo 实况天气（**无需任何 Key**，注册/配额/控制台配置一概不需要）。
 *
 * ### 为什么需要它
 * 高德天气要求在控制台单独开通「天气服务」，未开通会返回
 * `10002 SERVICE_NOT_AVAILABLE`，用户往往难以自行定位。
 * Open-Meteo 直接按**经纬度**查询（连 adcode 都不需要），
 * 作为兜底源可以保证：**只要车辆有坐标，天气就一定能显示**。
 *
 * ### 接口
 * ```
 * GET https://api.open-meteo.com/v1/forecast
 *     ?latitude=<纬度>&longitude=<经度>
 *     &current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m
 *     &timezone=Asia/Shanghai
 * ```
 * 返回 `current` 对象：`temperature_2m`(℃)、`relative_humidity_2m`(%)、
 * `weather_code`(WMO 码，见 [WmoCodeMap])、`wind_speed_10m`(km/h)、`wind_direction_10m`(度)、`time`。
 *
 * ⚠️ 坐标系：Open-Meteo 用 WGS84，这里拿到的是展示用的坐标（可能已转 GCJ-02）。
 *    两者相差约 300~600 米，而天气是区域级数据，**对结果没有实际影响**，故不做反算。
 *
 * 免费额度：非商业用途免费（约 10000 次/日），个人使用绰绰有余。
 */
object OpenMeteoResolver {

    private const val TAG = "OpenMeteoResolver"
    private const val BASE_URL = "https://api.open-meteo.com/v1/forecast"
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000

    /** 缓存（key: "纬度1位,经度1位#小时序号"），避免车辆静止时重复请求 */
    private val cache = object : LinkedHashMap<String, WeatherInfo>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WeatherInfo>): Boolean =
            size > 16
    }

    /**
     * 查询实况天气（同步，调用方需在 IO 线程执行）。
     * @param lat 纬度
     * @param lon 经度
     * @return 天气信息；失败返回 null
     */
    fun query(lat: Double, lon: Double): WeatherInfo? {
        val cacheKey = String.format(Locale.US, "%.1f,%.1f#%d", lat, lon, System.currentTimeMillis() / 3_600_000L)
        synchronized(cache) { cache[cacheKey] }?.let { return it }

        val info = try {
            val url = "$BASE_URL?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m" +
                "&timezone=Asia%2FShanghai"
            AppLogger.d(TAG, "➡️ 请求 Open-Meteo: lat=$lat, lon=$lon")
            val json = getJson(url)
            if (json == null) {
                AppLogger.w(TAG, "⬅️ Open-Meteo 无响应")
                return null
            }
            parse(json)
        } catch (e: Exception) {
            AppLogger.e(TAG, "❌ Open-Meteo 查询异常: ${e.message}")
            null
        }

        if (info == null) return null
        synchronized(cache) { cache[cacheKey] = info }
        AppLogger.i(
            TAG,
            "✅ Open-Meteo: ${info.weather} ${info.temperature}°C 湿度${info.humidity}%"
        )
        return info
    }

    /** 解析 `current` 对象 → [WeatherInfo] */
    private fun parse(json: JSONObject): WeatherInfo? {
        val current = json.optJSONObject("current") ?: run {
            AppLogger.w(TAG, "⬅️ Open-Meteo 返回缺少 current 字段")
            return null
        }
        val code = current.optInt("weather_code", -1)
        val temperature = if (current.has("temperature_2m")) {
            round(current.optDouble("temperature_2m", Double.NaN)).toInt().toString()
        } else ""
        val humidity = if (current.has("relative_humidity_2m")) {
            current.optInt("relative_humidity_2m", -1).takeIf { it >= 0 }?.toString().orEmpty()
        } else ""
        val windDeg = current.optDouble("wind_direction_10m", Double.NaN)
        val windKmh = current.optDouble("wind_speed_10m", Double.NaN)

        return WeatherInfo(
            weather = WmoCodeMap.text(code),
            temperature = temperature,
            humidity = humidity,
            windDirection = if (windDeg.isNaN()) "" else windDirectionText(windDeg),
            windPower = if (windKmh.isNaN()) "" else beaufortLevel(windKmh),
            // "2026-09-15T00:45" → "2026-09-15 00:45"
            reportTime = current.optString("time", "").replace('T', ' '),
            city = "",
            source = WeatherInfo.Source.OPEN_METEO
        )
    }

    /**
     * 风向角度（度，气象 convention：风**从**哪个方向吹来）→ 8 方位中文。
     * 与高德返回风格（「东北风」「东南风」）保持一致。
     */
    fun windDirectionText(degrees: Double): String {
        val d = ((degrees % 360) + 360) % 360   // 归一到 [0,360)
        return when {
            d >= 337.5 || d < 22.5 -> "北风"
            d < 67.5 -> "东北风"
            d < 112.5 -> "东风"
            d < 157.5 -> "东南风"
            d < 202.5 -> "南风"
            d < 247.5 -> "西南风"
            d < 292.5 -> "西风"
            else -> "西北风"
        }
    }

    /**
     * 风速 km/h → 蒲福风级（0~12），与高德 `windpower` 口径一致。
     * 阈值参照蒲福风级标准换算表。
     */
    fun beaufortLevel(kmh: Double): String {
        val level = when {
            kmh < 1 -> 0
            kmh < 6 -> 1
            kmh < 12 -> 2
            kmh < 20 -> 3
            kmh < 29 -> 4
            kmh < 39 -> 5
            kmh < 50 -> 6
            kmh < 62 -> 7
            kmh < 75 -> 8
            kmh < 89 -> 9
            kmh < 103 -> 10
            kmh < 118 -> 11
            else -> 12
        }
        return level.toString()
    }

    private fun getJson(url: String): JSONObject? {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
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
            if (body.isBlank()) return null
            return JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }
}
