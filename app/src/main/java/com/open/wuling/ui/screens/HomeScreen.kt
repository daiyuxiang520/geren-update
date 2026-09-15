package com.open.wuling.ui.screens

import com.open.wuling.analytics.UmengPageView

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.open.wuling.ble.BleAutoLockManager
import com.open.wuling.data.model.ControlCommand
import com.open.wuling.data.model.Vehicle
import com.open.wuling.data.model.VehicleStatus
import com.open.wuling.data.model.hasAnyOpen
import com.open.wuling.util.FormatUtils
import com.open.wuling.ui.theme.*
import com.open.wuling.ui.theme.LocalCardAlpha

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    vehicle: Vehicle?,
    isLoading: Boolean,
    errorMessage: String?,
    commandResult: com.open.wuling.CommandResult?,
    onRefresh: () -> Unit,
    onCommand: (ControlCommand) -> Unit,
    onClearError: () -> Unit,
    onOpenBleSettings: () -> Unit = {},
    bleConnectionState: BleAutoLockManager.ConnectionState = BleAutoLockManager.ConnectionState.Disconnected,
    onToggleBleConnection: () -> Unit = {},
    bleFilteredRssi: Int? = null
) {
    UmengPageView("车辆")

    val scrollState = rememberScrollState()

    // 仅在首次加载且未配置时自动刷新
    LaunchedEffect(vehicle) {
        if (vehicle == null) {
            onRefresh()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (vehicle == null) {
            // 无车辆状态：加载中 vs 未配置，给出不同提示（避免冷启动误报「请配置 Token」）
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(42.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "正在获取车辆数据…",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.DirectionsCar,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "暂无车辆信息",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "请配置 API Token 并刷新车辆状态",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onRefresh,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("刷新车辆状态")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
            ) {
                // 1. 顶部标题栏
                HomeTopBar(
                    vehicle = vehicle,
                    bleConnectionState = bleConnectionState,
                    onToggleBleConnection = onToggleBleConnection,
                    onRefresh = onRefresh
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 2. 车辆信息大卡片
                VehicleSummaryCard(vehicle = vehicle)

                Spacer(modifier = Modifier.height(20.dp))

                // 3. 快捷控制
                QuickControlSection(
                    isLocked = vehicle.status.isLocked,
                    isClimateOn = vehicle.status.isClimateOn,
                    windowsOpen = vehicle.status.windows.hasAnyOpen(
                        vehicle.status.window1OpenDegree,
                        vehicle.status.window2OpenDegree,
                        vehicle.status.window3OpenDegree,
                        vehicle.status.window4OpenDegree
                    ),
                    isPowerOn = FormatUtils.isPowerOn(vehicle.status.keyStatus),
                    onCommand = onCommand
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 4. 车辆数据详情
                DataGridCard(status = vehicle.status)

                Spacer(modifier = Modifier.height(20.dp))

                // 5. 车窗状态 / 车门状态 双卡片
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatusDotCard(
                        modifier = Modifier.weight(1f),
                        title = "车窗状态",
                        items = listOf(
                            !vehicle.status.windows.frontLeft to "左前",
                            !vehicle.status.windows.frontRight to "右前",
                            !vehicle.status.windows.rearLeft to "左后",
                            !vehicle.status.windows.rearRight to "右后"
                        )
                    )
                    StatusDotCard(
                        modifier = Modifier.weight(1f),
                        title = "车门状态",
                        items = listOf(
                            !vehicle.status.doors.frontLeft to "左前",
                            !vehicle.status.doors.frontRight to "右前",
                            !vehicle.status.doors.rearLeft to "左后",
                            !vehicle.status.doors.rearRight to "右后"
                        ),
                        hiddenOpenCount = if (vehicle.status.doors.trunk) 1 else 0
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // BLE 无感控车（功能入口保留）
                BleAutoLockSection(
                    onOpenSettings = onOpenBleSettings,
                    bleFilteredRssi = bleFilteredRssi
                )

                Spacer(modifier = Modifier.height(100.dp))
            }
        }

        // 加载指示器
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // 错误提示
        errorMessage?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = onClearError) {
                        Text("关闭", color = Color.White)
                    }
                },
                containerColor = PrimaryRed
            ) {
                Text(error)
            }
        }

        // 命令结果提示
        commandResult?.let { result ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor = if (result.success) PrimaryGreen else PrimaryRed
            ) {
                Text(result.message)
            }
        }
    }
}

// ==================== 1. 顶部标题栏 ====================

@Composable
private fun HomeTopBar(
    vehicle: Vehicle,
    bleConnectionState: BleAutoLockManager.ConnectionState,
    onToggleBleConnection: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "车辆控制",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = vehicle.displayName,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        // 注：已移除顶部栏「M」(MQTT 指示) 与蓝牙图标（无实际功能），仅保留刷新按钮

        IconButton(
            onClick = onRefresh,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "刷新",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ==================== 2. 车辆信息大卡片 ====================

@Composable
private fun VehicleSummaryCard(vehicle: Vehicle) {
    val status = vehicle.status
    val showFuel = vehicle.hasFuel && status.leftFuel > 0
    // v56：统一走 FormatUtils，避免 HomeScreen / 详情页 / 桌面小组件三处各写一套 keyStatus 判断
    val isPowerOn = FormatUtils.isPowerOn(status.keyStatus)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左列：车辆图片 + 四轮胎压圆点
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(112.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    if (vehicle.carInfo?.image?.isNotEmpty() == true) {
                        AsyncImage(
                            model = vehicle.carInfo.image,
                            contentDescription = vehicle.displayName,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.DirectionsCar,
                            contentDescription = null,
                            modifier = Modifier
                                .size(56.dp)
                                .align(Alignment.Center),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        TireDot(status.tirePressureFL)
                        TireDot(status.tirePressureFR)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        TireDot(status.tirePressureRL)
                        TireDot(status.tirePressureRR)
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 右列：综合续航（电续航+油续航） + 电量/油量进度条
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${status.range + if (showFuel) status.oilRange else 0}",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = " km 综合续航",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isPowerOn) BatteryGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
                            .align(Alignment.CenterVertically)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = FormatUtils.getPowerStatusText(status.keyStatus),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 绿色：电池电量 + 纯电续航
                RangeProgressBar(
                    icon = Icons.Filled.BatteryChargingFull,
                    percent = status.batteryLevel,
                    rangeKm = status.electricRange,
                    barColor = BatteryGreen
                )

                // 橙色：油量百分比 + 燃油续航（仅混动/燃油）
                if (showFuel) {
                    Spacer(modifier = Modifier.height(12.dp))
                    RangeProgressBar(
                        icon = Icons.Filled.LocalGasStation,
                        percent = status.leftFuel.coerceIn(0, 100),
                        rangeKm = status.oilRange,
                        barColor = PrimaryOrange
                    )
                }
            }
        }
    }
}

@Composable
private fun RangeProgressBar(
    icon: ImageVector,
    percent: Int,
    rangeKm: Int,
    barColor: Color
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = barColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$percent%",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "$rangeKm km",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { percent.coerceIn(0, 100) / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            gapSize = 0.dp,
            drawStopIndicator = {}
        )
    }
}

@Composable
private fun TireDot(value: Double) {
    val color = when {
        value <= 0.0 -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        value < 2.0 -> PrimaryRed
        else -> PrimaryOrange
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (value > 0) String.format("%.2f", value) else "--",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ==================== 3. 快捷控制 ====================

@Composable
private fun QuickControlSection(
    isLocked: Boolean,
    isClimateOn: Boolean,
    windowsOpen: Boolean,
    isPowerOn: Boolean,
    onCommand: (ControlCommand) -> Unit
) {
    Column {
        // v57：去掉「快捷控制」标题。原状态行被挤在标题右侧约 1/3 宽度里，四个维度必然折行，
        //      折了之后「车窗全关」还会被劈成两行。去掉标题后状态行独占整行，不再折行。
        //      这一组按钮的用途（解锁/空调/寻车…）本身已由图标+文字自解释，标题属于冗余。
        //
        // v53：状态行含「车窗」，未关时转橙 —— 只表达"有没有窗没关"这一个用户真正关心的信息，
        //      四窗状态全展开塞不下，且全关是默认态、无需报告。
        // v56：补上「上电/下电」。车已上电 = 有人在场/车在通电，是锁车前最该确认的一环，放在最前。
        Text(
            text = buildString {
                append("车辆状态：")
                append(if (isPowerOn) "上电" else "下电")
                append(" · ")
                append(if (isLocked) "已锁" else "未锁")
                append(" · ")
                append(if (isClimateOn) "空调开启" else "空调关闭")
                append(" · ")
                append(if (windowsOpen) "车窗未关" else "车窗全关")
            },
            fontSize = 13.sp,
            // 车窗未关 = 潜在风险（淋雨／被盗），用告警色；其余保持弱化文字色
            color = if (windowsOpen) PrimaryOrange else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (windowsOpen) FontWeight.Medium else FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(12.dp))

        // 第一行：解锁、空调、寻车、启动
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickButton(
                modifier = Modifier.weight(1f),
                icon = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                label = if (isLocked) "解锁" else "锁车",
                tint = if (isLocked) PrimaryGreen else PrimaryOrange,
                onClick = { onCommand(if (isLocked) ControlCommand.UNLOCK else ControlCommand.LOCK) }
            )
            QuickButton(
                modifier = Modifier.weight(1f),
                icon = if (isClimateOn) Icons.Filled.Air else Icons.Filled.Thermostat,
                label = "空调",
                tint = if (isClimateOn) MaterialTheme.colorScheme.primary else PrimaryOrange,
                onClick = { onCommand(if (isClimateOn) ControlCommand.CLIMATE_OFF else ControlCommand.CLIMATE_ON) }
            )
            QuickButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Search,
                label = "寻车",
                tint = MaterialTheme.colorScheme.primary,
                onClick = { onCommand(ControlCommand.FIND_CAR) }
            )
            QuickButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.PowerSettingsNew,
                // v56：按当前上/下电状态切换文案与配色。上电后按钮转为「已上电」提示态（绿），
                //      避免用户反复点「启动」却看不到反馈 —— 车机上这个按钮此前无任何状态表达。
                //      说明：底层仍走同一个 IGNITION 授权指令（远程启动/授权点火），文案随状态走。
                label = if (isPowerOn) "已上电" else "启动",
                tint = if (isPowerOn) BatteryGreen else MaterialTheme.colorScheme.primary,
                onClick = { onCommand(ControlCommand.IGNITION) }
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        // 第二行：开窗、关窗、预约充电、尾门锁
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.VerticalShadesClosed,
                label = "开窗",
                tint = MaterialTheme.colorScheme.primary,
                onClick = { onCommand(ControlCommand.WINDOW_OPEN) }
            )
            QuickButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.VerticalShades,
                label = "关窗",
                tint = MaterialTheme.colorScheme.primary,
                onClick = { onCommand(ControlCommand.WINDOW_CLOSE) }
            )
            QuickButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.EvStation,
                label = "预约充电",
                tint = MaterialTheme.colorScheme.primary,
                onClick = { onCommand(ControlCommand.CHARGE_RESERVE) }
            )
            QuickButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.DirectionsCar,
                label = "尾门锁",
                tint = MaterialTheme.colorScheme.primary,
                onClick = { onCommand(ControlCommand.TRUNK) }
            )
        }
    }
}

@Composable
private fun QuickButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(18.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 14.dp),
        modifier = modifier.height(84.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = tint,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ==================== 4. 车辆数据详情 ====================

@Composable
private fun DataGridCard(status: VehicleStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 18.dp, horizontal = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                DataCell("${status.batteryHealth}%", "电池健康", Modifier.weight(1f))
                DataCell(
                    if (status.leftBatteryPower > 0) String.format("%.1f", status.leftBatteryPower) + " kWh" else "--",
                    "剩余电量",
                    Modifier.weight(1f)
                )
                DataCell("${status.interiorTemperature}°C", "车内温度", Modifier.weight(1f))
                DataCell(
                    if (status.batAvgTemp > 0) "${status.batAvgTemp}°C" else "--",
                    "电池温度",
                    Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                DataCell("${status.mileage} km", "总里程", Modifier.weight(1f))
                DataCell("${status.yesterMileage} km", "昨日里程", Modifier.weight(1f))
                DataCell(
                    if (status.tmActTemp > 0) "${status.tmActTemp}°C" else "--",
                    "电机温度",
                    Modifier.weight(1f)
                )
                DataCell(
                    if (status.lowBatVol > 0) String.format("%.2f", status.lowBatVol) + " V" else "--",
                    "低压电池",
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DataCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ==================== 5. 车窗/车门状态双卡片 ====================

@Composable
private fun StatusDotCard(
    modifier: Modifier = Modifier,
    title: String,
    items: List<Pair<Boolean, String>>,
    hiddenOpenCount: Int = 0
) {
    val openCount = items.count { !it.first } + hiddenOpenCount
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (openCount == 0) "全部关闭" else "$openCount 扇未关",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (openCount == 0) BatteryGreen else PrimaryOrange
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                items.forEach { (closed, label) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (closed) BatteryGreen else PrimaryOrange)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ==================== BLE 无感控车（功能入口保留） ====================

@Composable
private fun BleAutoLockSection(onOpenSettings: () -> Unit, bleFilteredRssi: Int? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bluetooth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "无感控车",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "靠近自动解锁，远离自动上锁",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    bleFilteredRssi?.let { rssi ->
                        val rssiColor = when {
                            rssi >= -60 -> MaterialTheme.colorScheme.primary
                            rssi >= -80 -> PrimaryOrange
                            else -> PrimaryRed
                        }
                        Text(
                            text = "$rssi dBm",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = rssiColor
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "设置",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
