package com.open.wuling.ui.screens

import com.open.wuling.analytics.UmengPageView

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.open.wuling.ui.components.OemKeepAliveSheet
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
    val scrollState = rememberScrollState()

    var showTokenDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }
    var showThemeSettings by remember { mutableStateOf(false) }
    var showKeepAliveSheet by remember { mutableStateOf(false) }
    var showNotificationSheet by remember { mutableStateOf(false) }
    var showPrivacySheet by remember { mutableStateOf(false) }
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
                    subtitle = "当前版本 v${BuildConfig.VERSION_NAME} · 多源加速",
                    iconColor = PrimaryGreen,
                    onClick = { viewModel.appState.checkAppUpdate(manual = true) }
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
                SettingsItem(
                    icon = Icons.Filled.Info,
                    title = "用户协议",
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { }
                )

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                SettingsItem(
                    icon = Icons.Filled.PrivacyTip,
                    title = "隐私政策",
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { }
                )

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                SettingsItem(
                    icon = Icons.Filled.Info,
                    title = "关于我们",
                    subtitle = "开源致谢与版本信息",
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

                // 启用/禁用开关
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

                Spacer(modifier = Modifier.width(12.dp))

                // 清空按钮
                TextButton(onClick = {
                    AppLogger.clear()
                    logs = emptyList()
                }) {
                    Text("清空", color = PrimaryRed)
                }

                // 刷新按钮
                TextButton(onClick = { logs = AppLogger.getAllLogs() }) {
                    Text("刷新", color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Log list
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无日志\n执行操作后日志将显示在这里",
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
                    items(logs.reversed()) { entry ->
                        LogItem(entry = entry)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LogItem(entry: AppLogger.LogEntry) {
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
            .padding(vertical = 4.dp),
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
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
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
