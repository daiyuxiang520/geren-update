package com.open.wuling.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.open.wuling.oem.OemCompat
import com.open.wuling.ui.theme.PrimaryGreen
import com.open.wuling.ui.theme.PrimaryOrange
import com.open.wuling.ui.theme.PrimaryRed

/**
 * 「消息通知」设置底部弹层。
 *
 * 对应用途：
 * 1. 实时查看 **通知权限** 与 **各通知渠道** 的系统开关状态；
 * 2. 一键跳转系统设置去开启（国产 ROM 常把通知折叠/关闭，需用户手动放行）；
 * 3. 说明本应用的各类通知用途，让用户明白「为什么需要通知」。
 *
 * 依赖 [OemCompat] 的检测与跳转能力，覆盖 ColorOS/MIUI 等国产 ROM。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsSheet(
    isOpen: Boolean,
    onClose: () -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 每次打开重新检测（用户可能刚从系统设置返回）
    var refreshKey by remember { mutableStateOf(0) }

    val hasPermission = remember(refreshKey) { OemCompat.hasNotificationPermission(context) }
    val bleChannelOn = remember(refreshKey) {
        OemCompat.isNotificationChannelEnabled(context, OemCompat.BLE_CHANNEL_ID)
    }
    val vehicleChannelOn = remember(refreshKey) {
        OemCompat.isNotificationChannelEnabled(context, "vehicle_state_channel")
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "消息通知",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "管理通知权限与渠道，确保车辆提醒能及时送达",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            // ===== 总体权限状态 =====
            StatusCard(
                ok = hasPermission,
                title = if (hasPermission) "通知权限已开启" else "通知权限未开启",
                desc = if (hasPermission) {
                    "应用可以正常发送通知"
                } else {
                    "未开启时，车辆状态提醒与保活通知都无法送达"
                },
                actionText = if (hasPermission) "去系统设置" else "立即开启",
                onAction = { OemCompat.openNotificationSettings(context) }
            )

            Spacer(Modifier.height(20.dp))

            // ===== 通知渠道 =====
            Text(
                text = "通知渠道",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))

            ChannelItem(
                icon = Icons.Filled.DirectionsCar,
                title = "车辆状态提醒",
                desc = "解锁 / 上锁 / 门窗异常等状态变化提醒",
                enabled = vehicleChannelOn,
                onAction = { OemCompat.openNotificationSettings(context) }
            )
            Divider(color = MaterialTheme.colorScheme.surfaceVariant)
            ChannelItem(
                icon = Icons.Filled.Bluetooth,
                title = "无感控车（保活）",
                desc = "蓝牙常驻通知，是后台自动解锁的保活前提",
                enabled = bleChannelOn,
                onAction = { OemCompat.openNotificationSettings(context) }
            )

            Spacer(Modifier.height(20.dp))

            // ===== 离车提醒（App 内检测：车窗/车门/后备箱）（v61）=====
            val alertPrefs = remember { com.open.wuling.data.local.VehicleAlertPreferences(context) }
            val alertEnabled by alertPrefs.enabledFlow.collectAsState(initial = true)
            val alertWindows by alertPrefs.windowsFlow.collectAsState(initial = true)
            val alertDoors by alertPrefs.doorsFlow.collectAsState(initial = true)
            val alertTrunk by alertPrefs.trunkFlow.collectAsState(initial = true)

            Text(
                text = "离车提醒",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))

            AlertSwitchRow(
                title = "离车提醒总开关",
                desc = "车辆状态异常时发送系统通知，恢复正常自动撤回",
                checked = alertEnabled,
                onChange = { alertPrefs.setEnabled(it) }
            )
            Divider(color = MaterialTheme.colorScheme.surfaceVariant)
            AlertSwitchRow(
                title = "车窗未关",
                desc = "任一车窗未关（含开度大于 0）时提醒，并注明具体哪扇",
                checked = alertWindows,
                onChange = { alertPrefs.setWindows(it) },
                enabled = alertEnabled
            )
            Divider(color = MaterialTheme.colorScheme.surfaceVariant)
            AlertSwitchRow(
                title = "车门未锁",
                desc = "整车未上锁时提醒",
                checked = alertDoors,
                onChange = { alertPrefs.setDoors(it) },
                enabled = alertEnabled
            )
            Divider(color = MaterialTheme.colorScheme.surfaceVariant)
            AlertSwitchRow(
                title = "后备箱未关",
                desc = "后备箱处于打开状态时提醒",
                checked = alertTrunk,
                onChange = { alertPrefs.setTrunk(it) },
                enabled = alertEnabled
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(14.dp)
            ) {
                Text(
                    text = "提醒触发时机：App 打开期间每次车辆状态刷新后检测（前台约每 30 秒一次）。" +
                        "桌面小组件的异常角标不受此开关影响，始终跟随最新状态显示。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(20.dp))

            // ===== 说明 =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .padding(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "部分国产系统（如 ColorOS、MIUI）会默认折叠或关闭第三方应用通知，" +
                        "若收不到提醒，请在上方跳转系统设置后，将通知权限与渠道全部设为「允许」并关闭省电限制。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { refreshKey++ },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("重新检测")
            }
        }
    }
}

@Composable
private fun StatusCard(
    ok: Boolean,
    title: String,
    desc: String,
    actionText: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (ok) PrimaryGreen.copy(alpha = 0.10f) else PrimaryOrange.copy(alpha = 0.12f)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (ok) PrimaryGreen else PrimaryOrange,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = actionText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onAction)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun AlertSwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = desc,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled
        )
    }
}

@Composable
private fun ChannelItem(
    icon: ImageVector,
    title: String,
    desc: String,
    enabled: Boolean,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAction)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = if (enabled) "已开启" else "已关闭",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) PrimaryGreen else PrimaryRed
        )
    }
}
