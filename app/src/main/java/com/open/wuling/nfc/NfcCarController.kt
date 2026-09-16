package com.open.wuling.nfc

import android.content.Context
import com.open.wuling.analytics.UmengAnalytics
import com.open.wuling.data.api.APIConfig
import com.open.wuling.data.local.NfcTagPreferences
import com.open.wuling.data.repository.VehicleRepository
import com.open.wuling.data.store.TokenStore
import com.open.wuling.util.AppLogger
import com.open.wuling.util.VehicleAlertManager
import com.open.wuling.widget.VehicleStatusWidgetProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** NFC 切换执行结果 */
sealed class NfcOutcome {
    /** @param unlocked true=本次执行的是解锁；false=本次执行的是锁车 */
    data class Success(val unlocked: Boolean, val source: String) : NfcOutcome()
    data class Failure(val message: String) : NfcOutcome()
}

/**
 * NFC 车控执行器（v67）。
 *
 * [NfcTriggerActivity]（碰标签）与设置页的「模拟切换」共用这一条执行链路：
 * 恢复 Token → 决策当前锁车状态 → 反向下发门锁指令 → 持久化结果 → 同步桌面。
 *
 * 锁车状态决策优先级（用户规则：锁车状态碰=解锁，解锁状态碰=锁车）：
 * 1. 实时拉取车辆状态（2.5s 超时，最贴近真实，避免实体钥匙动过后方向反了）；
 * 2. 桌面小组件缓存（App 内每 30s 刷新成功后落盘）；
 * 3. 本机上次 NFC 动作（lastAction）；
 * 4. 兜底视为「已锁车」→ 首碰即解锁（走近车开门是最常见场景）。
 *
 * 指令通道与通知按钮/小组件一致（云端 /car/control/doorLock），
 * 好处是有明确的成功/失败回执；蓝牙直发是 fire-and-forget，无法可靠回显，故不采用。
 *
 * 同时承载「绑定标签」请求状态（StateFlow 驱动，设置页响应式跟随）：
 * [startBinding] 置位 → 系统 NDEF 派发拉起 NfcTriggerActivity →
 * [consumeBindingRequest] 取走密钥走「写标签」分支 → [onBindSuccess] 收尾。
 */
@Singleton
class NfcCarController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: VehicleRepository,
    private val tokenStore: TokenStore,
    val prefs: NfcTagPreferences
) {
    private val _enabled = MutableStateFlow(prefs.enabled)
    val enabledFlow: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _bound = MutableStateFlow(prefs.isBound)
    val boundFlow: StateFlow<Boolean> = _bound.asStateFlow()

    private val _lastAction = MutableStateFlow(prefs.lastAction)
    val lastActionFlow: StateFlow<String?> = _lastAction.asStateFlow()

    private val _bindingPending = MutableStateFlow(false)
    val bindingPendingFlow: StateFlow<Boolean> = _bindingPending.asStateFlow()

    /** 本次绑定要写入标签的随机密钥（startBinding 时生成） */
    var bindingSecret: String = ""
        private set

    fun setEnabled(value: Boolean) {
        prefs.enabled = value
        _enabled.value = value
    }

    /** 进入绑定模式：生成新密钥，下一个被碰到的标签会被写入该密钥 */
    fun startBinding() {
        bindingSecret = UUID.randomUUID().toString().replace("-", "").take(16)
        _bindingPending.value = true
    }

    fun cancelBinding() {
        _bindingPending.value = false
    }

    /**
     * NfcTriggerActivity 专用：取走待写密钥并结束绑定模式（单次有效，
     * 写失败需在设置页重新点「绑定」再试，避免绑定态悄悄滞留误写后续标签）。
     */
    fun consumeBindingRequest(): String? {
        if (!_bindingPending.value) return null
        _bindingPending.value = false
        return bindingSecret.takeIf { it.isNotBlank() }
    }

    /** 绑定写入成功后调用：保存密钥并自动启用 */
    fun onBindSuccess(secret: String) {
        prefs.boundSecret = secret
        prefs.enabled = true
        _enabled.value = true
        _bound.value = true
    }

    fun unbind() {
        prefs.unbind()
        _bound.value = false
        _lastAction.value = null
    }

    /**
     * 执行一次「碰标签」等价的切换：当前已锁 → 解锁；当前已解锁 → 锁车。
     * 挂起函数，调用方自行决定展示方式（Activity 全屏结果页 / 设置页 Toast）。
     */
    suspend fun executeToggle(): NfcOutcome {
        // 1. Token 恢复：进程被杀后 APIConfig 内存态为空，先从 DataStore 补
        if (!APIConfig.isConfigured) {
            val token = runCatching { tokenStore.getToken() }.getOrDefault("")
            if (token.isBlank()) return NfcOutcome.Failure("请先在 App 内配置 Access Token")
            APIConfig.setAccessToken(token)
        }

        // 2. VIN：与桌面小组件同一份落盘缓存
        val vin = readVin()
        if (vin.isBlank()) return NfcOutcome.Failure("车辆信息未就绪，请先打开 App 刷新一次")

        // 3. 决策当前锁车状态（见类注释的优先级）
        var source = "live"
        var currentLocked: Boolean? = withTimeoutOrNull(2500) {
            repository.fetchDefaultVehicleStatusQuick().getOrNull()?.status?.isLocked
        }
        if (currentLocked == null) {
            currentLocked = readCachedLocked()
            source = "cache"
        }
        if (currentLocked == null) {
            currentLocked = prefs.lastAction?.let { it == ACTION_LOCK }
            source = "last_action"
        }
        if (currentLocked == null) {
            currentLocked = true
            source = "default"
        }
        val locked = currentLocked
        val action = if (locked) ACTION_UNLOCK else ACTION_LOCK
        val target = if (locked) 0 else 1  // 门锁指令：0=解锁 1=锁车

        // 4. 下发指令
        val result = repository.controlDoorLock(vin, target)
        return result.fold(
            onSuccess = {
                prefs.lastAction = action
                _lastAction.value = action
                // 锁车成功就撤回「离车提醒」，避免已经锁了还挂着告警（与通知按钮行为一致）
                if (!locked) VehicleAlertManager.dismissAlerts(context)
                // 指令已下发，让小组件尽快拉一次最新状态
                runCatching { VehicleStatusWidgetProvider.requestRefresh(context) }
                UmengAnalytics.event(
                    context, "nfc_toggle",
                    mapOf("action" to action, "result" to "success", "source" to source)
                )
                AppLogger.i("NFC", "toggle[$source] locked=$locked -> $action OK")
                NfcOutcome.Success(unlocked = locked, source = source)
            },
            onFailure = { e ->
                UmengAnalytics.event(
                    context, "nfc_toggle",
                    mapOf("action" to action, "result" to "fail", "reason" to (e.message ?: "").take(80))
                )
                AppLogger.w("NFC", "toggle[$source] locked=$locked -> $action FAIL: ${e.message}")
                NfcOutcome.Failure(e.message ?: "操作失败")
            }
        )
    }

    private fun readVin(): String = try {
        val raw = context.getSharedPreferences("vehicle_cache", Context.MODE_PRIVATE)
            .getString("last_vehicle", null).orEmpty()
        if (raw.isBlank()) "" else JSONObject(raw).optString("vin", "")
    } catch (e: Exception) {
        ""
    }

    /** 小组件缓存里的最近已知锁车状态；无缓存/字段缺失返回 null */
    private fun readCachedLocked(): Boolean? = try {
        val raw = context.getSharedPreferences("widget_vehicle_status", Context.MODE_PRIVATE)
            .getString("data", null).orEmpty()
        if (raw.isBlank()) null else {
            val json = JSONObject(raw)
            if (json.has("locked")) json.getBoolean("locked") else null
        }
    } catch (e: Exception) {
        null
    }

    companion object {
        const val ACTION_UNLOCK = "unlock"
        const val ACTION_LOCK = "lock"

        /** 标签内 NDEF 记录的自定义 MIME 类型，与 Manifest 的 intent-filter 保持一致 */
        const val MIME_TYPE = "application/vnd.com.open.wuling.nfc"
    }
}
