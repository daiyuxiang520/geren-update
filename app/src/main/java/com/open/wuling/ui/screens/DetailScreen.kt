package com.open.wuling.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.open.wuling.data.model.ControlCommand
import com.open.wuling.data.model.Vehicle
import com.open.wuling.ui.components.CollectTimeText
import com.open.wuling.ui.theme.*
import com.open.wuling.ui.theme.LocalCardAlpha
import com.open.wuling.util.FormatUtils

@Composable
fun DetailScreen(
    modifier: Modifier = Modifier,
    vehicle: Vehicle?,
    onRefresh: () -> Unit = {},
    onQuickRefresh: () -> Unit = {},
    // v63：提醒条上的「立即锁车 / 一键关窗」走这里（由 MainActivity 转交 AppState）
    onCommand: (ControlCommand) -> Unit = {}
) {
    val scrollState = rememberScrollState()

    // 每 5 秒快速刷新（仅主状态，保留诊断/胎压/昨日里程）
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            onQuickRefresh()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        if (vehicle == null) {
            // 未配置状态
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = "请先配置 Token 并刷新车辆状态",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            val status = vehicle.status

            // v61：离车安全提醒条——车窗未关/车门未锁/后备箱未关时置顶展示（橙色，跟数据走，
            //      全部正常自动消失）。检测口径与系统通知共用 VehicleAlertManager.detectAlerts。
            //      这里不受通知开关控制：用户既然打开了详情页，就该看到完整真相。
            val safetyAlerts = remember(status) {
                com.open.wuling.util.VehicleAlertManager.detectAlerts(status, windows = true, doors = true, trunk = true)
            }
            if (safetyAlerts.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = PrimaryOrange.copy(alpha = 0.14f)
                    ),
                    shape = RoundedCornerShape(16.dp)
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
                            tint = PrimaryOrange,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "离车前请注意",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryOrange
                            )
                            Text(
                                text = safetyAlerts.joinToString("；"),
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // v63：能远程处理的问题直接在提醒条上给按钮（与通知 Action 同一套能力）
                    val hasUnlocked = safetyAlerts.any { it.contains("车门未锁") }
                    val hasWindowOpen = safetyAlerts.any { it.contains("车窗未关") }
                    if (hasUnlocked || hasWindowOpen) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (hasUnlocked) {
                                AlertActionButton(
                                    text = "立即锁车",
                                    modifier = Modifier.weight(1f),
                                    onClick = { onCommand(ControlCommand.LOCK) }
                                )
                            }
                            if (hasWindowOpen) {
                                AlertActionButton(
                                    text = "一键关窗",
                                    modifier = Modifier.weight(1f),
                                    onClick = { onCommand(ControlCommand.WINDOW_CLOSE) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // v58：删除「车辆信息」区块。该区块 v48 已删除过，但因工作区文件被旧副本覆盖而在 v56 静默回归。
            //      车辆静态档案（车型/VIN/车牌/颜色/购买信息等）请从「我的」页点击顶部车辆卡查看，
            //      那里的 VehicleInfoDialog 与本区块字段完全一致，信息不丢失。
            //      「详情」页回归其定位：只展示实时动态状态（电池/车门/车窗/胎压/定位…）。

            // ====== 电池 & 电量 ======
            DetailSectionHeader(icon = Icons.Filled.BatteryChargingFull, title = "电池与充电", color = PrimaryGreen)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow("电量 (SOC)", "${status.batteryLevel}%")
                    DetailRow("电池健康 (SOH)", "${status.batteryHealth}%")
                    DetailRow("电池状态", FormatUtils.getBatteryStatusText(status.batteryStatus))
                    DetailRow("电池平均温度", "${status.batAvgTemp}°C")
                    DetailRow("电池温度范围", "${status.batteryTempMin} ~ ${status.batteryTempMax}°C")
                    DetailRow("低压电池", "${status.lowBatVol} V")
                    DetailRow("剩余电量", "${status.leftBatteryPower} kWh")
                    DetailRow("电压", "${FormatUtils.formatIntValue(status.voltage)} V")
                    DetailRow("电流", "${FormatUtils.formatIntValue(status.current)} A")
                    DetailRow("充电状态", if (status.isCharging) "充电中" else "未充电")
                    // v69：充电功率。仅在「充电中」或服务端确实回传了功率时展示，
                    //      未充电时 chargePower 为空串 → null → 不显示该行，避免误导。
                    if (status.isCharging || (status.chargePower ?: 0.0) > 0.0) {
                        DetailRow("充电功率", FormatUtils.formatChargePower(status.chargePower))
                    }
                    DetailRow("充电指示灯", if (status.vecChrgStsIndOn) "亮" else "灭")
                    DetailRow("OBC 温度", "${status.tmActTemp}°C")
                    DetailRow("OBC 电流", "${status.obcOtpCur} A")
                    DetailRow("电机温度", "${status.invActTemp}°C")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ====== 续航 & 里程 ======
            DetailSectionHeader(icon = Icons.Filled.Speed, title = "续航与里程", color = PrimaryOrange)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow("剩余续航", "${status.range} km")
                    if (vehicle.hasFuel) {
                        DetailRow("燃油续航", "${status.oilRange} km")
                        if (status.leftFuel > 0) {
                            DetailRow("剩余油量", "${status.leftFuel}%")
                        }
                    }
                    DetailRow("总里程", "${status.mileage} km")
                    DetailRow("昨日里程", "${status.yesterMileage} km")
                    DetailRow("平均能耗", "${status.avgFuel}")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ====== 车门状态 ======
            DetailSectionHeader(icon = Icons.Filled.DoorFront, title = "车门状态", color = PrimaryPurple)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow("整车锁定", FormatUtils.getYesNo(status.isLocked))
                    DetailRow("左前门", "${FormatUtils.getOpenText(status.doors.frontLeft)} / ${FormatUtils.getLockText(status.doors.frontLeftLocked)}")
                    DetailRow("右前门", "${FormatUtils.getOpenText(status.doors.frontRight)} / ${FormatUtils.getLockText(status.doors.frontRightLocked)}")
                    DetailRow("左后门", "${FormatUtils.getOpenText(status.doors.rearLeft)} / ${FormatUtils.getLockText(status.doors.rearLeftLocked)}")
                    DetailRow("右后门", "${FormatUtils.getOpenText(status.doors.rearRight)} / ${FormatUtils.getLockText(status.doors.rearRightLocked)}")
                    DetailRow("尾箱", "${FormatUtils.getOpenText(status.doors.trunk)} / ${FormatUtils.getLockText(status.doors.trunkLocked)}")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ====== 车窗状态 ======
            DetailSectionHeader(icon = Icons.Filled.Visibility, title = "车窗状态", color = MaterialTheme.colorScheme.primary)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow("左前窗", "${FormatUtils.getOpenText(status.windows.frontLeft)} (${status.window1OpenDegree}%)")
                    DetailRow("右前窗", "${FormatUtils.getOpenText(status.windows.frontRight)} (${status.window2OpenDegree}%)")
                    DetailRow("左后窗", "${FormatUtils.getOpenText(status.windows.rearLeft)} (${status.window3OpenDegree}%)")
                    DetailRow("右后窗", "${FormatUtils.getOpenText(status.windows.rearRight)} (${status.window4OpenDegree}%)")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ====== 灯光状态 ======
            DetailSectionHeader(icon = Icons.Filled.Lightbulb, title = "灯光状态", color = PrimaryOrange)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow("前雾灯", FormatUtils.getOnOff(status.frontFogLight))
                    DetailRow("左转向灯", FormatUtils.getOnOff(status.leftTurnLight))
                    DetailRow("右转向灯", FormatUtils.getOnOff(status.rightTurnLight))
                    DetailRow("示廓灯", FormatUtils.getOnOff(status.positionLight))
                    DetailRow("远光灯", FormatUtils.getOnOff(status.dipHeadLight))
                    DetailRow("近光灯", FormatUtils.getOnOff(status.lowBeamLight))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ====== 温度 ======
            DetailSectionHeader(icon = Icons.Filled.Thermostat, title = "温度信息", color = PrimaryRed)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow("车内温度", "${status.interiorTemperature}°C")
                    DetailRow("空调温度", "${status.exteriorTemperature}°C")
                    DetailRow("空调状态", if (status.isClimateOn) "开启 (${FormatUtils.getClimateModeText(status.climateMode)})" else "关闭")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ====== 驾驶状态 ======
            DetailSectionHeader(icon = Icons.Filled.Build, title = "驾驶状态", color = MaterialTheme.colorScheme.primary)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow("档位", FormatUtils.getGearName(status.autoGearStatus))
                    DetailRow("方向盘角度", "${status.steeringWheelAngle}°")
                    DetailRow("刹车踏板", "${status.brakePedalPosition}")
                    DetailRow("油门踏板", "${status.accPosition}")
                    // v56：原「钥匙状态」保留（它是原始枚举：无钥匙/已连接/已启动），
                    //      在其上方补一行结果态「整机状态」，用户不必自己把 keyStatus 翻译成上/下电。
                    DetailRow("钥匙状态", FormatUtils.getKeyStatusText(status.keyStatus))
                    DetailRow("整机状态", FormatUtils.getPowerStatusText(status.keyStatus))
                    DetailRow("哨兵模式", if (status.sentinelModeStatus) "开启" else "关闭")
                    DetailRow("智能驾驶", if (status.intelligentCarSwitch == 1) "开启" else "关闭")
                    DetailRow("限距反馈", FormatUtils.safeString(status.limitFeedback))
                    if (status.averageSpeed.isNotEmpty()) {
                        DetailRow("平均车速", status.averageSpeed)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ====== 胎压 ======
            DetailSectionHeader(icon = Icons.Filled.TireRepair, title = "胎压监测", color = PrimaryGreen)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow("左前轮", "${FormatUtils.formatTirePressure(status.tirePressureFL)} bar")
                    DetailRow("右前轮", "${FormatUtils.formatTirePressure(status.tirePressureFR)} bar")
                    DetailRow("左后轮", "${FormatUtils.formatTirePressure(status.tirePressureRL)} bar")
                    DetailRow("右后轮", "${FormatUtils.formatTirePressure(status.tirePressureRR)} bar")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ====== 诊断状态 ======
            // ProblemConv(reverse=True): 0=异常, 1=正常
            // BinarySensorConv: 0=正常, 1=异常
            val enginePowText = FormatUtils.getDiagnosticStatus(status.enginePowStatus)
            val engineTempText = FormatUtils.getDiagnosticStatus(status.engineTempStatus)
            val absText = FormatUtils.getDiagnosticStatusBinary(status.absStatus)
            val powerSteeringText = FormatUtils.getDiagnosticStatusBinary(status.powerSteeringStatus)
            val hasAnyProblem = status.enginePowStatus == 0 || status.engineTempStatus == 0 ||
                                 status.absStatus == 1 || status.powerSteeringStatus == 1
            val diagColor = if (hasAnyProblem) PrimaryRed else PrimaryGreen

            DetailSectionHeader(icon = Icons.Filled.Build, title = "车辆诊断", color = diagColor)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow("动力系统", enginePowText, isWarning = status.enginePowStatus == 0)
                    DetailRow("发动机温度", engineTempText, isWarning = status.engineTempStatus == 0)
                    DetailRow("ABS系统", absText, isWarning = status.absStatus == 1)
                    DetailRow("动力转向", powerSteeringText, isWarning = status.powerSteeringStatus == 1)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ====== 定位 ======
            DetailSectionHeader(icon = Icons.Filled.LocationOn, title = "定位信息", color = PrimaryRed)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val lat = vehicle.location?.latitude
                    val lon = vehicle.location?.longitude
                    DetailRow("纬度", FormatUtils.formatCoordinate(lat))
                    DetailRow("经度", FormatUtils.formatCoordinate(lon))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ====== 数据采集时间 ======
            DetailSectionHeader(icon = Icons.Filled.Schedule, title = "数据时间", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow("采集时间", FormatUtils.safeString(status.collectTime))
                    // v62：相对时间 + 陈旧提示（解析失败时组件回退原样字符串）
                    Spacer(modifier = Modifier.height(8.dp))
                    CollectTimeText(collectTime = status.collectTime)
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

/**
 * 提醒条上的操作按钮（v63）。
 * 用实心橙底：提醒条本身就是橙色警示区，按钮要与「可点击」的语义区分开，
 * 不能做成低调的描边样式让人以为只是文字说明。
 */
@Composable
private fun AlertActionButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryOrange,
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

// ====== Section Header ======
@Composable
private fun DetailSectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, color: Color) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ====== Detail Row ======
@Composable
private fun DetailRow(label: String, value: String, isWarning: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = if (isWarning) PrimaryRed else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
    Divider(
        color = MaterialTheme.colorScheme.surfaceVariant,
        thickness = 0.5.dp
    )
}
