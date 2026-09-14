package com.open.wuling

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.open.wuling.data.local.AmapKeyManager
import com.open.wuling.data.local.BleAutoLockPreferences
import com.open.wuling.ui.components.ACControlSheet
import com.open.wuling.ui.components.BleAutoLockSheet
import com.open.wuling.ui.components.PermissionDeniedDialog
import com.open.wuling.ui.components.PermissionRequestDialog
import com.open.wuling.ui.components.UpdateDialog
import com.open.wuling.ui.components.openAppSettings
import com.open.wuling.ui.screens.DetailScreen
import com.open.wuling.ui.screens.EnergyScreen
import com.open.wuling.ui.screens.HomeScreen
import com.open.wuling.ui.screens.LocationScreen
import com.open.wuling.ui.screens.ProfileScreen
import com.open.wuling.ui.theme.WulingTheme
import com.open.wuling.ui.theme.buildCustomColorScheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class PermissionType {
    BLUETOOTH,
    LOCATION,
    NOTIFICATION
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private lateinit var bleAutoLockPreferences: BleAutoLockPreferences

    private var pendingPermissionType: PermissionType? = null
    private var onPermissionResult: ((Boolean) -> Unit)? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        onPermissionResult?.invoke(allGranted)
        onPermissionResult = null
        pendingPermissionType = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AmapKeyManager.loadFromPrefs(this)
        bleAutoLockPreferences = BleAutoLockPreferences(this)

        enableEdgeToEdge()
        setContent {
            AppContent(
                bleAutoLockPreferences = bleAutoLockPreferences,
                onRequestPermissions = { permissionType, callback ->
                    requestPermissions(permissionType, callback)
                }
            )
        }
    }

    fun checkPermissions(permissionType: PermissionType): Boolean {
        return when (permissionType) {
            PermissionType.BLUETOOTH -> checkBluetoothPermissions()
            PermissionType.LOCATION -> checkLocationPermission()
            PermissionType.NOTIFICATION -> checkNotificationPermission()
        }
    }

    private fun checkBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED &&
                    // ColorOS 等国产 ROM 扫描 BLE 时仍强制要求位置权限
                    checkLocationPermission()
        } else {
            checkLocationPermission()
        }
    }

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun requestPermissions(
        permissionType: PermissionType,
        onResult: (Boolean) -> Unit
    ) {
        if (checkPermissions(permissionType)) {
            onResult(true)
            return
        }

        pendingPermissionType = permissionType
        onPermissionResult = onResult

        val permissions = when (permissionType) {
            PermissionType.BLUETOOTH -> getBluetoothPermissions()
            PermissionType.LOCATION -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
            PermissionType.NOTIFICATION -> getNotificationPermissions()
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        } else {
            onResult(true)
        }
    }

    private fun getBluetoothPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // BLUETOOTH_SCAN 未声明 neverForLocation，需一并申请位置权限，
                // 否则在 OPPO/ColorOS 上扫描不到车机蓝牙
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        return permissions
    }

    private fun getNotificationPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                listOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }
    }
}

@Composable
fun AppContent(
    viewModel: MainViewModel = hiltViewModel(),
    bleAutoLockPreferences: BleAutoLockPreferences,
    onRequestPermissions: (PermissionType, (Boolean) -> Unit) -> Unit
) {
    val themePrefs = viewModel.themePreferences

    val themeMode by themePrefs.themeModeFlow.collectAsState(initial = 0)
    val useCustomColors by themePrefs.useCustomColorsFlow.collectAsState(initial = false)
    val useCustomBackground by themePrefs.useCustomBackgroundFlow.collectAsState(initial = false)
    val backgroundImagePath by themePrefs.backgroundImagePathFlow.collectAsState(initial = null)
    val backgroundBlur by themePrefs.backgroundBlurFlow.collectAsState(initial = 0f)
    val backgroundDimEnabled by themePrefs.backgroundDimEnabledFlow.collectAsState(initial = true)
    val cardAlpha by themePrefs.cardAlphaFlow.collectAsState(initial = 0.95f)
    val customPrimary by themePrefs.customPrimaryColorFlow.collectAsState(initial = 0xFF2D7AF6.toInt())
    val customBackground by themePrefs.customBackgroundColorFlow.collectAsState(initial = 0xFF0A0A0C.toInt())
    val customCard by themePrefs.customCardColorFlow.collectAsState(initial = 0xFF1A1A1E.toInt())
    val customTextPrimary by themePrefs.customTextPrimaryColorFlow.collectAsState(initial = 0xFFFFFFFF.toInt())
    val customTextSecondary by themePrefs.customTextSecondaryColorFlow.collectAsState(initial = 0xFFB0B0B0.toInt())

    val isSystemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemDark
    }

    val customScheme = remember(
        darkTheme, customPrimary, customBackground, customCard,
        customTextPrimary, customTextSecondary
    ) {
        buildCustomColorScheme(
            isDark = darkTheme,
            primaryColor = Color(customPrimary),
            backgroundColor = Color(customBackground),
            cardColor = Color(customCard),
            textPrimaryColor = Color(customTextPrimary),
            textSecondaryColor = Color(customTextSecondary)
        )
    }

    WulingTheme(
        darkTheme = darkTheme,
        useCustomColors = useCustomColors,
        customColorScheme = customScheme,
        cardAlpha = cardAlpha
    ) {
        MainScreen(
            useCustomBackground = useCustomBackground,
            backgroundImagePath = backgroundImagePath,
            backgroundBlur = backgroundBlur,
            backgroundDimEnabled = backgroundDimEnabled,
            bleAutoLockPreferences = bleAutoLockPreferences,
            onRequestPermissions = onRequestPermissions
        )
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(
    val appState: AppState,
    val themePreferences: com.open.wuling.data.local.ThemePreferences
) : ViewModel() {

}

data class TabItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    useCustomBackground: Boolean = false,
    backgroundImagePath: String? = null,
    backgroundBlur: Float = 0f,
    backgroundDimEnabled: Boolean = true,
    bleAutoLockPreferences: BleAutoLockPreferences,
    onRequestPermissions: (PermissionType, (Boolean) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val appState = viewModel.appState
    
    LaunchedEffect(Unit) {
        appState.init(context)
    }

    // 回到前台自动快速刷新（热启动时数据不过期，无需手动点刷新）
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var firstStart = true
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                if (firstStart) {
                    firstStart = false // 冷启动首刷由 appState.init() 负责
                } else {
                    appState.refreshVehicleStatus(isQuick = true, showLoading = false)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    val selectedVehicle by appState.selectedVehicle.collectAsState()
    val isLoading by appState.isLoading.collectAsState()
    val errorMessage by appState.errorMessage.collectAsState()
    val commandResult by appState.commandResult.collectAsState()
    val bleConnectionState by appState.bleConnectionState.collectAsState()
    val bleFilteredRssi by appState.bleFilteredRssi.collectAsState()
    val bleLogs by appState.bleLogs.collectAsState()
    val scannedDevices by appState.scannedDevices.collectAsState()
    val isScanningAll by appState.isScanningAll.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showACControl by remember { mutableStateOf(false) }
    var showBleSettings by remember { mutableStateOf(false) }
    // 「车辆能耗」二级页面（从我的页面进入，全屏覆盖内容区）
    var showEnergyPage by remember { mutableStateOf(false) }

    var showBluetoothPermissionDialog by remember { mutableStateOf(false) }
    var showBluetoothDeniedDialog by remember { mutableStateOf(false) }
    var showNotificationPermissionDialog by remember { mutableStateOf(false) }
    var showNotificationDeniedDialog by remember { mutableStateOf(false) }
    var showLocationServiceDialog by remember { mutableStateOf(false) }

    fun checkBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_SCAN
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.BLUETOOTH_CONNECT
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    val colorScheme = MaterialTheme.colorScheme

    val tabs = listOf(
        TabItem("车辆", Icons.Filled.Place, Icons.Outlined.Place),
        TabItem("详情", Icons.Filled.Article, Icons.Outlined.Article),
        TabItem("位置", Icons.Filled.Place, Icons.Outlined.Place),
        TabItem("我的", Icons.Filled.Person, Icons.Outlined.Person)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (useCustomBackground && backgroundImagePath != null) {
            val file = File(backgroundImagePath)
            if (file.exists()) {
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (backgroundBlur > 0f) Modifier.blur(backgroundBlur.dp) else Modifier),
                    contentScale = ContentScale.Crop
                )
                if (backgroundDimEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                    )
                }
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            // Android 15/16 起 targetSdk 35+ 强制 edge-to-edge 且无法关闭：
            // 使用 safeDrawing 让内容避开状态栏、导航栏与挖孔屏区域
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = if (useCustomBackground && backgroundImagePath != null) Color.Transparent else colorScheme.background,
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.navigationBarsPadding(),
                    containerColor = colorScheme.surface.copy(alpha = 0.95f)
                ) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == index) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title
                                )
                            },
                            label = { Text(tab.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colorScheme.primary,
                                selectedTextColor = colorScheme.primary,
                                unselectedIconColor = colorScheme.onSurfaceVariant,
                                unselectedTextColor = colorScheme.onSurfaceVariant,
                                indicatorColor = colorScheme.primary.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        ) { paddingValues ->
            if (showEnergyPage) {
                // 车辆能耗二级页面（返回时回到「我的」）
                EnergyScreen(
                    modifier = Modifier.padding(paddingValues),
                    vehicle = selectedVehicle,
                    onBack = { showEnergyPage = false }
                )
            } else when (selectedTab) {
                0 -> HomeScreen(
                    modifier = Modifier.padding(paddingValues),
                    vehicle = selectedVehicle,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    commandResult = commandResult,
                    onRefresh = { appState.refreshVehicleStatus() },
                    onCommand = { command ->
                        if (command == com.open.wuling.data.model.ControlCommand.CLIMATE_ON ||
                            command == com.open.wuling.data.model.ControlCommand.CLIMATE_OFF) {
                            showACControl = true
                        } else {
                            appState.executeCommand(command)
                        }
                    },
                    onClearError = { appState.clearError() },
                    onOpenBleSettings = {
                        val hasBluetoothPermission = checkBluetoothPermission()
                        val hasNotificationPermission = checkNotificationPermission()
                        
                        // ColorOS 上 BLE 扫描依赖定位服务，未开启会扫描不到车机
                        val locationServiceEnabled = com.open.wuling.oem.OemCompat.isLocationEnabled(context)

                        when {
                            !hasBluetoothPermission -> showBluetoothPermissionDialog = true
                            !hasNotificationPermission -> showNotificationPermissionDialog = true
                            !locationServiceEnabled -> showLocationServiceDialog = true
                            else -> showBleSettings = true
                        }
                    },
                    bleConnectionState = bleConnectionState,
                    onToggleBleConnection = { appState.toggleBleConnection() },
                    bleFilteredRssi = bleFilteredRssi
                )
                1 -> DetailScreen(
                    modifier = Modifier.padding(paddingValues),
                    vehicle = selectedVehicle,
                    onRefresh = { appState.refreshVehicleStatus() },
                    onQuickRefresh = { appState.refreshVehicleStatus(isQuick = true, showLoading = false) }
                )
                2 -> LocationScreen(
                    modifier = Modifier.padding(paddingValues),
                    vehicle = selectedVehicle
                )
                3 -> ProfileScreen(
                    modifier = Modifier.padding(paddingValues),
                    onNavigateToEnergy = { showEnergyPage = true }
                )
            }
        }
    }

    ACControlSheet(
        isOpen = showACControl,
        currentTemp = 24,
        currentFanLevel = 3,
        onClose = { showACControl = false },
        onQuickCool = {
            appState.executeQuickCool()
            showACControl = false
        },
        onQuickHeat = {
            appState.executeQuickHeat()
            showACControl = false
        },
        onCustomControl = { temperature, fanLevel, turnOn ->
            appState.executeCustomClimateCommand(temperature, fanLevel, turnOn)
            showACControl = false
        }
    )

    BleAutoLockSheet(
        isOpen = showBleSettings,
        preferences = bleAutoLockPreferences,
        bleManager = viewModel.appState.bleAutoLockManager,
        connectionState = bleConnectionState,
        logs = bleLogs,
        onClearLogs = { appState.clearBleLogs() },
        onCopyLogs = {
            val clipboard = android.content.Context.CLIPBOARD_SERVICE
            val manager = context.getSystemService(clipboard) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("蓝牙日志", bleLogs.joinToString("\n"))
            manager.setPrimaryClip(clip)
            android.widget.Toast.makeText(context, "日志已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
        },
        scannedDevices = scannedDevices,
        isScanningAll = isScanningAll,
        onStartScanAll = { appState.startScanAllDevices() },
        onStopScanAll = { appState.stopScanAllDevices() },
        onClearScannedDevices = { appState.clearScannedDevices() },
        onClose = { showBleSettings = false }
    )

    PermissionRequestDialog(
        showDialog = showBluetoothPermissionDialog,
        onDismiss = { showBluetoothPermissionDialog = false },
        onGrant = {
            showBluetoothPermissionDialog = false
            onRequestPermissions(PermissionType.BLUETOOTH) { granted ->
                if (granted) {
                    showNotificationPermissionDialog = true
                } else {
                    showBluetoothDeniedDialog = true
                }
            }
        },
        title = "蓝牙权限",
        description = "需要蓝牙权限来扫描和连接车辆蓝牙设备，实现无感控车功能。",
        icon = Icons.Default.Bluetooth
    )

    PermissionDeniedDialog(
        showDialog = showBluetoothDeniedDialog,
        onDismiss = { showBluetoothDeniedDialog = false },
        onOpenSettings = {
            showBluetoothDeniedDialog = false
            openAppSettings(context)
        },
        title = "权限被拒绝",
        description = "蓝牙权限被拒绝，无法使用无感控车功能。请前往设置页面开启权限。"
    )

    PermissionRequestDialog(
        showDialog = showNotificationPermissionDialog,
        onDismiss = {
            showNotificationPermissionDialog = false
            showBleSettings = true
        },
        onGrant = {
            showNotificationPermissionDialog = false
            onRequestPermissions(PermissionType.NOTIFICATION) {
                showBleSettings = true
            }
        },
        title = "通知权限",
        description = "需要通知权限来推送无感控车的操作状态和提醒。",
        icon = Icons.Default.Notifications
    )

    PermissionDeniedDialog(
        showDialog = showNotificationDeniedDialog,
        onDismiss = {
            showNotificationDeniedDialog = false
            showBleSettings = true
        },
        onOpenSettings = {
            showNotificationDeniedDialog = false
            openAppSettings(context)
        },
        title = "权限被拒绝",
        description = "通知权限被拒绝，无法接收无感控车的通知。请前往设置页面开启权限。"
    )

    // 定位服务未开启（ColorOS 扫描 BLE 的前置条件）
    PermissionRequestDialog(
        showDialog = showLocationServiceDialog,
        onDismiss = {
            showLocationServiceDialog = false
            showBleSettings = true
        },
        onGrant = {
            showLocationServiceDialog = false
            com.open.wuling.oem.OemCompat.openLocationSettings(context)
        },
        title = "定位服务未开启",
        description = "OPPO/ColorOS 在扫描车机蓝牙时要求开启定位服务，关闭状态会扫描不到车辆。请开启后再试。",
        icon = Icons.Default.Place
    )

    // 版本更新弹窗（自动检测 / 手动检查触发）
    UpdateDialog(appState = appState)
}
