package com.open.wuling.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import com.open.wuling.ui.theme.PrimaryOrange
import com.open.wuling.util.FormatUtils
import kotlinx.coroutines.delay

/**
 * 官方下发时间展示（v62，方案 C 全局版）。
 *
 * 显示「{prefix} 21:30:45 · 3 分钟前」，随每分钟自动刷新；
 * 超过 [staleThresholdMs]（默认 10 分钟）变橙色并追加
 * 「车辆可能已离线（TBox 休眠）」——车熄火后 TBox 睡眠，collectTime 停滞是正常
 * 现象，但不提示的话用户会误以为位置是「实时的」。
 *
 * collectTime 解析失败时回退显示原样字符串（不带相对时间）；
 * 为空时整个组件不渲染。
 */
@Composable
fun CollectTimeText(
    collectTime: String?,
    modifier: Modifier = Modifier,
    prefix: String = "更新于",
    staleThresholdMs: Long = 10 * 60 * 1000L
) {
    if (collectTime.isNullOrBlank()) return

    val epoch = remember(collectTime) { FormatUtils.parseCollectTime(collectTime) }

    // 每分钟 tick 一次，驱动「N 分钟前」走表（数据本身 30 秒才刷一次，分钟级足够）
    // 注：用 mutableStateOf 而非 mutableLongStateOf（后者要求 compose runtime 1.5+）
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            now = System.currentTimeMillis()
        }
    }

    val stale = epoch != null && (now - epoch) > staleThresholdMs
    val color = if (stale) PrimaryOrange else MaterialTheme.colorScheme.onSurfaceVariant

    val absText = if (epoch != null) FormatUtils.formatCollectTimeAbs(epoch, now) else collectTime
    val relText = if (epoch != null) " · ${FormatUtils.relativeTimeText(epoch, now)}" else ""
    val staleText = if (stale) " · 车辆可能已离线（TBox 休眠）" else ""

    Text(
        text = "$prefix $absText$relText$staleText",
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = color,
        modifier = modifier
    )
}
