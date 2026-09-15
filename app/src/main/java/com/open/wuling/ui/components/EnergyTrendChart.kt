package com.open.wuling.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.open.wuling.data.api.EnergyAPI
import com.open.wuling.ui.theme.LocalCardAlpha
import com.open.wuling.ui.theme.PrimaryOrange
import kotlin.math.max

/**
 * 能耗趋势柱状图（v54）
 *
 * 为什么自己画而不是引三方库：
 * 只需「一排柱子 + 容量自适应」这一种图形，引 MPAndroidChart / Vico 会带来
 * 额外依赖体积与版本兼容风险（项目当前 Compose BOM 2024.12.01）。
 * 用 Compose Canvas 手绘约 100 行，零新增依赖，且样式可完全贴合现有配色。
 *
 * ⚠️ 空值与 0 的处理是这里的核心设计：
 * `null`（接口无数据）与 `0.0`（有数据但值为零，如当天没开车）语义完全不同。
 * 前者该位置留空不画，后者画一根极矮的柱子 —— 绝不把「没数据」画成「0」，
 * 否则用户会误以为那天真的跑了零公里。
 *
 * @param onSelect 点击某根柱子时回调该点（v55）。传 null 则图表不可点。
 * @param selectedIndex 当前高亮的柱子下标（v55），由调用方回传，用于同步弹层与图表。
 */
@Composable
fun EnergyTrendChart(
    points: List<EnergyAPI.TrendPoint>,
    valueOf: (EnergyAPI.TrendPoint) -> Double?,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    unit: String = "",
    labelEvery: Int = 1,
    height: Int = 150,
    onSelect: ((Int) -> Unit)? = null,
    selectedIndex: Int? = null
) {
    // 只统计有效值，避免空值把坐标轴拉坏
    val values = points.map { valueOf(it) }
    val maxValue = values.filterNotNull().maxOrNull() ?: 0.0
    val hasAnyData = values.any { it != null }

    Column(modifier = modifier) {
        if (!hasAnyData) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "该区间暂无可绘制的数据",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        // 顶部标出峰值，让柱子高低有个参照
        Text(
            text = "峰值 ${fmtOrDash(maxValue)}$unit",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .then(
                    // v55：点击选柱。整条 slot 都是热区（不要求精确点中几 dp 宽的柱子），
                    // 车机上手指粗、柱子细，只有放大热区才点得准。
                    if (onSelect != null) {
                        Modifier.pointerInput(points.size, selectedIndex) {
                            detectTapGestures { offset ->
                                val n = points.size
                                if (n > 0) {
                                    val slot = size.width.toFloat() / n
                                    val idx = (offset.x / slot).toInt().coerceIn(0, n - 1)
                                    onSelect(idx)
                                }
                            }
                        }
                    } else Modifier
                )
        ) {
            val n = points.size
            if (n == 0) return@Canvas

            val bottomPad = 2f
            val topPad = 6f
            val usableH = size.height - topPad - bottomPad

            // 柱宽与间距：留 20% 作间隙，再按数量自适应（点多时自动变细）
            val slot = size.width / n
            val barW = (slot * 0.62f).coerceAtLeast(2f)

            // 基线（X 轴）
            drawLine(
                color = Color.Gray.copy(alpha = 0.25f),
                start = Offset(0f, size.height - bottomPad),
                end = Offset(size.width, size.height - bottomPad),
                strokeWidth = 2f
            )

            points.forEachIndexed { i, p ->
                val v = valueOf(p)
                val cx = slot * i + slot / 2f
                val isSel = selectedIndex == i

                if (v == null) {
                    // 无数据：只在基线处点一个极淡的小点，表示"这天没记录"
                    drawCircle(
                        color = Color.Gray.copy(alpha = 0.18f),
                        radius = 1.6f,
                        center = Offset(cx, size.height - bottomPad - 1f)
                    )
                } else {
                    // 值为 0 也画一根最小可见高度的柱，与「无数据」区分开
                    val ratio = if (maxValue <= 0.0) 0f else (v / maxValue).toFloat()
                    val h = max(usableH * ratio, 2f)
                    val top = size.height - bottomPad - h

                    if (isSel) {
                        // 选中高亮：柱子后面垫一层半透明底，让"当前看的是哪根"一目了然
                        drawRoundRect(
                            color = barColor.copy(alpha = 0.18f),
                            topLeft = Offset(slot * i, topPad),
                            size = androidx.compose.ui.geometry.Size(slot, usableH + bottomPad - topPad),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                        )
                    }

                    drawRoundRect(
                        // 选中的柱子加深，未选中若已有选中项则适度淡化，突出对比
                        color = if (selectedIndex != null && !isSel) barColor.copy(alpha = 0.45f) else barColor,
                        topLeft = Offset(cx - barW / 2f, top),
                        size = androidx.compose.ui.geometry.Size(barW, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                            barW / 2f, barW / 2f
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // X 轴标签
        //
        // ⚠️ v55 修复：原来用 `i % labelEvery == 0` 按**数组下标**抽稀，是错的。
        //    9 月 30 天 + labelEvery=5 时，标出的是下标 0/5/10/15/20/25 →
        //    对应日期 01/06/11/16/21/26，用户看到的是「0 0 11 1 2 2」这种
        //    被裁掉首位、毫无规律的乱码（线上实测截图）。
        //    改为**按日期值**判断：日维度标 1/5/10/15/20/25/30，月维度标 1/3/5…11 月。
        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEachIndexed { i, p ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (shouldShowLabel(p.label, labelEvery)) {
                        Text(
                            text = p.label,
                            fontSize = 10.sp,
                            fontWeight = if (selectedIndex == i) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedIndex == i) barColor
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * 是否显示 X 轴标签（v55）。
 *
 * 按标签**数值**决定，而非数组下标：
 * - 纯数字（日维度 "01".."31"）：整除 [every] 或首尾必显 → 1/5/10/15/20/25/30
 * - "N月"（月维度）：月份为奇数时显示，首尾必显 → 1/3/5/7/9/11 月
 * - 其他：全部显示（点数少时不必抽稀）
 *
 * 首尾强制显示：X 轴没有起点和终点日期会让人无法判断区间。
 */
private fun shouldShowLabel(label: String, every: Int): Boolean {
    if (every <= 1) return true
    val monthMatch = Regex("^(\\d+)月$").find(label)
    if (monthMatch != null) {
        val m = monthMatch.groupValues[1].toIntOrNull() ?: return true
        return m % 2 == 1 || m == 1 || m == 12
    }
    val day = label.toIntOrNull() ?: return true
    return day == 1 || day % every == 0
}

/**
 * 折线趋势图（v54，补充用）。
 *
 * 与柱状图互补：柱状看「单点绝对量」，折线看「变化趋势」。
 * 空值处**断开**而非连成直线（这是折线图最容易骗人的地方：
 * 若把 null 当 0 连过去，会凭空造出一段剧烈下跌）。
 */
@Composable
fun EnergyTrendLine(
    points: List<EnergyAPI.TrendPoint>,
    valueOf: (EnergyAPI.TrendPoint) -> Double?,
    modifier: Modifier = Modifier,
    lineColor: Color = PrimaryOrange,
    height: Int = 130
) {
    val values = points.map { valueOf(it) }
    val maxValue = values.filterNotNull().maxOrNull() ?: 0.0
    val hasAnyData = values.any { it != null }

    Column(modifier = modifier) {
        if (!hasAnyData) return@Column

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
        ) {
            val n = points.size
            if (n < 2) return@Canvas

            val bottomPad = 2f
            val topPad = 6f
            val usableH = size.height - topPad - bottomPad
            val slot = size.width / (n - 1)

            drawLine(
                color = Color.Gray.copy(alpha = 0.25f),
                start = Offset(0f, size.height - bottomPad),
                end = Offset(size.width, size.height - bottomPad),
                strokeWidth = 2f
            )

            // 分段构建路径：遇到 null 就断开，绝不跨越空值连线
            var path: Path? = null
            points.forEachIndexed { i, p ->
                val v = valueOf(p)
                if (v == null) {
                    path?.let {
                        drawPath(it, color = lineColor, style = Stroke(width = 3f))
                    }
                    path = null
                } else {
                    val ratio = if (maxValue <= 0.0) 0f else (v / maxValue).toFloat()
                    val x = slot * i
                    val y = size.height - bottomPad - usableH * ratio
                    val pt = Offset(x, y)

                    val cur = path
                    if (cur == null) {
                        path = Path().apply { moveTo(pt.x, pt.y) }
                    } else {
                        cur.lineTo(pt.x, pt.y)
                    }
                    // 数据点圆点
                    drawCircle(color = lineColor, radius = 2.6f, center = pt)
                }
            }
            path?.let { drawPath(it, color = lineColor, style = Stroke(width = 3f)) }
        }
    }
}

private fun fmtOrDash(v: Double?): String =
    if (v == null) "--" else if (v >= 100) "%.0f".format(v) else "%.1f".format(v)

/**
 * 油电构成饼图（v55，能量折算口径）。
 *
 * ## 为什么必须折算才能画成一块饼
 * 电耗单位是 kWh、油耗单位是 L。**单位不同的两个量不能相加算占比** ——
 * 「19.3 kWh 电 + 1.2 L 油」直接画饼，等于在算「3 个苹果 + 2 个橘子谁占大头」，
 * 数学上就是错的。
 *
 * 因此先把油耗按 **1 L 汽油 = 3.0 kWh**（项目既有常量 `EnergyAPI.FUEL_TO_KWH`，
 * 界面上「油电折算综合油耗」也一直用这个口径）折算成电能，两者单位统一后才成饼。
 * 这样得到的是「**能量构成**」：这辆车这段时间消耗的能量里，电和油各占多大比例，
 * 是一个有真实物理含义的问题。
 *
 * ⚠️ 因此标题必须写「能量构成（已折算）」并在图下注明折算比例。
 *    若只写「油电占比」，用户会误以为在比升与千瓦时的原始数量。
 *
 * 数据缺失（两个都是 null）时不画饼；真实为 0 时也不画饼，改为文案说明。
 * 绝不画一个 100% 的整圆来假装「全是电」—— 那可能是根本没数据。
 */
@Composable
fun EnergyPieChart(
    elec: Double?,
    fuel: Double?,
    modifier: Modifier = Modifier,
    size: Int = 160,
    label: String = ""
) {
    val fuelToKwh = 3.0   // 与 EnergyAPI.FUEL_TO_KWH 保持一致

    // 数据缺失：明确说明，不画图
    if (elec == null && fuel == null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(size.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "该周期无油电消耗数据",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val eKwh = (elec ?: 0.0).coerceAtLeast(0.0)
    val fKwh = ((fuel ?: 0.0).coerceAtLeast(0.0)) * fuelToKwh
    val total = eKwh + fKwh

    // 有数据但总量为 0（如纯电且当月没开、或数据全 0）：不画饼
    if (total <= 0.0) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(size.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "该周期无消耗",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (label.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
        return
    }

    val elecRatio = eKwh / total
    val fuelRatio = fKwh / total

    val elecColor = Color(0xFF2196F3)
    val fuelColor = PrimaryOrange

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(size.dp)) {
            val stroke = 26f
            val r = (minOf(this.size.width, this.size.height) - stroke) / 2f
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f

            // 只有电（纯电用车很常见）：整圆，不画 0 宽度的油扇区
            if (fuelRatio <= 0.0) {
                drawCircle(color = elecColor, radius = r, center = Offset(cx, cy))
            }
            // 只有油
            else if (elecRatio <= 0.0) {
                drawCircle(color = fuelColor, radius = r, center = Offset(cx, cy))
            }
            else {
                val start = -90f
                val elecSweep = (elecRatio * 360f).toFloat()

                // 电：先画
                drawArc(
                    color = elecColor,
                    startAngle = start,
                    sweepAngle = elecSweep,
                    useCenter = true,
                    topLeft = Offset(cx - r, cy - r),
                    size = androidx.compose.ui.geometry.Size(r * 2, r * 2)
                )
                // 油：接在后面
                drawArc(
                    color = fuelColor,
                    startAngle = start + elecSweep,
                    sweepAngle = 360f - elecSweep,
                    useCenter = true,
                    topLeft = Offset(cx - r, cy - r),
                    size = androidx.compose.ui.geometry.Size(r * 2, r * 2)
                )
            }

            // 中心留白挖成甜甜圈，中间放"总能量"，比实心饼更易读
            drawCircle(
                color = Color.White,
                radius = r * 0.52f,
                center = Offset(cx, cy)
            )
        }

        Spacer(modifier = Modifier.width(18.dp))

        Column {
            PieLegendRow(
                color = elecColor,
                name = "耗电量",
                raw = "%.1f kWh".format(eKwh),
                percent = "%.0f%%".format(elecRatio * 100)
            )
            Spacer(modifier = Modifier.height(10.dp))
            PieLegendRow(
                color = fuelColor,
                name = "耗油量",
                raw = if (fuel == null) "--" else "%.2f L".format(fuel),
                percent = "%.0f%%".format(fuelRatio * 100)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "合计 %.1f kWh".format(total),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (label.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
        }
    }
}

@Composable
private fun PieLegendRow(
    color: Color,
    name: String,
    raw: String,
    percent: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(color, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = percent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            Text(
                text = raw,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
