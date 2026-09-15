package com.open.wuling.data.repository

import com.open.wuling.data.api.APIConfig
import com.open.wuling.data.api.APIError
import com.open.wuling.data.api.BleKeyResponse
import com.open.wuling.data.api.CarStatusResponse
import com.open.wuling.data.api.CheckStatusResponse
import com.open.wuling.data.api.CommandResponse
import com.open.wuling.data.api.SearchCarResponse
import com.open.wuling.data.api.TirePressureResponse
import com.open.wuling.data.api.AuthorizeResponse
import com.open.wuling.data.api.WindowControlResponse
import com.open.wuling.data.api.WulingAPI
import com.open.wuling.data.api.toCarInfo
import com.open.wuling.data.api.toVehicleStatus
import com.open.wuling.data.model.Vehicle
import com.open.wuling.data.model.VehicleLocation
import com.open.wuling.data.model.VehicleStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 车辆数据 Repository - 解耦网络层与状态管理
 */
@Singleton
class VehicleRepository @Inject constructor(
    private val api: WulingAPI
) {
    private val TAG = "VehicleRepository"

    private data class Capabilities(
        val hasBattery: Boolean,
        val hasFuel: Boolean,
        val energyKind: String  // "ev" | "hybrid" | "fuel"
    )

    /**
     * 多信号检测车辆动力类型，参照 wulingscr.js detectCapabilities 逻辑。
     * engineType 不可靠（混动车型经常返回 0），改用字段值 + 关键词匹配。
     */
    private fun detectCapabilities(
        apiStatus: com.open.wuling.data.api.CarStatusApi,
        apiInfo: com.open.wuling.data.api.CarInfoApi?
    ): Capabilities {
        // Text hints for keyword matching (vehicle name/type/powerType)
        val textHints = sequenceOf(
            apiInfo?.powerType, apiInfo?.engineType?.toString(), apiInfo?.physicsEngine?.toString(),
            apiInfo?.carTypeName, apiInfo?.carName, apiInfo?.model,
            apiStatus.statusName
        ).map { (it ?: "").trim().lowercase() }
         .filter { it.isNotEmpty() }
         .joinToString("|")

        val evKeywords = listOf("纯电", "电动", "ev", "bev")
        val fuelKeywords = listOf("混动", "插混", "增程", "燃油", "汽油", "柴油", "phev", "hev", "reev", "hybrid")

        fun hasNumeric(vararg values: Any?): Boolean =
            values.any {
                when (it) {
                    is Number -> it.toDouble() > 0
                    is String -> it.toDoubleOrNull()?.let { d -> d > 0 } == true
                    else -> false
                }
            }

        // physicsEngine: 1=EV 2=HEV 3/4=PHEV/REEV 5=FuelCell
        val isPhysicsHybrid = (apiInfo?.physicsEngine ?: 0) >= 2 && (apiInfo?.physicsEngine ?: 0) <= 4

        val hasBatterySignal = hasNumeric(
            apiStatus.batterySoc, apiInfo?.supportBatteryIndicate,
            apiStatus.leftMileage, apiStatus.voltage,
            apiStatus.batSOH, apiStatus.batHealth, apiStatus.leftBatteryPower
        ) || evKeywords.any { textHints.contains(it) }
          || (apiInfo?.physicsEngine == 1)

        val hasFuelSignal = hasNumeric(
            apiInfo?.supportAvgFuel, apiInfo?.supportHybridMileage,
            apiStatus.leftFuel, apiStatus.oilLeftMileage,
            apiStatus.hybridMileage, apiStatus.avgFuel
        ) || fuelKeywords.any { textHints.contains(it) }
          || isPhysicsHybrid

        val energyKind = when {
            isPhysicsHybrid
                || hasNumeric(apiInfo?.supportHybridMileage, apiStatus.hybridMileage)
                || (hasBatterySignal && hasFuelSignal) -> "hybrid"
            hasBatterySignal -> "ev"
            hasFuelSignal -> "fuel"
            else -> "ev"  // default to EV
        }

        return Capabilities(hasBatterySignal, hasFuelSignal, energyKind)
    }

    private fun buildVehicleFromResponse(
        apiStatus: com.open.wuling.data.api.CarStatusApi,
        apiInfo: com.open.wuling.data.api.CarInfoApi?,
        status: VehicleStatus
    ): Vehicle {
        val carInfo = apiInfo?.toCarInfo()
        val caps = detectCapabilities(apiStatus, apiInfo)

        return Vehicle(
            id = apiInfo?.carInfoId?.toString() ?: "",
            vin = apiInfo?.vin ?: "",
            name = apiInfo?.carTypeName ?: apiInfo?.carName ?: "",
            licensePlate = apiInfo?.carPlate ?: "",
            model = apiInfo?.seriesCode ?: apiInfo?.carTypeName ?: "",
            status = status,
            carInfo = carInfo,
            // ⚠️ 条件赋值：接口任何一次没返回经纬度，location 就会变成 null
            //    （调用方需自行做「最后有效坐标」兜底，见 LocationScreen 的 activeCoord）。
            // 注：VehicleLocation.address 服务端**不提供**（CarStatusApi 无该字段），
            //     位置页地址一律由高德逆地理解析得到。
            location = if (apiStatus.latitude != null && apiStatus.longitude != null) {
                VehicleLocation(
                    latitude = apiStatus.latitude,
                    longitude = apiStatus.longitude
                )
            } else null,
            isPureElectric = caps.energyKind == "ev",
            hasFuel = caps.hasFuel,
            energyKind = caps.energyKind
        )
    }

    /**
     * 查询默认车辆状态
     * @return 包含车辆信息的 Vehicle 对象，或错误
     */
    suspend fun fetchDefaultVehicleStatus(): Result<Vehicle> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }

        val result = api.queryDefaultCarStatus()
        result.map { response ->
            val apiStatus = response.data?.carStatus ?: return@map createFallbackVehicle()
            val apiInfo = response.data.carInfo

            val checkStatus = api.checkCarStatus(apiInfo?.vin ?: "").getOrNull()?.data
            val status = apiStatus.toVehicleStatus(
                checkEnginePow = checkStatus?.enginePow,
                checkEngineTemp = checkStatus?.engineTemp,
                checkAbsio = checkStatus?.absio,
                checkPwrStrIo = checkStatus?.pwrStrIo
            )

            buildVehicleFromResponse(apiStatus, apiInfo, status)
        }
    }

    /**
     * 查询默认车辆状态（仅主状态 API，不含诊断/胎压/昨日里程）
     * 用于高频刷新场景（如详情页 5 秒轮询），只请求 1 个 API
     * @return 仅包含主状态数据的 Vehicle，需在调用方合并已有辅助数据
     */
    suspend fun fetchDefaultVehicleStatusQuick(): Result<Vehicle> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }

        api.queryDefaultCarStatus().map { response ->
            val apiStatus = response.data?.carStatus ?: return@map createFallbackVehicle()
            val apiInfo = response.data.carInfo

            val status = apiStatus.toVehicleStatus()
            buildVehicleFromResponse(apiStatus, apiInfo, status)
        }
    }

    /**
     * 查询昨日里程（独立接口）
     * @return 昨日里程数值，失败返回 null
     */
    suspend fun fetchYesterdayMileage(vin: String): Int? = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext null
        }

        api.fetchYesterdayMileage(vin).getOrNull()?.let { response ->
            val mileage = response.data?.trip
            if (mileage != null && mileage > 0) mileage else null
        }
    }

    /**
     * 查询胎压数据
     * @return 更新后的 VehicleStatus
     */
    suspend fun fetchTirePressure(vin: String, currentStatus: VehicleStatus): Result<VehicleStatus> =
        withContext(Dispatchers.IO) {
            if (!APIConfig.isConfigured) {
                return@withContext Result.failure(APIError("请先配置 Access Token"))
            }

            api.queryTirePressure(vin).map { response ->
                val tireData = response.data ?: return@map currentStatus

                val flPressure = tireData.lfTirPrsVal?.toDoubleOrNull()
                    ?: tireData.tirePressureFl?.toDoubleOrNull()
                val frPressure = tireData.rfTirPrVal?.toDoubleOrNull()
                    ?: tireData.tirePressureFr?.toDoubleOrNull()
                val lrPressure = tireData.lrTirPrVal?.toDoubleOrNull()
                    ?: tireData.tirePressureRl?.toDoubleOrNull()
                val rrPressure = tireData.rrTirPrVal?.toDoubleOrNull()
                    ?: tireData.tirePressureRr?.toDoubleOrNull()
                val tireTemp = tireData.tirTemp?.toIntOrNull()

                val hasTireData = flPressure != null || frPressure != null || lrPressure != null || rrPressure != null

                if (hasTireData) {
                    val flBar = if ((flPressure ?: 0.0) > 100) flPressure?.div(100) else flPressure
                    val frBar = if ((frPressure ?: 0.0) > 100) frPressure?.div(100) else frPressure
                    val lrBar = if ((lrPressure ?: 0.0) > 100) lrPressure?.div(100) else lrPressure
                    val rrBar = if ((rrPressure ?: 0.0) > 100) rrPressure?.div(100) else rrPressure

                    currentStatus.copy(
                        tirePressureFL = flBar ?: currentStatus.tirePressureFL,
                        tirePressureFR = frBar ?: currentStatus.tirePressureFR,
                        tirePressureRL = lrBar ?: currentStatus.tirePressureRL,
                        tirePressureRR = rrBar ?: currentStatus.tirePressureRR,
                        tireTemperature = tireTemp ?: currentStatus.tireTemperature
                    )
                } else {
                    currentStatus
                }
            }
        }

    /**
     * 发送远程控制指令
     */
    suspend fun sendCommand(command: String, params: Map<String, Any> = emptyMap()): Result<CommandResponse> =
        withContext(Dispatchers.IO) {
            api.sendCommand(command, params)
        }

    /**
     * 检查车辆状态
     */
    suspend fun checkCarStatus(vin: String): Result<CheckStatusResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }
        api.checkCarStatus(vin)
    }

    /**
     * 授权启动
     */
    suspend fun authorizeIgnition(vin: String): Result<AuthorizeResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }
        api.authorizeIgnition(vin)
    }

    /**
     * 寻车
     */
    suspend fun searchCar(vin: String): Result<SearchCarResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }
        api.searchCar(vin)
    }

    /**
     * 控制车窗
     */
    suspend fun controlWindow(vin: String, status: Int): Result<WindowControlResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }
        api.controlWindow(vin, status)
    }

    /**
     * 手机号 + 密码登录获取 Token（菱菱邦 hapi OAuth）
     * 登录本身不依赖已配置的 accessToken
     */
    suspend fun loginLlb(mobile: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        api.loginLlb(mobile, password)
    }

    /**
     * 控制门锁
     */
    suspend fun controlDoorLock(vin: String, status: Int): Result<CommandResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }
        api.controlDoorLock(vin, status)
    }

    /**
     * 控制空调
     */
    suspend fun controlAC(params: Map<String, Any>): Result<CommandResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }
        api.controlAC(params)
    }

    suspend fun queryBleKey(vin: String, userId: String): Result<BleKeyResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }
        api.queryBleKey(vin, userId)
    }

    private fun createFallbackVehicle(): Vehicle {
        return Vehicle(
            id = "",
            vin = "",
            name = "未知车辆",
            licensePlate = "",
            model = "",
            status = VehicleStatus(),
            location = null
        )
    }
}
