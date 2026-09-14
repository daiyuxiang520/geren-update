package com.open.wuling.ble

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.open.wuling.MainActivity
import com.open.wuling.R
import com.open.wuling.data.local.BleAutoLockPreferences
import com.open.wuling.util.BleAuthUtils
import com.open.wuling.util.NativeFreeProtocolUtils
import com.open.wuling.util.RssiFilter
import java.security.SecureRandom
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * BLE 无感控车管理器
 * 负责蓝牙扫描、连接、RSSI 监控和自动解锁/上锁逻辑
 */
class BleAutoLockManager(
    private val context: Context,
    private val preferences: BleAutoLockPreferences,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "BleAutoLockManager"
        private const val RSSI_READ_INTERVAL = 1000L
        private const val SCAN_TIMEOUT = 10000L
        private const val MANUAL_CONTROL_PAUSE = 60000L
        private const val MTU_SIZE = 251
        private const val DELAY_AFTER_CONNECT = 500L
        private const val DELAY_AFTER_NOTIFY = 500L
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var bluetoothGatt: BluetoothGatt? = null

    private val rssiFilter = RssiFilter()
    private val handler = Handler(Looper.getMainLooper())

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()
    private val maxLogLines = 100

    fun addLog(message: String) {
        scope.launch {
            if (preferences.logEnabled.first()) {
                val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
                val logMessage = "[$timestamp] $message"
                val newLogs = (_logs.value + logMessage).takeLast(maxLogLines)
                _logs.value = newLogs
            }
        }
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _currentRssi = MutableStateFlow<Int?>(null)
    val currentRssi: StateFlow<Int?> = _currentRssi.asStateFlow()

    private val _filteredRssi = MutableStateFlow<Int?>(null)
    val filteredRssi: StateFlow<Int?> = _filteredRssi.asStateFlow()
    
    private var isForegroundServiceRunning = false
    private val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val vehicleStateNotificationId = 1002
    private val vehicleStateChannelId = "vehicle_state_channel"

    data class ScannedDevice(
        val address: String,
        val name: String?,
        val rssi: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    private val _isScanningAll = MutableStateFlow(false)
    val isScanningAll: StateFlow<Boolean> = _isScanningAll.asStateFlow()

    private var allDevicesScanCallback: ScanCallback? = null

    private var isInCooldown = false
    private var isPausedByManualControl = false
    private var pauseResumeTime: Long = 0
    private var unlockConditionStartTime: Long = 0
    private var lockConditionStartTime: Long = 0
    private var lastKnownVehicleLocked: Boolean? = null
    private var lastAction: String? = null

    var onAutoUnlock: (suspend () -> Unit)? = null
    var onAutoLock: (suspend () -> Unit)? = null
    var onCheckVehicleLocked: (suspend () -> Boolean)? = null
    var onShowToast: (String) -> Unit = {}

    // ── v2.0.0 auth session state ──────────────────────────────
    private var notifyEnabled1 = false
    private var notifyEnabled2 = false
    private var pendingAuthGatt: BluetoothGatt? = null
    private var isAuthenticated = false
    private val secureRandom = SecureRandom()

    // auth random values
    private var authRandom2Local: Int = 0
    private var authRandom1Remote: Int? = null
    private var authSessionKey: ByteArray? = null
    private var bleKeyBytes: ByteArray? = null
    private var authPhase: String = ""  // "awaitingHelloResponse" | "awaitingChallengeAck"

    private var cachedTargetMac: String = ""
    private val pendingCommands = mutableListOf<String>()
    private var scanRetryCount = 0

    private val rssiReadRunnable = object : Runnable {
        override fun run() {
            bluetoothGatt?.readRemoteRssi()
            handler.postDelayed(this, RSSI_READ_INTERVAL)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            if (result == null) return
            val device = result.device
            addLog("扫描到设备: ${device.address}, 名称: ${device.name ?: "未知"}")
            if (device.address.equals(cachedTargetMac, ignoreCase = true)) {
                addLog("找到目标设备，开始连接")
                stopScan()
                connectToDevice(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            addLog("扫描失败: $errorCode")
            _connectionState.value = ConnectionState.Error("扫描失败: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            addLog("连接状态改变: status=$status, newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    addLog("蓝牙已连接")
                    bluetoothGatt = gatt
                    notifyEnabled1 = false
                    notifyEnabled2 = false
                    authPhase = ""; authRandom1Remote = null; authSessionKey = null
                    
                    addLog("请求高连接优先级")
                    gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    
                    addLog("延迟 ${DELAY_AFTER_CONNECT}ms 后协商 MTU")
                    handler.postDelayed({
                        addLog("请求 MTU: $MTU_SIZE")
                        gatt?.requestMtu(MTU_SIZE)
                    }, DELAY_AFTER_CONNECT)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    addLog("蓝牙已断开, status=$status")
                    _connectionState.value = ConnectionState.Disconnected
                    bluetoothGatt?.close(); bluetoothGatt = null

                    scope.launch {
                        if (preferences.enabled.first() && isAuthenticated) {
                            addLog("2秒后重新扫描")
                            delay(2000)
                            startScanning()
                        }
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            addLog("MTU 已改变: mtu=$mtu, status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                addLog("开始发现服务")
                gatt?.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            addLog("服务发现: status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                addLog("再次请求高连接优先级")
                gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                
                addLog("可用服务:")
                gatt?.services?.forEach { service ->
                    addLog("  - ${service.uuid}")
                    service.characteristics.forEach { char ->
                        addLog("    - ${char.uuid}")
                    }
                }
                enableNotifications(gatt)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)
            addLog("描述符写入: ${descriptor?.characteristic?.uuid}, status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val charUuid = descriptor?.characteristic?.uuid?.toString()
                
                if (charUuid?.equals(BleAuthUtils.NOTIFY_CHAR_UUID, ignoreCase = true) == true) {
                    addLog("通知1已开启 (0x2A6F)")
                    notifyEnabled1 = true
                    addLog("现在启用通知2")
                    enableSecondNotification(gatt)
                } else if (charUuid?.equals(BleAuthUtils.AUTH_CHAR_UUID, ignoreCase = true) == true) {
                    addLog("通知2已开启 (0x2A7F)")
                    notifyEnabled2 = true
                    addLog("两个通知都已开启")
                    startAuthAfterDelay(gatt)
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            addLog("特征写入: ${characteristic?.uuid}, status=$status")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            val data = characteristic?.value
            addLog("特征通知: ${characteristic?.uuid}, 数据长度: ${data?.size ?: 0}")
            if (data != null) {
                handleCharacteristicData(data)
            }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            super.onReadRemoteRssi(gatt, rssi, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _currentRssi.value = rssi
                val filtered = rssiFilter.addAndFilter(rssi)
                _filteredRssi.value = filtered
                addLog("RSSI: 原始=$rssi, 滤波=$filtered")
                scope.launch {
                    processRssi(filtered)
                }
            }
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt?) {
        addLog("开始启用通知")
        pendingAuthGatt = gatt
        
        val cmdService = gatt?.getService(java.util.UUID.fromString(BleAuthUtils.CMD_SERVICE_UUID))
        if (cmdService == null) {
            addLog("找不到 CMD 服务 (0x181A)")
        } else {
            val notifyChar1 = cmdService.getCharacteristic(java.util.UUID.fromString(BleAuthUtils.NOTIFY_CHAR_UUID))
            if (notifyChar1 != null) {
                addLog("启用通知1 (0x2A6F)")
                gatt.setCharacteristicNotification(notifyChar1, true)
                val descriptor = notifyChar1.getDescriptor(java.util.UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"))
                descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            } else {
                addLog("找不到通知特征1 (0x2A6F)")
            }
        }
    }
    
    private fun enableSecondNotification(gatt: BluetoothGatt?) {
        addLog("启用第二个通知")
        
        val authService = gatt?.getService(java.util.UUID.fromString(BleAuthUtils.AUTH_SERVICE_UUID))
        if (authService == null) {
            addLog("找不到鉴权服务 (0x182A)")
            startAuthAfterDelay(gatt)
        } else {
            val notifyChar2 = authService.getCharacteristic(java.util.UUID.fromString(BleAuthUtils.AUTH_CHAR_UUID))
            if (notifyChar2 != null) {
                addLog("启用通知2 (0x2A7F)")
                gatt.setCharacteristicNotification(notifyChar2, true)
                val descriptor = notifyChar2.getDescriptor(java.util.UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"))
                descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            } else {
                addLog("找不到通知特征2 (0x2A7F)")
                startAuthAfterDelay(gatt)
            }
        }
    }
    
    private fun startAuthAfterDelay(gatt: BluetoothGatt?) {
        // 已鉴权且车断连重连：跳过握手，直接回到就绪状态
        if (isAuthenticated && authSessionKey != null) {
            addLog("已鉴权, 跳过握手直接就绪")
            _connectionState.value = ConnectionState.Connected
            startRssiReading()
            drainPendingCommands()
            return
        }
        addLog("延迟 ${DELAY_AFTER_NOTIFY}ms 后开始鉴权")
        handler.postDelayed({
            scope.launch {
                startAuthentication(gatt)
            }
        }, DELAY_AFTER_NOTIFY)
    }

    /**
     * v2.0.0 hello/challenge auth: build hello frame → write to 0x2A6E
     */
    private suspend fun startAuthentication(gatt: BluetoothGatt?) {
        addLog("开始 v2.0.0 NativeFree 鉴权流程")

        val masterKey = preferences.bleMasterKey.first()
        val masterRandom = preferences.bleKeyMasterRandom.first()
        val bleKey = preferences.bleKeyId.first()

        if (bleKey.isEmpty() || masterKey.isEmpty() || masterRandom.isEmpty()) {
            addLog("鉴权失败: 缺少密钥数据")
            return
        }

        // Derive session key
        authSessionKey = NativeFreeProtocolUtils.deriveAuthSessionKey(masterKey, masterRandom)
        bleKeyBytes = NativeFreeProtocolUtils.deriveBleKeyBytes(bleKey)
        authRandom2Local = secureRandom.nextInt()
        addLog("authSessionKey=${NativeFreeProtocolUtils.toHex(authSessionKey!!)}")
        addLog("bleKeyBytes=${NativeFreeProtocolUtils.toHex(bleKeyBytes!!)}")
        addLog("authRandom2Local=$authRandom2Local")

        // Build hello frame
        val unixTime = System.currentTimeMillis() / 1000
        val hello = NativeFreeProtocolUtils.buildHelloFrame(unixTime, bleKeyBytes!!)
        addLog("HELLO帧: ${NativeFreeProtocolUtils.toHex(hello)}")

        // Write to auth write channel (0x2A6E on service 0x181A)
        val cmdService = gatt?.getService(java.util.UUID.fromString(BleAuthUtils.CMD_SERVICE_UUID))
        val authWriteChar = cmdService?.getCharacteristic(
            java.util.UUID.fromString(BleAuthUtils.AUTH_WRITE_CHAR_UUID))

        if (authWriteChar == null) {
            addLog("鉴权失败: 找不到鉴权写入特征 0x2A6E")
            return
        }

        authPhase = "awaitingHelloResponse"
        authWriteChar.value = hello
        authWriteChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        gatt.writeCharacteristic(authWriteChar)
        addLog("HELLO已发送到 0x2A6E")
    }
    
    /**
     * v2.0.0 notification handler: route auth (0x2A6F) vs control (0x2A7F)
     */
    private fun handleCharacteristicData(data: ByteArray) {
        scope.launch {
            val hex = NativeFreeProtocolUtils.toHex(data)
            addLog("收到通知, len=${data.size} hex=${hex.take(80)}")

            if (isAuthenticated) return@launch

            when (authPhase) {
                "awaitingHelloResponse" -> handleHelloResponse(data)
                "awaitingChallengeAck" -> handleChallengeAck(data)
                else -> addLog("非鉴权阶段通知, 忽略")
            }
        }
    }

    private suspend fun handleHelloResponse(data: ByteArray) {
        val key = authSessionKey ?: run { addLog("authSessionKey为空"); return }

        // Strip first byte (marker) like v2.0.0 d2.1/h.smali
        val stripped = if (data.size > 1) data.copyOfRange(1, data.size) else data
        addLog("HELLO回复: strippedLen=${stripped.size} hex=${NativeFreeProtocolUtils.toHex(stripped).take(80)}")

        if (stripped.size < 16 || stripped.size % 16 != 0) {
            addLog("回复数据长度异常: ${stripped.size} (需要16字节对齐)")
            return
        }

        // AES-128-ECB decrypt
        val decrypted = NativeFreeProtocolUtils.aesEcbDecrypt(key, stripped)
        val r1remote = NativeFreeProtocolUtils.readIntBE(decrypted, 8)
        authRandom1Remote = r1remote
        addLog("解密成功, authRandom1Remote=$r1remote")

        // Build challenge reply
        val challenge = NativeFreeProtocolUtils.buildChallengeReply(
            key, authRandom2Local, r1remote, bleKeyBytes!!)
        addLog("CHALLENGE帧: ${NativeFreeProtocolUtils.toHex(challenge).take(60)}")

        // Write challenge to 0x2A6E
        val gatt = bluetoothGatt ?: return
        val cmdService = gatt.getService(java.util.UUID.fromString(BleAuthUtils.CMD_SERVICE_UUID))
        val authWriteChar = cmdService?.getCharacteristic(
            java.util.UUID.fromString(BleAuthUtils.AUTH_WRITE_CHAR_UUID))

        if (authWriteChar != null) {
            authPhase = "awaitingChallengeAck"
            authWriteChar.value = challenge
            authWriteChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            gatt.writeCharacteristic(authWriteChar)
            addLog("CHALLENGE已发送到 0x2A6E")
        }
    }

    private suspend fun handleChallengeAck(data: ByteArray) {
        addLog("挑战ACK回复: len=${data.size}")
        authPhase = ""
        isAuthenticated = true
        scanRetryCount = 0
        _connectionState.value = ConnectionState.Connected
        bluetoothGatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
        addLog("鉴权成功！握手完成")
        startRssiReading()
        drainPendingCommands()
    }

    fun initialize() {
        addLog("initialize() 被调用")
        lastAction = null
        createVehicleStateNotificationChannel()
        observePreferences()
        scope.launch {
            start()
        }
    }

    private fun createVehicleStateNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                vehicleStateChannelId,
                "车辆状态变化",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "车辆自动解锁/上锁时的通知"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showVehicleUnlockedNotification() {
        addLog("发送车辆已解锁通知")
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, vehicleStateChannelId)
            .setContentTitle("车辆已解锁")
            .setContentText("通过蓝牙自动解锁成功")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()

        notificationManager.notify(vehicleStateNotificationId, notification)
    }

    private fun showVehicleLockedNotification() {
        addLog("发送车辆已上锁通知")
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, vehicleStateChannelId)
            .setContentTitle("车辆已上锁")
            .setContentText("通过蓝牙自动上锁成功")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()

        notificationManager.notify(vehicleStateNotificationId, notification)
    }

    private fun observePreferences() {
        scope.launch {
            preferences.enabled.collect { enabled ->
                updateForegroundService()
            }
        }
        scope.launch {
            preferences.foregroundServiceEnabled.collect { enabled ->
                updateForegroundService()
            }
        }
    }

    private fun updateForegroundService() {
        scope.launch {
            val enabled = preferences.enabled.first()
            val foregroundEnabled = preferences.foregroundServiceEnabled.first()
            
            if (enabled && foregroundEnabled) {
                startForegroundService()
            } else {
                stopForegroundService()
            }
        }
    }

    private fun startForegroundService() {
        if (!isForegroundServiceRunning) {
            addLog("启动前台保活服务")
            BleKeepAliveService.startService(context)
            isForegroundServiceRunning = true
        }
    }

    private fun stopForegroundService() {
        if (isForegroundServiceRunning) {
            addLog("停止前台保活服务")
            BleKeepAliveService.stopService(context)
            isForegroundServiceRunning = false
        }
    }

    /** 全局开关：关闭时彻底停止扫描和连接，避免干扰linkey */
    fun setEnabled(enabled: Boolean) {
        scope.launch {
            preferences.setEnabled(enabled)
            if (!enabled) stop()
        }
    }

    private suspend fun start() {
        if (!preferences.enabled.first()) { addLog("全局开关已关闭，跳过启动"); return }
        addLog("start() 开始执行")

        if (!hasRequiredPermissions()) {
            addLog("权限检查失败")
            _connectionState.value = ConnectionState.Error("缺少必要权限")
            return
        }
        addLog("权限检查通过")

        if (bluetoothAdapter?.isEnabled != true) {
            addLog("蓝牙未开启")
            _connectionState.value = ConnectionState.Error("蓝牙未开启")
            return
        }
        addLog("蓝牙已开启")

        cachedTargetMac = preferences.bleMac.first()
        addLog("目标 MAC 地址: $cachedTargetMac")
        if (cachedTargetMac.isEmpty()) {
            _connectionState.value = ConnectionState.Error("未绑定车辆蓝牙")
            return
        }

        updateForegroundService()
        addLog("准备开始扫描")
        startScanning()
    }

    private fun stop() {
        stopScan()
        disconnect()
        stopForegroundService()
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun startScanning() {
        if (_connectionState.value is ConnectionState.Scanning) return
        addLog("开始扫描蓝牙设备 (第${scanRetryCount + 1}次)")
        _connectionState.value = ConnectionState.Scanning
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        // v2.0.0: 用 ScanFilter 精准过滤目标 MAC，大幅提升扫描效率
        val filters = if (cachedTargetMac.isNotEmpty()) listOf(
            android.bluetooth.le.ScanFilter.Builder()
                .setDeviceAddress(cachedTargetMac.uppercase())
                .build()
        ) else null
        scanner.startScan(filters,
            android.bluetooth.le.ScanSettings.Builder()
                .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build(),
            scanCallback)

        // 扫描超时：connectTimeout 后触发重试
        handler.postDelayed({
            if (_connectionState.value is ConnectionState.Scanning) {
                stopScan()
                val backoff = (SCAN_TIMEOUT + scanRetryCount * 2000L).coerceAtMost(30000L)
                scanRetryCount++
                addLog("扫描超时，${backoff}ms后重试")
                handler.postDelayed({ scope.launch { startScanning() } }, backoff)
            }
        }, SCAN_TIMEOUT)
    }

    private fun stopScan() {
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {}
    }

    private fun connectToDevice(device: BluetoothDevice) {
        scanRetryCount = 0
        _connectionState.value = ConnectionState.Connecting
        addLog("连接设备: ${device.address}")
        
        stopScan()
        
        bluetoothGatt?.close()
        bluetoothGatt = null
        
        addLog("使用 autoConnect=false 直接连接")
        try {
            @Suppress("MissingPermission")
            val g = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            bluetoothGatt = g
            addLog("connectGatt 返回: ${g != null}")
        } catch (e: SecurityException) {
            addLog("连接失败: 缺少权限 - ${e.message}")
            _connectionState.value = ConnectionState.Error("缺少蓝牙连接权限")
        }
    }

    fun disconnect() {
        stopRssiReading()
        notifyEnabled1 = false; notifyEnabled2 = false
        isAuthenticated = false
        authPhase = ""; authRandom1Remote = null; authSessionKey = null
        bluetoothGatt?.close(); bluetoothGatt = null
        _connectionState.value = ConnectionState.Disconnected
    }

    /** 发送 BLE 控制指令，握手未完成时自动排队 */
    fun sendCommand(cmd: String) {
        addLog("sendCommand: $cmd (已鉴权=$isAuthenticated GATT=${bluetoothGatt != null})")
        if (!isAuthenticated) {
            if (!pendingCommands.contains(cmd)) {
                pendingCommands.add(cmd)
                addLog("指令排队: $cmd (等待握手完成)")
            }
            return
        }
        doSendCommand(cmd)
    }

    /**
     * v2.0.0 control frame: 39D6 prefix + marker + AES-ECB → write to 0x2A7E
     */
    private fun doSendCommand(cmd: String) {
        addLog("执行 v2.0.0 BLE 指令: $cmd")
        try {
            val r1r = authRandom1Remote ?: run { addLog("authRandom1Remote为空"); return }
            val r2l = authRandom2Local
            // v2.0.0: 控制帧用 authSplit = [r1,r2,r1,r2] 加密，不是 authSessionKey
            val ctrlKey = NativeFreeProtocolUtils.deriveSessionIv(r1r, r2l)
            val bkb = bleKeyBytes ?: run { addLog("bleKeyBytes为空, 请重新连接"); return }
            val gatt = bluetoothGatt
            if (gatt == null) { addLog("GATT未连接, 指令失败: $cmd"); return }

            val marker = when (cmd) {
                "UNLOCK" -> NativeFreeProtocolUtils.UNLOCK_MARKER
                "LOCK"   -> NativeFreeProtocolUtils.LOCK_MARKER
                else -> return
            }

            val controlRandom = secureRandom.nextInt()
            val frame = NativeFreeProtocolUtils.buildControlFrame(ctrlKey, marker, bkb, controlRandom)
            addLog("控制帧: ${NativeFreeProtocolUtils.toHex(frame).take(60)}")

            val svc = gatt.getService(java.util.UUID.fromString(BleAuthUtils.AUTH_SERVICE_UUID))
            val cmdChar = svc?.getCharacteristic(java.util.UUID.fromString(BleAuthUtils.CMD_CHAR_UUID))
            if (cmdChar != null) {
                cmdChar.value = frame
                cmdChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                gatt.writeCharacteristic(cmdChar)
                addLog("指令已发送到 0x2A7E")
            }
        } catch (e: Exception) { addLog("发送指令失败: ${e.message}") }
    }

    private fun drainPendingCommands() {
        if (pendingCommands.isEmpty()) return
        addLog("执行排队指令: ${pendingCommands.joinToString()}")
        val cmds = pendingCommands.toList(); pendingCommands.clear()
        cmds.forEach { doSendCommand(it) }
    }

    private fun startRssiReading() {
        handler.post(rssiReadRunnable)
    }

    private fun stopRssiReading() {
        handler.removeCallbacks(rssiReadRunnable)
    }

    private suspend fun processRssi(rssi: Int) {
        if (isInCooldown) return

        if (isPausedByManualControl) {
            if (System.currentTimeMillis() >= pauseResumeTime) {
                isPausedByManualControl = false
            } else {
                return
            }
        }

        if (!rssiFilter.hasEnoughData()) return

        val unlockRssi = preferences.unlockRssi.first()
        val unlockDuration = preferences.unlockDuration.first()
        val lockRssi = preferences.lockRssi.first()
        val lockDuration = preferences.lockDuration.first()
        val cooldownTime = preferences.cooldownTime.first()

        val isLocked = onCheckVehicleLocked?.invoke() ?: return
        lastKnownVehicleLocked = isLocked

        val currentTime = System.currentTimeMillis()

        if (lastAction == null) {
            if (isLocked) {
                if (rssi >= unlockRssi) {
                    if (unlockDuration == 0) {
                        triggerUnlock(cooldownTime)
                    } else {
                        if (unlockConditionStartTime == 0L) {
                            unlockConditionStartTime = currentTime
                        } else {
                            val elapsed = TimeUnit.MILLISECONDS.toSeconds(currentTime - unlockConditionStartTime)
                            if (elapsed >= unlockDuration) {
                                triggerUnlock(cooldownTime)
                            }
                        }
                    }
                } else {
                    unlockConditionStartTime = 0L
                }
                lockConditionStartTime = 0L
            } else {
                if (rssi <= lockRssi) {
                    if (lockDuration == 0) {
                        triggerLock(cooldownTime)
                    } else {
                        if (lockConditionStartTime == 0L) {
                            lockConditionStartTime = currentTime
                        } else {
                            val elapsed = TimeUnit.MILLISECONDS.toSeconds(currentTime - lockConditionStartTime)
                            if (elapsed >= lockDuration) {
                                triggerLock(cooldownTime)
                            }
                        }
                    }
                } else {
                    lockConditionStartTime = 0L
                }
                unlockConditionStartTime = 0L
            }
        } else if (lastAction == "unlock") {
            if (!isLocked) {
                if (rssi <= lockRssi) {
                    if (lockDuration == 0) {
                        triggerLock(cooldownTime)
                    } else {
                        if (lockConditionStartTime == 0L) {
                            lockConditionStartTime = currentTime
                        } else {
                            val elapsed = TimeUnit.MILLISECONDS.toSeconds(currentTime - lockConditionStartTime)
                            if (elapsed >= lockDuration) {
                                triggerLock(cooldownTime)
                            }
                        }
                    }
                } else {
                    lockConditionStartTime = 0L
                }
            }
        } else if (lastAction == "lock") {
            if (isLocked) {
                if (rssi >= unlockRssi) {
                    if (unlockDuration == 0) {
                        triggerUnlock(cooldownTime)
                    } else {
                        if (unlockConditionStartTime == 0L) {
                            unlockConditionStartTime = currentTime
                        } else {
                            val elapsed = TimeUnit.MILLISECONDS.toSeconds(currentTime - unlockConditionStartTime)
                            if (elapsed >= unlockDuration) {
                                triggerUnlock(cooldownTime)
                            }
                        }
                    }
                } else {
                    unlockConditionStartTime = 0L
                }
            }
        }
    }

    private suspend fun triggerUnlock(cooldownTime: Int) {
        onShowToast("正在自动解锁...")
        sendCommand("UNLOCK")
        lastAction = "unlock"
        showVehicleUnlockedNotification()
        enterCooldown(cooldownTime)
        unlockConditionStartTime = 0L
        lockConditionStartTime = 0L
    }

    private suspend fun triggerLock(cooldownTime: Int) {
        onShowToast("正在自动上锁...")
        sendCommand("LOCK")
        lastAction = "lock"
        showVehicleLockedNotification()
        enterCooldown(cooldownTime)
        unlockConditionStartTime = 0L
        lockConditionStartTime = 0L
    }

    private fun enterCooldown(seconds: Int) {
        isInCooldown = true
        handler.postDelayed({
            isInCooldown = false
        }, TimeUnit.SECONDS.toMillis(seconds.toLong()))
    }

    fun onManualControl() {
        isPausedByManualControl = true
        pauseResumeTime = System.currentTimeMillis() + MANUAL_CONTROL_PAUSE
        unlockConditionStartTime = 0L
        lockConditionStartTime = 0L
    }

    private fun hasRequiredPermissions(): Boolean {
        val requiredPermissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun destroy() {
        stop()
        handler.removeCallbacksAndMessages(null)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun startScanAllDevices() {
        if (_isScanningAll.value) return
        addLog("开始扫描所有附近设备")
        _isScanningAll.value = true
        _scannedDevices.value = emptyList()

        allDevicesScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                result?.let { scanResult ->
                    val device = scanResult.device
                    addLog("发现设备: ${device.address}, 名称: ${device.name ?: "未知"}, RSSI: ${scanResult.rssi}")
                    
                    val newDevice = ScannedDevice(
                        address = device.address,
                        name = device.name,
                        rssi = scanResult.rssi
                    )
                    
                    val currentList = _scannedDevices.value.toMutableList()
                    val existingIndex = currentList.indexOfFirst { it.address == device.address }
                    if (existingIndex >= 0) {
                        currentList[existingIndex] = newDevice
                    } else {
                        currentList.add(newDevice)
                    }
                    _scannedDevices.value = currentList.sortedByDescending { it.rssi }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                addLog("扫描所有设备失败: $errorCode")
                _isScanningAll.value = false
            }
        }

        bluetoothAdapter?.bluetoothLeScanner?.startScan(allDevicesScanCallback)
    }

    fun stopScanAllDevices() {
        if (!_isScanningAll.value) return
        addLog("停止扫描所有设备")
        _isScanningAll.value = false
        allDevicesScanCallback?.let {
            try {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(it)
            } catch (e: Exception) {
            }
        }
        allDevicesScanCallback = null
    }

    fun clearScannedDevices() {
        _scannedDevices.value = emptyList()
    }

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Scanning : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }
}
