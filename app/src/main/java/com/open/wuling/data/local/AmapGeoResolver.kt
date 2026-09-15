package com.open.wuling.data.local

import com.open.wuling.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * 高德「Web 服务」统一解析器：一次逆地理编码（regeo）同时拿到 **文字地址** 与 **adcode**，
 * 再由 adcode 查询 **实况天气**。
 *
 * ### 为什么合并成一个类（v33 重构背景）
 * v31/v32 里地址（[AddressResolver]）与天气（[AmapWeatherResolver]）各自独立发 regeo 请求：
 *   - 位置页两个 `LaunchedEffect` key 相同、互相干扰，30 秒数据刷新时被反复取消；
 *   - 天气链路更长（regeo + weather 两次串行请求），更容易在刷新周期内被 cancel；
 *   - 同一 Key 并发请求还可能被高德限流。
 * 表现为「地址能看到、天气看不到」。现合并为一次 regeo + 一次 weather，
 * 由调用方在**单个协程**里串行执行，请求数从 3 次/轮降到 2 次/轮。
 *
 * ### 数据链路
 * ```
 *   GCJ-02 坐标 ──regeo(extensions=base)──▶ formatted_address + adcode
 *                                              │
 *                                              └──weather(extensions=base)──▶ 实况天气
 * ```
 *
 * ⚠️ 坐标系：高德 API 使用 **GCJ-02（火星坐标）**，必须传入**纠偏后**坐标，
 *    请先经 [CoordConverter.convert] 处理（见 LocationScreen 的 displayLocation）。
 *
 * ⚠️ Key 类型：必须是高德 **「Web 服务」** 类型 Key，且控制台需分别绑定
 *    「逆地理编码」与「天气查询」两个服务；它与地图用的「Web JS API」Key 不通用。
 *
 * ### 缓存
 *   - 坐标（4 位小数，约 11 米）→ (地址, adcode)，LRU 32 条：车停着每 30 秒刷新不重复请求；
 *   - adcode + 小时 → 天气，LRU 32 条：实况每小时更新，1 小时内复用足够，避免打爆配额。
 */
object AmapGeoResolver {

    private const val TAG = "AmapGeoResolver"
    private const val REGEO_URL = "https://restapi.amap.com/v3/geocode/regeo"
    private const val WEATHER_URL = "https://restapi.amap.com/v3/weather/weatherInfo"
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000

    // ==================== 对外数据模型 ====================

    // 天气统一用 WeatherInfo（与 Open-Meteo 兜底源共用同一结构），
    // 便于 UI 无差别渲染，并在高德不可用时无缝降级。

    /**
     * 解析结果。
     *
     * @param address   文字地址（优先高德 formatted_address，为空时按行政区拼装），失败为 null
     * @param adcode    行政区编码，用于天气查询，失败为 null
     * @param weather   实况天气；未配置天气服务 / 查询失败时为 null
     * @param geoError  逆地理失败原因（null 表示成功）。用于 UI 明确提示，避免静默。
     * @param weatherError 天气失败原因（null 表示成功；地址成功但天气失败时单独提示）
     */
    data class Result(
        val address: String? = null,
        val adcode: String? = null,
        val weather: WeatherInfo? = null,
        val geoError: GeoError? = null,
        val weatherError: GeoError? = null
    ) {
        val hasAddress: Boolean get() = !address.isNullOrEmpty()
        val hasWeather: Boolean get() = weather != null
    }

    /**
     * 失败原因分类。
     * 高德错误码：10001 INVALID_USER_KEY、10002 SERVICE_NOT_AVAILABLE、
     * 10003 DAILY_QUERY_OVER_LIMIT、10009 USERKEY_PLAT_NOMATCH、10012 NO_PERMISSION。
     */
    enum class GeoError {
        /** 未配置 Key */
        NO_KEY,

        /** Key 无效 / 类型不符 / 未绑定对应服务 */
        INVALID_KEY,

        /** 配额超限或频率受限 */
        OVER_LIMIT,

        /** 逆地理成功但未返回 adcode（境外/海上等） */
        NO_ADCODE,

        /** 网络异常、超时、响应解析失败 */
        NETWORK;

        /** 面向用户的提示文案 */
        fun message(service: String): String = when (this) {
            NO_KEY -> "请先配置「高德天气 Key」（需「Web 服务」类型）"
            INVALID_KEY -> "高德 Key 无效，或未在控制台绑定「$service」服务"
            OVER_LIMIT -> "高德「$service」今日配额已用尽，请稍后重试"
            NO_ADCODE -> "该位置未返回行政区编码，无法查询天气"
            NETWORK -> "$service 查询失败，请检查网络后重试"
        }
    }

    // ==================== 缓存 ====================

    /** 坐标 → (地址, adcode) */
    private val geoCache = object : LinkedHashMap<String, Pair<String, String>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<String, String>>): Boolean =
            size > 32
    }

    /** "adcode#小时序号" → 天气 */
    private val weatherCache = object : LinkedHashMap<String, WeatherInfo>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WeatherInfo>): Boolean =
            size > 32
    }

    /**
     * 最后一次成功拿到的天气。
     * 网络临时抖动/单次请求失败时用它兜底，避免界面「闪空」——
     * 天气是小时级数据，短暂复用上一份结果远好过显示空白。
     */
    @Volatile
    private var lastWeather: WeatherInfo? = null

    // ==================== 对外入口 ====================

    /**
     * 一次解析：坐标 → 地址 + adcode，随后按需查询天气。
     *
     * **同步方法，调用方必须放到 IO 线程执行**（如 `withContext(Dispatchers.IO)`）。
     *
     * @param key        高德「Web 服务」类型 Key（逆地理 + 天气共用）
     * @param gcjLat     GCJ-02 纬度（纠偏后）
     * @param gcjLon     GCJ-02 经度（纠偏后）
     * @param withWeather 是否继续查询天气（false 时只解析地址，省一次请求）
     * @param forceRefresh 忽略缓存强制刷新（用户手动点击刷新时用）
     */
    fun resolve(
        key: String,
        gcjLat: Double,
        gcjLon: Double,
        withWeather: Boolean = true,
        forceRefresh: Boolean = false
    ): Result {
        if (key.isBlank()) return Result(geoError = GeoError.NO_KEY)

        val coordKey = String.format(Locale.US, "%.4f,%.4f", gcjLat, gcjLon)

        // ---- ① 地址 + adcode（带缓存）----
        val cached = if (forceRefresh) null else synchronized(geoCache) { geoCache[coordKey] }
        val geo: Pair<String, String> = cached ?: when (val r = regeo(key, gcjLon, gcjLat)) {
            is GeoOutcome.Ok -> {
                synchronized(geoCache) { geoCache[coordKey] = r.address to r.adcode }
                r.address to r.adcode
            }
            // 逆地理失败时同样拿上次天气兜底：地址没了但天气还能看，
            // 总好过整块信息一起消失。
            is GeoOutcome.Err -> return Result(geoError = r.error, weather = lastWeather)
        }

        val (address, adcode) = geo
        if (!withWeather) {
            return Result(address = address.ifEmpty { null }, adcode = adcode.ifEmpty { null })
        }

        // ---- ② 天气（adcode 为空则无法继续，明确提示而非静默）----
        if (adcode.isEmpty()) {
            return Result(address = address.ifEmpty { null }, weatherError = GeoError.NO_ADCODE)
        }

        val hourKey = "$adcode#${System.currentTimeMillis() / 3_600_000L}"
        if (!forceRefresh) {
            synchronized(weatherCache) { weatherCache[hourKey] }?.let {
                return Result(address = address.ifEmpty { null }, adcode = adcode, weather = it)
            }
        }

        return when (val w = fetchWeather(key, adcode)) {
            is WeatherOutcome.Ok -> {
                synchronized(weatherCache) { weatherCache[hourKey] = w.weather }
                lastWeather = w.weather
                Result(address = address.ifEmpty { null }, adcode = adcode, weather = w.weather)
            }
            is WeatherOutcome.Err -> {
                // 单次失败时先用上次结果兜底，避免界面闪空；
                // 失败原因照常透出，调用方可自行决定是否提示。
                val fallback = lastWeather
                if (fallback != null) {
                    AppLogger.w(TAG, "天气查询失败，暂用上次结果兜底")
                }
                Result(
                    address = address.ifEmpty { null },
                    adcode = adcode,
                    weather = fallback,
                    weatherError = if (fallback != null) null else w.error
                )
            }
        }
    }

    /** 清空全部缓存（Key 变更 / 用户手动刷新时可调用） */
    fun clearCache() {
        synchronized(geoCache) { geoCache.clear() }
        synchronized(weatherCache) { weatherCache.clear() }
        lastWeather = null
    }

    // ==================== 逆地理编码 ====================

    private sealed class GeoOutcome {
        class Ok(val address: String, val adcode: String) : GeoOutcome()
        class Err(val error: GeoError) : GeoOutcome()
    }

    /**
     * 逆地理编码：GCJ-02 坐标 → 文字地址 + adcode。
     * `extensions=base` 返回基本信息，同时含 formatted_address 与 addressComponent.adcode。
     */
    private fun regeo(key: String, gcjLon: Double, gcjLat: Double): GeoOutcome {
        AppLogger.d(TAG, "➡️ 高德逆地理请求: location=$gcjLon,$gcjLat (key=${maskKey(key)})")
        val json = try {
            getJson("$REGEO_URL?location=$gcjLon,$gcjLat&extensions=base&key=$key")
        } catch (e: Exception) {
            AppLogger.e(TAG, "❌ 高德逆地理异常: ${e.message}")
            return GeoOutcome.Err(GeoError.NETWORK)
        }
        if (json == null) {
            AppLogger.w(TAG, "⬅️ 高德逆地理无响应")
            return GeoOutcome.Err(GeoError.NETWORK)
        }

        if (json.optString("status") != "1") {
            val info = json.optString("info")
            val infocode = json.optString("infocode")
            AppLogger.w(TAG, "⬅️ 高德逆地理失败: infocode=$infocode info=$info")
            return GeoOutcome.Err(classify(info, infocode))
        }

        val regeocode = json.optJSONObject("regeocode")
            ?: return GeoOutcome.Err(GeoError.NETWORK)
        val component = regeocode.optJSONObject("addressComponent")

        val address = buildAddress(regeocode)
        val adcode = optStringSafe(component, "adcode")

        if (address.isEmpty() && adcode.isEmpty()) {
            AppLogger.w(TAG, "⬅️ 高德逆地理返回空地址与空 adcode")
            return GeoOutcome.Err(GeoError.NETWORK)
        }
        AppLogger.i(TAG, "✅ 高德逆地理: adcode=$adcode 地址=${address.take(30)}")
        return GeoOutcome.Ok(address, adcode)
    }

    /**
     * 组装地址：优先用高德返回的 formatted_address；
     * 若为空（部分场景不返回），退化为「省+市+区+街道+门牌」拼装。
     */
    private fun buildAddress(regeocode: JSONObject): String {
        val formatted = optStringSafe(regeocode, "formatted_address")
        if (formatted.isNotEmpty()) return formatted

        val c = regeocode.optJSONObject("addressComponent") ?: return ""
        val sb = StringBuilder()
        // 省（直辖市时 province 已含「市」，city 为空需跳过重复）
        val province = optStringSafe(c, "province")
        val city = optStringSafe(c, "city")
        val district = optStringSafe(c, "district")
        sb.append(province)
        if (city.isNotEmpty() && !province.endsWith(city)) sb.append(city)
        sb.append(district)

        // 街道 + 门牌
        c.optJSONObject("streetNumber")?.let { sn ->
            val street = optStringSafe(sn, "street")
            val number = optStringSafe(sn, "number")
            sb.append(street).append(number)
        }
        // 若仍过短，补 township（乡镇/街道名）
        if (sb.length < 6) sb.append(optStringSafe(c, "township"))

        return sb.toString().trim()
    }

    // ==================== 天气查询 ====================

    private sealed class WeatherOutcome {
        class Ok(val weather: WeatherInfo) : WeatherOutcome()
        class Err(val error: GeoError) : WeatherOutcome()
    }

    /** 天气查询：adcode → 实况（extensions=base 返回 lives 实况数组） */
    private fun fetchWeather(key: String, adcode: String): WeatherOutcome {
        AppLogger.d(TAG, "➡️ 高德天气请求: city=$adcode (key=${maskKey(key)})")
        val json = try {
            getJson("$WEATHER_URL?city=$adcode&extensions=base&key=$key")
        } catch (e: Exception) {
            AppLogger.e(TAG, "❌ 高德天气异常: ${e.message}")
            return WeatherOutcome.Err(GeoError.NETWORK)
        }
        if (json == null) {
            AppLogger.w(TAG, "⬅️ 高德天气无响应")
            return WeatherOutcome.Err(GeoError.NETWORK)
        }

        if (json.optString("status") != "1") {
            val info = json.optString("info")
            val infocode = json.optString("infocode")
            // 原始错误码写进调试日志：用户在「我的 → 调试日志」可直接看到是 10002（未开通服务）
            // 还是 10001（Key 无效）/ 10003（配额超限），无需猜测。
            AppLogger.w(TAG, "⬅️ 高德天气失败: infocode=$infocode info=$info")
            return WeatherOutcome.Err(classify(info, infocode))
        }

        val live = json.optJSONArray("lives")?.optJSONObject(0)
        if (live == null) {
            AppLogger.w(TAG, "⬅️ 高德天气返回缺少 lives 字段")
            return WeatherOutcome.Err(GeoError.NETWORK)
        }

        val w = WeatherInfo(
            weather = live.optString("weather", ""),
            temperature = live.optString("temperature", ""),
            humidity = live.optString("humidity", ""),
            windDirection = live.optString("winddirection", ""),
            windPower = live.optString("windpower", ""),
            reportTime = live.optString("reporttime", ""),
            city = live.optString("city", ""),
            source = WeatherInfo.Source.AMAP
        )
        AppLogger.i(TAG, "✅ 高德天气: ${w.weather} ${w.temperature}°C 湿度${w.humidity}%")
        return WeatherOutcome.Ok(w)
    }

    // ==================== 工具 ====================

    /** 高德错误 → 分类（便于 UI 给出针对性提示） */
    private fun classify(info: String, infocode: String): GeoError {
        if (infocode in setOf("10003", "10004", "10014", "10019", "10020", "10021")) {
            return GeoError.OVER_LIMIT
        }
        if (infocode in setOf("10001", "10002", "10009", "10012", "10013")) {
            return GeoError.INVALID_KEY
        }
        val lower = info.lowercase(Locale.US)
        return when {
            lower.contains("over_limit") || lower.contains("daily_query") -> GeoError.OVER_LIMIT
            lower.contains("invalid_user_key") || lower.contains("userkey") ||
                lower.contains("no_permission") || lower.contains("service_not_available") ||
                lower.contains("invalid_key") -> GeoError.INVALID_KEY
            else -> GeoError.NETWORK
        }
    }

    /** Key 脱敏：只保留前 4 后 2 位，便于日志排查又不会泄露完整密钥 */
    private fun maskKey(key: String): String {
        if (key.length <= 8) return "****"
        return "${key.take(4)}****${key.takeLast(2)}"
    }

    /**
     * 安全读字符串字段。
     * 高德规则：字段有值时为字符串，无值时可能返回空数组 `[]`，
     * `optString` 对数组会返回其 JSON 文本（如 `"[]"`），故需显式判空。
     */
    private fun optStringSafe(obj: JSONObject?, name: String): String {
        if (obj == null) return ""
        val v = obj.opt(name)
        return when (v) {
            null, JSONObject.NULL -> ""
            is String -> v.trim()
            is JSONArray -> ""   // 空数组 / 数组型返回值在此场景无意义
            else -> v.toString().trim()
        }
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
