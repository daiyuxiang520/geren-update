package com.open.wuling.data.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit

/**
 * 版本更新信息（对应 update.json 字段）
 */
data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val updateLog: String,
    val apkUrl: String,
    val forceUpdate: Boolean,
    val md5: String
)

/**
 * 自动更新网络与文件校验工具（纯 Kotlin object，不依赖 Hilt）。
 * 复用 OkHttp；下载与计算 MD5 均切到 IO 调度器执行。
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 并发拉取 update.json 专用客户端（超时更短）。
     * 并发场景下一个慢源不该拖垮整体，故单独收紧超时；整体还有 [JSON_FETCH_BUDGET_MS] 兜底。
     */
    private val jsonClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    /** 并发拉取 update.json 的整体时间预算（毫秒），超时后按已收到的结果取最大版本 */
    private const val JSON_FETCH_BUDGET_MS = 8_000L

    /**
     * 快速通道命中后的宽限时间（毫秒）。
     * 某个源已返回「比本地更新」时，再给其余源一点时间，避免只取到较早返回的那个版本。
     */
    private const val JSON_FAST_PATH_GRACE_MS = 500L

    /**
     * 上次成功的加速源前缀（如 "https://ghproxy.net/"）。
     * 由 AppState 在启动时从 SharedPreferences 注入、成功后回写，
     * 使下次优先命中可用源，避免每次都从头逐个尝试。
     */
    @Volatile
    var lastGoodMirror: String? = null

    /** 成功源变化时的回调（供 AppState 持久化） */
    @Volatile
    var onMirrorSucceeded: ((String) -> Unit)? = null

    /**
     * 上次测速选出的「最快下载源」（apk 下载链路专用，与 json 检查链路分开）。
     *
     * 下载 APK 走的是另一组 URL（apkUrl 经镜像转换），其速度表现与拉 json 并不一致，
     * 因此单独记忆。下次下载时若该源仍可用，直接跳到队首，省去一轮测速。
     */
    @Volatile
    var bestDownloadMirror: String? = null

    /** 最快下载源变化时的回调（供 AppState 持久化） */
    @Volatile
    var onBestDownloadMirror: ((String) -> Unit)? = null

    /**
     * 拉取 update.json：**并发**请求所有候选源，返回「版本号最大」的那份结果。
     *
     * ### 为什么不再「首个成功即用」
     * 加速源是第三方 CDN，命中陈旧的缓存副本时会返回**旧版** update.json（HTTP 200 且格式合法）。
     * 顺序遍历 + 首个成功即用的策略下，若列表靠前的源缓存未刷新，用户就会一直
     * 被告知「已是最新版本」，新版本永远检测不到。
     *
     * 并发取最大值后：**谁最新听谁的** —— 只要有一个源已刷新，就能拿到最新版本，
     * 不依赖单个源的缓存时效。
     *
     * ### 两条路径
     * - **快速通道**：任一源返回的版本号 > [currentVersionCode] → 说明确实有更新，
     *   短暂宽限后即返回，不必干等其余慢源（常见情况下 1 秒内完成）。
     * - **等待取最大**：所有源都返回 ≤ 本地版本（即「无更新」）时，等齐/超时后取最大值，
     *   确保是真正的「无更新」而非「所有源都缓存了旧版」。
     *
     * @param jsonUrls            候选地址（各加速源拼接的 raw 直链）
     * @param currentVersionCode  本地版本号，用于快速通道判断；传 -1 则始终走等待取最大
     */
    suspend fun fetchUpdateInfo(
        jsonUrls: List<String>,
        currentVersionCode: Int = -1
    ): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val ordered = prioritize(jsonUrls, lastGoodMirror)
        if (ordered.isEmpty()) {
            null
        } else {
            // 各源返回的最新结果：url → info，取 versionCode 最大的一份
            val best = AtomicReference<Pair<String, AppUpdateInfo>?>(null)
            val foundNewer = AtomicBoolean(false)
            val finished = AtomicInteger(0)

            supervisorScope {
                val jobs = ordered.map { url ->
                    async {
                        try {
                            val info = fetchOne(url, jsonClient)
                            if (info != null) {
                                best.updateAndGet { cur ->
                                    if (cur == null || info.versionCode > cur.second.versionCode) {
                                        url to info
                                    } else cur
                                }
                                if (currentVersionCode >= 0 && info.versionCode > currentVersionCode) {
                                    foundNewer.set(true)
                                }
                            }
                        } finally {
                            finished.incrementAndGet()
                        }
                    }
                }

                // 退出条件：全部结束 / 超预算 / 快速通道命中
                val deadline = System.currentTimeMillis() + JSON_FETCH_BUDGET_MS
                while (finished.get() < jobs.size && System.currentTimeMillis() < deadline) {
                    if (foundNewer.get()) break
                    delay(80)
                }
                // 快速通道命中时给其余源一点宽限，尽量拿到最大版本
                if (foundNewer.get()) delay(JSON_FAST_PATH_GRACE_MS)

                jobs.forEach { it.cancel() }   // 取消仍在飞行的请求（已结束的 job 无副作用）
            }

            val result = best.get()
            if (result == null) {
                Log.w(TAG, "all mirrors failed (${ordered.size} sources)")
                null
            } else {
                // 记住给出最大版本的源：它缓存最新鲜，下次优先
                rememberMirror(result.first)
                Log.d(
                    TAG,
                    "update json: picked v${result.second.versionCode} from ${hostOf(result.first)} " +
                        "(current=$currentVersionCode)"
                )
                result.second
            }
        }
    }

    /** 兼容单 URL 调用 */
    suspend fun fetchUpdateInfo(jsonUrl: String, currentVersionCode: Int = -1): AppUpdateInfo? =
        fetchUpdateInfo(listOf(jsonUrl), currentVersionCode)

    /** 把命中 lastGoodMirror 的 URL 排到最前（其余保持原顺序） */
    private fun prioritize(urls: List<String>, good: String?): List<String> {
        if (good.isNullOrBlank()) return urls
        val hit = urls.filter { it.startsWith(good) }
        if (hit.isEmpty()) return urls
        return hit + urls.filterNot { it.startsWith(good) }
    }

    /** 记录成功的源（含前缀提取），并通知持久化 */
    private fun rememberMirror(successUrl: String) {
        val prefix = mirrorPrefixOf(successUrl) ?: return
        if (prefix != lastGoodMirror) {
            lastGoodMirror = prefix
            Log.d(TAG, "mirror remembered: $prefix")
        }
        onMirrorSucceeded?.invoke(prefix)
    }

    /** 从完整 URL 中提取加速源前缀（形如 https://host/），非 raw 路径返回 null */
    private fun mirrorPrefixOf(url: String): String? = try {
        val idx = url.indexOf("https://raw.githubusercontent.com/")
        if (idx <= 0) null else url.substring(0, idx)
    } catch (e: Exception) {
        null
    }

    /**
     * 拉取单个源的 update.json。
     * @param http 使用的 OkHttp 客户端（并发检查用超时更短的 [jsonClient]）
     */
    private fun fetchOne(jsonUrl: String, http: OkHttpClient = client): AppUpdateInfo? = try {
        val sep = if (jsonUrl.contains("?")) "&" else "?"
        val url = "$jsonUrl${sep}t=${System.currentTimeMillis()}"
        val request = Request.Builder()
            .url(url)
            // 时间戳之外再补两个标准反缓存头：部分 CDN 对带参 URL 仍会缓存，
            // 但对 no-cache 请求头会回源校验。
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "update json HTTP ${resp.code} @ $jsonUrl")
                null
            } else {
                val txt = resp.body?.string().orEmpty()
                val j = JSONObject(txt)
                val info = AppUpdateInfo(
                    versionCode = j.optInt("versionCode", -1),
                    versionName = j.optString("versionName", ""),
                    updateLog = j.optString("updateLog", ""),
                    apkUrl = j.optString("apkUrl", ""),
                    forceUpdate = j.optBoolean("forceUpdate", false),
                    md5 = j.optString("md5", "")
                )
                // versionCode 非法视为该源异常，继续尝试其它源
                if (info.versionCode < 0) null else info
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "fetchOne failed @ $jsonUrl: ${e.message}")
        null
    }

    /**
     * 下载 APK 到 destFile；依次尝试多个 URL（镜像兜底）。
     *
     * 策略（C1 + 记忆）：
     *  1. 若上次测速的最快源仍在候选列表中，直接把它排到最前，跳过测速；
     *  2. 否则并发探测各源的响应速度，取最快的排到最前；
     *  3. 按排好的顺序依次尝试下载，任一成功即返回。
     *
     * onProgress 回调 0..100（按 content-length 估算，无长度时只在结束回调 100）。
     */
    suspend fun downloadApk(
        apkUrls: List<String>,
        destFile: File,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val ordered = orderBySpeed(apkUrls)
        var lastError: Exception? = null
        for (url in ordered) {
            try {
                downloadOne(url, destFile, onProgress)
                // 成功：记住这个源，下次直接优先（若变化则回调持久化）
                rememberBestDownloadMirror(url)
                return@withContext
            } catch (e: Exception) {
                lastError = e
                Log.e(TAG, "download failed @ $url: ${e.message}")
                destFile.delete()
            }
        }
        throw lastError ?: RuntimeException("全部下载地址失效")
    }

    /**
     * 决定下载顺序：
     *  - 已记住的最快源命中候选 → 直接提到最前（不再测速）；
     *  - 否则并发测速所有候选，按「响应耗时升序」排列。
     */
    private fun orderBySpeed(urls: List<String>): List<String> {
        if (urls.size <= 1) return urls

        // ① 记忆命中：直接用它，省一轮测速
        val good = bestDownloadMirror
        if (!good.isNullOrBlank()) {
            val hit = urls.firstOrNull { it.startsWith(good) }
            if (hit != null) {
                Log.d(TAG, "download: 使用记忆的最快源 $good")
                return listOf(hit) + urls.filterNot { it == hit }
            }
        }

        // ② 并发测速
        val measured = measureSpeeds(urls)
        if (measured.isEmpty()) return urls

        Log.d(TAG, "download 测速结果: " + measured.joinToString { "${hostOf(it.first)}=${it.second}ms" })
        return measured.map { it.first } + urls.filterNot { u -> measured.any { it.first == u } }
    }

    /**
     * 并发 HEAD 探测各源响应耗时（毫秒），返回按耗时升序的 (url, costMs) 列表。
     *
     * 用 HEAD 而非 GET：只读响应头（含 content-length）不下载正文，开销极小。
     * 每个探测带 6 秒超时，全部失败则返回空列表（调用方退回原顺序）。
     */
    private fun measureSpeeds(urls: List<String>): List<Pair<String, Long>> {
        val probeClient = client.newBuilder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()

        val results = java.util.concurrent.ConcurrentHashMap<String, Long>()
        val latch = java.util.concurrent.CountDownLatch(urls.size)

        urls.forEach { url ->
            Thread {
                try {
                    val start = System.currentTimeMillis()
                    val req = Request.Builder().url(url).head().build()
                    probeClient.newCall(req).execute().use { resp ->
                        // 只认 2xx/3xx：404 之类的"快"没有意义
                        if (resp.isSuccessful) {
                            results[url] = System.currentTimeMillis() - start
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "测速失败 ${hostOf(url)}: ${e.message}")
                } finally {
                    latch.countDown()
                }
            }.apply { isDaemon = true }.start()
        }

        // 等所有探测结束，但最多等 8 秒（超时的直接忽略）
        return try {
            latch.await(8, TimeUnit.SECONDS)
            results.entries.sortedBy { it.value }.map { it.key to it.value }
        } catch (e: InterruptedException) {
            emptyList()
        }
    }

    /** 记录下载成功的源，变化时通知持久化 */
    private fun rememberBestDownloadMirror(successUrl: String) {
        val prefix = mirrorPrefixOf(successUrl) ?: return
        if (prefix != bestDownloadMirror) {
            bestDownloadMirror = prefix
            Log.d(TAG, "best download mirror remembered: $prefix")
        }
        onBestDownloadMirror?.invoke(prefix)
    }

    /** 从 URL 中取 host，仅用于日志可读性 */
    private fun hostOf(url: String): String = try {
        java.net.URI(url).host ?: url
    } catch (e: Exception) {
        url
    }

    private fun downloadOne(apkUrl: String, destFile: File, onProgress: (Int) -> Unit) {
        val resp = client.newCall(Request.Builder().url(apkUrl).build()).execute()
        if (!resp.isSuccessful) throw RuntimeException("下载失败 HTTP ${resp.code}")
        val body: ResponseBody = resp.body ?: throw RuntimeException("空响应")
        val total = body.contentLength()
        destFile.parentFile?.mkdirs()
        FileOutputStream(destFile).use { out ->
            body.byteStream().use { input ->
                val buf = ByteArray(8192)
                var read: Int
                var downloaded = 0L
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    downloaded += read
                    if (total > 0) onProgress(((downloaded * 100) / total).toInt())
                }
            }
        }
        onProgress(100)
    }

    /**
     * 公开测速入口（供更新弹窗 UI 实时展示各加速源延迟）。
     * 复用内部 measureSpeeds 的并发 HEAD 探测，返回 Map<url, 延迟ms>，
     * 未响应/超时的源映射为 null，便于 UI 标记「不可用」。
     */
    suspend fun measureMirrorSpeeds(urls: List<String>): Map<String, Long?> = withContext(Dispatchers.IO) {
        val measured = measureSpeeds(urls) // List<Pair<url, ms>>，仅含成功项
        val ok = measured.toMap()          // Map<String, Long>，成功的源及其延迟
        // 保持 urls 原顺序，成功源有延迟，其余（不可达/超时）映射为 null
        urls.associateWith { ok[it] }
    }

    /** 计算文件 MD5（小写 32 位），用于下载后防篡改校验 */
    fun md5(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().use { fis ->
            val buf = ByteArray(8192)
            var read: Int
            while (fis.read(buf).also { read = it } != -1) md.update(buf, 0, read)
        }
        return BigInteger(1, md.digest()).toString(16).padStart(32, '0')
    }
}
