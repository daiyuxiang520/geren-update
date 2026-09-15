package com.open.wuling.data.local

import android.content.Context
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 车辆坐标纠偏（WGS84 → GCJ-02）
 *
 * 背景：车载 TBOX 上报的 GPS 坐标通常是 WGS84（国际标准），而高德地图使用 GCJ-02（火星坐标系）。
 * 若直接把 WGS84 坐标投到高德地图上，位置会偏移约 200~600 米（方向随地区变化）。
 *
 * v50 起纠偏为**恒定开启**：
 * - 所有入口（地图投点 / 导航找车 / 分享位置 / 逆地理与天气解析）都走 [convert]，
 *   把 API 坐标视为 WGS84 并转换为 GCJ-02 后再使用。
 * - 历史遗留的 `coord_offset_enabled` 偏好项已废弃，[isOffsetEnabled] 恒返回 true，
 *   [setOffsetEnabled] 仅作兼容保留（写值不再影响任何行为）。
 *
 * 境外坐标（中国范围外）不做转换，原样返回，所以恒定开启是安全的。
 */
object CoordConverter {

    private const val PREFS_NAME = "wuling_config"
    private const val KEY_OFFSET_ENABLED = "coord_offset_enabled"

    /** 纠偏恒定开启（v50 起不再读偏好） */
    const val ALWAYS_ENABLED = true

    /**
     * 纠偏是否开启 —— 自 v50 起恒为 true。
     *
     * 保留该函数是为了兼容可能存在的调用方；判断逻辑已上收到 [convert]，
     * 调用方无需（也不应）再据此做分支。
     */
    @Deprecated("纠偏已恒定开启，请直接调用 convert()", ReplaceWith("ALWAYS_ENABLED"))
    fun isOffsetEnabled(context: Context): Boolean = ALWAYS_ENABLED

    /**
     * 兼容保留：早期版本允许用户在「位置」页关闭纠偏，该 UI 已移除。
     * 写入的值不再参与任何判断，此处仅保证老调用不崩。
     */
    @Deprecated("纠偏已恒定开启，此方法不再产生效果")
    fun setOffsetEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit() // 清掉历史残留值，避免以后有人误读
            .remove(KEY_OFFSET_ENABLED)
            .apply()
    }

    /**
     * 坐标纠偏：WGS84 → GCJ-02，恒定执行（境外坐标在 [wgs84ToGcj02] 内原样返回）。
     * @return GCJ-02 坐标（Pair<纬度, 经度>）
     */
    fun convert(context: Context, latitude: Double, longitude: Double): Pair<Double, Double> {
        return wgs84ToGcj02(latitude, longitude)
    }

    /** 中国范围粗判（纠偏仅在境内有意义） */
    fun outOfChina(lat: Double, lon: Double): Boolean {
        return lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271
    }

    /** 标准 WGS84 → GCJ-02 纠偏算法 */
    fun wgs84ToGcj02(wgsLat: Double, wgsLon: Double): Pair<Double, Double> {
        if (outOfChina(wgsLat, wgsLon)) return wgsLat to wgsLon

        val dLat = transformLat(wgsLon - 105.0, wgsLat - 35.0)
        val dLon = transformLon(wgsLon - 105.0, wgsLat - 35.0)
        val radLat = wgsLat / 180.0 * Math.PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        val offsetLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * Math.PI)
        val offsetLon = (dLon * 180.0) / (A / sqrtMagic * cos(radLat) * Math.PI)

        val gcjLat = wgsLat + offsetLat
        val gcjLon = wgsLon + offsetLon
        return gcjLat to gcjLon
    }

    /** GCJ-02 → WGS84（反向近似，用于调试对比） */
    fun gcj02ToWgs84(gcjLat: Double, gcjLon: Double): Pair<Double, Double> {
        if (outOfChina(gcjLat, gcjLon)) return gcjLat to gcjLon
        val (lat, lon) = wgs84ToGcj02(gcjLat, gcjLon)
        return (gcjLat * 2 - lat) to (gcjLon * 2 - lon)
    }

    /** 估算两点间直线距离（米），用于日志展示纠偏幅度 */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = abs(lat1 - lat2)
        val dLon = abs(lon1 - lon2)
        return kotlin.math.sqrt(dLat * dLat + dLon * dLon) * 111000.0
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y +
            0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * Math.PI) + 40.0 * sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * Math.PI) + 320.0 * sin(y * Math.PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLon(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y +
            0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * Math.PI) + 40.0 * sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * Math.PI) + 300.0 * sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
        return ret
    }

    private const val A = 6378245.0               // 长半轴
    private const val EE = 0.00669342162296594323 // 偏心率平方
}
