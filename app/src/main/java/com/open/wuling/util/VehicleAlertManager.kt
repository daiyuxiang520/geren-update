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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * 「离车提醒」检测器（v61）。
 *
 * 数据流：AppState 每次车辆状态刷新成功后回调 [onStatusRefreshed]，这里依据用户开关
 * 检测三类异常——**车窗未关 / 车门未锁 / 后备箱未关**——并发系统通知；状态恢复正常时
 * 自动撤回通知。
 *
 * 防骚扰策略：
 * - 异常持续存在期间**只发一次**（以异常清单字符串为指纹，指纹不变不重发）；
 * - 任一项恢复、或出现新的异常项 → 视为指纹变化，重新发一条（内容为当前完整清单）；
 * - 全部恢复 → 撤回通知并清空指纹。
 * App 重启后指纹清零：若异常仍在，会再提醒一次（间隔可能隔天，重发是合理的）。
 *
 * 注意：本类不判断蓝牙离开事件（v61 未做 B 方案），触发频率完全跟随 App 前台刷新节奏；
 * 桌面小组件的异常角标走独立链路（WidgetProvider 直接读缓存渲染），不经此处。
 */
object VehicleAlertManager {

    private const val CHANNEL_ID = "vehicle_alert_channel"
    private const val NOTIFICATION_ID = 2001

    /** 通知点击后进入主页 */
    private const val IMPORTANCE = NotificationManager.IMPORTANCE_DEFAULT

    @Volatile
    private var appContext: Context? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 开关的内存缓存（Flow 异步同步，检测路径零挂起） */
    @Volatile private var enabled: Boolean = true
    @Volatile private var alertWindows: Boolean = true
    @Volatile private var alertDoors: Boolean = true
    @Volatile private var alertTrunk: Boolean = true

    /** 当前已通知的异常清单指纹（null = 无活跃告警） */
    private var activeFingerprint: String? = null

    /** 由 Application.onCreate 注入；随后创建通知渠道并开始同步开关缓存 */
    fun attachContext(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        createChannel()

        val prefs = VehicleAlertPreferences(context)
        prefs.enabledFlow.onEach { enabled = it }.launchIn(scope)
        prefs.windowsFlow.onEach { alertWindows = it }.launchIn(scope)
        prefs.doorsFlow.onEach { alertDoors = it }.launchIn(scope)
        prefs.trunkFlow.onEach { alertTrunk = it }.launchIn(scope)
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
        val ctx = appContext ?: return
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
            notifyAlerts(ctx, alerts)
        }
    }

    private fun notifyAlerts(ctx: Context, alerts: List<String>) {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            ctx, 2001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = alerts.joinToString("\n")
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("车辆安全提醒")
            .setContentText(alerts.first() + if (alerts.size > 1) " 等 ${alerts.size} 项" else "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // 通知权限未授予：静默跳过（详情页提醒条仍会显示）
        }
    }

    private fun createChannel() {
        val ctx = appContext ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "离车提醒",
                IMPORTANCE
            ).apply {
                description = "车窗未关、车门未锁、后备箱未关等安全状态提醒"
                enableVibration(true)
            }
            ctx.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
