package com.open.wuling.ui.screens

import com.open.wuling.analytics.UmengPageView

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import android.widget.Toast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.open.wuling.BuildConfig
import com.open.wuling.MainViewModel
import com.open.wuling.data.api.APIConfig
import com.open.wuling.util.AppLogger
import com.open.wuling.ui.theme.PrimaryGreen
import com.open.wuling.ui.theme.PrimaryOrange
import com.open.wuling.ui.theme.PrimaryRed
import com.open.wuling.ui.components.DetailRow
import com.open.wuling.ui.components.NotificationSettingsSheet
import com.open.wuling.ui.components.NfcSettingsSheet
import com.open.wuling.ui.components.OemKeepAliveSheet
import com.open.wuling.ui.components.MqttSettingsSheet
import com.open.wuling.data.mqtt.MqttConnectionState
import com.open.wuling.ui.components.PrivacySettingsSheet
import com.open.wuling.ui.theme.LocalCardAlpha
import com.open.wuling.util.FormatUtils

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onNavigateToEnergy: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    UmengPageView("我的")

    val user by viewModel.appState.user.collectAsState()
    val selectedVehicle by viewModel.appState.selectedVehicle.collectAsState()
    // Token 配置状态（响应式订阅，保存后实时刷新「已配置/未配置」）
    val tokenConfigured by viewModel.appState.tokenConfigured.collectAsState()
    // NFC 车控启用状态（v67）
    val nfcEnabled by viewModel.appState.nfcController.enabledFlow.collectAsState()
    // v73：MQTT 实时推送配置与连接状态（设置页入口展示）
    val mqttCfg by viewModel.appState.mqttConfig.collectAsState()
    val mqttState by viewModel.appState.mqttConnectionState.collectAsState()
    // v75：当前 App 更新通道（稳定版 / 测试版）
    val updateChannel by viewModel.appState.updateChannelPrefs.channelFlow.collectAsState(
        initial = com.open.wuling.data.local.UpdateChannelPreferences.CHANNEL_STABLE
    )
    val scrollState = rememberScrollState()

    var showUpdateChannelDialog by remember { mutableStateOf(false) }

    var showTokenDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }
    var showThemeSettings by remember { mutableStateOf(false) }
    var showKeepAliveSheet by remember { mutableStateOf(false) }
    var showNotificationSheet by remember { mutableStateOf(false) }
    var showPrivacySheet by remember { mutableStateOf(false) }
    var showNfcSheet by remember { mutableStateOf(false) }
    var showMqttSheet by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showVehicleInfo by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf("") }
    var loginMobile by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginLoading by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 主题模式
    val themeMode by viewModel.themePreferences.themeModeFlow.collectAsState(initial = 0)
    val useCustomColors by viewModel.themePreferences.useCustomColorsFlow.collectAsState(initial = false)
    val themeModeText = when (themeMode) {
        1 -> "浅色模式"
        2 -> "深色模式"
        else -> "跟随系统"
    }

    // 从车辆信息中获取显示数据
    val carInfo = selectedVehicle?.carInfo
    val vehicleName = carInfo?.carTypeName?.ifEmpty { carInfo.carName } 
                    ?: selectedVehicle?.displayName 
                    ?: "未绑定车辆"
    val vehicleImage = carInfo?.image?.ifEmpty { null }
    val bindPhone = carInfo?.bindCarUserMobile?.ifEmpty { null }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Profile Header - 车辆信息（点击查看完整车辆详情）
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showVehicleInfo = true },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 车辆图片（与首页车辆卡一致：圆角方框、无背景底、Fit 完整显示整车）
                Box(
                    modifier = Modifier
                        .width(88.dp)
                        .height(72.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!vehicleImage.isNullOrEmpty()) {
                        Image(
                            painter = rememberAsyncImagePainter(model = vehicleImage),
                            contentDescription = "车辆图片",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.DirectionsCar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // 绑定手机号（置顶，作为主标题显示）
                    Text(
                        text = if (!bindPhone.isNullOrEmpty()) bindPhone else "未绑定手机",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // 车型（车系名，置于下方）
                    Text(
                        text = vehicleName.ifEmpty { "未绑定车辆" },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 可点击提示：查看完整车辆信息
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "查看车辆信息",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Vehicle Section
        Text(
            text = "我的车辆",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        user.vehicles.forEach { vehicle ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = vehicle.displayName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = vehicle.model,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Settings Section
        Text(
            text = "设置",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                // 车辆能耗（二级页面入口）
                SettingsItem(
                    icon = Icons.Filled.ElectricBolt,
                    title = "车辆能耗",
                    subtitle = "行驶里程与能耗统计",
                    iconColor = PrimaryOrange,
                    onClick = onNavigateToEnergy
                )

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                // 检查更新
                SettingsItem(
                    icon = Icons.Filled.SystemUpdate,
                    title = "检查更新",
                    subtitle = "当前版本 v${BuildConfig.VERSION_NAME}${updateChannelTail(updateChannel)} · 多源加速",
                    iconColor = PrimaryGreen,
                    onClick = { viewModel.appState.checkAppUpdate(manual = true) }
                )

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                // 更新通道（v75：稳定版 / 测试版）
                UpdateChannelItem(
                    currentChannel = updateChannel,
                    onChannelChange = { viewModel.appState.updateChannelPrefs.setChannel(it) }
                )

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                // API Token
                SettingsItem(
                    icon = Icons.Filled.Key,
                    title = "API Token",
                    subtitle = if (tokenConfigured) "已配置" else "未配置",
                    iconColor = MaterialTheme.colorScheme.primary,
                    showCheck = tokenConfigured,
                    onClick = { showTokenDialog = true }
                )

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                SettingsItem(
                    icon = Icons.Filled.Notifications,
                    title = "消息通知",
                    subtitle = "权限与通知渠道管理",
                    iconColor = PrimaryOrange,
                    onClick = { showNotificationSheet = true }
                )

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                SettingsItem(
                    icon = Icons.Filled.Shield,
                    title = "隐私设置",
                    subtitle = "隐私政策与权限数据管理",
                    iconColor = PrimaryGreen,
                    onClick = { showPrivacySheet = true }
                )

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                SettingsItem(
                    icon = Icons.Filled.Settings,
                    title = "主题设置",
                    subtitle = if (useCustomColors) "自定义 · $themeModeText" else themeModeText,
                    iconColor = MaterialTheme.colorScheme.primary,
                    onClick = { showThemeSettings = true }
                )

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                SettingsItem(
                    icon = Icons.Filled.BugReport,
                    title = "调试日志",
                    subtitle = "查看 API 请求和响应",
                    iconColor = PrimaryOrange,
                    onClick = { showLogDialog = true }
                )

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                SettingsItem(
                    icon = Icons.Filled.Shield,
                    title = "系统保活设置",
                    subtitle = "${com.open.wuling.oem.OemCompat.romDisplayName()} · 自启动与后台运行",
                    iconColor = PrimaryGreen,
                    onClick = { showKeepAliveSheet = true }
                )

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                // NFC 车控（v67）：碰标签切换解锁/锁车
                SettingsItem(
                    icon = Icons.Filled.Nfc,
                    title = "NFC 车控",
                    subtitle = if (nfcEnabled) "已启用 · 碰标签切换锁车" else "未启用",
                    iconColor = PrimaryOrange,
                    showCheck = nfcEnabled,
                    onClick = { showNfcSheet = true }
                )

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                // v73：MQTT 实时推送设置入口
                SettingsItem(
                    icon = Icons.Filled.Cloud,
                    title = "MQTT 实时推送",
                    subtitle = if (!mqttCfg.enabled) "未启用" else when (mqttState) {
                        MqttConnectionState.SUBSCRIBED -> "已订阅"
                        MqttConnectionState.CONNECTED -> "已连接"
                        MqttConnectionState.ERROR -> "连接错误"
                        MqttConnectionState.CONNECTING, MqttConnectionState.RECONNECTING -> "连接中…"
                        else -> "已启用"
                    },
                    iconColor = PrimaryGreen,
                    showCheck = mqttCfg.enabled && mqttState.isActive,
                    onClick = { showMqttSheet = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Tools Section — 蓝牙钥匙 & ADB
        Text(
            text = "工具",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                SettingsItem(
                    icon = Icons.Filled.Lock,
                    title = "获取蓝牙钥匙",
                    subtitle = "从服务器下载 BLE 数字钥匙",
                    iconColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        scope.launch {
                            viewModel.appState.fetchAndStoreBleKey()
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // About Section
        Text(
            text = "关于",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                // 注：「用户协议」「隐私政策」两项已移除——
                // 隐私政策统一由「设置 → 隐私设置」提供（含权限状态与数据管理，更完整），
                // 避免「关于」与「设置」两处重复入口。
                SettingsItem(
                    icon = Icons.Filled.Info,
                    title = "关于我们",
                    subtitle = "开源仓库 · 开源致谢与版本信息",
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { showAboutDialog = true }
                )

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "版本",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // v70：自动重登设置（token 被官方 App 等其它端顶掉时自愈）
        val autoRelogin by viewModel.appState.autoReloginEnabled.collectAsState()
        val hasCred by viewModel.appState.hasSavedCredentials.collectAsState()
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "自动重新登录",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (hasCred) "已保存登录凭据（Keystore 加密）；Token 被其它设备顶掉时自动恢复"
                        else "登录后自动保存凭据，Token 失效时自动恢复登录",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoRelogin,
                    onCheckedChange = { viewModel.appState.setAutoReloginEnabled(it) }
                )
            }
            if (hasCred) {
                TextButton(
                    onClick = { viewModel.appState.clearSavedCredentials() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    Text(
                        text = "清除已保存的登录凭据",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Logout Button
        Button(
            onClick = { showLogoutDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = "退出登录",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryRed
            )
        }

        Spacer(modifier = Modifier.height(100.dp))
    }

    // Token Input Dialog
    // 注意：这里必须用自定义 Dialog 而不是 AlertDialog ——
    // AlertDialog 的 text slot 内嵌 verticalScroll 容器会拦截 OutlinedTextField
    // 的焦点请求，触屏设备上点击输入框无法聚焦/无法输入。
    // 自定义 Dialog + Surface 是与页面等价的标准容器，TextField 焦点与输入正常。
    if (showTokenDialog) {
        Dialog(
            onDismissRequest = { showTokenDialog = false }
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .imePadding()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "获取 Token",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                    Text(
                        text = "方式一：手机号 + 密码登录（推荐，自动获取）",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = loginMobile,
                        onValueChange = { loginMobile = it },
                        label = { Text("手机号") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = loginPassword,
                        onValueChange = { loginPassword = it },
                        label = { Text("密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    loginError?.let { err ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = err,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            loginLoading = true
                            loginError = null
                            viewModel.appState.loginWithPassword(
                                loginMobile.trim(), loginPassword
                            ) { ok, msg ->
                                loginLoading = false
                                // Toast 双保险：即使对话框状态异常用户也能看到结果
                                Toast.makeText(
                                    context,
                                    if (ok) "登录成功，Token 已自动保存" else "登录失败：$msg",
                                    Toast.LENGTH_LONG
                                ).show()
                                if (ok) {
                                    showTokenDialog = false
                                    loginMobile = ""
                                    loginPassword = ""
                                    loginError = null
                                } else {
                                    loginError = msg
                                }
                            }
                        },
                        enabled = !loginLoading && loginMobile.trim().isNotEmpty() && loginPassword.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (loginLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (loginLoading) "登录中..." else "登录并获取 Token")
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "方式二：手动粘贴 Token",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("Access Token") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "手动获取：打开五菱官方App → 登录后抓包获取 accessToken → 粘贴到上方输入框",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showTokenDialog = false }) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val trimmedToken = tokenInput.trim()
                            if (trimmedToken.isNotEmpty()) {
                                viewModel.appState.saveAndConfigureToken(trimmedToken)
                                showTokenDialog = false
                                tokenInput = ""
                                Toast.makeText(context, "Token 已保存", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = tokenInput.trim().isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }

    // Logout Confirm Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(
                    text = "确认退出登录？",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "退出后将清除已保存的 Token，需要重新配置才能使用远程控制功能。",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.appState.logout()
                        showLogoutDialog = false
                        tokenInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)
                ) {
                    Text("退出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Log Viewer Bottom Sheet
    if (showLogDialog) {
        LogViewerSheet(
            onDismiss = { showLogDialog = false }
        )
    }

    // Theme Settings Bottom Sheet
    if (showThemeSettings) {
        ThemeSettingsSheet(
            onDismiss = { showThemeSettings = false }
        )
    }

    // 系统保活设置（OPPO/ColorOS 自启动、电池优化、通知等）
    OemKeepAliveSheet(
        isOpen = showKeepAliveSheet,
        onClose = { showKeepAliveSheet = false }
    )

    // 关于我们（开源致谢 + 版本信息）
    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    // 车辆信息详情（点击顶部车辆卡进入，内容与「详情」页车辆信息一致）
    if (showVehicleInfo) {
        VehicleInfoDialog(vehicle = selectedVehicle, onDismiss = { showVehicleInfo = false })
    }

    // 消息通知设置（权限状态 + 通知渠道 + 跳转系统设置）
    NotificationSettingsSheet(
        isOpen = showNotificationSheet,
        onClose = { showNotificationSheet = false }
    )

    // 隐私设置（隐私政策全文 + 权限管理 + 本地数据清理）
    PrivacySettingsSheet(
        isOpen = showPrivacySheet,
        onClose = { showPrivacySheet = false },
        onClearLogs = {
            AppLogger.clear()
            // 清理更新安装包缓存（updates 目录）
            runCatching {
                java.io.File(context.cacheDir, "updates").deleteRecursively()
            }
            Toast.makeText(context, "已清除本机缓存数据", Toast.LENGTH_SHORT).show()
        }
    )

    // NFC 车控（v67：启用开关 + 绑定标签 + 模拟切换）
    NfcSettingsSheet(
        isOpen = showNfcSheet,
        controller = viewModel.appState.nfcController,
        onClose = { showNfcSheet = false }
    )

    // MQTT 实时推送（v73：可配置 broker / 凭证接口 / 订阅 topic）
    if (showMqttSheet) {
        MqttSettingsSheet(
            appState = viewModel.appState,
            onClose = { showMqttSheet = false }
        )
    }
}

/**
 * 车辆信息详情弹层。
 *
 * 展示内容与「详情」页的「车辆信息」区块**完全一致**（同一套接口字段），
 * 从「我的」页点击顶部车辆卡进入，无需切到「详情」Tab。
 *
 * @param vehicle 当前选中车辆（null 时展示未绑定提示）
 */
@Composable
private fun VehicleInfoDialog(vehicle: com.open.wuling.data.model.Vehicle?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.DirectionsCar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "车辆信息",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    if (vehicle == null) {
                        Text(
                            text = "暂无绑定车辆，请先配置 Token 并刷新",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    } else {
                        val info = vehicle.carInfo
                        if (info != null) {
                            DetailRow("车型", FormatUtils.safeString(info.carTypeName.ifEmpty { vehicle.name }))
                            DetailRow("型号", FormatUtils.safeString(info.model.ifEmpty { vehicle.model }))
                            DetailRow("配置", FormatUtils.safeString(info.seriesCode))
                            DetailRow("VIN", FormatUtils.safeString(info.vin.ifEmpty { vehicle.vin }))
                            DetailRow("车牌", FormatUtils.safeString(info.carPlate.ifEmpty { vehicle.licensePlate }))
                            DetailRow("颜色", FormatUtils.safeString(info.colorName.ifEmpty { info.colorCode }))
                            DetailRow("年份", FormatUtils.safeString(info.carYear))
                            DetailRow("VSN", FormatUtils.safeString(info.vsn))
                            DetailRow("等级", FormatUtils.safeString(info.level))
                            DetailRow("动力类型", FormatUtils.getPowerTypeDisplay(vehicle))
                            DetailRow("供应商", FormatUtils.safeString(info.providerCode))
                            DetailRow("购买人", FormatUtils.safeString(info.purchaseUserName))
                            DetailRow("购买店号", FormatUtils.safeString(info.purchaseShopNum))
                            DetailRow("购车日期", FormatUtils.formatDate(info.purchaseDate))
                            DetailRow("绑定手机", FormatUtils.safeString(info.bindCarUserMobile))
                            DetailRow("绑定状态", if (info.finishBind) "已绑定" else "未绑定")
                            DetailRow("蓝牙钥匙", FormatUtils.safeString(info.bluetoothKeyConnectMark))
                            DetailRow("摇晃解锁", if (info.shakeLock == 1) "开启" else "关闭")
                        } else {
                            DetailRow("车型", FormatUtils.safeString(vehicle.name))
                            DetailRow("型号", FormatUtils.safeString(vehicle.model))
                            DetailRow("车牌", FormatUtils.safeString(vehicle.licensePlate))
                            DetailRow("VIN", FormatUtils.safeString(vehicle.vin))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 「关于我们」对话框。
 *
 * 除版本信息外，重点列出本项目依赖的第三方开源项目，
 * 其中更新下载所使用的 GitHub 公益加速源来自 XIU2 的「Github 增强 - 高速下载」，
 * 特此在 App 内致谢。
 */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    /** 打开外链（统一加 NEW_TASK，避免非 Activity 上下文崩溃） */
    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    text = "关于我们",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "五菱车控  v${BuildConfig.VERSION_NAME}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "本应用为非官方社区项目，仅供个人学习与技术交流使用，" +
                        "与上汽通用五菱无任何关联。请遵守相关服务条款，勿用于商业用途。",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ===== 开源仓库（本项目源码地址）=====
                Text(
                    text = "开源仓库",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .clickable { openUrl(OPEN_SOURCE_REPO_URL) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "本项目完全开源",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = OPEN_SOURCE_REPO_DISPLAY,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "前往",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "开源致谢",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 致谢一：项目来源（hasscc/wuling 上游思路）
                Text(
                    text = "特别感谢 hasscc/wuling",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "本项目的车控协议思路参考自 hasscc 的开源项目 wuling（Home Assistant 版）。" +
                        "本应用在此基础上以原生 Android 独立实现，不依赖 Home Assistant，也不依赖官方 App。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "github.com/hasscc/wuling",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        openUrl("https://github.com/hasscc/wuling")
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 致谢二：加速源来自 XIU2/UserScript
                Text(
                    text = "特别感谢 XIU2 的「Github 增强 - 高速下载」",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "本应用的版本更新检查与 APK 下载所用的 GitHub 公益加速源，" +
                        "提取自该项目脚本（GPL-3.0），并经过本地实测筛选。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "github.com/XIU2/UserScript",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        openUrl("https://github.com/XIU2/UserScript")
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "其它开源组件",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "· Jetpack Compose / AndroidX\n" +
                        "· Kotlin Coroutines\n" +
                        "· OkHttp\n" +
                        "· Gson\n" +
                        "· Hilt（Dagger）\n" +
                        "· Coil\n" +
                        "· AndroidX DataStore\n" +
                        "· AndroidX Security Crypto\n" +
                        "· 友盟+ U-App（统计）",
                    fontSize = 12.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("知道了")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogViewerSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var logs by remember { mutableStateOf(AppLogger.getAllLogs()) }
    var logEnabled by remember { mutableStateOf(AppLogger.isEnabled()) }

    // v59：筛选（等级 / tag）与搜索
    var levelFilter by remember { mutableStateOf<AppLogger.Level?>(null) }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    // v59：details 展开状态（key=时间戳毫秒，毫秒内冲突极低，丢状态也只是收起）
    val expandedMap = remember { mutableStateMapOf<Long, Boolean>() }

    val context = LocalContext.current

    // v59：打开期间每秒自动刷新（此前需手动点「刷新」；详情页 5 秒轮询在滚，手动跟不住）
    // v84：生命周期敏感——仅页面 RESUMED 时轮询，退到后台暂停，避免后台空转耗电。
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                logs = AppLogger.getAllLogs()
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    // 等级 / tag 可选项从当前日志派生
    val tagOptions = remember(logs) { logs.map { it.tag }.distinct().sorted() }
    val filtered = remember(logs, levelFilter, tagFilter, query) {
        logs.filter { entry ->
            (levelFilter == null || entry.level == levelFilter) &&
                (tagFilter == null || entry.tag == tagFilter) &&
                (query.isBlank() || entry.message.contains(query, ignoreCase = true) ||
                    entry.tag.contains(query, ignoreCase = true) ||
                    entry.details?.contains(query, ignoreCase = true) == true)
        }.asReversed() // 最新在前；asReversed 是视图不复制列表
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "调试日志",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.weight(1f))

                // 启用/禁用开关（v59：状态持久化到 DataStore，重启后保持）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "记录",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = logEnabled,
                        onCheckedChange = {
                            logEnabled = it
                            AppLogger.setEnabled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // v59：导出——把当前筛选结果拼成文本走系统分享，报问题时直接发出
                TextButton(
                    enabled = filtered.isNotEmpty(),
                    onClick = {
                        val text = filtered.sortedBy { it.timestamp }
                            .joinToString("\n") { entry ->
                                buildString {
                                    append("[${entry.formattedTime}][${entry.level.name}][${entry.tag}] ${entry.message}")
                                    if (!entry.details.isNullOrBlank()) append("\n    ${entry.details}")
                                }
                            }
                        runCatching {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(intent, "分享调试日志"))
                        }
                    }
                ) {
                    Text("导出", color = MaterialTheme.colorScheme.primary)
                }

                // 清空（v59：内存 + 磁盘一起清）
                TextButton(onClick = {
                    AppLogger.clear()
                    logs = emptyList()
                }) {
                    Text("清空", color = PrimaryRed)
                }
            }

            // v59：筛选行——等级 chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = levelFilter == null,
                    onClick = { levelFilter = null },
                    label = { Text("全部", fontSize = 12.sp) }
                )
                AppLogger.Level.entries.forEach { level ->
                    FilterChip(
                        selected = levelFilter == level,
                        onClick = { levelFilter = if (levelFilter == level) null else level },
                        label = {
                            Text(
                                text = when (level) {
                                    AppLogger.Level.DEBUG -> "D"
                                    AppLogger.Level.INFO -> "I"
                                    AppLogger.Level.WARN -> "W"
                                    AppLogger.Level.ERROR -> "E"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }
            }

            // v59：筛选行——tag 下拉 + 搜索框
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (tagOptions.isNotEmpty()) {
                    var tagMenuOpen by remember { mutableStateOf(false) }
                    Box {
                        FilterChip(
                            selected = tagFilter != null,
                            onClick = { tagMenuOpen = true },
                            label = { Text(text = tagFilter ?: "Tag", fontSize = 12.sp, maxLines = 1) }
                        )
                        DropdownMenu(expanded = tagMenuOpen, onDismissRequest = { tagMenuOpen = false }) {
                            DropdownMenuItem(text = { Text("全部 Tag") }, onClick = { tagFilter = null; tagMenuOpen = false })
                            tagOptions.forEach { tag ->
                                DropdownMenuItem(
                                    text = { Text(tag, maxLines = 1) },
                                    onClick = { tagFilter = tag; tagMenuOpen = false }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f).height(52.dp),
                    placeholder = { Text("搜索消息/Tag/详情…", fontSize = 13.sp) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Log list
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (logs.isEmpty()) "暂无日志\n执行操作后日志将显示在这里" else "无匹配结果",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filtered.size) { index ->
                        val entry = filtered[index]
                        LogItem(
                            entry = entry,
                            detailsExpanded = expandedMap[entry.timestamp] == true,
                            onToggleDetails = {
                                expandedMap[entry.timestamp] = !(expandedMap[entry.timestamp] ?: false)
                            },
                            onCopy = {
                                val full = buildString {
                                    append("[${entry.formattedTime}][${entry.level.name}][${entry.tag}] ${entry.message}")
                                    if (!entry.details.isNullOrBlank()) append("\n${entry.details}")
                                }
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("log", full))
                                Toast.makeText(context, "已复制该条日志", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LogItem(
    entry: AppLogger.LogEntry,
    detailsExpanded: Boolean,
    onToggleDetails: () -> Unit,
    onCopy: () -> Unit
) {
    val levelColor = when (entry.level) {
        AppLogger.Level.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        AppLogger.Level.INFO -> MaterialTheme.colorScheme.primary
        AppLogger.Level.WARN -> PrimaryOrange
        AppLogger.Level.ERROR -> PrimaryRed
    }

    val levelPrefix = when (entry.level) {
        AppLogger.Level.DEBUG -> "D"
        AppLogger.Level.INFO -> "I"
        AppLogger.Level.WARN -> "W"
        AppLogger.Level.ERROR -> "E"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            // v59：点击卡片复制该条完整日志；有详情时点正文切换展开/收起
            .clickable(onClick = onCopy),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[$levelPrefix]",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = levelColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.formattedTime,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.tag,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = entry.message,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            entry.details?.let { details ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = details,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = if (detailsExpanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onToggleDetails)
                )
            }

            // v59：详情被截断时给出可展开提示
            if (entry.details != null && entry.details.length > 160 && !detailsExpanded) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "点击详情展开…",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.clickable(onClick = onToggleDetails)
                )
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    showCheck: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            subtitle?.let {
                Text(
                    text = it,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (showCheck) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = PrimaryGreen,
                modifier = Modifier.size(20.dp)
            )
        }

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/** 本项目开源仓库地址（App 内「关于我们」展示并跳转） */
private const val OPEN_SOURCE_REPO_URL = "https://github.com/daiyuxiang520/geren-update"
private const val OPEN_SOURCE_REPO_DISPLAY = "github.com/daiyuxiang520/geren-update"

/**
 * 版本号后缀：测试版通道时显式标注，避免用户误以为装的是正式版。
 * 稳定版通道不追加任何后缀，保持原样。
 */
private fun updateChannelTail(channel: String): String =
    if (channel == com.open.wuling.data.local.UpdateChannelPreferences.CHANNEL_BETA) " · 测试版" else ""

/**
 * 「更新通道」设置项（v75）：稳定版 / 测试版 二选一。
 *
 * 两条发布轨道各自独立（详见 UpdateConfig.UPDATE_JSON_URLS_BETA）：
 *  - 稳定版：正式发布的版本，面向全部用户；
 *  - 测试版：灰度验证 / 问题排查用，可能不稳定，用户自愿选择。
 *
 * 切换后即时持久化，下次「检查更新」即按新通道拉取。
 */
@Composable
private fun UpdateChannelItem(
    currentChannel: String,
    onChannelChange: (String) -> Unit
) {
    val isBeta = currentChannel == com.open.wuling.data.local.UpdateChannelPreferences.CHANNEL_BETA
    val summary = if (isBeta) "接收测试版更新（可能不稳定）" else "仅接收正式发布的版本"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isBeta) Icons.Filled.BugReport else Icons.Filled.Shield,
                contentDescription = null,
                tint = if (isBeta) Color(0xFFFF9800) else PrimaryGreen,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "更新通道",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = summary,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            ChannelChip(
                label = "稳定版",
                selected = !isBeta,
                modifier = Modifier.weight(1f),
                onClick = { onChannelChange(com.open.wuling.data.local.UpdateChannelPreferences.CHANNEL_STABLE) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            ChannelChip(
                label = "测试版",
                selected = isBeta,
                modifier = Modifier.weight(1f),
                onClick = { onChannelChange(com.open.wuling.data.local.UpdateChannelPreferences.CHANNEL_BETA) }
            )
        }
    }
}

/** 更新通道单选项（选中时高亮底色 + 边框着色） */
@Composable
private fun ChannelChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (selected) PrimaryGreen.copy(alpha = 0.15f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val borderColor = if (selected) PrimaryGreen else Color.Transparent
    val textColor = if (selected) PrimaryGreen else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = textColor
        )
    }
}
