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
        val lastSeen: Long
    )

    /** 记录一次观测。坐标缺失时静默跳过（接口偶发不返回经纬度，属正常） */
    fun record(context: Context, vehicle: Vehicle?) {
        val loc = vehicle?.location ?: return
        record(context, loc.latitude, loc.longitude)
    }

    fun record(context: Context, lat: Double, lon: Double) {
        if (lat == 0.0 && lon == 0.0) return
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
                        lastSeen = o.optLong("last", 0L)
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

    private fun save(context: Context, points: List<Point>) {
        val arr = JSONArray()
        points.forEach { p ->
            arr.put(
                JSONObject()
                    .put("lat", p.lat)
                    .put("lon", p.lon)
                    .put("first", p.firstSeen)
                    .put("last", p.lastSeen)
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
