package com.open.wuling.data.local

import android.content.Context
import com.open.wuling.data.model.Vehicle
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 停车记录（v63）。
 *
 * 官方网关没有行程/轨迹接口（EnergyScreen 探测结论），所以这里做的是**本地观测式记录**：
 * 每次车辆状态刷新时看一眼当前坐标，与最近一次记录比较——
 * - 距离 < 50m：认为还在同一个地方，只更新 lastSeen（用于算停留时长）；
 * - 距离 ≥ 50m：认为车辆移动过，新增一条停车点。
 *
 * 存的是 TBOX 上报的**原始坐标**（WGS84），与 vehicle.location 一致；
 * 展示/导航时再走 CoordConverter 纠偏，避免二次转换叠加误差。
 *
 * 数据量很小（最多 50 条），直接用 SharedPreferences + JSON，不引入数据库。
 */
object ParkingHistoryStore {

    private const val PREFS = "parking_history"
    private const val KEY_POINTS = "points"
    private const val MAX_POINTS = 50
    private const val SAME_SPOT_METERS = 50.0

    data class Point(
        val lat: Double,
        val lon: Double,
        /** 首次观测到该位置的时间（毫秒） */
        val firstSeen: Long,
        /** 最后一次仍在该位置的时间（毫秒） */
        val lastSeen: Long,
        /** 文字地点名（逆地理得到，可为空：未配置 Key / 网络失败 / 高速途经点不查） */
        val name: String? = null
    )

    /** 记录一次观测。坐标缺失时静默跳过（接口偶发不返回经纬度，属正常） */
    fun record(context: Context, vehicle: Vehicle?) {
        val loc = vehicle?.location ?: return
        record(context, loc.latitude, loc.longitude)
    }

    fun record(context: Context, lat: Double, lon: Double) {
        if (lat == 0.0 && lon == 0.0) return
        // v84：与 saveName 共用 writeLock，避免「状态刷新记录」与「逆地理写名」并发时
        //      读-改-写互相覆盖（丢失更新/脏读）。
        synchronized(writeLock) {
            val now = System.currentTimeMillis()
            val points = load(context).toMutableList()

            // 与最近一条比较：50m 内视为同一停车点，只续期 lastSeen
            val last = points.firstOrNull()
            if (last != null && distanceMeters(last.lat, last.lon, lat, lon) < SAME_SPOT_METERS) {
                points[0] = last.copy(lastSeen = now)
            } else {
                points.add(0, Point(lat, lon, now, now))
            }

            while (points.size > MAX_POINTS) points.removeAt(points.size - 1)
            save(context, points)
        }
    }

    /** 最近停车点在前 */
    fun load(context: Context): List<Point> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_POINTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<Point>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    Point(
                        lat = o.optDouble("lat", 0.0),
                        lon = o.optDouble("lon", 0.0),
                        firstSeen = o.optLong("first", 0L),
                        lastSeen = o.optLong("last", 0L),
                        name = o.optString("name", "").ifEmpty { null }
                    )
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_POINTS).apply()
    }

    // ==================== 地点名补全（逆地理） ====================

    /**
     * 仅对"停过且缺地名"的点做逆地理，避免高速途经点打爆高德配额。
     */
    private const val STAY_THRESHOLD_MS = 120_000L

    private val writeLock = Any()

    /**
     * 该点是否需要补全地名：已停 ≥2 分钟且尚无 name。
     * 与 [formatStayDuration] 的"途经(<60s)"语义区分：高速上连串跨 50m 新建的点停留≈0，全部被挡掉。
     */
    fun shouldEnrich(p: Point): Boolean = p.name == null && (p.lastSeen - p.firstSeen) >= STAY_THRESHOLD_MS

    /**
     * 纠偏(WGS84→GCJ-02) + 逆地理，返回文字地名；无 Key / 失败 / 坐标异常返回 null。
     * 同步方法，调用方须在 IO 线程执行（如 `withContext(Dispatchers.IO)`）。
     */
    fun enrichPoint(context: Context, p: Point): String? {
        val key = AmapKeyManager.getWeatherKey()
        if (key.isBlank()) return null
        val (gcjLat, gcjLon) = CoordConverter.convert(context, p.lat, p.lon)
        val res = AmapGeoResolver.resolve(key, gcjLat, gcjLon, withWeather = false)
        return res.address
    }

    /** 按坐标匹配写回 name（与列表顺序无关；并发写由 [writeLock] 保护） */
    fun saveName(context: Context, p: Point, name: String) {
        synchronized(writeLock) {
            val points = load(context).toMutableList()
            val idx = points.indexOfFirst { it.lat == p.lat && it.lon == p.lon }
            if (idx >= 0 && points[idx].name == null) {
                points[idx] = points[idx].copy(name = name)
                save(context, points)
            }
        }
    }

    private fun save(context: Context, points: List<Point>) {
        val arr = JSONArray()
        points.forEach { p ->
        arr.put(
            JSONObject()
                .put("lat", p.lat)
                .put("lon", p.lon)
                .put("first", p.firstSeen)
                .put("last", p.lastSeen)
                .put("name", p.name ?: "")
        )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_POINTS, arr.toString())
            .apply()
    }

    /** 球面距离（米），精度对本场景（判断"是不是同一个地方"）足够 */
    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a))
    }
}
