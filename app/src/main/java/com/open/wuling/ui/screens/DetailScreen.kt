package com.open.wuling.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.open.wuling.data.model.Vehicle
import com.open.wuling.ui.theme.*
import com.open.wuling.ui.theme.LocalCardAlpha
import com.open.wuling.util.FormatUtils

@Composable
fun DetailScreen(
    modifier: Modifier = Modifier,
    vehicle: Vehicle?,
    onRefresh: () -> Unit = {},
    onQuickRefresh: () -> Unit = {}
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
            val info = vehicle.carInfo

            // ====== 车辆信息 ======
            DetailSectionHeader(icon = Icons.Filled.DirectionsCar, title = "车辆信息", color = MaterialTheme.colorScheme.primary)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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

            Spacer(modifier = Modifier.height(20.dp))

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
                    DetailRow("钥匙状态", FormatUtils.getKeyStatusText(status.keyStatus))
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
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
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
