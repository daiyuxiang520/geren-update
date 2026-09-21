package com.open.wuling.ui.screens

import com.open.wuling.analytics.UmengPageView

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.open.wuling.data.api.EnergyAPI
import com.open.wuling.data.model.Vehicle
import com.open.wuling.ui.components.EnergyPieChart
import com.open.wuling.ui.components.EnergyTrendChart
import com.open.wuling.ui.components.EnergyTrendLine
import com.open.wuling.ui.theme.LocalCardAlpha
import com.open.wuling.ui.theme.PrimaryOrange
import com.open.wuling.ui.theme.PrimaryRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.util.Locale

/**
 * 车辆能耗二级页面
 *
 * 数据源: apigw.sgmwcloud.com.cn/data_center（免登录硬编码密钥）
 * 维度: 日(td) / 月(cm+tds) / 年(cy+tds)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnergyScreen(
    modifier: Modifier = Modifier,
    vehicle: Vehicle?,
    onBack: () -> Unit
) {
    UmengPageView("车辆能耗")

    val vin = vehicle?.vin ?: ""

    // 0=日 1=月 2=年，默认月度（接口字段最全）
    var tabIndex by remember { mutableIntStateOf(1) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedYear by remember { mutableIntStateOf(Year.now().value) }
    var refreshKey by remember { mutableIntStateOf(0) }

    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var stats by remember { mutableStateOf<EnergyAPI.EnergyStats?>(null) }

    // ===== 趋势图（v54）=====
    // 与汇总卡片各自独立加载：图表请求失败不该把上面的汇总数字一起拖垮，
    // 因此单独持有一份 loading / error，互不干扰。
    var trendLoading by remember { mutableStateOf(false) }
    var trendError by remember { mutableStateOf<String?>(null) }
    var trendPoints by remember { mutableStateOf<List<EnergyAPI.TrendPoint>>(emptyList()) }
    // 趋势图指标：0=能耗 1=里程
    var trendMetric by remember { mutableIntStateOf(0) }
    // v55：被点击选中的柱子（null = 未选中），作为弹详情浮层开关。
    // ⚠️ v82 曾把「图表高亮」也塞进这个变量，导致切日期/刷新/切 tab 自动弹窗；
    // v83 起拆出 highlightTrendIndex 专管高亮，本变量仅由用户点击设置（见下方说明）。
    var selectedTrendIndex by remember { mutableStateOf<Int?>(null) }
    // v83：图表视觉高亮（日维度默认指向所选日），**不控制弹窗**。
    // 与 selectedTrendIndex 分离，避免「高亮」副作用触发「弹窗」。
    var highlightTrendIndex by remember { mutableStateOf<Int?>(null) }
    // 弹层里展示的明细（点柱子后才去拿，避免每次加载都多打请求）
    var detailStats by remember { mutableStateOf<EnergyAPI.EnergyStats?>(null) }
    var detailLoading by remember { mutableStateOf(false) }

    // 日期选择器对话框
    var showPicker by remember { mutableStateOf(false) }
    // 可选下限：车机最早有能耗记录的日期（默认兜底 2024-01-01）
    var earliestDate by remember { mutableStateOf(LocalDate.of(2024, 1, 1)) }

    // 首次进入探测车机数据起始日（结果缓存，失败兜底 2024-01）
    LaunchedEffect(vin) {
        if (vin.isEmpty()) return@LaunchedEffect
        earliestDate = withContext(Dispatchers.IO) { EnergyAPI.probeEarliestMonth(vin) }
    }

    // 数据加载：进入页面 / 切 Tab / 翻时间 / 手动刷新 均触发
    LaunchedEffect(tabIndex, selectedDate, selectedMonth, selectedYear, refreshKey) {
        if (vin.isEmpty()) return@LaunchedEffect
        loading = true
        errorMessage = null
        stats = try {
            withContext(Dispatchers.IO) {
                when (tabIndex) {
                    0 -> EnergyAPI.fetchDaily(vin, selectedDate.toString())
                    1 -> EnergyAPI.fetchMonthly(vin, selectedMonth.year, selectedMonth.monthValue)
                    else -> EnergyAPI.fetchYearly(vin, selectedYear)
                }
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: "网络请求失败"
            null
        }
        loading = false
    }

    // ===== 趋势数据加载（v54）=====
    // 日维度 → tds 逐日（一次请求拿整月）；月/年维度 → cm 逐月（并行 12 次）。
    // 单独 effect：不阻塞上面的汇总加载，慢的话图表区自己显示加载态。
    LaunchedEffect(tabIndex, selectedDate, selectedMonth, selectedYear, refreshKey) {
        if (vin.isEmpty()) return@LaunchedEffect
        trendLoading = true
        trendError = null
        trendPoints = try {
            withContext(Dispatchers.IO) {
                when (tabIndex) {
                    // 日维度：画「当月逐日」，比只画一天有意义得多
                    0 -> EnergyAPI.fetchDailyTrend(vin, YearMonth.from(selectedDate))
                    // 月/年维度：画「所选年份」的 1–12 月（v82 修复 —— 旧实现锚定今天，
                    // 切到历史年份时趋势图与汇总卡片说的不是同一段时间）
                    1 -> EnergyAPI.fetchYearMonthsTrend(vin, selectedMonth.year)
                    else -> EnergyAPI.fetchYearMonthsTrend(vin, selectedYear)
                }
            }
        } catch (e: Exception) {
            trendError = e.message ?: "趋势数据加载失败"
            emptyList()
        }
        // 日维度：高亮落到「所选日」；月/年维度无单日概念，清空高亮。
        // 只动 highlightTrendIndex，不清则弹窗 —— 弹窗必须由用户点击触发（v83 修复）。
        // 同时把 selectedTrendIndex 清空，避免切日期/切 tab 后旧弹窗内容与新页面不一致。
        highlightTrendIndex =
            if (tabIndex == 0 && trendPoints.isNotEmpty())
                (selectedDate.dayOfMonth - 1).coerceIn(0, trendPoints.lastIndex)
            else null
        selectedTrendIndex = null
        trendLoading = false
    }

    // ===== 点击柱子 → 拿该点明细（v55）=====
    //
    // 浮层要显示的是「那一天的完整数据」，而趋势数据 tds 只有能耗两个字段，
    // 拿不到里程/行程数。所以点开时**按需**再查一次对应粒度的汇总接口：
    // 日→td（含里程）、月→cm（含里程与行程数）。只在用户真的点击时才请求，
    // 不给页面加载增加任何常态开销。
    LaunchedEffect(selectedTrendIndex) {
        val idx = selectedTrendIndex ?: return@LaunchedEffect
        val point = trendPoints.getOrNull(idx)
        if (point == null) { detailStats = null; return@LaunchedEffect }
        if (vin.isEmpty()) return@LaunchedEffect

        detailLoading = true
        detailStats = null
        detailStats = try {
            withContext(Dispatchers.IO) {
                // v82：明细与年月直接读 point.date，不再靠 now() 反推。
                // TrendPoint 自带上真实日期后，图、弹窗、请求三处同一来源，彻底消除错位。
                val d = point.date
                if (d == null) {
                    null
                } else if (tabIndex == 0) {
                    EnergyAPI.fetchDaily(vin, d.toString())
                } else {
                    EnergyAPI.fetchMonthly(vin, d.year, d.monthValue)
                }
            }
        } catch (e: Exception) {
            null
        }
        detailLoading = false
    }

    // ===== 柱子详情浮层（v55）=====
    selectedTrendIndex?.let { idx ->
        val point = trendPoints.getOrNull(idx)
        if (point != null) {
            TrendDetailDialog(
                title = if (tabIndex == 0) {
                    val d = point.date
                    if (d != null) "${d.year}年${d.monthValue}月${d.dayOfMonth}日"
                    else "${selectedDate.year}年${selectedDate.monthValue}月${point.label.toIntOrNull() ?: point.label}日"
                } else {
                    val d = point.date
                    if (d != null) "${d.year}年${d.monthValue}月"
                    else {
                        val back = trendPoints.size - 1 - idx
                        val ym = YearMonth.now().minusMonths(back.toLong())
                        "${ym.year}年${ym.monthValue}月"
                    }
                },
                // 浮层优先显示从汇总接口拿到的完整数据，未回来时用趋势点的值兜底
                stats = detailStats,
                fallbackElec = point.elec,
                fallbackFuel = point.fuel,
                loading = detailLoading,
                onDismiss = { selectedTrendIndex = null }
            )
        }
    }

    // 日期/月/年选择对话框（方案A：日=月历、月=年月网格、年=年份列表，同款风格）
    if (showPicker) {
        EnergyPickerDialog(
            tabIndex = tabIndex,
            initialDate = selectedDate,
            initialMonth = selectedMonth,
            initialYear = selectedYear,
            minDate = earliestDate,
            maxDate = LocalDate.now(),
            onDismiss = { showPicker = false },
            onPickDate = { selectedDate = it; showPicker = false },
            onPickMonth = { selectedMonth = it; showPicker = false },
            onPickYear = { selectedYear = it; showPicker = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ============== 顶部导航栏 ==============
        TopAppBar(
            title = {
                Text(
                    text = "能耗数据",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            actions = {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 18.dp)
                            .size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "刷新",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                // 重置为当前时间（按当前维度）
                IconButton(onClick = {
                    when (tabIndex) {
                        0 -> selectedDate = LocalDate.now()
                        1 -> selectedMonth = YearMonth.now()
                        else -> selectedYear = Year.now().value
                    }
                }) {
                    Icon(
                        imageVector = Icons.Filled.Today,
                        contentDescription = "回到当前时间",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // VIN 缺失警告
            if (vin.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = PrimaryRed.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = PrimaryRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "未获取到车辆 VIN，无法查询能耗数据",
                            fontSize = 13.sp,
                            color = PrimaryRed,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ============== 统计周期小字 ==============
            Text(
                text = when (tabIndex) {
                    0 -> "${selectedDate} · 单日明细"
                    1 -> "${selectedMonth.year}年${selectedMonth.monthValue}月 · 每日明细"
                    else -> "${selectedYear}年 · 12个月分解"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // ============== 日 / 月 / 年 Tab ==============
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf("日", "月", "年").forEachIndexed { index, label ->
                    val selected = tabIndex == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = LocalCardAlpha.current),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                // 切维度时保留已选日期，不重置（重置由顶栏按钮触发）
                                if (tabIndex != index) tabIndex = index
                            }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 15.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ============== 时间选择器 ==============
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    when (tabIndex) {
                        0 -> selectedDate = selectedDate.minusDays(1)
                        1 -> selectedMonth = selectedMonth.minusMonths(1)
                        else -> selectedYear -= 1
                    }
                }) {
                    Icon(
                        imageVector = Icons.Filled.ChevronLeft,
                        contentDescription = "上一个",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = LocalCardAlpha.current),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { showPicker = true }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (tabIndex) {
                                0 -> selectedDate.toString()
                                1 -> "${selectedMonth.year}年${selectedMonth.monthValue}月"
                                else -> "${selectedYear}年"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = "选择日期",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(onClick = {
                    when (tabIndex) {
                        0 -> selectedDate = selectedDate.plusDays(1)
                        1 -> selectedMonth = selectedMonth.plusMonths(1)
                        else -> selectedYear += 1
                    }
                }) {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "下一个",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ============== 汇总标题 ==============
            Text(
                text = when (tabIndex) {
                    0 -> "单日汇总"
                    1 -> "月度汇总"
                    else -> "年度汇总"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stats?.let {
                    if (it.startDate != null && it.endDate != null) "${it.startDate} 至 ${it.endDate}" else "-- 至 --"
                } ?: "-- 至 --",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp)
            )

            // 加载失败提示
            errorMessage?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "加载失败: $err",
                    fontSize = 12.sp,
                    color = PrimaryOrange
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ============== 数据卡片网格（2 列 × 4 行） ==============
            val s = stats
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EnergyCard(
                    value = fmt(s?.elec, 2), label = "耗电量(kWh)",
                    modifier = Modifier.weight(1f)
                )
                EnergyCard(
                    value = fmt(s?.fuel, 2), label = "耗油量(L)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EnergyCard(
                    value = fmt(s?.elecPer100, 1), label = "百公里电耗(kWh/100km)",
                    modifier = Modifier.weight(1f)
                )
                EnergyCard(
                    value = fmt(s?.fuelPer100, 2), label = "实际燃油油耗(L/100km)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EnergyCard(
                    value = fmt(s?.mixedPer100, 2), label = "油电折算综合油耗(L/100km)",
                    note = "1 L = 3.0 kWh",
                    valueColor = PrimaryOrange,
                    modifier = Modifier.weight(1f)
                )
                EnergyCard(
                    value = fmt(s?.mileage, 0), label = "行驶里程(km)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // 日维度无当日行程数（td 接口只有累计值），显示 --
                EnergyCard(
                    value = s?.tripCount?.toString() ?: "--",
                    label = "行程数(次)",
                    modifier = Modifier.weight(1f)
                )
                EnergyCard(
                    value = s?.drivingDays?.toString() ?: "--",
                    label = "行驶天数(天)",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ============== 能耗趋势图（v54）==============
            //
            // 为什么用趋势图替代「行驶记录列表」：
            // 探测确认网关没有单次行程(trip)接口，逐日明细(tds)只有电耗/油耗两个数字、
            // 连里程都没有 —— 铺成列表会是一串没头没尾的数字，用户看了更困惑。
            // 同样的数据画成趋势，反而能看出「哪个月用得多」，这才是数据支撑得住的价值。
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "能耗趋势",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        // 指标切换：日维度无里程（tds 不返回），只给能耗选项
                        val metrics = if (tabIndex == 0) listOf("能耗") else listOf("能耗", "里程")
                        metrics.forEachIndexed { idx, name ->
                            val selected = trendMetric == idx
                            Box(
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .background(
                                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { trendMetric = idx }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = name,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    when {
                        trendLoading -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        trendError != null -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "趋势加载失败: $trendError",
                                fontSize = 12.sp,
                                color = PrimaryOrange,
                                textAlign = TextAlign.Center
                            )
                        }

                        else -> {
                            val useMileage = tabIndex != 0 && trendMetric == 1
                            EnergyTrendChart(
                                points = trendPoints,
                                valueOf = { p -> if (useMileage) p.mileage else p.elec },
                                unit = if (useMileage) " km" else " kWh",
                                barColor = if (useMileage) MaterialTheme.colorScheme.primary else PrimaryOrange,
                                labelEvery = if (tabIndex == 0) 5 else 1,
                                // v55：点柱子弹该周期完整明细（v83：点击时高亮与弹窗同步设置）
                                onSelect = {
                                    selectedTrendIndex = it
                                    highlightTrendIndex = it
                                },
                                selectedIndex = selectedTrendIndex ?: highlightTrendIndex
                            )
                        }
                    }

                    // 日维度补一条油耗折线（tds 有油耗字段，顺手用上）
                    if (tabIndex == 0 && !trendLoading && trendError == null &&
                        trendPoints.any { it.fuel != null }
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "油耗趋势(L)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        EnergyTrendLine(
                            points = trendPoints,
                            valueOf = { p -> p.fuel },
                            lineColor = PrimaryOrange,
                            height = 90
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = (if (tabIndex == 0) {
                            "${selectedDate.year}年${selectedDate.monthValue}月 · 逐日"
                        } else {
                            "近 12 个月 · 逐月"
                        }) + " · 点击柱状图查看明细",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ============== 油电构成饼图（v55，能量折算口径）==============
            //
            // ⚠️ 电耗单位 kWh、油耗单位 L，**单位不同不能直接相加算占比**
            //    （3 个苹果 + 2 个橘子算不出谁占大头），因此按项目既有的
            //    1 L = 3.0 kWh 折算成统一能量单位后再成饼，得到"能量构成"。
            //    标题与脚注都写明口径，避免被误读成"升与千瓦时比大小"。
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "能量构成（已折算）",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (tabIndex) {
                            0 -> "${selectedDate} 当日"
                            1 -> "${selectedMonth.year}年${selectedMonth.monthValue}月"
                            else -> "${selectedYear}年"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    EnergyPieChart(
                        elec = s?.elec,
                        fuel = s?.fuel,
                        label = "按 1 L 汽油 = 3.0 kWh 折算为能量后计算占比"
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ============== 底部补充卡片 ==============
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "补充统计",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // 日维度主卡显示 -- 时，这里展示 td 自带的历史累计值
                    val tripValue = s?.tripCount ?: s?.cumulativeTrips
                    val tripLabel = if (s?.tripCount == null && s?.cumulativeTrips != null) "行程数(次)·累计" else "行程数(次)"
                    val accompanyValue = s?.accompanyDays ?: s?.cumulativeAccompanyDays
                    val accompanyLabel = if (s?.accompanyDays == null && s?.cumulativeAccompanyDays != null) "陪伴天数(天)·累计" else "陪伴天数(天)"
                    Row(modifier = Modifier.fillMaxWidth()) {
                        SupplementItem(
                            value = tripValue?.toString() ?: "--",
                            label = tripLabel,
                            modifier = Modifier.weight(1f)
                        )
                        SupplementItem(
                            value = s?.drivingDays?.toString() ?: "--",
                            label = "行驶天数(天)",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        SupplementItem(
                            value = accompanyValue?.toString() ?: "--",
                            label = accompanyLabel,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // 无数据提示（请求成功但该周期没有任何记录）
            if (!loading && errorMessage == null && s != null &&
                s.mileage == null && s.elec == null && s.fuel == null && s.tripCount == null
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "该周期暂无行程数据",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ============== 组件与格式化 ==============

/**
 * 柱子详情浮层（v55）。
 *
 * 设计要点：
 * - 点图表柱子弹出，展示该周期的**完整明细**（含里程、行程数等趋势图拿不到的字段）
 * - 数据未回来时先用趋势点的能耗值兜底，避免浮层一打开是空的
 * - 里程等字段缺失显示 `--`，不显示 0 —— 与图表区同一原则，不把"没数据"说成"零"
 */
@Composable
private fun TrendDetailDialog(
    title: String,
    stats: EnergyAPI.EnergyStats?,
    fallbackElec: Double?,
    fallbackFuel: Double?,
    loading: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 能耗两项：优先用接口值，未回来时用趋势点兜底
                val elec = stats?.elec ?: fallbackElec
                val fuel = stats?.fuel ?: fallbackFuel

                DetailLine("耗电量", fmt(elec, 2) + " kWh")
                Spacer(modifier = Modifier.height(10.dp))
                DetailLine("耗油量", fmt(fuel, 2) + " L")
                Spacer(modifier = Modifier.height(10.dp))
                DetailLine("行驶里程", fmt(stats?.mileage, 1) + " km")
                Spacer(modifier = Modifier.height(10.dp))
                DetailLine("百公里电耗", fmt(stats?.elecPer100, 1) + " kWh/100km")
                Spacer(modifier = Modifier.height(10.dp))
                DetailLine("百公里油耗", fmt(stats?.fuelPer100, 2) + " L/100km")

                if (stats?.tripCount != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    DetailLine("行程数", "${stats.tripCount} 次")
                }
                if (stats?.drivingDays != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    DetailLine("行驶天数", "${stats.drivingDays} 天")
                }

                // 日维度拿不到里程时说明原因，免得用户以为坏了
                if (stats != null && stats.mileage == null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "该周期接口未返回里程数据",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "关闭",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EnergyCard(
    value: String,
    label: String,
    note: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            note?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = it,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun SupplementItem(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 数值格式化: null → "--"；按指定小数位（美式小数点，避免区域设置差异） */
private fun fmt(v: Double?, digits: Int): String =
    v?.let { String.format(Locale.US, "%.${digits}f", it) } ?: "--"

// ============================================================
// 日期选择对话框（方案A：日=月历、月=年月网格、年=年份列表）
// ============================================================

private val WEEK_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
private fun EnergyPickerDialog(
    tabIndex: Int,
    initialDate: LocalDate,
    initialMonth: YearMonth,
    initialYear: Int,
    minDate: LocalDate,
    maxDate: LocalDate,
    onDismiss: () -> Unit,
    onPickDate: (LocalDate) -> Unit,
    onPickMonth: (YearMonth) -> Unit,
    onPickYear: (Int) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                when (tabIndex) {
                    0 -> DateGridPicker(initialDate, minDate, maxDate, onPickDate)
                    1 -> MonthGridPicker(initialMonth, minDate, maxDate, onPickMonth)
                    else -> YearListPicker(initialYear, minDate, maxDate, onPickYear)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onDismiss() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "取消",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 顶部「‹ 标题 ›」翻页栏 */
@Composable
private fun PickerHeader(
    title: String,
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onTitleClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onPrev?.invoke() }, enabled = onPrev != null) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = "上一个",
                tint = if (onPrev != null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .then(if (onTitleClick != null) Modifier.clickable { onTitleClick() } else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "下一个",
                tint = if (onNext != null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
    }
}

/** 日维度：月历网格 */
@Composable
private fun DateGridPicker(
    initialDate: LocalDate,
    minDate: LocalDate,
    maxDate: LocalDate,
    onPick: (LocalDate) -> Unit
) {
    val today = maxDate
    var cursor by remember { mutableStateOf(YearMonth.from(initialDate)) }
    // false=日历视图，true=年月快选视图
    var yearMonthMode by remember { mutableStateOf(false) }

    val minMonth = YearMonth.from(minDate)
    val maxMonth = YearMonth.from(maxDate)

    if (yearMonthMode) {
        // 复用月选择器切年月，再回到日历
        MonthGridPicker(
            initialMonth = cursor,
            minDate = minDate,
            maxDate = maxDate,
            onPick = { cursor = it; yearMonthMode = false }
        )
        return
    }

    Column(modifier = Modifier.width(300.dp)) {
        PickerHeader(
            title = "${cursor.year}年${cursor.monthValue}月",
            onPrev = if (cursor > minMonth) ({ cursor = cursor.minusMonths(1) }) else null,
            onNext = if (cursor < maxMonth) ({ cursor = cursor.plusMonths(1) }) else null,
            onTitleClick = { yearMonthMode = true }
        )
        Spacer(modifier = Modifier.height(4.dp))

        // 星期表头（周一起）
        Row(modifier = Modifier.fillMaxWidth()) {
            WEEK_LABELS.forEach { w ->
                Text(
                    text = w,
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        // 日期网格
        val firstDay = cursor.atDay(1)
        // DayOfWeek: MONDAY=1 ... SUNDAY=7 → 周一起偏移 0..6
        val leading = firstDay.dayOfWeek.value - 1
        val daysInMonth = cursor.lengthOfMonth()
        val cells = leading + daysInMonth
        val rows = (cells + 6) / 7

        Column {
            for (r in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val dayNum = r * 7 + c - leading + 1
                        if (dayNum in 1..daysInMonth) {
                            val date = cursor.atDay(dayNum)
                            val selectable = !date.isBefore(minDate) && !date.isAfter(maxDate)
                            val isSelected = date == initialDate
                            val isToday = date == today
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .then(
                                            if (isSelected) Modifier.background(MaterialTheme.colorScheme.primary)
                                            else if (isToday) Modifier.border(
                                                1.dp,
                                                MaterialTheme.colorScheme.primary,
                                                CircleShape
                                            )
                                            else Modifier
                                        )
                                        .then(
                                            if (selectable) Modifier.clickable { onPick(date) }
                                            else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dayNum.toString(),
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.onPrimary
                                            !selectable -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                            isToday -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        } else {
                            Spacer(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 月维度：年份翻页 + 12 个月网格 */
@Composable
private fun MonthGridPicker(
    initialMonth: YearMonth,
    minDate: LocalDate,
    maxDate: LocalDate,
    onPick: (YearMonth) -> Unit
) {
    var year by remember { mutableIntStateOf(initialMonth.year) }
    val minMonth = YearMonth.from(minDate)
    val maxMonth = YearMonth.from(maxDate)

    Column(modifier = Modifier.width(300.dp)) {
        PickerHeader(
            title = "${year}年",
            onPrev = if (year > minMonth.year) ({ year -= 1 }) else null,
            onNext = if (year < maxMonth.year) ({ year += 1 }) else null
        )
        Spacer(modifier = Modifier.height(8.dp))

        for (r in 0 until 4) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (c in 0 until 3) {
                    val monthNum = r * 3 + c + 1
                    val ym = YearMonth.of(year, monthNum)
                    val selectable = !ym.isBefore(minMonth) && !ym.isAfter(maxMonth)
                    val isSelected = ym == initialMonth
                    val isCurrent = ym == YearMonth.from(maxDate)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(3.dp)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = LocalCardAlpha.current)
                                }
                            )
                            .then(if (selectable) Modifier.clickable { onPick(ym) } else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${monthNum}月",
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                !selectable -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                isCurrent -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }
    }
}

/** 年维度：年份列表（滚动） */
@Composable
private fun YearListPicker(
    initialYear: Int,
    minDate: LocalDate,
    maxDate: LocalDate,
    onPick: (Int) -> Unit
) {
    val minYear = minDate.year
    val maxYear = maxDate.year

    Column(modifier = Modifier.width(300.dp)) {
        Text(
            text = "选择年份",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            textAlign = TextAlign.Center
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .verticalScroll(rememberScrollState())
        ) {
            (maxYear downTo minYear).forEach { y ->
                val isSelected = y == initialYear
                val isCurrent = y == maxYear
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .height(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                isSelected -> MaterialTheme.colorScheme.primary
                                isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = LocalCardAlpha.current)
                            }
                        )
                        .clickable { onPick(y) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${y}年",
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            isCurrent -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}
