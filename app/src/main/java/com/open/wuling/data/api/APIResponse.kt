package com.open.wuling.data.api

import com.google.gson.annotations.SerializedName

// ============== LLB OAuth Login Response（手机号+密码登录，hapi 网关） ==============
data class LlbLoginResponse(
    @SerializedName("result") val result: Boolean = false,
    @SerializedName("errorCode") val errorCode: String? = null,
    @SerializedName("errorMessage") val errorMessage: String? = null,
    @SerializedName("data") val data: LlbLoginData? = null
)

data class LlbLoginData(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("expires_in") val expiresIn: Long = 0,
    @SerializedName("state") val state: String? = null
)

// ============== Generic API Response ==============
data class APIResponse<T>(
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("result") val result: Boolean? = null,
    @SerializedName("errorCode") val errorCode: String? = null,
    @SerializedName("errorMessage") val errorMessage: String? = null,
    @SerializedName("data") val data: T? = null,
    @SerializedName("message") val message: String? = null
) {
    val isSuccess: Boolean
        get() = success == true || result == true || errorCode == "0" || errorCode == null
}

// ============== Car Status Response ==============
data class CarStatusResponse(
    @SerializedName("data") val data: CarStatusData? = null,
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("result") val result: Boolean? = null,
    @SerializedName("errorCode") val errorCode: String? = null,
    @SerializedName("errorMessage") val errorMessage: String? = null,
    @SerializedName("message") val message: String? = null
) {
    val isSuccess: Boolean
        get() = success == true || result == true || errorCode == "0" || errorCode == null
}

data class CarStatusData(
    @SerializedName("carStatus") val carStatus: CarStatusApi? = null,
    @SerializedName("carInfo") val carInfo: CarInfoApi? = null
)

data class CarStatusApi(
    // 基础状态
    @SerializedName("status") val status: String? = null,
    @SerializedName("statusToast") val statusToast: String? = null,
    @SerializedName("statusName") val statusName: String? = null,

    // 灯光
    @SerializedName("frontFogLight") val frontFogLight: String? = null,
    @SerializedName("leftTurnLight") val leftTurnLight: String? = null,
    @SerializedName("positionLight") val positionLight: String? = null,
    @SerializedName("rightTurnLight") val rightTurnLight: String? = null,
    @SerializedName("dipHeadLight") val dipHeadLight: String? = null,
    @SerializedName("lowBeamLight") val lowBeamLight: String? = null,

    // 燃油/电量
    @SerializedName("leftFuel") val leftFuel: String? = null,
    @SerializedName("keyStatus") val keyStatus: String? = null,
    @SerializedName("autoGearStatus") val autoGearStatus: String? = null,

    // 车窗（状态 + 开度）
    @SerializedName("window4Status") val window4Status: Int? = null,
    @SerializedName("window3Status") val window3Status: Int? = null,
    @SerializedName("window2Status") val window2Status: Int? = null,
    @SerializedName("window1Status") val window1Status: Int? = null,
    @SerializedName("windowStatus") val windowStatus: Int? = null,
    @SerializedName("window1OpenDegree") val window1OpenDegree: Int? = null,
    @SerializedName("window2OpenDegree") val window2OpenDegree: Int? = null,
    @SerializedName("window3OpenDegree") val window3OpenDegree: Int? = null,
    @SerializedName("window4OpenDegree") val window4OpenDegree: Int? = null,

    // 车门（打开状态 + 锁定状态）
    @SerializedName("door4OpenStatus") val door4OpenStatus: Int? = null,
    @SerializedName("door3OpenStatus") val door3OpenStatus: Int? = null,
    @SerializedName("door3LockStatus") val door3LockStatus: Int? = null,
    @SerializedName("door4LockStatus") val door4LockStatus: Int? = null,
    @SerializedName("door2OpenStatus") val door2OpenStatus: Int? = null,
    @SerializedName("door1OpenStatus") val door1OpenStatus: Int? = null,
    @SerializedName("doorLockStatus") val doorLockStatus: Int? = null,
    @SerializedName("doorOpenStatus") val doorOpenStatus: Int? = null,
    @SerializedName("door2LockStatus") val door2LockStatus: Int? = null,
    @SerializedName("door1LockStatus") val door1LockStatus: Int? = null,
    @SerializedName("tailDoorOpenStatus") val tailDoorOpenStatus: Int? = null,
    @SerializedName("tailDoorLockStatus") val tailDoorLockStatus: Int? = null,

    // 电池
    @SerializedName("lowBatVol") val lowBatVol: Double? = null,
    @SerializedName("batterySoc") val batterySoc: Int? = null,
    @SerializedName("batAvgTemp") val batAvgTemp: Int? = null,
    @SerializedName("batMinTemp") val batMinTemp: Int? = null,
    @SerializedName("batMaxTemp") val batMaxTemp: Int? = null,
    @SerializedName("batSOH") val batSOH: Int? = null,
    @SerializedName("batHealth") val batHealth: Int? = null,
    @SerializedName("batteryStatus") val batteryStatus: String? = null,
    @SerializedName("batteryIndicate") val batteryIndicate: String? = null,

    // 温度
    @SerializedName("tmActTemp") val tmActTemp: Int? = null,
    @SerializedName("obcTemp") val obcTemp: String? = null,
    @SerializedName("invActTemp") val invActTemp: Int? = null,
    @SerializedName("interiorTemperature") val interiorTemperature: Int? = null,
    @SerializedName("accCntTemp") val accCntTemp: Double? = null,
    @SerializedName("cdjTemp") val cdjTemp: String? = null,

    // 充电
    @SerializedName("vecChrgingSts") val vecChrgingSts: Int? = null,
    @SerializedName("vecChargeSts") val vecChargeSts: Int? = null,
    @SerializedName("vecChrgStsIndOn") val vecChrgStsIndOn: Int? = null,
    @SerializedName("obcOtpCur") val obcOtpCur: Double? = null,
    @SerializedName("leftChargeTime") val leftChargeTime: Int? = null,
    @SerializedName("charging") val charging: String? = null,
    @SerializedName("voltage") val voltage: Double? = null,
    @SerializedName("current") val current: Double? = null,
    @SerializedName("chargePower") val chargePower: String? = null,
    @SerializedName("wireConnect") val wireConnect: String? = null,
    @SerializedName("rechargeStatus") val rechargeStatus: String? = null,
    @SerializedName("leftBatteryPower") val leftBatteryPower: Double? = null,

    // 里程与续航
    @SerializedName("leftMileage") val leftMileage: Int? = null,
    @SerializedName("mileage") val mileage: Int? = null,
    @SerializedName("yesterMileage") val yesterMileage: Int? = null,
    @SerializedName("oilLeftMileage") val oilLeftMileage: Int? = null,
    @SerializedName("hybridMileage") val hybridMileage: String? = null,
    @SerializedName("avgFuel") val avgFuel: Double? = null,

    // 定位
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,

    // 驾驶状态
    @SerializedName("strWhAng") val strWhAng: String? = null,
    @SerializedName("brakPedalPos") val brakPedalPos: String? = null,
    @SerializedName("accActPos") val accActPos: String? = null,
    @SerializedName("vehSpdAvgDrvn") val vehSpdAvgDrvn: String? = null,

    // 空调
    @SerializedName("acStatus") val acStatus: Int? = null,

    // 安全
    @SerializedName("sentinelModeStatus") val sentinelModeStatus: String? = null,
    @SerializedName("limitFeedback") val limitFeedback: String? = null,

    // 座椅
    @SerializedName("seat1HotStatus") val seat1HotStatus: String? = null,
    @SerializedName("seat2HotStatus") val seat2HotStatus: String? = null,
    @SerializedName("seat3HotStatus") val seat3HotStatus: String? = null,
    @SerializedName("seat4HotStatus") val seat4HotStatus: String? = null,
    @SerializedName("seat1WindStatus") val seat1WindStatus: String? = null,
    @SerializedName("seat2WindStatus") val seat2WindStatus: String? = null,
    @SerializedName("seat3WindStatus") val seat3WindStatus: String? = null,
    @SerializedName("seat4WindStatus") val seat4WindStatus: String? = null,

    // 其他
    @SerializedName("intelligentCarSwitch") val intelligentCarSwitch: Int? = null,
    @SerializedName("leftSlidingDoorStatus") val leftSlidingDoorStatus: String? = null,
    @SerializedName("rightSlidingDoorStatus") val rightSlidingDoorStatus: String? = null,
    @SerializedName("collectTime") val collectTime: String? = null,

    // 引擎类型（来自 carInfo，但解析时用于判断纯电/混动）
    @SerializedName("engineType") val engineType: Int? = null,

    // 胎压（来自 carStatus 的胎压字段）
    @SerializedName("tirePressureFl") val tirePressureFl: String? = null,
    @SerializedName("tirePressureFr") val tirePressureFr: String? = null,
    @SerializedName("tirePressureRl") val tirePressureRl: String? = null,
    @SerializedName("tirePressureRr") val tirePressureRr: String? = null,

    // 档位
    @SerializedName("gearStatus") val gearStatus: String? = null
)

data class CarInfoApi(
    @SerializedName("carInfoId") val carInfoId: Long? = null,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("vin") val vin: String? = null,
    @SerializedName("carName") val carName: String? = null,
    @SerializedName("colorCode") val colorCode: String? = null,
    @SerializedName("vsn") val vsn: String? = null,
    @SerializedName("carPlate") val carPlate: String? = null,
    @SerializedName("relation") val relation: Int? = null,
    @SerializedName("carPosition") val carPosition: Int? = null,
    @SerializedName("bindCarUserMobile") val bindCarUserMobile: String? = null,
    @SerializedName("hasMoreCar") val hasMoreCar: Int? = null,
    @SerializedName("finishBind") val finishBind: Boolean? = null,
    @SerializedName("carOwnerUserId") val carOwnerUserId: String? = null,
    @SerializedName("shakeLock") val shakeLock: Int? = null,
    @SerializedName("bluetoothKeyConnectMark") val bluetoothKeyConnectMark: String? = null,
    @SerializedName("userInfoAuthStatus") val userInfoAuthStatus: String? = null,
    @SerializedName("userInfoAuthExpireTime") val userInfoAuthExpireTime: Long? = null,
    @SerializedName("locationAuthStatus") val locationAuthStatus: String? = null,
    @SerializedName("locationAuthExpireTime") val locationAuthExpireTime: Long? = null,
    @SerializedName("providerCode") val providerCode: String? = null,
    @SerializedName("carTypeName") val carTypeName: String? = null,
    @SerializedName("model") val model: String? = null,
    @SerializedName("level") val level: String? = null,
    @SerializedName("engineType") val engineType: Int? = null,
    @SerializedName("image") val image: String? = null,
    @SerializedName("controlView") val controlView: Int? = null,
    @SerializedName("bleType") val bleType: Int? = null,
    @SerializedName("folderUrl") val folderUrl: String? = null,
    @SerializedName("physicsEngine") val physicsEngine: Int? = null,
    @SerializedName("supportMqtt") val supportMqtt: Int? = null,
    @SerializedName("supportCarConditionPoll") val supportCarConditionPoll: Int? = null,
    @SerializedName("conditionPollTime") val conditionPollTime: Int? = null,
    @SerializedName("isAuthIdentity") val isAuthIdentity: Int? = null,
    @SerializedName("imageNameRule") val imageNameRule: List<String>? = null,
    @SerializedName("telematicsPlatform") val telematicsPlatform: Int? = null,
    @SerializedName("showWidgets") val showWidgets: Boolean? = null,
    @SerializedName("telematicsCarStatus") val telematicsCarStatus: Int? = null,
    @SerializedName("carYear") val carYear: String? = null,
    @SerializedName("onlyLocalInfo") val onlyLocalInfo: Boolean? = null,
    @SerializedName("colorName") val colorName: String? = null,
    @SerializedName("purchaseDate") val purchaseDate: Long? = null,
    @SerializedName("purchaseUserName") val purchaseUserName: String? = null,
    @SerializedName("purchaseShopNum") val purchaseShopNum: String? = null,
    @SerializedName("supportBatteryIndicate") val supportBatteryIndicate: Int? = null,
    @SerializedName("supportChargeRemain") val supportChargeRemain: Int? = null,
    @SerializedName("supportChargePower") val supportChargePower: Int? = null,
    @SerializedName("supportAvgFuel") val supportAvgFuel: Int? = null,
    @SerializedName("supportHybridMileage") val supportHybridMileage: Int? = null,
    @SerializedName("seriesCode") val seriesCode: String? = null,
    @SerializedName("supportAutoAir") val supportAutoAir: Int? = null,
    @SerializedName("supportAvgElectronFuel") val supportAvgElectronFuel: Int? = null,
    @SerializedName("powerType") val powerType: String? = null,
    @SerializedName("carOwnerDay") val carOwnerDay: Int? = null,
    @SerializedName("supportNewCarUi") val supportNewCarUi: Int? = null,
    @SerializedName("supportUserInfoAuth") val supportUserInfoAuth: Int? = null,
    @SerializedName("supportLocationAuth") val supportLocationAuth: Int? = null,
    @SerializedName("junPlusSupportCar") val junPlusSupportCar: Int? = null,
    @SerializedName("supportBDCAutoAir") val supportBDCAutoAir: Int? = null,
    // 兼容旧字段
    @SerializedName("carId") val carId: String? = null,
    @SerializedName("carBrandName") val carBrandName: String? = null,
    @SerializedName("carSeriesName") val carSeriesName: String? = null,
    @SerializedName("carModelName") val carModelName: String? = null
)

// ============== Tire Pressure Response ==============
data class TirePressureResponse(
    @SerializedName("data") val data: TirePressureData? = null
)

data class TirePressureData(
    // 主状态 API 返回的胎压字段（备用）
    @SerializedName("tirePressureFl") val tirePressureFl: String? = null,
    @SerializedName("tirePressureFr") val tirePressureFr: String? = null,
    @SerializedName("tirePressureRl") val tirePressureRl: String? = null,
    @SerializedName("tirePressureRr") val tirePressureRr: String? = null,
    // 单独胎压 API 返回的字段（优先）
    @SerializedName("lfTirPrsVal") val lfTirPrsVal: String? = null,
    @SerializedName("rfTirPrVal") val rfTirPrVal: String? = null,
    @SerializedName("lrTirPrVal") val lrTirPrVal: String? = null,
    @SerializedName("rrTirPrVal") val rrTirPrVal: String? = null,
    @SerializedName("tirTemp") val tirTemp: String? = null  // 轮胎温度
)

// ============== Command Response ==============
data class CommandResponse(
    @SerializedName("result") val result: Boolean? = null,
    @SerializedName("message") val message: String? = null
)

// ============== Check Status Response ==============
data class CheckStatusResponse(
    @SerializedName("data") val data: CheckStatusData? = null,
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("result") val result: Boolean? = null,
    @SerializedName("errorCode") val errorCode: String? = null,
    @SerializedName("errorMessage") val errorMessage: String? = null
) {
    val isSuccess: Boolean
        get() = success == true || result == true || errorCode == "0" || errorCode == null
}

data class CheckStatusData(
    @SerializedName("enginePow") val enginePow: Int? = null,
    @SerializedName("engineTemp") val engineTemp: Int? = null,
    @SerializedName("absio") val absio: Int? = null,
    @SerializedName("pwrStrIo") val pwrStrIo: Int? = null
)

// ============== Authorize Response ==============
data class AuthorizeResponse(
    @SerializedName("data") val data: AuthorizeData? = null,
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("result") val result: Boolean? = null,
    @SerializedName("errorCode") val errorCode: String? = null,
    @SerializedName("errorMessage") val errorMessage: String? = null
) {
    val isSuccess: Boolean
        get() = success == true || result == true || errorCode == "0" || errorCode == null
}

data class AuthorizeData(
    @SerializedName("authorizeResult") val authorizeResult: Boolean? = null,
    @SerializedName("authorizeMessage") val authorizeMessage: String? = null
)

// ============== Search Car Response ==============
data class SearchCarResponse(
    @SerializedName("data") val data: SearchCarData? = null,
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("result") val result: Boolean? = null,
    @SerializedName("errorCode") val errorCode: String? = null,
    @SerializedName("errorMessage") val errorMessage: String? = null
) {
    val isSuccess: Boolean
        get() = success == true || result == true || errorCode == "0" || errorCode == null
}

data class SearchCarData(
    @SerializedName("searchResult") val searchResult: Boolean? = null,
    @SerializedName("searchMessage") val searchMessage: String? = null
)

// ============== Yesterday Mileage Response ==============
data class YesterdayMileageData(
    @SerializedName("trip") val trip: Int? = null
)

// ============== Window Control Response ==============
data class WindowControlResponse(
    @SerializedName("data") val data: WindowControlData? = null,
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("result") val result: Boolean? = null,
    @SerializedName("errorCode") val errorCode: String? = null,
    @SerializedName("errorMessage") val errorMessage: String? = null
) {
    val isSuccess: Boolean
        get() = success == true || result == true || errorCode == "0" || errorCode == null
}

data class WindowControlData(
    @SerializedName("controlResult") val controlResult: Boolean? = null,
    @SerializedName("controlMessage") val controlMessage: String? = null
)

// ============== BLE Key Response ==============
data class BleKeyResponse(
    @SerializedName("data") val data: BleKeyData? = null,
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("result") val result: Boolean? = null,
    @SerializedName("errorCode") val errorCode: String? = null,
    @SerializedName("errorMessage") val errorMessage: String? = null
) {
    val isSuccess: Boolean
        get() = success == true || result == true || errorCode == "0" || errorCode == null
}

data class BleKeyData(
    @SerializedName("bleMac") val bleMac: String? = null,
    @SerializedName("macAddress") val macAddress: String? = null,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("collectTime") val collectTime: String? = null,
    @SerializedName("keyId") val keyId: String? = null,
    @SerializedName("keyType") val keyType: String? = null,
    @SerializedName("keyMasterRandom") val keyMasterRandom: String? = null,
    @SerializedName("endTime") val endTime: String? = null,
    @SerializedName("masterKey") val masterKey: String? = null,
    @SerializedName("vin") val vin: String? = null
)

// ============== 循环预约充电 (v69) ==============
// 字段来源：官方 App（com.cloudy.linglingbang）dex 中的 ReserveChargeStatusInfoBean，
//          并已用线上只读查询 /car/cycle/charge/query 闭环验证，字段完全一致。
// 全部为 String：服务端下发的是字符串型（如 startHour="22"），无预约时为空串 ""。
//
// 注意：chargeLimit / chargeModel / chargeRequest / type 的枚举语义官方代码里未明确
//      （官方日志里仅见 reserveChargePowerUpLimit 等字样，dex 结构损坏无法继续下挖），
//      因此本 App 提交预约时**原样回传**服务端当前值，绝不臆造枚举。
data class ReserveChargeInfo(
    @SerializedName("startHour") val startHour: String? = null,
    @SerializedName("startMinute") val startMinute: String? = null,
    @SerializedName("endHour") val endHour: String? = null,
    @SerializedName("endMinute") val endMinute: String? = null,
    @SerializedName("chargeLimit") val chargeLimit: String? = null,
    @SerializedName("chargeModel") val chargeModel: String? = null,
    @SerializedName("chargeRequest") val chargeRequest: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("collectTime") val collectTime: String? = null
) {
    /** 是否已设置有效预约（起止时间齐全才算） */
    val hasReservation: Boolean
        get() = !startHour.isNullOrBlank() && !startMinute.isNullOrBlank() &&
                !endHour.isNullOrBlank() && !endMinute.isNullOrBlank()

    fun startTimeText(): String = hhmm(startHour, startMinute)
    fun endTimeText(): String = hhmm(endHour, endMinute)

    private fun hhmm(h: String?, m: String?): String {
        val hh = h?.trim()?.takeIf { it.isNotEmpty() } ?: return "--:--"
        val mm = m?.trim()?.takeIf { it.isNotEmpty() } ?: "00"
        return "${hh.padStart(2, '0')}:${mm.padStart(2, '0')}"
    }
}

// ============== API Error ==============
// open：允许子类携带语义（如会话过期），调用方按类型而非字符串判断
open class APIError(message: String) : Exception(message)

/** v70：服务端 500009 —— token 已失效（被其它端登录顶掉/过期）。触发自动重登。 */
class SessionExpiredError(message: String) : APIError(message)
