package com.open.wuling.util

/**
 * 格式化工具类
 * 提供常用的数据格式化函数，供全项目复用
 */
object FormatUtils {

    // physicsEngine: 1=纯电 2=油混 3=插混 4=增程
    private val PHYSICS_ENGINE_MAP = mapOf(
        1 to "纯电动", 2 to "油电混动", 3 to "插电混动", 4 to "插电混动"
    )

    fun getPowerTypeName(engineType: Int): String = when (engineType) {
        0 -> "插电混动"
        1 -> "纯电动"
        2 -> "增程"
        else -> "未知"
    }

    /**
     * 获取动力类型显示名称（多源综合判断）。
     * 优先 energyKind → physicsEngine → engineType。
     */
    fun getPowerTypeDisplay(vehicle: com.open.wuling.data.model.Vehicle): String {
        val info = vehicle.carInfo
        return when (vehicle.energyKind) {
            "hybrid" -> {
                val pe = info?.physicsEngine ?: 0
                PHYSICS_ENGINE_MAP[pe] ?: "混动"
            }
            "fuel" -> "燃油"
            "ev" -> "纯电动"
            else -> "纯电动"
        }
    }

    /**
     * 获取电池状态文本
     */
    fun getBatteryStatusText(batteryStatus: String): String = when (batteryStatus) {
        "0" -> "正常"
        "1" -> "低电量"
        "2" -> "极低电量"
        else -> batteryStatus.ifEmpty { "未知" }
    }

    /**
     * 获取档位名称（五菱API档位映射）
     * 10=P驻车, 12=D前进, 13=N空挡, 14=R倒车
     */
    fun getGearName(gearStatus: String): String = when (gearStatus) {
        "10" -> "P (驻车)"
        "12" -> "D (前进)"
        "13" -> "N (空挡)"
        "14" -> "R (倒车)"
        else -> "N (空挡)"
    }

    /**
     * 获取钥匙状态文本（五菱API映射）
     * 0=无钥匙, 1=已连接, 2=已启动
     */
    fun getKeyStatusText(keyStatus: String): String = when (keyStatus) {
        "0" -> "无钥匙"
        "1" -> "已连接"
        "2" -> "已启动"
        else -> "未知"
    }

    /**
     * 布尔值转"打开/关闭"
     */
    fun getOpenText(isOpen: Boolean): String = if (isOpen) "打开" else "关闭"

    /**
     * 获取空调模式文本
     * 0=关闭, 1=制冷, 2=制热
     */
    fun getClimateModeText(acStatus: Int): String = when (acStatus) {
        0 -> "关闭"
        1 -> "制冷"
        2 -> "制热"
        else -> "关闭"
    }

    /**
     * 获取空调模式文本（基于模式字符串）
     */
    fun getClimateModeText(mode: String): String = when (mode) {
        "cool" -> "制冷"
        "heat" -> "制热"
        else -> "关闭"
    }

    /**
     * 布尔值转"锁定/未锁"
     */
    fun getLockText(isLocked: Boolean): String = if (isLocked) "锁定" else "未锁"

    /**
     * 布尔值转"是/否"
     */
    fun getYesNo(value: Boolean): String = if (value) "是" else "否"

    /**
     * 布尔值转"开启/关闭"
     */
    fun getOnOff(value: Boolean): String = if (value) "开启" else "关闭"

    /**
     * 格式化胎压值
     * @param value 胎压值（bar）
     * @return 格式化后的字符串，如果值为0则返回"--"
     */
    fun formatTirePressure(value: Double): String = when {
        value <= 0.0 -> "--"
        else -> String.format("%.2f", value)
    }

    /**
     * 格式化充电时间
     * @param minutes 剩余分钟数，null表示未知
     * @return 格式化后的字符串
     */
    fun formatChargingTime(minutes: Int?): String = when {
        minutes == null -> "-- 分钟"
        minutes <= 0 -> "-- 分钟"
        minutes < 60 -> "$minutes 分钟"
        else -> {
            val hours = minutes / 60
            val mins = minutes % 60
            if (mins > 0) "${hours} 小时 ${mins} 分钟" else "${hours} 小时"
        }
    }

    /**
     * 格式化时间戳为日期字符串
     */
    fun formatDate(timestamp: Long): String {
        if (timestamp <= 0) return "--"
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        } catch (e: Exception) {
            "--"
        }
    }

    /**
     * 格式化坐标值
     */
    fun formatCoordinate(value: Double?, precision: Int = 6): String =
        value?.let { "%.${precision}f".format(it) } ?: "--"

    /**
     * 格式化电池/电流值（整数）
     */
    fun formatIntValue(value: Double): String =
        if (value == 0.0) "--" else "${value.toInt()}"

    /**
     * 安全格式化字符串，避免 null 和空字符串
     */
    fun safeString(value: String?, fallback: String = "--"): String =
        value?.takeIf { it.isNotEmpty() } ?: fallback

    /**
     * 获取诊断状态文本（用于 ProblemConv(reverse=True) 类型字段）
     * 0=异常/有故障, 1=正常
     */
    fun getDiagnosticStatus(status: Int): String = when (status) {
        0 -> "异常"
        1 -> "正常"
        else -> "未知"
    }

    /**
     * 获取诊断状态布尔值（用于 BinarySensorConv 类型字段）
     * 0=正常, 1=异常
     */
    fun getDiagnosticStatusBinary(status: Int): String = when (status) {
        0 -> "正常"
        1 -> "异常"
        else -> "未知"
    }

    /**
     * 获取座椅加热状态文本
     * null/空/非法值=无此功能, 0=关闭, 1-3=加热档位
     */
    fun getSeatHeatingStatus(value: String?): String {
        if (value.isNullOrBlank()) return "无此功能"
        val level = value.toIntOrNull() ?: return "无此功能"
        return when (level) {
            0 -> "关闭"
            1, 2, 3 -> "${level}档"
            else -> "无此功能"
        }
    }
}
