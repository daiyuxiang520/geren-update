package com.open.wuling.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.open.wuling.MainActivity
import com.open.wuling.R
import com.open.wuling.data.local.VehicleAlertPreferences
import com.open.wuling.data.model.Vehicle
import com.open.wuling.data.model.VehicleStatus
import com.open.wuling.receiver.VehicleActionReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * 车辆状态提醒检测器（v61 创建，v63 扩展）。
 *
 * 数据流：AppState 每次车辆状态刷新成功后回调 [onStatusRefreshed]，这里依据用户开关检测异常。
 *
 * 一、安全提醒（离车提醒）：车窗未关 / 车门未锁 / 后备箱未关 → 通知可一键处理。
 * 二、充电与电量提醒（v63）：开始充电 / 已充满 / 充电中断 / 电量偏低 → 边沿触发。
 *
 * 防骚扰策略（两类共用）：
 * - 异常持续存在期间**只发一次**（以事件指纹为准，指纹不变不重发）；
 * - 状态变化产生新指纹 → 重新发一条（内容为当前情况）；
 * - 全部恢复 → 撤回通知并清空指纹。
 * App 重启后指纹清零：若异常仍在，会再提醒一次（间隔可能隔天，重发是合理的）。
 *
 * 注意：本类不判断蓝牙离开事件，触发频率完全跟随 App 前台刷新节奏；
 * 桌面小组件的异常角标走独立链路（WidgetProvider 直接读缓存渲染），不经此处。
 */
object VehicleAlertManager {

    private const val CHANNEL_ID = "vehicle_alert_channel"
    private const val CHANNEL_ID_CHARGE = "vehicle_charge_channel"
    private const val NOTIFICATION_ID = 2001
    private const val NOTIFICATION_ID_CHARGE = 2002

    private const val IMPORTANCE = NotificationManager.IMPORTANCE_DEFAULT

    @Volatile
    private var appContext: Context? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 开关的内存缓存（Flow 异步同步，检测路径零挂起） */
    @Volatile private var enabled: Boolean = true
    @Volatile private var alertWindows: Boolean = true
    @Volatile private var alertDoors: Boolean = true
    @Volatile private var alertTrunk: Boolean = true
    @Volatile private var alertCharge: Boolean = true
    @Volatile private var alertLowBattery: Boolean = true
    @Volatile private var lowBatteryThreshold: Int = 20

    /** 当前已通知的安全异常清单指纹（null = 无活跃告警） */
    private var activeFingerprint: String? = null

    /** 当前已通知的充电/电量事件指纹（null = 无活跃告警） */
    private var chargeFingerprint: String? = null

    /** 上一次的充电状态（边沿检测用，null = 尚未观测到） */
    @Volatile private var lastCharging: Boolean? = null

    /** 上一次电量是否高于低电量阈值（低电量只在「跌破」时提醒一次） */
    @Volatile private var lastSocAboveThreshold: Boolean? = null

    /** 由 Application.onCreate 注入；随后创建通知渠道并开始同步开关缓存 */
    fun attachContext(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        createChannels()

        val prefs = VehicleAlertPreferences(context)
        prefs.enabledFlow.onEach { enabled = it }.launchIn(scope)
        prefs.windowsFlow.onEach { alertWindows = it }.launchIn(scope)
        prefs.doorsFlow.onEach { alertDoors = it }.launchIn(scope)
        prefs.trunkFlow.onEach { alertTrunk = it }.launchIn(scope)
        prefs.chargeFlow.onEach { alertCharge = it }.launchIn(scope)
        prefs.lowBatteryFlow.onEach { alertLowBattery = it }.launchIn(scope)
        prefs.lowBatteryThresholdFlow.onEach { lowBatteryThreshold = it }.launchIn(scope)
    }

    /**
     * 纯检测函数：给定状态与开关，返回异常清单（人话描述，如「车窗未关（左前/右后）」）。
     * 通知与详情页提醒条共用，保证两处口径一致。
     *
     * 车窗判定沿用 v53 口径：状态位与开度任一表明打开即算未关。
     */
    fun detectAlerts(
        s: VehicleStatus,
        windows: Boolean,
        doors: Boolean,
        trunk: Boolean
    ): List<String> {
        val alerts = mutableListOf<String>()
        if (windows) {
            val open = mutableListOf<String>()
            if (s.windows.frontLeft || s.window1OpenDegree > 0) open.add("左前")
            if (s.windows.frontRight || s.window2OpenDegree > 0) open.add("右前")
            if (s.windows.rearLeft || s.window3OpenDegree > 0) open.add("左后")
            if (s.windows.rearRight || s.window4OpenDegree > 0) open.add("右后")
            if (open.isNotEmpty()) alerts.add("车窗未关（${open.joinToString("/")}）")
        }
        if (doors && !s.isLocked) {
            alerts.add("车门未锁")
        }
        if (trunk && s.doors.trunk) {
            alerts.add("后备箱未关")
        }
        return alerts
    }

    /** AppState 每次刷新成功后调用（含快速刷新） */
    fun onStatusRefreshed(vehicle: Vehicle) {
        checkSafetyAlerts(vehicle)
        checkChargeAlerts(vehicle)
    }

    /** 外部（通知按钮执行成功后）主动撤回安全提醒 */
    fun dismissAlerts(context: Context?) {
        val ctx = context ?: appContext ?: return
        synchronized(this) {
            activeFingerprint = null
        }
        runCatching { NotificationManagerCompat.from(ctx).cancel(NOTIFICATION_ID) }
    }

    // ===================== 一、安全提醒 =====================

    private fun checkSafetyAlerts(vehicle: Vehicle) {
        val ctx = appContext ?: return
        if (!enabled) {
            // 总开关关闭：撤掉可能存在的旧通知，避免「关了开关还挂着通知」
            dismissAlerts(ctx)
            return
        }
        val alerts = detectAlerts(vehicle.status, alertWindows, alertDoors, alertTrunk)

        synchronized(this) {
            if (alerts.isEmpty()) {
                // 全部恢复 → 撤回通知
                if (activeFingerprint != null) {
                    activeFingerprint = null
                    runCatching { NotificationManagerCompat.from(ctx).cancel(NOTIFICATION_ID) }
                }
                return
            }

            val fingerprint = alerts.joinToString("\n")
            if (fingerprint == activeFingerprint) return  // 异常持续，不重复打扰

            activeFingerprint = fingerprint
            notifyAlerts(ctx, vehicle, alerts)
        }
    }

    private fun notifyAlerts(ctx: Context, vehicle: Vehicle, alerts: List<String>) {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            ctx, 2001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = alerts.joinToString("\n")
        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("车辆安全提醒")
            .setContentText(alerts.first() + if (alerts.size > 1) " 等 ${alerts.size} 项" else "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // v63：能远程处理的问题直接给按钮，别让用户自己再去翻 App
        val vin = vehicle.vin
        if (vin.isNotEmpty()) {
            if (alerts.any { it.contains("车门未锁") }) {
                builder.addAction(
                    R.drawable.ic_action_lock, "立即锁车",
                    VehicleActionReceiver.createPendingIntent(
                        ctx, VehicleActionReceiver.CMD_LOCK, vin, 9101
                    )
                )
            }
            if (alerts.any { it.contains("车窗未关") }) {
                builder.addAction(
                    R.drawable.ic_action_window, "一键关窗",
                    VehicleActionReceiver.createPendingIntent(
                        ctx, VehicleActionReceiver.CMD_CLOSE_WINDOW, vin, 9102
                    )
                )
            }
        }

        try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // 通知权限未授予：静默跳过（详情页提醒条仍会显示）
        }
    }

    // ===================== 二、充电与电量提醒（v63） =====================

    /**
     * 充电事件边沿检测。
     *
     * 只认「状态跳变」：开始充 / 到阈值 / 停了（充满或中断）/ 跌破低电量线。
     * 稳态期间（一直在充电且没到阈值）不产生任何通知，避免每 30 秒弹一次。
     */
    private fun checkChargeAlerts(vehicle: Vehicle) {
        val ctx = appContext ?: return
        val s = vehicle.status
        val soc = s.batteryLevel
        val charging = s.isCharging
        val wasCharging = lastCharging
        val aboveBefore = lastSocAboveThreshold

        lastCharging = charging
        lastSocAboveThreshold = soc > lowBatteryThreshold

        val socValid = soc > 0
        if (!socValid && !charging) return

        if (!alertCharge && !alertLowBattery) {
            clearChargeNotification(ctx)
            return
        }

        val full = VehicleAlertPreferences.FULL_BATTERY_THRESHOLD

        val event: Pair<String, Pair<String, String>>? = when {
            // 开始充电
            alertCharge && charging && wasCharging == false ->
                "start" to ("开始充电" to "当前电量 $soc%")
            // 充电中达到「已充满」阈值
            alertCharge && charging && soc >= full ->
                "full" to ("电量已充满" to "当前电量 $soc%，可以拔枪了")
            // 停止充电：达到阈值算完成，否则算中断
            alertCharge && wasCharging == true && !charging && socValid ->
                if (soc >= full) "done" to ("充电完成" to "最终电量 $soc%")
                else "interrupt" to ("充电中断" to "当前电量 $soc%，未充满就停止了")
            // 跌破低电量线（仅在「之前还在阈值之上」时提醒一次）
            alertLowBattery && !charging && socValid && soc <= lowBatteryThreshold && aboveBefore == true ->
                "low$lowBatteryThreshold" to ("电量偏低" to "当前电量 $soc%，建议及时补能")
            else -> null
        }

        if (event == null) {
            // 无事件：如果低电量已回升，撤掉旧的低电量通知
            if (chargeFingerprint?.startsWith("low") == true && soc > lowBatteryThreshold) {
                clearChargeNotification(ctx)
            }
            return
        }

        val (fingerprint, titleText) = event
        if (fingerprint == chargeFingerprint) return  // 同一事件不重复

        chargeFingerprint = fingerprint
        notifyCharge(ctx, titleText.first, titleText.second)
    }

    private fun notifyCharge(ctx: Context, title: String, text: String) {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            ctx, 2002, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID_CHARGE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID_CHARGE, notification)
        } catch (_: SecurityException) {
            // 通知权限未授予：静默跳过
        }
    }

    private fun clearChargeNotification(ctx: Context) {
        chargeFingerprint = null
        runCatching { NotificationManagerCompat.from(ctx).cancel(NOTIFICATION_ID_CHARGE) }
    }

    private fun createChannels() {
        val ctx = appContext ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val manager = ctx.getSystemService(NotificationManager::class.java)

            val safety = NotificationChannel(CHANNEL_ID, "离车提醒", IMPORTANCE).apply {
                description = "车窗未关、车门未锁、后备箱未关等安全状态提醒"
                enableVibration(true)
            }
            val charge = NotificationChannel(CHANNEL_ID_CHARGE, "充电与电量", IMPORTANCE).apply {
                description = "开始充电、已充满、充电中断、电量偏低提醒"
                enableVibration(true)
            }
            manager.createNotificationChannel(safety)
            manager.createNotificationChannel(charge)
        }
    }
}
