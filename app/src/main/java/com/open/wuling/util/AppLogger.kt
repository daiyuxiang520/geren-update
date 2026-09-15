package com.open.wuling.util

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.google.gson.JsonParser
import com.open.wuling.analytics.UmengAnalytics
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.firstOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 应用内日志管理器（v59 全量重做）
 *
 * 能力：
 * 1. 内存环形队列（[MAX_LOGS] 条，供调试日志面板实时查看）
 * 2. **磁盘持久化**：按天落盘 `filesDir/logs/app-YYYY-MM-DD.log`（每行一个 JSON），
 *    杀进程后重启可回填最近日志；保留 [RETAIN_DAYS] 天 / 总量 [RETAIN_BYTES]，启动时异步清理
 * 3. 「记录」开关**持久化**到 DataStore（此前重启即失效）
 * 4. WARN/ERROR 上报友盟「错误分析」（去重 + 时间窗限流）
 *
 * 线程安全：磁盘写入走单线程 executor（同时保证写文件顺序）；时间格式化用 ThreadLocal
 * （此前 SimpleDateFormat 共享实例在 Compose 多线程渲染下可能错乱）。
 */

/** 日志开关持久化 DataStore（顶层委托，AppLogger 为 object 无法用 Hilt 注入） */
private val Context.logDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_log_settings")

object AppLogger {
    private const val MAX_LOGS = 500
    private val logs = ConcurrentLinkedQueue<LogEntry>()
    private val enabled = AtomicBoolean(true)

    /** 应用上下文（由 Application 注入；未注入时不上报友盟、不落盘，仅内存记录） */
    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var appContext: Context? = null

    /** 是否把 ERROR/WARN 上报友盟（可动态关闭） */
    private val uploadEnabled = AtomicBoolean(true)

    /** 日志文件目录（attachContext 时初始化） */
    @Volatile
    private var logDir: File? = null

    /** 磁盘 IO 单线程 executor：保证追加顺序，也天然规避 SimpleDateFormat 并发问题 */
    private val diskExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "AppLogger-Disk") }

    /** 磁盘保留策略：3 天 / 2MB，超出从最旧删除 */
    private const val RETAIN_DAYS = 3
    private const val RETAIN_BYTES = 2L * 1024 * 1024

    /** 文件名日期格式（仅 diskExecutor 线程使用，无并发问题） */
    private val fileDayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** 去重：同一 tag+message 的最近上报时间戳 */
    private val lastReportedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** 时间窗内已上报条数（简化限流，防崩溃循环刷屏） */
    private val reportCount = AtomicLong(0)
    private const val REPORT_WINDOW_MS = 10_000L
    private const val REPORT_MAX_PER_WINDOW = 20L

    @Volatile
    private var windowStart = System.currentTimeMillis()

    /** 由 Application.onCreate 注入上下文；随后异步回填磁盘日志、清理过期文件、恢复开关状态 */
    fun attachContext(context: Context) {
        val app = context.applicationContext
        appContext = app
        logDir = File(app.filesDir, "logs").apply { mkdirs() }

        diskExecutor.execute {
            // 1. 恢复「记录」开关（此前重启即丢，v59 起持久化）
            runCatching {
                val saved = runBlocking {
                    app.logDataStore.data.firstOrNull()?.get(LOG_ENABLED_KEY)
                }
                saved?.let { enabled.set(it) }
            }
            // 2. 回填最近日志到内存（供打开面板时立即可见）
            runCatching { loadFromDisk() }
            // 3. 清理过期文件
            runCatching { cleanupDisk() }
        }
    }

    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val level: Level,
        val tag: String,
        val message: String,
        val details: String? = null
    ) {
        val formattedTime: String
            get() = dateFormat.get()!!.format(Date(timestamp))

        companion object {
            // SimpleDateFormat 非线程安全；LogItem 渲染发生在多线程 Compose 环境下，用 ThreadLocal 隔离
            private val dateFormat = object : ThreadLocal<SimpleDateFormat>() {
                override fun initialValue() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            }
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

        // 保持内存队列上限
        while (logs.size > MAX_LOGS) {
            logs.poll()
        }

        // 落盘（异步、单线程、失败静默——日志绝不能反过来影响主流程）
        appendToDisk(entry)

        // ERROR/WARN 上报友盟错误分析（去重 + 限流）
        if (level == Level.ERROR || level == Level.WARN) {
            reportToUmeng(level, tag, message, details)
        }
    }

    // ==================== 磁盘持久化 ====================
    // 文件格式：每行一个 JSON 对象 {"t":时间戳,"l":"INFO","g":"TAG","m":"消息","d":"详情"}

    private fun appendToDisk(entry: LogEntry) {
        val dir = logDir ?: return
        diskExecutor.execute {
            runCatching {
                val file = File(dir, "app-${fileDayFormat.format(Date(entry.timestamp))}.log")
                val json = buildString {
                    append("{\"t\":").append(entry.timestamp)
                    append(",\"l\":\"").append(entry.level.name).append('"')
                    append(",\"g\":\"").append(jsonEscape(entry.tag)).append('"')
                    append(",\"m\":\"").append(jsonEscape(entry.message)).append('"')
                    if (!entry.details.isNullOrEmpty()) {
                        append(",\"d\":\"").append(jsonEscape(entry.details)).append('"')
                    }
                    append("}")
                }
                file.appendText(json + "\n")
            }
        }
    }

    /** 启动时把磁盘上最近的日志回填进内存（今天 + 昨天的文件，最多取最后 [MAX_LOGS] 行） */
    private fun loadFromDisk() {
        val dir = logDir ?: return
        val files = dir.listFiles { f -> f.name.startsWith("app-") && f.name.endsWith(".log") }
            ?.sortedBy { it.name }?.takeLast(2) ?: return
        val lines = mutableListOf<String>()
        for (f in files) {
            runCatching {
                lines.addAll(f.readLines().takeLast(MAX_LOGS))
            }
        }
        val parsed = lines.takeLast(MAX_LOGS).mapNotNull { line ->
            runCatching {
                val o = JsonParser.parseString(line).asJsonObject
                LogEntry(
                    timestamp = o.get("t")?.asLong ?: System.currentTimeMillis(),
                    level = o.get("l")?.asString?.let { runCatching { Level.valueOf(it) }.getOrNull() } ?: Level.INFO,
                    tag = o.get("g")?.asString ?: "APP",
                    message = o.get("m")?.asString ?: "",
                    details = o.get("d")?.takeIf { !it.isJsonNull }?.asString
                )
            }.getOrNull()
        }.sortedBy { it.timestamp }
        parsed.forEach { entry ->
            logs.add(entry)
        }
        while (logs.size > MAX_LOGS) {
            logs.poll()
        }
    }

    /** 清理：删 3 天前文件；总量超 2MB 时从最旧删起 */
    private fun cleanupDisk() {
        val dir = logDir ?: return
        val files = dir.listFiles { f -> f.name.startsWith("app-") && f.name.endsWith(".log") }?.sortedBy { it.name } ?: return
        val expireBefore = System.currentTimeMillis() - RETAIN_DAYS * 24 * 60 * 60 * 1000L
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (f.lastModified() < expireBefore || total > RETAIN_BYTES) {
                val size = f.length()
                if (f.delete()) total -= size
            }
            if (total <= RETAIN_BYTES) break
        }
    }

    /** 极简 JSON 字符串转义（配合手工拼接行格式） */
    private fun jsonEscape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

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

    /** 清空内存 + 磁盘（「调试日志」面板与「隐私设置 → 清除本地数据」共用） */
    fun clear() {
        logs.clear()
        val dir = logDir ?: return
        diskExecutor.execute {
            runCatching {
                dir.listFiles { f -> f.name.startsWith("app-") }?.forEach { it.delete() }
            }
        }
    }

    /** 导出当前内存日志为分享用纯文本（按时间正序） */
    fun exportText(): String = logs.toList()
        .sortedBy { it.timestamp }
        .joinToString("\n") { entry ->
            buildString {
                append("[${entry.formattedTime}][${entry.level.name}][${entry.tag}] ${entry.message}")
                if (!entry.details.isNullOrBlank()) append("\n    ${entry.details}")
            }
        }

    fun setEnabled(enable: Boolean) {
        enabled.set(enable)
        val ctx = appContext ?: return
        diskExecutor.execute {
            runCatching {
                runBlocking { ctx.logDataStore.edit { it[LOG_ENABLED_KEY] = enable } }
            }
        }
    }

    fun isEnabled(): Boolean = enabled.get()

    /** 开/关「WARN/ERROR 上报友盟」（本地日志不受影响） */
    fun setUploadEnabled(enable: Boolean) {
        uploadEnabled.set(enable)
    }

    fun isUploadEnabled(): Boolean = uploadEnabled.get()

    /** DataStore 键：记录开关（v59 起持久化） */
    private val LOG_ENABLED_KEY = booleanPreferencesKey("log_enabled")

    // API 请求日志快捷方法（WulingAPI 拦截器调用，v59 起真正接入）
    fun apiRequest(api: String, body: String?) {
        d("API", "➡️ 请求: $api", body?.take(800))
    }

    fun apiResponse(api: String, code: Int, body: String?) {
        i("API", "⬅️ 响应: $api (HTTP $code)", body?.take(800))
    }

    fun apiError(api: String, error: String) {
        e("API", "❌ 错误: $api - $error")
    }
}
