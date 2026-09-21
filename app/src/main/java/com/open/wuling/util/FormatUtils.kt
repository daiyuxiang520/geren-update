package com.open.wuling.util

/**
 * 格式化工具类
 * 提供常用的数据格式化函数，供全项目复用
 */
object FormatUtils {

    // physicsEngine: 1=纯电 2=油混 3=插混 4=增程
    private val PHYSICS_ENGINE_MAP = mapOf(
        1 to "纯电动", 2 to "油电混动", 3 to "插电混动", 4 to "增程"
    )

    /**
     * 获取动力类型显示名称（单一口径）。
     *
     * v84：此前本函数与 PHYSICS_ENGINE_MAP 对同一编码给出不同结果（如 2 → 此处「增程」
     * 而 map 为「油电混动」），语义矛盾。现统一走 PHYSICS_ENGINE_MAP，避免误用。
     */
    fun getPowerTypeName(engineType: Int): String =
        PHYSICS_ENGINE_MAP[engineType] ?: "未知"

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
     * 上/下电文本（五菱API keyStatus：0=无钥匙, 1=已连接, 2=已启动）
     *
     * v56：keyStatus 是钥匙连接态，不是点火态。直接按「非 2 即下电」展示会在
     * 车机靠近车辆、蓝牙钥匙已连接但未上电时误报「下电」，所以这里把 0/1 都
     * 归为下电并统一走 getPowerStatusText 判断，避免各处自行比较字符串。
     */
    fun isPowerOn(keyStatus: String?): Boolean = keyStatus == "2"

    /**
     * 上/下电文本。未知（keyStatus 为 null 或空）返回「--」，不猜测。
     */
    fun getPowerStatusText(keyStatus: String?): String = when (keyStatus) {
        "2" -> "上电"
        "0", "1" -> "下电"
        else -> "--"
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
     * 格式化充电功率
     *
     * 服务端 carStatus.chargePower 在下发时为字符串，未充电/无数据时空串。
     * 单位待实测确认（官方 dex 中仅见 " kW"/" kW·h/100km" 的能耗文案，
     * 无法静态确认功率是 kW 还是 W），故此处做双向防御：
     *   - 正常量级（<= 100）按 kW 处理，如 6.6 → "6.6 kW"
     *   - 超过 100 的量级判定为 W，除以 1000 后按 kW 显示，如 6600 → "6.6 kW"
     * 等用户插枪实测一次真实值后，若发现偏大/偏小 1000 倍，只需改本函数一处。
     *
     * @param power 服务端原始功率值，null/0 表示无数据
     * @return 展示用字符串，无数据时返回 "--"
     */
    fun formatChargePower(power: Double?): String {
        val v = power ?: return "--"
        if (v <= 0.0) return "--"
        val kw = if (v > 100.0) v / 1000.0 else v
        return "${String.format("%.1f", kw)} kW"
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

    // ====== 官方采集时间（v62）======
    //
    // 车况接口的 collectTime 与经纬度在同一个响应包里下发，它就是「官方下发车辆
    // 位置/状态数据」的时间戳。官方格式未经文档确认（实测为可读字符串），这里
    // 同时兼容常见的几种写法 + 纯数字时间戳，解析失败返回 null，调用方自行回退。

    private val COLLECT_TIME_FORMATS = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy/MM/dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy年MM月dd日 HH:mm:ss"
    )

    /**
     * 解析官方 collectTime 为 epoch 毫秒。
     * 兼容 "yyyy-MM-dd HH:mm:ss" 等常见格式与纯数字（秒/毫秒）时间戳；失败返回 null。
     * 每次新建 SimpleDateFormat（非线程安全，且本函数会被 IO 线程调用）。
     */
    fun parseCollectTime(raw: String?): Long? {
        val s = raw?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        // 纯数字：1e11 以上按毫秒（1973 年起），以下按秒（2286 年前的秒级时间戳都 < 1e10）
        s.toLongOrNull()?.let { n ->
            return if (n >= 100_000_000_000L) n else n * 1000
        }
        for (pattern in COLLECT_TIME_FORMATS) {
            try {
                val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                sdf.isLenient = false
                val d = sdf.parse(s) ?: continue
                return d.time
            } catch (_: Exception) {
                // 换下一个格式
            }
        }
        return null
    }

    /** 相对时间文案：刚刚 / N 分钟前 / N 小时前 / N 天前。未来时间（时钟偏差）按刚刚处理。 */
    fun relativeTimeText(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val minutes = (nowMs - epochMs) / 60_000
        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "$minutes 分钟前"
            minutes < 1440 -> "${minutes / 60} 小时前"
            else -> "${minutes / 1440} 天前"
        }
    }

    /** 绝对时间：当天显示 HH:mm:ss，跨天显示 MM-dd HH:mm:ss（找车场景下「哪天」同样重要）。 */
    fun formatCollectTimeAbs(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        return try {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
            val nowCal = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
            val sameDay = cal.get(java.util.Calendar.YEAR) == nowCal.get(java.util.Calendar.YEAR) &&
                cal.get(java.util.Calendar.DAY_OF_YEAR) == nowCal.get(java.util.Calendar.DAY_OF_YEAR)
            val pattern = if (sameDay) "HH:mm:ss" else "MM-dd HH:mm:ss"
            java.text.SimpleDateFormat(pattern, java.util.Locale.US).format(java.util.Date(epochMs))
        } catch (_: Exception) {
            "--"
        }
    }
}
