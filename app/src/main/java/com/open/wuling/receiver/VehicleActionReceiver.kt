package com.open.wuling.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.open.wuling.data.api.APIConfig
import com.open.wuling.data.repository.VehicleRepository
import com.open.wuling.data.store.TokenStore
import com.open.wuling.util.AppLogger
import com.open.wuling.util.VehicleAlertManager
import com.open.wuling.widget.VehicleStatusWidgetProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 后台车控指令执行器（v63）。
 *
 * 通知上的「立即锁车 / 一键关窗」和桌面小组件的快捷按钮都走这里：
 * 广播进来 → 必要时从 DataStore 恢复 Token → 后台发指令 → Toast 提示 → 触发小组件刷新。
 *
 * 为什么必须在 Receiver 里做：通知 Action / 小组件点击都不经过 Activity，
 * 只有 BroadcastReceiver 能拿到这次「点击事件」并执行网络请求（goAsync 争取异步时间）。
 *
 * Token 恢复的必要性：APIConfig.accessToken 是内存单例，进程被杀后为空；
 * 用户直接点通知按钮时很可能就是刚拉起的新进程，不恢复 Token 会直接失败。
 */
@AndroidEntryPoint
class VehicleActionReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: VehicleRepository
    @Inject lateinit var tokenStore: TokenStore

    companion object {
        const val ACTION_CONTROL = "com.open.wuling.action.CONTROL"
        const val EXTRA_COMMAND = "extra_command"
        const val EXTRA_VIN = "extra_vin"

        const val CMD_LOCK = "lock"
        const val CMD_CLOSE_WINDOW = "closeWindow"
        const val CMD_FIND_CAR = "findCar"

        /**
         * 构造一个点击即执行指令的 PendingIntent。
         * requestCode 需按按钮区分，否则多个按钮会复用同一个 PendingIntent。
         */
        fun createPendingIntent(
            context: Context,
            command: String,
            vin: String,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, VehicleActionReceiver::class.java).apply {
                action = ACTION_CONTROL
                putExtra(EXTRA_COMMAND, command)
                putExtra(EXTRA_VIN, vin)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getBroadcast(context, requestCode, intent, flags)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CONTROL) return
        val command = intent.getStringExtra(EXTRA_COMMAND) ?: return
        val vin = intent.getStringExtra(EXTRA_VIN) ?: return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val message = try {
                runCommand(context, command, vin)
            } catch (e: Exception) {
                AppLogger.w("Action", "执行指令 $command 异常：${e.message}")
                "操作失败：${e.message ?: "未知错误"}"
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            // 指令已下发，让小组件尽快拉一次最新状态，别等下一个刷新周期
            runCatching { VehicleStatusWidgetProvider.requestRefresh(context) }
            pendingResult.finish()
        }
    }

    private suspend fun runCommand(context: Context, command: String, vin: String): String {
        if (!APIConfig.isConfigured) {
            val token = runCatching { tokenStore.getToken() }.getOrDefault("")
            if (token.isBlank()) return "请先配置 Access Token"
            APIConfig.setAccessToken(token)
        }

        val (ok, msg) = when (command) {
            CMD_LOCK -> repository.controlDoorLock(vin, 1).toOutcome("锁车指令已发送", "锁车失败")
            CMD_CLOSE_WINDOW -> repository.controlWindow(vin, 0).toOutcome("关窗指令已发送", "关窗失败")
            CMD_FIND_CAR -> repository.searchCar(vin).toOutcome("寻车指令已发送", "寻车失败")
            else -> return "未知指令"
        }

        // 处理成功了就把对应的安全提醒撤掉，避免「已经锁了还挂着提醒」
        if (ok && (command == CMD_LOCK || command == CMD_CLOSE_WINDOW)) {
            VehicleAlertManager.dismissAlerts(context)
        }
        AppLogger.i("Action", "$command -> $msg")
        return msg
    }

    private fun <T> Result<T>.toOutcome(okMsg: String, failMsg: String): Pair<Boolean, String> =
        if (isSuccess) true to okMsg
        else false to "$failMsg：${exceptionOrNull()?.message ?: "未知错误"}"
}
