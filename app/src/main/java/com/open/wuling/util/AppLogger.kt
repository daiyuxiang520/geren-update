package com.open.wuling.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 应用内日志管理器
 * 用于收集和展示 API 请求/响应日志
 */
object AppLogger {
    private const val MAX_LOGS = 200
    private val logs = ConcurrentLinkedQueue<LogEntry>()
    private val enabled = AtomicBoolean(true)

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
    }

    fun getAllLogs(): List<LogEntry> = logs.toList()

    fun clear() {
        logs.clear()
    }

    fun setEnabled(enable: Boolean) {
        enabled.set(enable)
    }

    fun isEnabled(): Boolean = enabled.get()

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
