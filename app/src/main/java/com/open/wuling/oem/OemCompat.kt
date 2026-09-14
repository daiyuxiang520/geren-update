package com.open.wuling.oem

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * 国产 ROM 兼容层（重点适配 OPPO / 一加 / realme 的 ColorOS 系）。
 *
 * 适配思路：
 * 1. 只用公开 API 做状态检测，不碰 SystemProperties 等非 SDK 接口
 *    （Android 16 收紧了隐藏 API 访问，反射 getSystemProperty 存在风险）。
 * 2. 跳转设置页采用「多候选 + 兜底」策略：ColorOS 各版本页面类名差异很大，
 *    逐个尝试，全部失败则回落到系统标准设置页。
 */
object OemCompat {

    private const val TAG = "OemCompat"

    private val OPPO_BRANDS = setOf("oppo", "realme", "oneplus", "oplus")

    // ── 厂商识别 ────────────────────────────────────────────────

    /** OPPO 系（含一加、realme，共用 ColorOS / OPLUS 底层） */
    fun isOppoFamily(): Boolean {
        val brand = Build.BRAND.orEmpty().lowercase()
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        return brand in OPPO_BRANDS || manufacturer in OPPO_BRANDS || hasColorOsFingerprint()
    }

    private fun hasColorOsFingerprint(): Boolean {
        val fingerprint = Build.FINGERPRINT.orEmpty().lowercase()
        val display = Build.DISPLAY.orEmpty().lowercase()
        return fingerprint.contains("coloros") || fingerprint.contains("oplus")
                || display.contains("coloros") || display.contains("oplus")
    }

    /** 用于界面展示的 ROM 名称 */
    fun romDisplayName(): String = when {
        Build.BRAND.orEmpty().equals("realme", ignoreCase = true) -> "realme UI"
        Build.BRAND.orEmpty().equals("oneplus", ignoreCase = true) -> "ColorOS (一加)"
        isOppoFamily() -> "ColorOS"
        else -> "Android"
    }

    // ── 状态检测 ────────────────────────────────────────────────

    fun hasBluetoothPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(context, Manifest.permission.BLUETOOTH_SCAN) &&
                    hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun hasLocationPermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasNotificationPermission(context: Context): Boolean {
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return enabled
        return enabled && hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    }

    /** 指定通知渠道是否被用户关闭（ColorOS 可单独关闭渠道） */
    fun isNotificationChannelEnabled(context: Context, channelId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return true
        val channel = manager.getNotificationChannel(channelId) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** 定位开关是否打开 —— ColorOS 上 BLE 扫描强依赖位置服务，未开启会扫描不到车机 */
    fun isLocationEnabled(context: Context): Boolean {
        val manager = context.getSystemService(LocationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    /** 是否已加入电池优化白名单（ColorOS 上称为「允许后台高耗电」） */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val manager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return runCatching { manager.isIgnoringBatteryOptimizations(context.packageName) }
            .getOrDefault(false)
    }

    // ── 设置页跳转 ──────────────────────────────────────────────

    /**
     * 自启动管理（ColorOS 关键项）：未开启时进程被杀后无法自动恢复控车服务。
     */
    fun openAutoStartSettings(context: Context): Boolean {
        return startFirst(
            context,
            listOf(
                component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                component("com.coloros.safecenter", "com.coloros.privacypermissionsentry.PermissionTopActivity"),
                component("com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity"),
                component("com.coloros.oppoguardelf", "com.coloros.powermanager.startup.StartupAppListActivity"),
                component("com.coloros.phonemanager", "com.coloros.phonemanager.startupapp.StartupAppListActivity"),
                component("com.coloros.safecenter", "com.coloros.startupmanager.startup.StartupAppListActivity")
            )
        ) || openAppDetails(context)
    }

    /**
     * 电池 / 省电优化：优先直接请求加入白名单，失败再进 OEM 页面与系统页面。
     */
    fun openBatterySettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isIgnoringBatteryOptimizations(context).not()) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            if (startSafe(context, intent)) return true
        }
        return startFirst(
            context,
            listOf(
                component("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerConsumptionActivity"),
                component("com.oplus.powermanager", "com.oplus.powermanager.fuelgaue.PowerConsumptionActivity"),
                component("com.coloros.powermanager", "com.coloros.powermanager.PowerManagerActivity"),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            )
        ) || openAppDetails(context)
    }

    /** 后台运行 / 应用速冻（ColorOS 会冻结长时间后台的应用） */
    fun openBackgroundRunSettings(context: Context): Boolean {
        return startFirst(
            context,
            listOf(
                component("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerConsumptionActivity"),
                component("com.oplus.powermanager", "com.oplus.powermanager.fuelgaue.PowerConsumptionActivity"),
                Intent(Settings.ACTION_APPLICATION_SETTINGS)
            )
        ) || openAppDetails(context)
    }

    /** 通知设置（含通知类别 / 渠道） */
    fun openNotificationSettings(context: Context): Boolean {
        val intents = mutableListOf<Intent>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intents += Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            intents += Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, BLE_CHANNEL_ID)
        }
        intents += component(
            "com.coloros.notificationmanager",
            "com.coloros.notificationmanager.NotificationCenterActivity"
        )
        intents += Intent(Settings.ACTION_APPLICATION_SETTINGS)
        return startFirst(context, intents) || openAppDetails(context)
    }

    /** 定位设置（ColorOS 蓝牙扫描依赖） */
    fun openLocationSettings(context: Context): Boolean {
        return startFirst(context, listOf(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                || openAppDetails(context)
    }

    /** 蓝牙设置 */
    fun openBluetoothSettings(context: Context): Boolean {
        return startFirst(context, listOf(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)))
                || openAppDetails(context)
    }

    /** 系统应用详情页（最终兜底） */
    fun openAppDetails(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        return startFirst(context, listOf(intent))
    }

    // ── 内部工具 ────────────────────────────────────────────────

    const val BLE_CHANNEL_ID = "ble_keep_alive_channel"

    private fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun component(pkg: String, cls: String): Intent =
        Intent().setComponent(ComponentName(pkg, cls))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun startSafe(context: Context, intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.d(TAG, "start failed: $intent -> ${e.message}")
            false
        }
    }

    /** 依次尝试候选 Intent，返回是否成功启动 */
    private fun startFirst(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            if (startSafe(context, intent)) return true
        }
        return false
    }
}
