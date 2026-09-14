package com.open.wuling.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.open.wuling.oem.OemCompat

/**
 * 系统保活设置引导（针对 OPPO / 一加 / realme 的 ColorOS 系做重点适配）。
 *
 * ColorOS 默认会：禁止自启动、限制后台高耗电、冻结长时间后台应用、
 * 并把常驻通知折叠进通知中心 —— 这几项不打开，息屏或清后台后无感控车必然失效。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OemKeepAliveSheet(
    isOpen: Boolean,
    onClose: () -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 从系统设置页返回后自动刷新状态
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val items = remember(tick) { buildKeepAliveItems(context) }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "系统保活设置",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${OemCompat.romDisplayName()} · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "无感控车需要应用在后台持续保持蓝牙连接。国产 ROM 默认会限制后台运行，" +
                        "请逐项开启；从设置页返回后本页状态会自动刷新。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            items.forEach { item ->
                KeepAliveRow(item = item) { item.onOpen(context) }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(4.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "OPPO / ColorOS 额外提示",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "1. 多任务界面中把本应用「下拉锁定」，否则一键清理后服务会被终止。\n" +
                                "2. 关闭「省电模式」与「睡眠待机优化」，ColorOS 会在夜间冻结后台应用。\n" +
                                "3. 车机蓝牙扫描依赖位置服务，请保持定位开启。\n" +
                                "4. 通知栏常驻通知若被折叠，可在通知设置中把「无感控车」渠道设为重要。",
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("我知道了")
            }
        }
    }
}

@Composable
private fun KeepAliveRow(
    item: KeepAliveItem,
    onClick: () -> Unit
) {
    val containerColor = when (item.state) {
        KeepAliveState.OK -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        KeepAliveState.WARN -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        KeepAliveState.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val (statusText, statusColor) = when (item.state) {
        KeepAliveState.OK -> "已开启" to Color(0xFF2E7D32)
        KeepAliveState.WARN -> "未开启" to Color(0xFFC62828)
        KeepAliveState.UNKNOWN -> "需检查" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(statusColor.copy(alpha = 0.14f), RoundedCornerShape(50))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(text = statusText, fontSize = 11.5.sp, color = statusColor)
            }
        }
    }
}

private enum class KeepAliveState { OK, WARN, UNKNOWN }

private data class KeepAliveItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val state: KeepAliveState,
    val onOpen: (Context) -> Unit
)

private fun buildKeepAliveItems(context: Context): List<KeepAliveItem> {
    val items = mutableListOf<KeepAliveItem>()

    items += KeepAliveItem(
        title = "自启动管理",
        description = "ColorOS 需手动放行，否则开机/被杀后无法恢复",
        icon = Icons.Filled.RestartAlt,
        state = KeepAliveState.UNKNOWN
    ) { OemCompat.openAutoStartSettings(it) }

    items += KeepAliveItem(
        title = "后台高耗电 / 电池优化",
        description = "加入白名单，避免系统限制后台活动",
        icon = Icons.Filled.BatteryFull,
        state = if (OemCompat.isIgnoringBatteryOptimizations(context)) KeepAliveState.OK else KeepAliveState.WARN
    ) { OemCompat.openBatterySettings(it) }

    items += KeepAliveItem(
        title = "通知权限",
        description = "常驻通知是前台服务保活的必要条件",
        icon = Icons.Filled.Notifications,
        state = if (OemCompat.hasNotificationPermission(context)) KeepAliveState.OK else KeepAliveState.WARN
    ) { OemCompat.openNotificationSettings(it) }

    items += KeepAliveItem(
        title = "通知渠道未被关闭",
        description = "ColorOS 可单独关闭「无感控车」渠道",
        icon = Icons.Filled.NotificationsActive,
        state = if (OemCompat.isNotificationChannelEnabled(context, OemCompat.BLE_CHANNEL_ID)) {
            KeepAliveState.OK
        } else {
            KeepAliveState.WARN
        }
    ) { OemCompat.openNotificationSettings(it) }

    items += KeepAliveItem(
        title = "蓝牙权限",
        description = "扫描与连接车机蓝牙（Android 12+ 细分权限）",
        icon = Icons.Filled.Bluetooth,
        state = if (OemCompat.hasBluetoothPermissions(context)) KeepAliveState.OK else KeepAliveState.WARN
    ) { OemCompat.openAppDetails(it) }

    items += KeepAliveItem(
        title = "定位权限",
        description = "ColorOS 扫描蓝牙时通常仍要求位置权限",
        icon = Icons.Filled.MyLocation,
        state = if (OemCompat.hasLocationPermission(context)) KeepAliveState.OK else KeepAliveState.WARN
    ) { OemCompat.openAppDetails(it) }

    items += KeepAliveItem(
        title = "定位服务开关",
        description = "关闭后蓝牙扫描在 ColorOS 上会无结果",
        icon = Icons.Filled.GpsFixed,
        state = if (OemCompat.isLocationEnabled(context)) KeepAliveState.OK else KeepAliveState.WARN
    ) { OemCompat.openLocationSettings(it) }

    items += KeepAliveItem(
        title = "后台运行 / 应用速冻",
        description = "避免长时间后台被系统冻结",
        icon = Icons.Filled.AcUnit,
        state = KeepAliveState.UNKNOWN
    ) { OemCompat.openBackgroundRunSettings(it) }

    items += KeepAliveItem(
        title = "多任务锁定",
        description = "在最近任务中下拉锁定本应用（系统不提供跳转）",
        icon = Icons.Filled.Lock,
        state = KeepAliveState.UNKNOWN
    ) { OemCompat.openAppDetails(it) }

    return items
}
