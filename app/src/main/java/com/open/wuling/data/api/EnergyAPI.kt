package com.open.wuling.data.api

import com.open.wuling.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.TimeUnit

/**
 * APIGW 能耗数据中心（逆向 50Car v5.42 协议）
 *
 * 网关: https://apigw.sgmwcloud.com.cn/data_center/ads_use_drive_trip_*
 * 鉴权: 硬编码 appKey + md5(APP_SECRET + 毫秒时间戳) 签名，无需用户登录态
 *
 * 协议要点（逆向实测，勿改）:
 * - header 必须带 version=v1（漏了报 4001 鉴权version不正确）
 * - 签名只含 APP_SECRET + 毫秒时间戳，body/path/appKey 都不参与
 * - 请求体字段全 snake_case（on_year / on_month / on_date / start_date / end_date）
 * - 响应为三层嵌套: response.data.data[] 才是业务记录
 * - 能耗类字段返回 String（"464.000000"），计数类返回 Integer，取值需兼容
 */
object EnergyAPI {

    private const val BASE_URL = "https://apigw.sgmwcloud.com.cn"

    /**
     * 能耗数据中心的 appKey / appSecret。
     *
     * 不硬编码在源码中，改由 local.properties 提供（见 local.properties.example），
     * 构建时通过 BuildConfig 注入，避免在公开仓库中分发网关凭据。
     * 签名算法：md5(APP_SECRET + 毫秒时间戳)
     */
    private val APP_KEY: String get() = BuildConfig.ENERGY_APP_KEY
    private val APP_SECRET: String get() = BuildConfig.ENERGY_APP_SECRET

    /** model 字段必填但服务端仅按 VIN 反查车型，固定填文档实测值 */
    private const val DEFAULT_MODEL = "F510CPHEV"

    /** 官方折算比例: 1L 汽油 = 3.0 kWh 电能 */
    const val FUEL_TO_KWH = 3.0

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        // v59：接入调试日志（与 WulingAPI 共用拦截器，报文脱敏）
        .addInterceptor(createAppLogInterceptor())
        .build()

    /** 年度视图并行拉取 12 个月 cm 数据用的线程池 */
    private val pool = java.util.concurrent.Executors.newFixedThreadPool(4)

    // ============== 数据模型 ==============

    /**
     * 某一统计周期的能耗汇总。
     * null 表示接口无此数据（UI 显示 --）。
     */
    data class EnergyStats(
        val mileage: Double? = null,        // 行驶里程 km
        val elec: Double? = null,           // 耗电量 kWh
        val fuel: Double? = null,           // 耗油量 L
        val elecPer100: Double? = null,     // 百公里电耗 kWh/100km
        val fuelPer100: Double? = null,     // 实际燃油油耗 L/100km
        val mixedPer100: Double? = null,    // 油电折算综合油耗 L/100km
        val tripCount: Int? = null,         // 行程数（当期）
        val drivingDays: Int? = null,       // 行驶天数（官方无字段，由 tds 逐日明细统计）
        val accompanyDays: Int? = null,     // 陪伴天数（当期）
        val startDate: String? = null,      // 统计区间起
        val endDate: String? = null,        // 统计区间止
        // 以下仅日维度（td 接口）返回，为历史累计值
        val cumulativeTrips: Int? = null,
        val cumulativeAccompanyDays: Int? = null,
        val cumulativeMileage: Double? = null
    )

    // ============== 签名与请求 ==============

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** 统一 POST（调用方需在 IO 线程执行）；code != 0 抛异常 */
    private fun post(path: String, body: JSONObject): JSONObject {
        val ts = System.currentTimeMillis().toString()
        val request = Request.Builder()
            .url("$BASE_URL$path")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Accept", "application/json")
            .header("version", "v1")
            .header("appKey", APP_KEY)
            .header("timestamp", ts)
            .header("signature", md5(APP_SECRET + ts))
            .build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
            val json = JSONObject(text)
            val code = json.optInt("code", -1)
            if (code != 0) {
                throw RuntimeException("[$code] ${json.optString("message", "未知错误")}")
            }
            return json
        }
    }

    /** 提取三层嵌套中的业务记录数组 */
    private fun records(json: JSONObject): JSONArray =
        json.optJSONObject("data")?.optJSONArray("data") ?: JSONArray()

    private fun JSONArray.asMapList(): List<JSONObject> =
        (0 until length()).map { getJSONObject(it) }

    /** 兼容 String/Integer 两种返回类型 */
    private fun JSONObject.dbl(key: String): Double? = when (val v = opt(key)) {
        null, JSONObject.NULL -> null
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    }

    private fun JSONObject.int(key: String): Int? = when (val v = opt(key)) {
        null, JSONObject.NULL -> null
        is Number -> v.toInt()
        is String -> v.toDoubleOrNull()?.toInt()
        else -> null
    }

    // ============== 派生指标 ==============

    /**
     * 百公里能耗与油电折算综合油耗。
     * 里程 <= 0 时不计算（防除零），返回 (null, null, null)。
     * 综合油耗 = 百公里油耗 + 百公里电耗 / 3.0（1L = 3.0 kWh）
     */
    private fun derive(elec: Double?, fuel: Double?, mileage: Double?): Triple<Double?, Double?, Double?> {
        if (mileage == null || mileage <= 0.0) return Triple(null, null, null)
        val e100 = elec?.let { it / mileage * 100 }
        val f100 = fuel?.let { it / mileage * 100 }
        val mixed = listOfNotNull(f100, e100?.div(FUEL_TO_KWH)).takeIf { it.isNotEmpty() }?.sum()
        return Triple(e100, f100, mixed)
    }

    /** tds 单次区间请求，返回逐日明细记录 */
    private fun tdsRecords(vin: String, startDate: String, endDate: String): List<JSONObject> {
        val body = JSONObject()
            .put("vin", vin)
            .put("model", DEFAULT_MODEL)
            .put("start_date", startDate)
            .put("end_date", endDate)
            .put("page_num", "1")
            .put("page_size", "500")
        return records(post("/data_center/ads_use_drive_trip_tds", body)).asMapList()
    }

    /** 逐日明细求和: (总电耗 kWh, 总油耗 L, 行驶天数) */
    private fun sumTds(list: List<JSONObject>): Triple<Double, Double, Int> {
        var elec = 0.0
        var fuel = 0.0
        var days = 0
        for (r in list) {
            val e = r.dbl("use_calculate_soc_consumption_td") ?: 0.0
            val f = r.dbl("use_fuel_consumption_td") ?: 0.0
            elec += e
            fuel += f
            if (e > 0.0001 || f > 0.0001) days += 1
        }
        return Triple(elec, fuel, days)
    }

    /**
     * tds 区间能耗（该接口只有逐日能耗，没有里程/行程数）。
     * 返回 (总电耗, 总油耗, 行驶天数)，全空时返回 (null, null, null) 由 UI 显示 --。
     *
     * 实测: 全年长区间单次查询在历史年份（如 2024/2025）返回空数组，
     * 近期月份正常 —— 疑似服务端对超长区间做了裁剪。
     * 因此策略为: 全年单查 → 结果为空时自动按季度分段重试 → 仍无则显示 --。
     */
    private fun fetchTds(vin: String, startDate: String, endDate: String): Triple<Double?, Double?, Int?> {
        val first = try {
            tdsRecords(vin, startDate, endDate)
        } catch (e: Exception) {
            emptyList<JSONObject>()
        }
        if (first.isNotEmpty()) {
            val (e, f, d) = sumTds(first)
            return Triple(e, f, d)
        }

        // 分段重试: 按季度切片逐段拉取合并
        try {
            val start = java.time.LocalDate.parse(startDate)
            val end = java.time.LocalDate.parse(endDate)
            var elec = 0.0
            var fuel = 0.0
            var days = 0
            var gotAny = false
            var cur = start
            while (!cur.isAfter(end)) {
                val segEnd = minOf(cur.plusMonths(3).minusDays(1), end)
                val seg = try {
                    tdsRecords(vin, cur.toString(), segEnd.toString())
                } catch (e: Exception) {
                    emptyList<JSONObject>()
                }
                if (seg.isNotEmpty()) {
                    gotAny = true
                    val (e, f, d) = sumTds(seg)
                    elec += e
                    fuel += f
                    days += d
                }
                cur = segEnd.plusDays(1)
            }
            if (gotAny) return Triple(elec, fuel, days)
        } catch (e: Exception) {
            // 解析失败等异常，落到下方返回 null
        }
        return Triple(null, null, null)
    }

    // ============== 三维度查询（IO 线程调用） ==============

    /** 日维度: td 接口（当日里程/能耗 + 历史累计行程/陪伴天数/里程） */
    fun fetchDaily(vin: String, date: String): EnergyStats {
        val body = JSONObject()
            .put("vin", vin)
            .put("model", DEFAULT_MODEL)
            .put("on_date", date)
            .put("page_num", "1")
            .put("page_size", "200")
        val list = records(post("/data_center/ads_use_drive_trip_td", body)).asMapList()
        if (list.isEmpty()) return EnergyStats(startDate = date, endDate = date)
        val r = list[0]
        val mileage = r.dbl("drive_fixed_mileage_td")
        val elec = r.dbl("use_calculate_soc_consumption_td")
        val fuel = r.dbl("use_fuel_consumption_td")
        val (e100, f100, mixed) = derive(elec, fuel, mileage)
        return EnergyStats(
            mileage = mileage, elec = elec, fuel = fuel,
            elecPer100 = e100, fuelPer100 = f100, mixedPer100 = mixed,
            tripCount = null,   // td 的 trip_count_td 是历史累计，无当日行程数
            drivingDays = null, // 单日行驶天数无来源，显示 --
            accompanyDays = null,
            startDate = date, endDate = date,
            cumulativeTrips = r.int("trip_count_td"),
            cumulativeAccompanyDays = r.int("accompanying_days_td"),
            cumulativeMileage = r.dbl("drive_fixed_mileage_sum_td")
        )
    }

    /** 月维度: cm 汇总（字段最全）+ tds 当月明细（统计行驶天数） */
    fun fetchMonthly(vin: String, year: Int, month: Int): EnergyStats {
        val cmBody = JSONObject()
            .put("vin", vin)
            .put("model", DEFAULT_MODEL)
            .put("on_year", year.toString())
            .put("on_month", month.toString())
        val cmList = records(post("/data_center/ads_use_drive_trip_cm", cmBody)).asMapList()
        if (cmList.isEmpty()) return EnergyStats()
        val cm = cmList[0]
        val mileage = cm.dbl("drive_fixed_mileage_cm")
        val elec = cm.dbl("use_calculate_soc_consumption_cm")
        val fuel = cm.dbl("use_fuel_consumption_cm")
        val (e100, f100, mixed) = derive(elec, fuel, mileage)
        val drivingDays = fetchTds(vin, cm.optString("start_date"), cm.optString("end_date")).third
        return EnergyStats(
            mileage = mileage, elec = elec, fuel = fuel,
            elecPer100 = e100, fuelPer100 = f100, mixedPer100 = mixed,
            tripCount = cm.int("trip_count_cm"),
            drivingDays = drivingDays,
            accompanyDays = cm.int("accompanying_days_cm"),
            startDate = cm.optString("start_date").ifEmpty { null },
            endDate = cm.optString("end_date").ifEmpty { null }
        )
    }

    /**
     * 年维度查询。
     *
     * 实测口径（v3.8 修复）:
     * - cy 年度汇总接口只对近期年份（如 2026）有数据，2024/2025 返回空
     * - tds 逐日明细历史年份也拉不到
     * - cm 月度接口完整保留全部历史数据
     * 因此策略: 能耗/里程/行程数/陪伴天数一律以 12 个月 cm 并行累加为基础，
     * cy 有数据时优先用 cy（口径为官方年度汇总）；行驶天数仍由 tds 尽力拉取（拉不到显示 --）。
     */
    fun fetchYearly(vin: String, year: Int): EnergyStats {
        // 1) cy 年度汇总（尽力而为，历史年份可能为空）
        val cy = try {
            val cyBody = JSONObject()
                .put("vin", vin)
                .put("model", DEFAULT_MODEL)
                .put("on_year", year.toString())
            records(post("/data_center/ads_use_drive_trip_cy", cyBody)).asMapList().firstOrNull()
        } catch (e: Exception) {
            null
        }

        // 2) 12 个月 cm 并行累加（历史年份唯一可靠来源；未来月份返回空自动跳过）
        var cmMileage = 0.0
        var cmElec = 0.0
        var cmFuel = 0.0
        var cmTrips = 0
        var cmAccompany = 0
        var cmGot = false
        var minDate: String? = null
        var maxDate: String? = null
        val futures = (1..12).map { month ->
            pool.submit(java.util.concurrent.Callable {
                try {
                    val body = JSONObject()
                        .put("vin", vin)
                        .put("model", DEFAULT_MODEL)
                        .put("on_year", year.toString())
                        .put("on_month", month.toString())
                    records(post("/data_center/ads_use_drive_trip_cm", body)).asMapList().firstOrNull()
                } catch (e: Exception) {
                    null
                }
            })
        }
        for (f in futures) {
            val cm = f.get() ?: continue
            cmGot = true
            cmMileage += cm.dbl("drive_fixed_mileage_cm") ?: 0.0
            cmElec += cm.dbl("use_calculate_soc_consumption_cm") ?: 0.0
            cmFuel += cm.dbl("use_fuel_consumption_cm") ?: 0.0
            cmTrips += cm.int("trip_count_cm") ?: 0
            cmAccompany += cm.int("accompanying_days_cm") ?: 0
            val sd = cm.optString("start_date")
            val ed = cm.optString("end_date")
            if (sd.isNotEmpty() && (minDate == null || sd < minDate)) minDate = sd
            if (ed.isNotEmpty() && (maxDate == null || ed > maxDate)) maxDate = ed
        }

        if (!cmGot && cy == null) return EnergyStats() // 该年份完全无数据

        // 3) 合成: cy 优先，缺失时回退 cm 累加值
        val mileage = cy?.dbl("drive_fixed_mileage_cy") ?: cmMileage.takeIf { cmGot }
        val tripCount = cy?.int("trip_count_cy") ?: cmTrips.takeIf { cmGot }
        val accompanyDays = cy?.int("accompanying_days_cy") ?: cmAccompany.takeIf { cmGot }
        val start = cy?.optString("start_date")?.ifEmpty { null } ?: minDate ?: "$year-01-01"
        val end = cy?.optString("end_date")?.ifEmpty { null } ?: maxDate ?: "$year-12-31"

        // 4) 行驶天数: tds 尽力拉取（近期年份可得，历史年份拉不到显示 --）
        val drivingDays = fetchTds(vin, start, end).third

        val (e100, f100, mixed) = derive(cmElec.takeIf { cmGot }, cmFuel.takeIf { cmGot }, mileage)
        return EnergyStats(
            mileage = mileage,
            elec = cmElec.takeIf { cmGot },
            fuel = cmFuel.takeIf { cmGot },
            elecPer100 = e100, fuelPer100 = f100, mixedPer100 = mixed,
            tripCount = tripCount,
            drivingDays = drivingDays,
            accompanyDays = accompanyDays,
            startDate = start, endDate = end
        )
    }

    /**
     * 探测车机最早有能耗记录的月份（供日期选择器下限使用）。
     *
     * 接口无「起始日」字段，故从当前月向前逐月查询 cm 接口，找到最早有记录的月份，
     * 取其 1 号为下限。封顶向前 5 年（60 次轻量请求，实测较快）。
     * 失败或全空时兜底 2024-01-01。结果由调用方缓存，避免重复探测。
     */
    fun probeEarliestMonth(vin: String): LocalDate {
        val fallback = LocalDate.of(2024, 1, 1)
        return try {
            var cursor = YearMonth.now()
            var earliest: YearMonth? = null
            val limit = cursor.minusYears(5)
            while (!cursor.isBefore(limit)) {
                val body = JSONObject()
                    .put("vin", vin)
                    .put("model", DEFAULT_MODEL)
                    .put("on_year", cursor.year.toString())
                    .put("on_month", cursor.monthValue.toString())
                val hit = records(post("/data_center/ads_use_drive_trip_cm", body)).asMapList()
                    .any { m ->
                        val mileage = m.dbl("drive_fixed_mileage_cm") ?: 0.0
                        val elec = m.dbl("use_calculate_soc_consumption_cm") ?: 0.0
                        val fuel = m.dbl("use_fuel_consumption_cm") ?: 0.0
                        mileage > 0.0001 || elec > 0.0001 || fuel > 0.0001
                    }
                if (hit) earliest = cursor else if (earliest != null) break // 连续空即停止
                cursor = cursor.minusMonths(1)
            }
            earliest?.atDay(1) ?: fallback
        } catch (e: Exception) {
            fallback
        }
    }

    // ============== 趋势图数据（v54） ==============

    /**
     * 趋势图上的一个数据点。全部字段可空 —— 缺数据时图上该点留空，不画成 0，
     * 避免把「没数据」误读成「当天没开」。
     */
    data class TrendPoint(
        val label: String,          // X 轴短标签，如 "08-15" / "3月"
        val mileage: Double? = null,
        val elec: Double? = null,
        val fuel: Double? = null
    )

    /**
     * 月度趋势：近 [months] 个月，逐月一个点。
     *
     * 数据源为 cm 月度汇总（历史数据完整、字段最全，含里程），并行拉取。
     * 一次最多 12 个请求；未来月份返回空，自动跳过（不会画成 0）。
     */
    fun fetchMonthlyTrend(vin: String, months: Int = 12): List<TrendPoint> {
        val now = YearMonth.now()
        val targets = (months - 1 downTo 0).map { now.minusMonths(it.toLong()) }

        val futures = targets.map { ym ->
            pool.submit(java.util.concurrent.Callable {
                try {
                    val body = JSONObject()
                        .put("vin", vin)
                        .put("model", DEFAULT_MODEL)
                        .put("on_year", ym.year.toString())
                        .put("on_month", ym.monthValue.toString())
                    records(post("/data_center/ads_use_drive_trip_cm", body)).asMapList().firstOrNull()
                } catch (e: Exception) {
                    null
                }
            })
        }

        return futures.mapIndexed { i, f ->
            val ym = targets[i]
            val label = "${ym.monthValue}月"
            val cm = try { f.get() } catch (e: Exception) { null }
            if (cm == null) {
                TrendPoint(label)
            } else {
                TrendPoint(
                    label = label,
                    mileage = cm.dbl("drive_fixed_mileage_cm"),
                    elec = cm.dbl("use_calculate_soc_consumption_cm"),
                    fuel = cm.dbl("use_fuel_consumption_cm")
                )
            }
        }
    }

    /**
     * 日趋势：指定月份内逐日一个点。
     *
     * 数据源为 tds 逐日明细 —— **一次请求即可拿到整月**，效率高。
     * ⚠️ 实测 tds 只有能耗字段，**不含里程**，所以日趋势的 mileage 恒为 null，
     *    图上只画能耗曲线。这不是 bug，是数据源本身的限制。
     */
    fun fetchDailyTrend(vin: String, yearMonth: YearMonth): List<TrendPoint> {
        val start = yearMonth.atDay(1)
        val end = yearMonth.atEndOfMonth()

        val list = try {
            tdsRecords(vin, start.toString(), end.toString())
        } catch (e: Exception) {
            emptyList<JSONObject>()
        }

        // 按日期归拢，方便查表（接口可能不返回某些日期）
        val byDate = list.associateBy { it.optString("on_date") }

        return (1..yearMonth.lengthOfMonth()).map { day ->
            val d = yearMonth.atDay(day)
            val r = byDate[d.toString()]
            val elec = r?.dbl("use_calculate_soc_consumption_td")
            val fuel = r?.dbl("use_fuel_consumption_td")
            TrendPoint(
                label = "%02d".format(day),
                mileage = null,   // tds 无里程字段
                elec = elec,
                fuel = fuel
            )
        }
    }
}
