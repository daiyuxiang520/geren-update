package com.open.wuling.util

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.open.wuling.analytics.UmengAnalytics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 应用内日志管理器
 * 用于收集和展示 API 请求/响应日志
 *
 * 档位1 增强：ERROR/WARN 会**同时上报到友盟「错误分析」**（若友盟已初始化）。
 * 为避免刷屏与超频，做了按键去重 + 时间窗限流（见 [reportToUmeng]）。
 */
object AppLogger {
    private const val MAX_LOGS = 200
    private val logs = ConcurrentLinkedQueue<LogEntry>()
    private val enabled = AtomicBoolean(true)

    /** 应用上下文（由 Application 注入；未注入时不上报，仅本地记录） */
    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var appContext: Context? = null

    /** 是否把 ERROR/WARN 上报友盟（可动态关闭） */
    private val uploadEnabled = AtomicBoolean(true)

    /** 去重：同一 tag+message 的最近上报时间戳 */
    private val lastReportedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** 时间窗内已上报条数（简化限流，防崩溃循环刷屏） */
    private val reportCount = AtomicLong(0)
    private const val REPORT_WINDOW_MS = 10_000L
    private const val REPORT_MAX_PER_WINDOW = 20L

    @Volatile
    private var windowStart = System.currentTimeMillis()

    /** 由 Application.onCreate 注入上下文，之后 WARN/ERROR 才会上报友盟 */
    fun attachContext(context: Context) {
        appContext = context.applicationContext
    }

    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val level: Level,
        val tag: String,
        val message: String,
        val details: String? = null
    ) {
        val formattedTime: String
            get() = dateFormat.format(Date(timestamp))

        companion object {
            private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        }
    }

    enum class Level {
        DEBUG, INFO, WARN, ERROR
    }

    fun d(tag: String, message: String, details: String? = null) {
        if (enabled.get()) {
            addLog(Level.DEBUG, tag, message, details)
        }
        Log.d(tag, message)
    }

    fun i(tag: String, message: String, details: String? = null) {
        if (enabled.get()) {
            addLog(Level.INFO, tag, message, details)
        }
        Log.i(tag, message)
    }

    fun w(tag: String, message: String, details: String? = null) {
        if (enabled.get()) {
            addLog(Level.WARN, tag, message, details)
        }
        Log.w(tag, message)
    }

    fun e(tag: String, message: String, details: String? = null) {
        if (enabled.get()) {
            addLog(Level.ERROR, tag, message, details)
        }
        Log.e(tag, message)
    }

    private fun addLog(level: Level, tag: String, message: String, details: String?) {
        val entry = LogEntry(level = level, tag = tag, message = message, details = details)
        logs.add(entry)

        // 保持日志数量限制
        while (logs.size > MAX_LOGS) {
            logs.poll()
        }

        // ERROR/WARN 上报友盟错误分析（去重 + 限流）
        if (level == Level.ERROR || level == Level.WARN) {
            reportToUmeng(level, tag, message, details)
        }
    }

    /**
     * 上报到友盟「错误分析」。
     * - 未注入 Context / 未初始化友盟 / 上报开关关闭时静默跳过；
     * - 同一 tag+message 在 [REPORT_WINDOW_MS] 内只报一次（防重复）；
     * - 时间窗内最多 [REPORT_MAX_PER_WINDOW] 条（防崩溃循环刷屏）。
     */
    private fun reportToUmeng(level: Level, tag: String, message: String, details: String?) {
        val ctx = appContext ?: return
        if (!uploadEnabled.get()) return

        // 限流
        val now = System.currentTimeMillis()
        if (now - windowStart > REPORT_WINDOW_MS) {
            windowStart = now
            reportCount.set(0)
        }
        if (reportCount.get() >= REPORT_MAX_PER_WINDOW) return

        // 去重
        val key = "$level|$tag|$message"
        val last = lastReportedAt[key]
        if (last != null && now - last < REPORT_WINDOW_MS) return
        lastReportedAt[key] = now

        // 清理过期去重键，防 Map 无限增长
        if (lastReportedAt.size > 200) {
            val expireBefore = now - REPORT_WINDOW_MS * 6
            lastReportedAt.entries.removeIf { it.value < expireBefore }
        }

        reportCount.incrementAndGet()
        val summary = buildString {
            append("[$level][$tag] ")
            append(message)
            if (!details.isNullOrBlank()) {
                append(" | ")
                append(details.take(300))
            }
        }
        UmengAnalytics.reportError(ctx, summary)
    }

    fun getAllLogs(): List<LogEntry> = logs.toList()

    fun clear() {
        logs.clear()
    }

    fun setEnabled(enable: Boolean) {
        enabled.set(enable)
    }

    fun isEnabled(): Boolean = enabled.get()

    /** 开/关「WARN/ERROR 上报友盟」（本地日志不受影响） */
    fun setUploadEnabled(enable: Boolean) {
        uploadEnabled.set(enable)
    }

    fun isUploadEnabled(): Boolean = uploadEnabled.get()

    // API 请求日志快捷方法
    fun apiRequest(api: String, body: String?) {
        d("API", "➡️ 请求: $api", body?.take(500))
    }

    fun apiResponse(api: String, code: Int, body: String?) {
        i("API", "⬅️ 响应: $api (HTTP $code)", body?.take(500))
    }

    fun apiError(api: String, error: String) {
        e("API", "❌ 错误: $api - $error")
    }
}
