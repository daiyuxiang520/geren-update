package com.open.wuling

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.open.wuling.BuildConfig
import com.open.wuling.analytics.UmengAnalytics
import com.open.wuling.ble.BleAutoLockManager
import com.open.wuling.data.update.AppUpdateInfo
import com.open.wuling.data.update.UpdateChecker
import com.open.wuling.data.update.UpdateConfig
import com.open.wuling.data.api.APIConfig
import com.open.wuling.data.api.CommandResponse
import com.open.wuling.data.local.BleAutoLockPreferences
import com.open.wuling.data.api.BleKeyResponse
import com.open.wuling.data.model.ControlCommand
import com.open.wuling.data.model.User
import com.open.wuling.data.model.Vehicle
import com.open.wuling.data.repository.VehicleRepository
import com.open.wuling.data.store.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppState @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val tokenStore: TokenStore,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    private val TAG = "AppState"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 车辆状态本地缓存（冷启动秒开用） */
    private val gson = com.google.gson.Gson()
    private val CACHE_PREFS = "vehicle_cache"
    private val CACHE_KEY_VEHICLE = "last_vehicle"
    private val CACHE_KEY_MIRROR = "last_good_mirror"
    private val CACHE_KEY_BEST_DL = "best_download_mirror"

    private val _user = MutableStateFlow(User(id = "", name = "用户", phone = ""))
    val user: StateFlow<User> = _user.asStateFlow()

    // Token 配置状态（Compose 响应式）
    // 注意：APIConfig.isConfigured 是普通属性，UI 直接读取不会触发重组，
    // ProfileScreen 必须订阅此 StateFlow 才能在保存 Token 后实时刷新「已配置/未配置」
    private val _tokenConfigured = MutableStateFlow(APIConfig.isConfigured)
    val tokenConfigured: StateFlow<Boolean> = _tokenConfigured.asStateFlow()
    
    // 自动刷新相关
    private var autoRefreshJob: kotlinx.coroutines.Job? = null
    private val refreshInterval = 30000L // 30秒自动刷新一次

    private val _selectedVehicle = MutableStateFlow<Vehicle?>(null)
    val selectedVehicle: StateFlow<Vehicle?> = _selectedVehicle.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ============== App 自动更新（免费托管方案） ==============
    private val _pendingUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val pendingUpdate: StateFlow<AppUpdateInfo?> = _pendingUpdate.asStateFlow()

    // 下载进度：null=隐藏；0..100 表示下载中
    private val _updateProgress = MutableStateFlow<Int?>(null)
    val updateProgress: StateFlow<Int?> = _updateProgress.asStateFlow()

    private val _updateError = MutableStateFlow<String?>(null)
    val updateError: StateFlow<String?> = _updateError.asStateFlow()

    /** 用户手动选中的下载源前缀（null=自动）。仅本次更新流程生效，不持久化。 */
    private val _selectedMirror = MutableStateFlow<String?>(null)
    val selectedMirror: StateFlow<String?> = _selectedMirror.asStateFlow()

    /** 各加速源实时延迟（url -> ms，null=不可达/未测），供更新弹窗展示 */
    private val _mirrorSpeeds = MutableStateFlow<Map<String, Long?>>(emptyMap())
    val mirrorSpeeds: StateFlow<Map<String, Long?>> = _mirrorSpeeds.asStateFlow()

    private val _commandResult = MutableStateFlow<CommandResult?>(null)
    val commandResult: StateFlow<CommandResult?> = _commandResult.asStateFlow()

    // BLE 无感控车相关
    @Volatile
    var bleAutoLockManager: BleAutoLockManager? = null
        private set
    private lateinit var bleAutoLockPreferences: BleAutoLockPreferences
    private lateinit var appContext: Context

    val bleConnectionState: StateFlow<BleAutoLockManager.ConnectionState>
        get() = bleAutoLockManager?.connectionState ?: MutableStateFlow(BleAutoLockManager.ConnectionState.Disconnected).asStateFlow()

    val bleLogs: StateFlow<List<String>>
        get() = bleAutoLockManager?.logs ?: MutableStateFlow(emptyList<String>()).asStateFlow()

    val scannedDevices: StateFlow<List<BleAutoLockManager.ScannedDevice>>
        get() = bleAutoLockManager?.scannedDevices ?: MutableStateFlow(emptyList<BleAutoLockManager.ScannedDevice>()).asStateFlow()

    val isScanningAll: StateFlow<Boolean>
        get() = bleAutoLockManager?.isScanningAll ?: MutableStateFlow(false).asStateFlow()

    val bleFilteredRssi: StateFlow<Int?>
        get() = bleAutoLockManager?.filteredRssi ?: MutableStateFlow<Int?>(null).asStateFlow()

    /** 从服务器获取 BLE 钥匙并存入本地配置 */
    suspend fun fetchAndStoreBleKey() {
        val vehicle = _selectedVehicle.value ?: return
        val vin = vehicle.vin
        val userId = vehicle.carInfo?.bindCarUserMobile ?: vehicle.carInfo?.userId.orEmpty()
        if (vin.isEmpty()) return
        val result = vehicleRepository.queryBleKey(vin, userId)
        result.onSuccess { response ->
            val data = response.data ?: return@onSuccess
            val manager = bleAutoLockManager ?: return@onSuccess
            manager.addLog("获取到蓝牙钥匙: ${data.bleMac}")
            bleAutoLockPreferences.setBleKeyData(
                bleMac = data.bleMac ?: "",
                userId = data.userId ?: "",
                collectTime = data.collectTime ?: "",
                // keyId是十进制数字,转十六进制: 837503→000CC77F
                keyId = (data.keyId ?: "").toLongOrNull()?.let { d ->
                    java.lang.Long.toString(d, 16).padStart(8, '0').uppercase()
                } ?: (data.keyId ?: ""),
                keyType = data.keyType ?: "",
                keyMasterRandom = data.keyMasterRandom ?: "",
                endTime = data.endTime ?: "",
                masterKey = data.masterKey ?: "",
                vin = data.vin ?: ""
            )
        }
    }

    fun clearBleLogs() {
        bleAutoLockManager?.clearLogs()
    }

    fun startScanAllDevices() {
        bleAutoLockManager?.startScanAllDevices()
    }

    fun stopScanAllDevices() {
        bleAutoLockManager?.stopScanAllDevices()
    }

    fun clearScannedDevices() {
        bleAutoLockManager?.clearScannedDevices()
    }

    /**
     * 初始化：从持久化存储恢复 Token 和 BLE 配置
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        bleAutoLockPreferences = BleAutoLockPreferences(appContext)
        bleAutoLockManager = BleAutoLockManager(
            context = appContext,
            preferences = bleAutoLockPreferences,
            scope = scope
        ).apply {
            onCheckVehicleLocked = {
                _selectedVehicle.value?.status?.isLocked ?: true
            }
            onShowToast = { message ->
                _commandResult.value = CommandResult(success = true, message = message)
                scope.launch {
                    kotlinx.coroutines.delay(2000)
                    _commandResult.value = null
                }
            }
        }

        scope.launch {
            // ① 先用上次缓存立刻显示（冷启动秒出数据，不等网络）
            loadVehicleCache()?.let { cached ->
                if (_selectedVehicle.value == null) {
                    _selectedVehicle.value = cached
                    Log.d(TAG, "冷启动已加载本地车辆缓存：${cached.vin}")
                }
            }

            // ② 读取 Token
            val savedToken = tokenStore.getToken()
            if (savedToken.isNotEmpty()) {
                configure(savedToken)
                // ③ 静默刷新最新数据（有缓存时不再显示全屏 loading，避免遮挡已有内容）
                val hasCache = _selectedVehicle.value != null
                var loaded = false
                repeat(3) { attempt ->
                    if (!loaded) {
                        val result = refreshVehicleStatusAwait(showLoading = !hasCache)
                        loaded = result
                        if (!loaded && attempt < 2) {
                            Log.w(TAG, "冷启动首次刷新失败，${3 + attempt * 3} 秒后重试")
                            kotlinx.coroutines.delay(3000L + attempt * 3000L)
                        }
                    }
                }
                // 启动自动刷新
                startAutoRefresh()
                // 自动启动 BLE（如果已启用）
                kotlinx.coroutines.delay(1000)
                val isBleEnabled = bleAutoLockPreferences.enabled.first()
                val hasMac = bleAutoLockPreferences.bleMac.first().isNotEmpty()
                if (isBleEnabled && hasMac) {
                    bleAutoLockManager?.initialize()
                }
            }
        }

        // App 冷启动自动检查更新（与登录态无关，网络未就绪时静默失败）
        scope.launch { checkAppUpdate() }

        // 埋点：冷启动（按版本分布活跃用户；未 init 时暂存，init 成功后由 flushPending 补发）
        UmengAnalytics.eventPending(appContext, "app_launch", mapOf("version" to BuildConfig.VERSION_NAME))

        // 恢复上次成功的加速源 + 注册回写回调（减少无谓的重试开销）
        run {
            val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
            UpdateChecker.lastGoodMirror = prefs.getString(CACHE_KEY_MIRROR, null)
            UpdateChecker.onMirrorSucceeded = { prefix ->
                prefs.edit().putString(CACHE_KEY_MIRROR, prefix).apply()
            }
            // 下载链路的最快源记忆（测速选出的结果，与 json 检查链路分开）
            UpdateChecker.bestDownloadMirror = prefs.getString(CACHE_KEY_BEST_DL, null)
            UpdateChecker.onBestDownloadMirror = { prefix ->
                prefs.edit().putString(CACHE_KEY_BEST_DL, prefix).apply()
            }
        }
    }

    /**
     * 冷启动立即显示上次的车辆数据。
     *
     * 车辆状态原本只存在内存（_selectedVehicle），App 一退出即丢失，
     * 导致每次冷启动都要等网络请求回来才有内容（期间显示「暂无车辆信息」）。
     * 这里把每次刷新成功的结果用 Gson 序列化落盘，启动时先读缓存即刻渲染，
     * 再静默联网刷新覆盖，做到「一进 App 就有数据」。
     */
    private fun loadVehicleCache(): Vehicle? = try {
        val raw = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
            .getString(CACHE_KEY_VEHICLE, null)
        if (raw.isNullOrBlank()) null
        else gson.fromJson(raw, Vehicle::class.java)
    } catch (e: Exception) {
        Log.w(TAG, "loadVehicleCache failed: ${e.message}")
        null
    }

    /** 刷新成功后落盘缓存，供下次冷启动使用 */
    private fun saveVehicleCache(vehicle: Vehicle) {
        try {
            context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(CACHE_KEY_VEHICLE, gson.toJson(vehicle))
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "saveVehicleCache failed: ${e.message}")
        }
    }

    fun toggleBleConnection() {
        val manager = bleAutoLockManager ?: return
        val currentState = manager.connectionState.value
        Log.d(TAG, "toggleBleConnection() called, currentState: $currentState")
        scope.launch {
            if (currentState is BleAutoLockManager.ConnectionState.Connected) {
                Log.d(TAG, "Disconnecting BLE")
                manager.addLog("断开蓝牙连接")
                bleAutoLockPreferences.setEnabled(false)
                manager.destroy()
            } else {
                Log.d(TAG, "Connecting BLE")
                manager.addLog("开始连接蓝牙")
                // 先获取蓝牙 MAC 地址
                val currentMac = bleAutoLockPreferences.bleMac.first()
                Log.d(TAG, "Current MAC: $currentMac")
                val currentMasterKey = bleAutoLockPreferences.bleMasterKey.first()
                manager.addLog("当前保存的 MAC: ${if (currentMac.isEmpty()) "空" else currentMac}")
                manager.addLog("当前保存的 MasterKey: ${if (currentMasterKey.isEmpty()) "空" else currentMasterKey.take(8)}...")
                
                if (currentMac.isEmpty() || currentMasterKey.isEmpty()) {
                    Log.d(TAG, "MAC or MasterKey is empty, fetching from API")
                    manager.addLog("MAC 或密钥为空，从 API 获取")
                    
                    // 获取当前车辆信息
                    val currentVehicle = _selectedVehicle.value
                    val vin = currentVehicle?.vin ?: ""
                    val phone = currentVehicle?.carInfo?.bindCarUserMobile ?: ""
                    
                    manager.addLog("车辆 VIN: $vin")
                    manager.addLog("手机号: $phone")
                    
                    if (vin.isEmpty() || phone.isEmpty()) {
                        manager.addLog("错误: 车辆信息不完整")
                        _commandResult.value = CommandResult(success = false, message = "车辆信息不完整，请刷新车辆状态")
                        kotlinx.coroutines.delay(2000)
                        _commandResult.value = null
                        return@launch
                    }
                    
                    Log.d(TAG, "Requesting BLE key with vin: $vin, phone: $phone")
                    manager.addLog("请求 BLE 钥匙...")
                    manager.addLog("请求 URL: ${APIConfig.baseURL}/car/control/ble/key/query")
                    manager.addLog("请求体: {\"vin\":\"$vin\",\"userId\":\"$phone\"}")
                    
                    val result = vehicleRepository.queryBleKey(vin, phone)
                    if (result.isSuccess) {
                        val response = result.getOrNull()
                        Log.d(TAG, "API response: $response")
                        manager.addLog("API 响应成功")
                        if (response?.isSuccess == true && response.data?.bleMac != null) {
                            val data = response.data
                            Log.d(TAG, "Setting BLE key data: $data")
                            manager.addLog("获取到蓝牙 MAC: ${data.bleMac}")
                            manager.addLog("获取到 userId: ${data.userId}")
                            manager.addLog("获取到 keyId: ${data.keyId}")
                            manager.addLog("获取到 keyType: ${data.keyType}")
                            
                            // v2.0.0 逻辑: keyId 是十进制数字 → 转十六进制 → 补零到8位
                            var processedKeyId = (data.keyId ?: "").trim()
                            manager.addLog("原始 keyId: $processedKeyId")
                            processedKeyId = processedKeyId.toLongOrNull()?.let { dec ->
                                java.lang.Long.toString(dec, 16).padStart(8, '0').uppercase()
                            } ?: processedKeyId
                            manager.addLog("转换后 keyId: $processedKeyId")
                            
                            bleAutoLockPreferences.setBleKeyData(
                                bleMac = data.bleMac ?: "",
                                userId = data.userId ?: "",
                                collectTime = data.collectTime ?: "",
                                keyId = processedKeyId,
                                keyType = data.keyType ?: "",
                                keyMasterRandom = data.keyMasterRandom ?: "",
                                endTime = data.endTime ?: "",
                                masterKey = data.masterKey ?: "",
                                vin = data.vin ?: ""
                            )
                            bleAutoLockPreferences.setEnabled(true)
                            manager.initialize()
                        } else {
                            val errorMsg = response?.errorMessage ?: "获取蓝牙钥匙失败"
                            manager.addLog("API 返回错误: $errorMsg")
                            _commandResult.value = CommandResult(success = false, message = errorMsg)
                            kotlinx.coroutines.delay(2000)
                            _commandResult.value = null
                        }
                    } else {
                        val error = result.exceptionOrNull()
                        Log.e(TAG, "API error", error)
                        manager.addLog("API 请求异常: ${error?.message}")
                        _commandResult.value = CommandResult(success = false, message = error?.message ?: "获取蓝牙钥匙失败")
                        kotlinx.coroutines.delay(2000)
                        _commandResult.value = null
                    }
                } else {
                    Log.d(TAG, "MAC already exists, enabling BLE")
                    manager.addLog("使用已保存的 MAC 地址")
                    bleAutoLockPreferences.setEnabled(true)
                    manager.initialize()
                }
            }
        }
    }
    
    /**
     * 启动自动刷新
     */
    private fun startAutoRefresh() {
        // 先停止之前的任务
        stopAutoRefresh()
        
        // 启动新的自动刷新任务
        autoRefreshJob = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(refreshInterval)
                if (APIConfig.isConfigured) {
                    refreshVehicleStatus(showLoading = false)
                }
            }
        }
        Log.d(TAG, "自动刷新已启动，间隔 ${refreshInterval/1000} 秒")
    }
    
    /**
     * 停止自动刷新
     */
    private fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
        Log.d(TAG, "自动刷新已停止")
    }

    fun configure(token: String) {
        Log.d(TAG, "configure() called")
        APIConfig.setAccessToken(token)
        // 同步响应式状态，触发 UI 刷新
        _tokenConfigured.value = APIConfig.isConfigured
        Log.d(TAG, "APIConfig.isConfigured: ${APIConfig.isConfigured}")
    }

    /**
     * 刷新车辆状态
     * @param isQuick 是否快速刷新（仅获取主状态，不获取胎压/昨日里程，保留诊断数据）
     * @param preserveLock 是否保留本地锁定状态
     * @param preserveClimate 是否保留本地空调状态
     * @param showLoading 是否显示加载状态
     */
    fun refreshVehicleStatus(
        isQuick: Boolean = false,
        preserveLock: Boolean = false,
        preserveClimate: Boolean = false,
        showLoading: Boolean = true
    ) {
        if (!APIConfig.isConfigured) {
            // Token 尚未从 DataStore 恢复完成时（冷启动竞态）静默跳过，由 init 的自动刷新兜底，
            // 避免在 UI 上闪现"请先配置 Access Token"的假错误
            Log.d(TAG, "refreshVehicleStatus skipped: token not configured yet")
            return
        }

        scope.launch {
            doRefreshVehicleStatus(isQuick, preserveLock, preserveClimate, showLoading)
        }
    }

    /**
     * 可等待的刷新版本：返回本次刷新是否成功（供冷启动重试逻辑使用）
     */
    private suspend fun refreshVehicleStatusAwait(
        isQuick: Boolean = false,
        preserveLock: Boolean = false,
        preserveClimate: Boolean = false,
        showLoading: Boolean = true
    ): Boolean {
        if (!APIConfig.isConfigured) return false
        return doRefreshVehicleStatus(isQuick, preserveLock, preserveClimate, showLoading)
    }

    /**
     * 刷新核心逻辑（挂起函数，串行执行）
     * @return 本次刷新是否成功
     */
    private suspend fun doRefreshVehicleStatus(
        isQuick: Boolean,
        preserveLock: Boolean,
        preserveClimate: Boolean,
        showLoading: Boolean
    ): Boolean {
        if (showLoading) {
            _isLoading.value = true
        }
        _errorMessage.value = null

            val currentVehicle = _selectedVehicle.value
            val preservedIsLocked = if (preserveLock) currentVehicle?.status?.isLocked else null
            val preservedIsClimateOn = if (preserveClimate) currentVehicle?.status?.isClimateOn else null

            val fetchResult = if (isQuick) {
                vehicleRepository.fetchDefaultVehicleStatusQuick()
            } else {
                vehicleRepository.fetchDefaultVehicleStatus()
            }

            fetchResult.onSuccess { apiVehicle ->
                var finalVehicle = apiVehicle
                var finalStatus = apiVehicle.status

                if (isQuick && currentVehicle != null) {
                    finalStatus = finalStatus.copy(
                        enginePowStatus = currentVehicle.status.enginePowStatus,
                        engineTempStatus = currentVehicle.status.engineTempStatus,
                        absStatus = currentVehicle.status.absStatus,
                        powerSteeringStatus = currentVehicle.status.powerSteeringStatus,
                        tirePressureFL = currentVehicle.status.tirePressureFL,
                        tirePressureFR = currentVehicle.status.tirePressureFR,
                        tirePressureRL = currentVehicle.status.tirePressureRL,
                        tirePressureRR = currentVehicle.status.tirePressureRR,
                        tireTemperature = currentVehicle.status.tireTemperature,
                        yesterMileage = currentVehicle.status.yesterMileage
                    )
                }

                if (preservedIsLocked != null) {
                    finalStatus = finalStatus.copy(isLocked = preservedIsLocked)
                }
                if (preservedIsClimateOn != null) {
                    finalStatus = finalStatus.copy(isClimateOn = preservedIsClimateOn)
                }

                finalVehicle = finalVehicle.copy(status = finalStatus)

                if (isQuick && currentVehicle != null) {
                    _selectedVehicle.value = finalVehicle
                    updateVehicleInList(finalVehicle)
                } else {
                    updateVehicleFromAPI(finalVehicle)
                    if (!isQuick) {
                        finalVehicle.vin.takeIf { it.isNotEmpty() }?.let { vin ->
                            fetchAndApplyTirePressure(vin)
                            fetchAndApplyYesterdayMileage(vin)
                        }
                    }
                }

                // 落盘缓存（供下次冷启动秒开） + 桌面小组件同步
                saveVehicleCache(finalVehicle)
                com.open.wuling.widget.VehicleStatusWidgetProvider.saveCacheAndPush(context, finalVehicle)
                // v64：控制条只依赖 VIN，缓存落盘后重绑一次 —— 刷新后桌面按钮即刻可用
                com.open.wuling.widget.VehicleControlsWidgetProvider.pushAll(context)

                // 埋点：App 主动刷新车辆状态成功 + 同步到桌面小组件
                UmengAnalytics.event(appContext, "refresh_status", mapOf("result" to "success", "quick" to isQuick.toString()))
                UmengAnalytics.event(appContext, "widget_refresh", mapOf("result" to "success"))

                // 「离车提醒」检测（车窗未关/车门未锁/后备箱未关 → 系统通知；恢复自动撤回）（v61）
                com.open.wuling.util.VehicleAlertManager.onStatusRefreshed(finalVehicle)

                // v63：停车记录——本地观测式记录，同一位置只续期、移动过才新增
                com.open.wuling.data.local.ParkingHistoryStore.record(context, finalVehicle)
            }.onFailure { error ->
                _errorMessage.value = error.message
                // 埋点：App 主动刷新车辆状态失败
                UmengAnalytics.event(appContext, "refresh_status", mapOf("result" to "fail"))
            }

            if (showLoading) {
                _isLoading.value = false
            }
            return fetchResult.isSuccess
    }

    private fun fetchAndApplyTirePressure(vin: String) {
        scope.launch {
            val currentVehicle = _selectedVehicle.value ?: return@launch
            vehicleRepository.fetchTirePressure(vin, currentVehicle.status)
                .onSuccess { updatedStatus ->
                    val updatedVehicle = currentVehicle.copy(status = updatedStatus)
                    _selectedVehicle.value = updatedVehicle
                    updateVehicleInList(updatedVehicle)
                }
                .onFailure { error ->
                    Log.e(TAG, "胎压获取失败: ${error.message}")
                }
        }
    }

    private fun fetchAndApplyYesterdayMileage(vin: String) {
        scope.launch {
            kotlinx.coroutines.delay(800)
            val mileage = vehicleRepository.fetchYesterdayMileage(vin)
            if (mileage != null && mileage > 0) {
                val currentVehicle = _selectedVehicle.value ?: return@launch
                val updatedStatus = currentVehicle.status.copy(yesterMileage = mileage)
                val updatedVehicle = currentVehicle.copy(status = updatedStatus)
                _selectedVehicle.value = updatedVehicle
                updateVehicleInList(updatedVehicle)
                Log.d(TAG, "昨日里程更新: ${mileage} km (from /car/yesterday/mileage)")
            } else {
                Log.d(TAG, "昨日里程接口未返回有效数据，保持当前值")
            }
        }
    }

    fun executeCommand(command: ControlCommand) {
        if (!APIConfig.isConfigured) {
            _errorMessage.value = "请先配置 Access Token"
            return
        }

        scope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val vehicle = _selectedVehicle.value
            if (vehicle == null) {
                _commandResult.value = CommandResult(
                    success = false,
                    message = "请先选择车辆"
                )
                _isLoading.value = false
                return@launch
            }

            val result = when (command) {
                ControlCommand.LOCK -> vehicleRepository.controlDoorLock(vehicle.vin, 1)
                ControlCommand.UNLOCK -> vehicleRepository.controlDoorLock(vehicle.vin, 0)
                ControlCommand.CLIMATE_ON -> vehicleRepository.controlAC(mapOf(
                    "vin" to vehicle.vin,
                    "accOnOff" to "1",
                    "status" to "1",
                    "temperature" to "24",
                    "blowerLvl" to "3",
                    "duration" to "10"
                ))
                ControlCommand.CLIMATE_OFF -> vehicleRepository.controlAC(mapOf(
                    "vin" to vehicle.vin,
                    "accOnOff" to "0",
                    "status" to "0"
                ))
                ControlCommand.FLASH -> vehicleRepository.sendCommand("flash")
                ControlCommand.HONK -> vehicleRepository.sendCommand("honk")
                ControlCommand.TRUNK -> vehicleRepository.sendCommand("trunk")
                ControlCommand.FIND_CAR -> vehicleRepository.searchCar(vehicle.vin)
                ControlCommand.WINDOW_OPEN -> vehicleRepository.controlWindow(vehicle.vin, 1)
                ControlCommand.WINDOW_CLOSE -> vehicleRepository.controlWindow(vehicle.vin, 0)
                ControlCommand.IGNITION -> vehicleRepository.authorizeIgnition(vehicle.vin)
                ControlCommand.CHARGE_RESERVE -> Result.failure(
                    com.open.wuling.data.api.APIError("该功能暂未接入，请使用官方App设置预约充电")
                )
            }

            result.onSuccess { 
                updateLocalState(command)
                _commandResult.value = CommandResult(
                    success = true,
                    message = "${command.displayName}成功"
                )
                // 埋点：远程控制成功
                UmengAnalytics.event(
                    appContext,
                    "remote_command",
                    mapOf("command" to command.name, "result" to "success")
                )
                // 记录刚刚通过命令更新的本地状态
                val preserveLockState = command == ControlCommand.LOCK || command == ControlCommand.UNLOCK
                val preserveClimateState = command == ControlCommand.CLIMATE_ON || command == ControlCommand.CLIMATE_OFF
                
                // 命令执行成功后立即关闭加载状态
                _isLoading.value = false
                
                // 控制成功后延迟刷新车辆状态，确保同步
                kotlinx.coroutines.delay(5000)
                refreshVehicleStatus(preserveLock = preserveLockState, preserveClimate = preserveClimateState, showLoading = false)
            }.onFailure { error ->
                _commandResult.value = CommandResult(
                    success = false,
                    message = error.message ?: "操作失败"
                )
                _isLoading.value = false
                // 埋点：远程控制失败（带指令名与原因）
                UmengAnalytics.event(
                    appContext,
                    "remote_command",
                    mapOf(
                        "command" to command.name,
                        "result" to "fail",
                        "reason" to (error.message ?: "操作失败").take(80)
                    )
                )
            }

            // 自动消失提示
            kotlinx.coroutines.delay(2000)
            _commandResult.value = null
        }
    }

    /**
     * 保存 Token 到持久化存储并刷新车辆状态
     */
    /**
     * 手机号 + 密码登录并自动保存 Token（hapi OAuth）
     * 结果通过回调返回（主线程），成功后自动保存并刷新车辆状态
     */
    fun loginWithPassword(mobile: String, password: String, onResult: (Boolean, String) -> Unit) {
        if (mobile.isBlank() || password.isBlank()) {
            onResult(false, "请输入手机号和密码")
            return
        }
        scope.launch {
            vehicleRepository.loginLlb(mobile.trim(), password)
                .onSuccess { token ->
                    saveAndConfigureToken(token)
                    onResult(true, "登录成功，Token 已自动保存")
                    // 埋点：登录成功（手机号脱敏）
                    UmengAnalytics.event(
                        appContext,
                        "login_result",
                        mapOf(
                            "result" to "success",
                            "mobile" to UmengAnalytics.maskPhone(mobile.trim())
                        )
                    )
                }
                .onFailure { error ->
                    val msg = error.message ?: "登录失败"
                    onResult(false, msg)
                    // 埋点：登录失败（带原因，便于友盟看失败分布）
                    UmengAnalytics.event(
                        appContext,
                        "login_result",
                        mapOf(
                            "result" to "fail",
                            "reason" to msg.take(80)
                        )
                    )
                }
        }
    }

    fun saveAndConfigureToken(token: String) {
        configure(token)
        scope.launch {
            tokenStore.saveToken(token)
            // 保存后自动刷新车辆状态
            refreshVehicleStatus()
            // 启动自动刷新
            startAutoRefresh()
        }
    }

    /**
     * 清除 Token（退出登录）
     */
    fun logout() {
        APIConfig.setAccessToken("")
        _tokenConfigured.value = false
        _selectedVehicle.value = null
        _user.value = User(id = "", name = "用户", phone = "")
        // 停止自动刷新
        stopAutoRefresh()
        scope.launch {
            tokenStore.clearToken()
        }
        Log.d(TAG, "用户已退出登录")
    }

    private fun updateLocalState(command: ControlCommand) {
        val vehicle = _selectedVehicle.value ?: return
        val status = vehicle.status

        val newStatus = when (command) {
            ControlCommand.LOCK -> status.copy(isLocked = true)
            ControlCommand.UNLOCK -> status.copy(isLocked = false)
            ControlCommand.CLIMATE_ON -> status.copy(isClimateOn = true)
            ControlCommand.CLIMATE_OFF -> status.copy(isClimateOn = false)
            else -> status
        }

        val updatedVehicle = vehicle.copy(status = newStatus)
        _selectedVehicle.value = updatedVehicle
        updateVehicleInList(updatedVehicle)
    }

    private fun updateVehicleFromAPI(vehicle: Vehicle) {
        // 更新或添加车辆到列表
        val currentVehicles = _user.value.vehicles.toMutableList()
        val index = currentVehicles.indexOfFirst { it.vin == vehicle.vin }
        if (index >= 0) {
            currentVehicles[index] = vehicle
            Log.d(TAG, "Vehicle updated in list: ${vehicle.displayName}")
        } else if (vehicle.vin.isNotEmpty()) {
            currentVehicles.add(vehicle)
            Log.d(TAG, "Vehicle added to list: ${vehicle.displayName}")
        }

        // 更新用户对象
        val updatedUser = _user.value.copy(vehicles = currentVehicles)
        _user.value = updatedUser
        Log.d(TAG, "User updated with ${currentVehicles.size} vehicles")

        // 更新选中车辆
        if (_selectedVehicle.value?.vin == vehicle.vin) {
            _selectedVehicle.value = vehicle
            Log.d(TAG, "Selected vehicle updated: ${vehicle.displayName}")
        } else if (_selectedVehicle.value == null || _selectedVehicle.value?.vin?.isEmpty() == true) {
            _selectedVehicle.value = vehicle
            Log.d(TAG, "Selected vehicle set: ${vehicle.displayName}")
        }
    }

    private fun updateVehicleInList(vehicle: Vehicle) {
        val currentVehicles = _user.value.vehicles.toMutableList()
        val index = currentVehicles.indexOfFirst { it.vin == vehicle.vin }
        if (index >= 0) {
            currentVehicles[index] = vehicle
            _user.value = _user.value.copy(vehicles = currentVehicles)
        }
    }

    private suspend fun executeACCommandInternal(
        params: Map<String, String>,
        successMessage: String,
        errorMessage: String
    ) {
        _isLoading.value = true
        _errorMessage.value = null

        val vehicle = _selectedVehicle.value
        if (vehicle == null) {
            _commandResult.value = CommandResult(
                success = false,
                message = "请先选择车辆"
            )
            _isLoading.value = false
            return
        }

        val result = vehicleRepository.controlAC(params)

        result.onSuccess {
            updateLocalState(ControlCommand.CLIMATE_ON)
            _commandResult.value = CommandResult(
                success = true,
                message = successMessage
            )
            // 命令执行成功后立即关闭加载状态
            _isLoading.value = false
            
            kotlinx.coroutines.delay(5000)
            refreshVehicleStatus(preserveClimate = true, showLoading = false)
        }.onFailure { error ->
            _commandResult.value = CommandResult(
                success = false,
                message = error.message ?: errorMessage
            )
            _isLoading.value = false
        }

        kotlinx.coroutines.delay(2000)
        _commandResult.value = null
    }

    fun executeCustomClimateCommand(temperature: Int, fanLevel: Int, turnOn: Boolean) {
        if (!APIConfig.isConfigured) {
            _errorMessage.value = "请先配置 Access Token"
            return
        }

        scope.launch {
            val vehicle = _selectedVehicle.value ?: return@launch
            val params = mutableMapOf(
                "vin" to vehicle.vin,
                "accOnOff" to if (turnOn) "1" else "0",
                "status" to if (turnOn) "1" else "0"
            )
            if (turnOn) {
                params["temperature"] = temperature.toString()
                params["blowerLvl"] = fanLevel.toString()
            }
            val successMsg = if (turnOn) {
                "空调已开启 (${temperature}°C, 风速${fanLevel}档)"
            } else {
                "空调已关闭"
            }
            if (!turnOn) {
                updateLocalState(ControlCommand.CLIMATE_OFF)
            }
            executeACCommandInternal(params, successMsg, "空调控制失败")
        }
    }

    fun executeQuickCool() {
        if (!APIConfig.isConfigured) {
            _errorMessage.value = "请先配置 Access Token"
            return
        }

        scope.launch {
            val vehicle = _selectedVehicle.value ?: return@launch
            executeACCommandInternal(
                mapOf(
                    "vin" to vehicle.vin,
                    "accOnOff" to "1",
                    "status" to "1",
                    "temperature" to "17",
                    "blowerLvl" to "7",
                    "duration" to "20"
                ),
                "快速制冷已开启 (17°C, 最大风速)",
                "快速制冷失败"
            )
        }
    }

    fun executeQuickHeat() {
        if (!APIConfig.isConfigured) {
            _errorMessage.value = "请先配置 Access Token"
            return
        }

        scope.launch {
            val vehicle = _selectedVehicle.value ?: return@launch
            executeACCommandInternal(
                mapOf(
                    "vin" to vehicle.vin,
                    "accOnOff" to "1",
                    "status" to "1",
                    "temperature" to "33",
                    "blowerLvl" to "7",
                    "duration" to "20"
                ),
                "快速制热已开启 (33°C, 最大风速)",
                "快速制热失败"
            )
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // ============== App 自动更新逻辑 ==============

    /**
     * 检查更新：拉取 update.json，若远端 versionCode 大于本地则弹出更新对话框。
     * @param manual 是否由用户手动触发（手动时若无更新弹 Toast 提示已是最新）
     */
    fun checkAppUpdate(manual: Boolean = false) {
        scope.launch {
            // 传入本地版本号：并发拉取时一旦有源返回更高版本即可提前返回（快速通道）
            val info = UpdateChecker.fetchUpdateInfo(
                UpdateConfig.UPDATE_JSON_URLS,
                BuildConfig.VERSION_CODE
            )
            if (info != null && info.versionCode > BuildConfig.VERSION_CODE && info.apkUrl.isNotBlank()) {
                _selectedMirror.value = null    // 每次新弹窗回到「自动」，手动选择仅本次生效
                _mirrorSpeeds.value = emptyMap() // 旧测速结果作废，由弹窗重新测
                _pendingUpdate.value = info
                // 埋点：检查更新发现新版本（manual 区分手动/自动，便于分析更新转化）
                UmengAnalytics.event(
                    appContext,
                    "check_update",
                    mapOf(
                        "result" to "has_update",
                        "manual" to manual.toString(),
                        "local" to BuildConfig.VERSION_NAME,
                        "remote" to info.versionName
                    )
                )
            } else if (manual) {
                Toast.makeText(
                    appContext,
                    "已是最新版本 (v${BuildConfig.VERSION_NAME})",
                    Toast.LENGTH_SHORT
                ).show()
                UmengAnalytics.event(
                    appContext,
                    "check_update",
                    mapOf("result" to "latest", "manual" to "true", "local" to BuildConfig.VERSION_NAME)
                )
            }
        }
    }

    /** 关闭更新对话框（强制更新时不允许关闭） */
    fun dismissUpdate() {
        val info = _pendingUpdate.value
        if (info?.forceUpdate != true) _pendingUpdate.value = null
    }

    /** 设置手动下载源（null=自动）。仅影响本次更新流程，不持久化。 */
    fun setSelectedMirror(prefix: String?) {
        _selectedMirror.value = prefix
    }

    /** 触发各加速源延迟测速（更新弹窗展示用） */
    fun measureMirrors(apkUrl: String) {
        if (apkUrl.isBlank()) return
        scope.launch {
            val urls = UpdateConfig.mirrorCandidates(apkUrl)
            _mirrorSpeeds.value = UpdateChecker.measureMirrorSpeeds(urls)
        }
    }

    /**
     * 构造本次下载的候选 URL 列表。
     * - 未手动选源：返回全部加速源候选（downloadApk 内部自动测速/记忆择优）。
     * - 已手动选源：仅返回该源（强制），绕过自动排序。
     */
    private fun buildDownloadUrls(apkUrl: String, chosen: String?): List<String> {
        if (chosen.isNullOrBlank()) return UpdateConfig.mirrorCandidates(apkUrl)
        return if (apkUrl.startsWith("https://raw.githubusercontent.com/")) {
            listOf("$chosen$apkUrl")
        } else {
            listOf(apkUrl)
        }
    }

    /** 开始后台下载 APK → MD5 校验 → 调起安装；失败回退浏览器下载 */
    fun startUpdateInstall() {
        val info = _pendingUpdate.value ?: return
        scope.launch {
            _updateError.value = null
            _updateProgress.value = 0
            val dest = java.io.File(appContext.cacheDir, "updates/update.apk")
            try {
                val urls = buildDownloadUrls(info.apkUrl, _selectedMirror.value)
                UpdateChecker.downloadApk(urls, dest) { p ->
                    _updateProgress.value = p
                }
                // MD5 校验（若 json 提供了 md5）
                if (info.md5.isNotBlank()) {
                    val calc = UpdateChecker.md5(dest)
                    if (!calc.equals(info.md5, ignoreCase = true)) {
                        throw RuntimeException("MD5 校验失败，安装包可能已被篡改")
                    }
                }
                _updateProgress.value = null
                installApk(dest)
                _pendingUpdate.value = null
            } catch (e: Exception) {
                _updateProgress.value = null
                _updateError.value = e.message ?: "下载失败"
            }
        }
    }

    /** 浏览器打开 APK 直链（下载/安装失败时的兜底；优先用国内可达的镜像地址） */
    fun openApkInBrowser() {
        val info = _pendingUpdate.value ?: return
        try {
            val bestUrl = UpdateConfig.mirrorCandidates(info.apkUrl).firstOrNull() ?: info.apkUrl
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(bestUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
        } catch (e: Exception) {
            _updateError.value = "无法打开浏览器：${e.message}"
        }
    }

    /** 调起系统安装器安装 APK；Android 8+ 需先授予「安装未知应用」权限 */
    private fun installApk(file: java.io.File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pm = appContext.packageManager
                if (!pm.canRequestPackageInstalls()) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${appContext.packageName}")
                    )
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(intent)
                    _updateError.value = "请先开启「允许安装未知应用」，再点立即更新"
                    return
                }
            }
            val uri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            appContext.startActivity(intent)
        } catch (e: Exception) {
            // 任何异常都回退到浏览器下载
            openApkInBrowser()
        }
    }

    fun selectVehicle(vehicle: Vehicle) {
        _selectedVehicle.value = vehicle
    }
}

data class CommandResult(
    val success: Boolean,
    val message: String
)
