package com.open.wuling.ui.screens

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
