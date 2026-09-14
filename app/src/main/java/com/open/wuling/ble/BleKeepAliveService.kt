package com.open.wuling.ble

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.open.wuling.MainActivity
import com.open.wuling.R

/**
 * 无感控车保活前台服务。
 *
 * Android 16 (API 36) 适配要点：
 * 1. 后台启动前台服务会抛 ForegroundServiceStartNotAllowedException，必须捕获；
 * 2. connectedDevice 类型的 FGS 要求已授予 BLUETOOTH_CONNECT / BLUETOOTH_SCAN，
 *    否则系统会抛 SecurityException，因此启动前先校验；
 * 3. startForeground 必须带 foregroundServiceType，且与 Manifest 声明一致。
 */
class BleKeepAliveService : Service() {

    companion object {
        private const val TAG = "BleKeepAlive"
        const val CHANNEL_ID = "ble_keep_alive_channel"
        private const val NOTIFICATION_ID = 1001

        /**
         * 启动保活服务。
         * @return 是否真正发起启动（false 表示权限不足或被系统限制）
         */
        fun startService(context: Context): Boolean {
            // Android 16：connectedDevice 前台服务需要蓝牙权限，缺失会被系统拒绝
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothPermission(context)) {
                Log.w(TAG, "缺少蓝牙运行时权限，拒绝启动前台服务")
                return false
            }

            return try {
                val intent = Intent(context, BleKeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (e: Exception) {
                // Android 12+ 后台启动限制 / Android 16 更严格的 FGS 启动策略
                Log.w(TAG, "启动前台服务被系统拒绝: ${e.javaClass.simpleName}: ${e.message}")
                false
            }
        }

        fun stopService(context: Context) {
            runCatching { context.stopService(Intent(context, BleKeepAliveService::class.java)) }
        }

        fun hasBluetoothPermission(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            }
            val connect = ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            val scan = ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
            return connect || scan
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        }.onFailure {
            // 极端情况下（权限被回收）无法进入前台，直接停掉避免 ANR
            Log.w(TAG, "startForeground 失败: ${it.message}")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // ColorOS 一键清理后会走到这里，START_STICKY 有可能被系统忽略，
        // 真正的恢复依赖用户在「自启动管理」中放行。
        Log.d(TAG, "onTaskRemoved: 如需自动恢复请在系统设置中允许自启动")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "无感控车保活",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持蓝牙连接和自动解锁/上锁功能"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("无感控车已启用")
            .setContentText("保持蓝牙连接以实现自动解锁/上锁")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
