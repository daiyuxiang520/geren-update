package com.open.wuling.data.model

// ============== User ==============
data class User(
    val id: String,
    val name: String,
    val phone: String,
    val avatar: String? = null,
    val vehicles: List<Vehicle> = emptyList()
)

// ============== Vehicle ==============
data class Vehicle(
    val id: String,
    val vin: String,
    val name: String,
    val licensePlate: String,
    val model: String,
    val status: VehicleStatus,
    val carInfo: CarInfo? = null,
    val location: VehicleLocation? = null,
    val isPureElectric: Boolean = true,
    /** 是否有燃油组件（混动/增程/燃油车为 true，纯电为 false） */
    val hasFuel: Boolean = false,
    /** "ev" | "hybrid" | "fuel" */
    val energyKind: String = "ev"
) {
    val displayName: String
        get() = if (licensePlate.isEmpty()) name else licensePlate

    val isHybrid: Boolean get() = energyKind == "hybrid"
}

// ============== CarInfo（车辆信息，来自 carInfo 字段） ==============
data class CarInfo(
    val carInfoId: Long = 0,
    val userId: String = "",
    val vin: String = "",
    val carName: String = "",
    val colorCode: String = "",
    val colorName: String = "",
    val vsn: String = "",
    val carPlate: String = "",
    val carTypeName: String = "",
    val model: String = "",
    val level: String = "",
    val engineType: Int = 0,
    val image: String = "",
    val providerCode: String = "",
    val carYear: String = "",
    val seriesCode: String = "",
    val powerType: String = "",
    val purchaseDate: Long = 0,
    val purchaseUserName: String = "",
    val purchaseShopNum: String = "",
    val carOwnerDay: Int = 0,
    val bindCarUserMobile: String = "",
    val finishBind: Boolean = false,
    val shakeLock: Int = 0,
    val bluetoothKeyConnectMark: String = "",
    val supportAutoAir: Int = 0,
    val supportChargeRemain: Int = 0,
    val supportChargePower: Int = 0,
    val supportAvgFuel: Int = 0,
    val supportHybridMileage: Int = 0,
    val supportMqtt: Int = 0,
    val controlView: Int = 0,
    val bleType: Int = 0,
    val physicsEngine: Int = 0
)

// ============== VehicleStatus ==============
data class VehicleStatus(
    // 基础
    val batteryLevel: Int = 75,
    val range: Int = 420,
    val electricRange: Int = 420,
    val oilRange: Int = 300,
    val isLocked: Boolean = true,
    val isClimateOn: Boolean = false,
    val climateMode: String = "off",  // 空调模式: off=关闭, cool=制冷, heat=制热
    val climateTemperature: Int = 24,
    val doors: DoorStatus = DoorStatus(),
    val windows: WindowStatus = WindowStatus(),
    val mileage: Int = 12580,
    val isCharging: Boolean = false,
    val chargingTimeRemaining: Int? = null,
    val vecChargeSts: Int = 0,  // 充电状态（0-未知，1-连接中，2-充电中）
    val tirePressureFL: Double = 2.3,
    val tirePressureFR: Double = 2.3,
    val tirePressureRL: Double = 2.3,
    val tirePressureRR: Double = 2.3,
    val tireTemperature: Int = 0,  // 轮胎温度
    val interiorTemperature: Int = 25,
    val exteriorTemperature: Int = 20,
    val batteryHealth: Int = 95,
    val batteryTempMin: Int = 20,
    val batteryTempMax: Int = 28,
    val voltage: Double = 350.0,
    val current: Double = 50.0,
    val chargePower: Double = 6.6,
    val gearStatus: String = "P",

    // 灯光
    val frontFogLight: Boolean = false,
    val leftTurnLight: Boolean = false,
    val positionLight: Boolean = false,
    val rightTurnLight: Boolean = false,
    val dipHeadLight: Boolean = false,
    val lowBeamLight: Boolean = false,

    // 钥匙 & 档位
    val keyStatus: String = "0",
    val autoGearStatus: String = "10",

    // 车窗开度 (0-100%)
    val window1OpenDegree: Int = 0,
    val window2OpenDegree: Int = 0,
    val window3OpenDegree: Int = 0,
    val window4OpenDegree: Int = 0,

    // 电池详细
    val lowBatVol: Double = 0.0,
    val batAvgTemp: Int = 0,
    val batteryStatus: String = "0",
    val leftBatteryPower: Double = 0.0,

    // 电机/充电温度
    val tmActTemp: Int = 0,
    val invActTemp: Int = 0,
    val obcOtpCur: Double = 0.0,

    // 充电指示
    val vecChrgStsIndOn: Boolean = false,
    val chargingRaw: String = "0",

    // 里程
    val yesterMileage: Int = 0,
    val avgFuel: Double = 0.0,  // 平均能耗 (kWh/100km 或 L/100km)
    val leftFuel: Int = 0,      // 剩余油量百分比 (混动车型，0-100)
    val hybridMileageKm: Int? = null, // 混动里程（来自 hybridMileage 字段）

    // 驾驶状态
    val steeringWheelAngle: String = "0",
    val brakePedalPosition: String = "0",
    val accPosition: String = "0",
    val averageSpeed: String = "",

    // 安全
    val sentinelModeStatus: Boolean = false,
    val limitFeedback: String = "-1",

    // 座椅
    val seat1HotStatus: String = "",
    val seat2HotStatus: String = "",
    val seat3HotStatus: String = "",
    val seat4HotStatus: String = "",
    val seat1WindStatus: String = "",
    val seat2WindStatus: String = "",
    val seat3WindStatus: String = "",
    val seat4WindStatus: String = "",

    // 其他
    val intelligentCarSwitch: Int = 0,
    val collectTime: String = "",

    // ====== 诊断状态 (CheckStatus) ======
    // 来自 /car/check/all API
    // ProblemConv(reverse=True): 值被反转 - 0=异常, 1=正常
    // 0=异常/有故障, 1=正常
    val enginePowStatus: Int = 0,      // 动力系统: 0=异常, 1=正常
    val engineTempStatus: Int = 0,     // 发动机温度: 0=异常, 1=正常
    // BinarySensorConv: 0=正常, 1=异常
    val absStatus: Int = 0,            // ABS系统: 0=正常, 1=异常
    val powerSteeringStatus: Int = 0   // 动力转向: 0=正常, 1=异常
)

// ============== DoorStatus ==============
data class DoorStatus(
    val frontLeft: Boolean = false,
    val frontRight: Boolean = false,
    val rearLeft: Boolean = false,
    val rearRight: Boolean = false,
    val trunk: Boolean = false,
    // 锁定状态（独立于打开状态）
    val frontLeftLocked: Boolean = true,
    val frontRightLocked: Boolean = true,
    val rearLeftLocked: Boolean = true,
    val rearRightLocked: Boolean = true,
    val trunkLocked: Boolean = true
)

// ============== WindowStatus ==============
data class WindowStatus(
    val frontLeft: Boolean = false,
    val frontRight: Boolean = false,
    val rearLeft: Boolean = false,
    val rearRight: Boolean = false
)

// ============== VehicleLocation ==============
data class VehicleLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

// ============== ControlCommand ==============
enum class ControlCommand(val rawValue: String, val displayName: String) {
    LOCK("lockDoor", "锁车"),
    UNLOCK("unlockDoor", "解锁"),
    CLIMATE_ON("climateOn", "开启空调"),
    CLIMATE_OFF("climateOff", "关闭空调"),
    FLASH("flashLight", "闪灯"),
    HONK("honk", "鸣笛"),
    TRUNK("openTailBox", "尾箱"),
    FIND_CAR("findCar", "寻车"),
    WINDOW_OPEN("openWindow", "开窗"),
    WINDOW_CLOSE("closeWindow", "关窗"),
    IGNITION("ignition", "启动"),
    CHARGE_RESERVE("reserveCharge", "预约充电")
}

// ============== Mock Data ==============
object MockData {

    val mockVehicleStatus = VehicleStatus(
        batteryLevel = 75,
        range = 420,
        electricRange = 420,
        oilRange = 0,
        isLocked = true,
        isClimateOn = false,
        mileage = 12580,
        isCharging = false
    )

    val mockHybridStatus = VehicleStatus(
        batteryLevel = 60,
        range = 720,
        electricRange = 120,
        oilRange = 600,
        isLocked = false,
        isClimateOn = true,
        mileage = 8560,
        isCharging = true,
        chargingTimeRemaining = 180
    )

    val mockLocation = VehicleLocation(
        latitude = 31.2304,
        longitude = 121.4737,
        address = "上海市人民广场"
    )

    val mockVehicle = Vehicle(
        id = "vehicle_001",
        vin = "TESTVIN0000000001",
        name = "我的五菱",
        licensePlate = "测试A00001",
        model = "五菱星光",
        status = mockVehicleStatus,
        location = mockLocation,
        isPureElectric = true,
        energyKind = "ev"
    )

    val mockHybridVehicle = Vehicle(
        id = "vehicle_002",
        vin = "TESTVIN0000000002",
        name = "五菱星辰",
        licensePlate = "测试B00002",
        model = "五菱星辰混动",
        status = mockHybridStatus,
        location = mockLocation,
        isPureElectric = false,
        hasFuel = true,
        energyKind = "hybrid"
    )

    val mockUser = User(
        id = "user_001",
        name = "五菱车主",
        phone = "138****0000",
        vehicles = listOf(mockVehicle, mockHybridVehicle)
    )
}
