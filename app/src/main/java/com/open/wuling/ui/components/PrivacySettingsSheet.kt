package com.open.wuling.ui.components

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.open.wuling.oem.OemCompat
import com.open.wuling.ui.theme.PrimaryGreen
import com.open.wuling.ui.theme.PrimaryRed
import kotlinx.coroutines.launch

/**
 * 「隐私设置」底部弹层。
 *
 * 提供三类能力：
 * 1. **隐私政策全文**——随时查看当前生效的隐私政策文本（与首启同意门同一份常量）；
 * 2. **权限授权状态**——直观展示各敏感权限的授权情况，一键跳转系统设置管理；
 * 3. **数据管理**——清除本机缓存数据（调试日志、更新缓存等）。
 *
 * 注意：清除凭证/退出登录仍由「我的」页的退出登录按钮负责，此处只做只读展示与轻量清理，
 * 避免用户误触导致重新配置 Token。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsSheet(
    isOpen: Boolean,
    onClose: () -> Unit,
    onClearLogs: () -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var refreshKey by remember { mutableStateOf(0) }
    var showPolicyDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // 权限状态（每次展开/点重新检测时刷新）
    val permissions = remember(refreshKey) { collectPermissionStates(context) }

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
                text = "隐私设置",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "查看隐私政策、管理权限与本地数据",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            // ===== 隐私政策 =====
            Text(
                text = "隐私政策",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showPolicyDialog = true }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.PrivacyTip,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("查看隐私政策全文", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "了解我们收集哪些信息以及如何使用",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("查看", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(20.dp))

            // ===== 权限管理 =====
            Text(
                text = "权限管理",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "以下权限均可随时在系统设置中关闭",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            permissions.forEach { p ->
                PermissionRow(state = p) { OemCompat.openAppDetails(context) }
                Divider(color = MaterialTheme.colorScheme.surfaceVariant)
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { OemCompat.openAppDetails(context) }) {
                Text("前往系统应用权限管理", color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(12.dp))

            // ===== 数据管理 =====
            Text(
                text = "数据管理",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showClearConfirm = true }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = PrimaryRed,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("清除本机缓存数据", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "清理调试日志与安装包缓存（不影响登录状态）",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { refreshKey++ },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("重新检测权限")
            }
        }
    }

    // 隐私政策全文弹窗
    if (showPolicyDialog) {
        Dialog(onDismissRequest = { showPolicyDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    Text(
                        text = "隐私政策",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = PRIVACY_POLICY_TEXT,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { showPolicyDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("我知道了")
                    }
                }
            }
        }
    }

    // 清除缓存确认
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清除本机缓存数据？") },
            text = { Text("将清理调试日志与更新安装包缓存，不会影响登录状态与车辆绑定。") },
            confirmButton = {
                TextButton(onClick = {
                    onClearLogs()
                    showClearConfirm = false
                }) {
                    Text("确认清除", color = PrimaryRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

private data class PermissionState(
    val icon: ImageVector,
    val title: String,
    val desc: String,
    val granted: Boolean,
    val optional: Boolean
)

private fun collectPermissionStates(context: Context): List<PermissionState> {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val list = mutableListOf<PermissionState>()

    list += PermissionState(
        icon = Icons.Filled.Notifications,
        title = "通知权限",
        desc = "车辆提醒与保活通知",
        granted = OemCompat.hasNotificationPermission(context),
        optional = false
    )

    list += PermissionState(
        icon = Icons.Filled.Bluetooth,
        title = "蓝牙权限",
        desc = "连接车机实现无感控车",
        granted = OemCompat.hasBluetoothPermissions(context),
        optional = true
    )

    list += PermissionState(
        icon = Icons.Filled.Place,
        title = "定位权限",
        desc = "车辆定位与蓝牙扫描（国产系统强制要求）",
        granted = OemCompat.hasLocationPermission(context),
        optional = true
    )

    return list
}

@Composable
private fun PermissionRow(state: PermissionState, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = state.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(state.title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(
                state.desc,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = if (state.granted) "已授权" else "未授权",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (state.granted) PrimaryGreen else PrimaryRed
        )
    }
}
