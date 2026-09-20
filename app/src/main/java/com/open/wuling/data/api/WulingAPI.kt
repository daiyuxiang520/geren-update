package com.open.wuling.data.api

import android.util.Log
import com.google.gson.Gson
import com.open.wuling.BuildConfig
import com.open.wuling.data.model.CarInfo
import com.open.wuling.data.model.DoorStatus
import com.open.wuling.data.model.VehicleStatus
import com.open.wuling.data.model.WindowStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.open.wuling.util.AppLogger
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private val JSON_MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()

/** 菱菱邦 hapi OAuth 登录（无需签名，form-urlencoded） */
private const val LLB_LOGIN_URL = "https://hapi.00bang.cn/llb/oauth/llb/ucenter/login"

/**
 * v74：官方 MQTT 凭证接口。
 * 未加固 APK 反编译确认为 `/junApi/sgmw` + `/base/mqtt/auth`：
 *   POST body {"vin":"<VIN>"} → {"result":true,"data":{"token":"<32位hex>"}}
 * （v73 曾误用 /sgmw/base/parking/mqtt/confirm，实测 404，已修正）
 */
private const val MQTT_CREDENTIAL_URL = "https://openapi.baojun.net/junApi/sgmw/base/mqtt/auth"

/**
 * v59：接入 AppLogger 的网络日志拦截器（供 WulingAPI / EnergyAPI 共用）。
 *
 * 请求（方法+路径+脱敏后请求体）与响应（HTTP 码+脱敏后响应体）写入「我的 → 调试日志」，
 * release 包同样生效——这是调试日志面板的核心数据源（此前 release 下 HttpLoggingInterceptor
 * 为 NONE，面板里看不到任何五菱 API 报文）。
 *
 * 只记录 URL 路径与报文，不记录请求头（头里集中了 token/签名/设备信息，无需落盘）。
 */
internal fun createAppLogInterceptor(): okhttp3.Interceptor {
    return okhttp3.Interceptor { chain ->
        val request = chain.request()
        val path = "${request.method} ${request.url.encodedPath}"
        try {
            // 请求体脱敏后入日志（buffered body 可重复读取；API 层全部为 JSON/Form buffered body）
            val reqBodyText = request.body?.let { body ->
                runCatching {
                    val buffer = okio.Buffer()
                    body.writeTo(buffer)
                    sanitizeForLog(buffer.readUtf8())
                }.getOrNull()
            }
            AppLogger.apiRequest(path, reqBodyText)
        } catch (_: Exception) {
            // 日志绝不影响主流程
        }
        val response = chain.proceed(request)
        try {
            // peekBody 不消耗响应流，不影响后续 gson.fromJson
            val respText = response.peekBody(256 * 1024).string()
            AppLogger.apiResponse(path, response.code, sanitizeForLog(respText))
        } catch (_: Exception) {
        }
        response
    }
}

/**
 * v59：调试日志脱敏。
 *
 * 登录/车辆/控制接口的报文里含密码、手机号、token、VIN 等敏感信息，
 * 落入 AppLogger（会持久化到磁盘并可导出分享）前必须打码：
 * - password / client_secret / sgmwclientsecret：整值替换为 ******
 * - access_token / accessToken / token：保留前 6 位，其余 ****
 * - mobile：保留前 3 后 4
 * - vin：保留后 4 位
 */
private fun sanitizeForLog(raw: String): String {
    if (raw.isEmpty()) return raw
    var s = raw
    s = s.replace(Regex("""("(?:password|client_secret|sgmwclientsecret)"\s*:\s*")[^"]*(")"""), "$1******$2")
    s = s.replace(Regex("""("(?:access_token|accessToken|saccessToken|token)"\s*:\s*")([^"]{0,6})[^"]*(")"""), "$1$2****$3")
    s = s.replace(Regex("""("(?:mobile)"\s*:\s*")(\d{3})\d*(\d{4})(")"""), "$1$2****$3$4")
    s = s.replace(Regex("""("(?:vin)"\s*:\s*")([^"]{0,4})[^"]*(")"""), "$1****$2$3")
    return s
}

/**
 * 菱菱邦 OAuth 客户端凭据。
 *
 * 值不硬编码在源码中，改由 local.properties 提供（见项目根目录 local.properties.example），
 * 构建时通过 BuildConfig 注入。这样做是为了避免在公开仓库中分发客户端凭据。
 */
private val LLB_CLIENT_ID: String get() = BuildConfig.LLB_CLIENT_ID
private val LLB_CLIENT_SECRET: String get() = BuildConfig.LLB_CLIENT_SECRET

class WulingAPI @Inject constructor() {
    private val TAG = "WulingAPI"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // V824 签名拦截器：按实际请求 URL 统一计算 sgmwsignature（修复 500011 车控签名不合规）
        // 签名串 = canonicalPath + accessToken + nonce + timestamp + appVersion（SHA-256 小写hex）
        .addInterceptor { chain ->
            val request = chain.request()
            val token = APIConfig.accessToken
            if (token.isNotEmpty() && request.url.host.contains("baojun.net") && request.header("sgmwtimestamp") != null) {
                val ts = request.header("sgmwtimestamp")!!
                val nc = request.header("sgmwnonce") ?: ""
                val sign = generateSignature(canonicalSignaturePath(request.url.toString()), token, ts, nc)
                chain.proceed(request.newBuilder().header("sgmwsignature", sign).build())
            } else {
                chain.proceed(request)
            }
        }
        .addInterceptor(createAppLogInterceptor())
        .build()

    private val gson = Gson()

    // 线程安全的请求锁
    private val requestMutex = Mutex()

    /**
     * V8.2.4+ 新版车控签名（从真身 App v5.42 generateSgmwV824Signature 逆向验证）
     * 签名串: canonicalPath + accessToken + nonce + timestamp + appVersion，SHA-256 小写hex
     * 2026-09 实测：旧算法返回 500011（车控签名不合规），本算法通过（假 token 返回 500009）
     */
    private fun generateSignature(canonicalPath: String, accessToken: String, timestamp: String, nonce: String): String {
        val signStr = canonicalPath + accessToken + nonce + timestamp + APIConfig.appVersion
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(signStr.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * 真身 canonicalSignaturePath：URL 去掉 scheme://host 取路径，去 junApi/ 前缀，开头补 /
     * 例: https://carc.baojun.net/junApi/sgmw/xxx -> /sgmw/xxx
     */
    private fun canonicalSignaturePath(url: String): String {
        var path = url.substringAfter("://").substringAfter('/')
        if (path.startsWith("junApi/")) path = path.removePrefix("junApi/")
        return "/$path"
    }

    private fun generateRandomLetters(length: Int): String {
        val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { letters.random() }.joinToString("")
    }

    /**
     * 手机号 + 密码登录获取 llbToken（菱菱邦 OAuth）
     * 关键点：hapi 网关不需要签名；必须 form-urlencoded；字段名是 mobile / client_id（下划线）
     * 成功返回 access_token（90 天有效期），可直接作为车控 accessToken 使用
     */
    suspend fun loginLlb(mobile: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("mobile", mobile)
                .add("password", password)
                .add("client_id", LLB_CLIENT_ID)
                .add("client_secret", LLB_CLIENT_SECRET)
                .add("response_type", "token")
                .add("state", UUID.randomUUID().toString().replace("-", ""))
                .build()

            val request = Request.Builder()
                .url(LLB_LOGIN_URL)
                .post(formBody)
                .header("Accept", "application/json")
                .header("User-Agent", "okhttp/4.9.0")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (body == null) {
                Result.failure(APIError("网络错误"))
            } else {
                val loginResult = gson.fromJson(body, LlbLoginResponse::class.java)
                val token = loginResult.data?.accessToken
                if (loginResult.result && !token.isNullOrEmpty()) {
                    Log.i(TAG, "llb 登录成功，token 有效期 ${loginResult.data?.expiresIn ?: 0} 秒")
                    Result.success(token)
                } else {
                    val errMsg = loginResult.errorMessage ?: loginResult.errorCode ?: "登录失败"
                    Log.w(TAG, "llb 登录失败: $errMsg")
                    Result.failure(APIError(errMsg))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "llb 登录异常: ${e.message}")
            Result.failure(APIError(e.message ?: "登录失败"))
        }
    }

    private fun buildCommonHeaders(accessToken: String, timestamp: String, nonce: String): Map<String, String> {
        // 注意：sgmwsignature 由 SignatureInterceptor 按实际请求 URL 统一计算（V824 算法）
        return mapOf(
            "Content-Type" to "application/json; charset=UTF-8",
            "Accept" to "application/json",
            "User-Agent" to "okhttp/4.9.0",
            "channel" to "linglingbang",
            "platformNo" to "Android",
            "appVersionCode" to APIConfig.apiVersionCode,
            "version" to APIConfig.apiVersion,
            "imei" to APIConfig.deviceImei,
            "imsi" to "unknown",
            "deviceModel" to APIConfig.deviceModel,
            "deviceBrand" to APIConfig.deviceBrand,
            "deviceType" to "Android",
            "accessChannel" to "1",
            "sgmwaccesstoken" to accessToken,
            "sgmwtimestamp" to timestamp,
            "sgmwnonce" to nonce,
            "sgmwclientid" to APIConfig.clientId,
            "sgmwclientsecret" to APIConfig.clientSecret,
            "sgmwappcode" to APIConfig.appCode,
            "sgmwappversion" to APIConfig.appVersion,
            "sgmwsystem" to APIConfig.system,
            "sgmwsystemversion" to APIConfig.systemVersion,
            "sgmwplatformno" to "android"
        )
    }

    /**
     * 带重试的网络请求执行器（使用指数退避策略）
     * @param maxRetries 最大重试次数（不含首次请求）
     * @param baseDelayMs 初始重试间隔毫秒
     * @param maxDelayMs 最大重试间隔毫秒
     * @param block 实际请求逻辑
     */
    private suspend fun <T> executeWithRetry(
        maxRetries: Int = 2,
        baseDelayMs: Long = 1000,
        maxDelayMs: Long = 5000,
        block: suspend () -> Result<T>
    ): Result<T> {
        var lastError: Throwable? = null
        repeat(maxRetries + 1) { attempt ->
            if (attempt > 0) {
                Log.w(TAG, "请求失败，第 ${attempt} 次重试...")
            }
            val result = block()
            if (result.isSuccess) return result
            lastError = result.exceptionOrNull()
            if (attempt < maxRetries) {
                // 指数退避：baseDelay * 2^attempt，最大不超过 maxDelayMs
                val delayMs = minOf(baseDelayMs * (1 shl attempt), maxDelayMs)
                Log.d(TAG, "等待 ${delayMs}ms 后重试...")
                delay(delayMs)
            }
        }
        Log.e(TAG, "请求失败，已重试 ${maxRetries} 次")
        return Result.failure(lastError ?: APIError("请求失败"))
    }

    suspend fun queryDefaultCarStatus(): Result<CarStatusResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }

        executeWithRetry {
            requestMutex.withLock {
                val timestamp = System.currentTimeMillis().toString()
                val nonce = generateRandomLetters(10)
                val headers = buildCommonHeaders(APIConfig.accessToken, timestamp, nonce)

                val url = "${APIConfig.baseURL}/userCarRelation/queryDefaultCarStatus"
                val requestBuilder = Request.Builder()
                    .url(url)
                    .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                headers.forEach { (key, value) -> requestBuilder.header(key, value) }

                val response = client.newCall(requestBuilder.build()).execute()
                val body = response.body?.string()

                val result = if (body != null) {
                    try {
                        val carStatusResponse = gson.fromJson(body, CarStatusResponse::class.java)
                        
                        if (carStatusResponse.isSuccess) {
                            if (carStatusResponse.data != null) {
                                Result.success(carStatusResponse)
                            } else {
                                Result.failure(APIError("API返回数据为空"))
                            }
                        } else {
                            val errorMsg = carStatusResponse.errorMessage ?: carStatusResponse.message ?: "请求失败"
                            val errorCode = carStatusResponse.errorCode ?: "unknown"
                            
                            when (errorCode) {
                                // v70：500009 改用专用类型，调用方据此自动重登
                                "500009" -> Result.failure(SessionExpiredError("登录已失效，正在自动重新登录"))
                                else -> Result.failure(APIError("$errorMsg (错误码: $errorCode)"))
                            }
                        }
                    } catch (e: Exception) {
                        Result.failure(APIError("解析错误: " + e.message))
                    }
                } else {
                    Result.failure(APIError("网络错误：响应体为空"))
                }
                result
            }
        }
    }

    suspend fun queryTirePressure(vin: String): Result<TirePressureResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }

        executeWithRetry {
            requestMutex.withLock {
                try {
                    val timestamp = System.currentTimeMillis().toString()
                    val nonce = generateRandomLetters(10)
                    val headers = buildCommonHeaders(APIConfig.accessToken, timestamp, nonce)

                    val params = mapOf("vin" to vin)
                    val jsonBody = gson.toJson(params)

                    val requestBuilder = Request.Builder()
                        .url("${APIConfig.baseURL}/car/info/tire/pressure")
                        .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    headers.forEach { (key, value) -> requestBuilder.header(key, value) }

                    val response = client.newCall(requestBuilder.build()).execute()
                    val body = response.body?.string()

                    if (body != null) {
                        val tireResponse = gson.fromJson(body, TirePressureResponse::class.java)
                        Result.success(tireResponse)
                    } else {
                        Result.failure(APIError("网络错误"))
                    }
                } catch (e: Exception) {
                    Result.failure(APIError(e.message ?: "网络错误"))
                }
            }
        }
    }

    suspend fun sendCommand(command: String, params: Map<String, Any> = emptyMap()): Result<CommandResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }

        executeWithRetry {
            requestMutex.withLock {
                try {
                    val timestamp = System.currentTimeMillis().toString()
                    val nonce = generateRandomLetters(10)
                    val headers = buildCommonHeaders(APIConfig.accessToken, timestamp, nonce)

                    val allParams = params.toMutableMap()
                    allParams["command"] = command
                    val jsonBody = gson.toJson(allParams)

                    val requestBuilder = Request.Builder()
                        .url("${APIConfig.baseURL}/remote/control")
                        .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    headers.forEach { (key, value) -> requestBuilder.header(key, value) }

                    val response = client.newCall(requestBuilder.build()).execute()
                    val body = response.body?.string()

                    if (body != null) {
                        val cmdResponse = gson.fromJson(body, CommandResponse::class.java)
                        Result.success(cmdResponse)
                    } else {
                        Result.failure(APIError("网络错误"))
                    }
                } catch (e: Exception) {
                    Result.failure(APIError(e.message ?: "网络错误"))
                }
            }
        }
    }

    // 直接控制API（与wuling-main项目一致）
    suspend fun controlDoorLock(vin: String, status: Int): Result<CommandResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }

        executeWithRetry {
            requestMutex.withLock {
                try {
                    val timestamp = System.currentTimeMillis().toString()
                    val nonce = generateRandomLetters(10)
                    val headers = buildCommonHeaders(APIConfig.accessToken, timestamp, nonce)

                    val params = mapOf(
                        "vin" to vin,
                        "status" to status
                    )
                    val jsonBody = gson.toJson(params)

                    val requestBuilder = Request.Builder()
                        .url("${APIConfig.baseURL}/car/control/doorLock")
                        .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    headers.forEach { (key, value) -> requestBuilder.header(key, value) }

                    val response = client.newCall(requestBuilder.build()).execute()
                    val body = response.body?.string()

                    if (body != null) {
                        Result.success(gson.fromJson(body, CommandResponse::class.java))
                    } else {
                        Result.failure(APIError("网络错误"))
                    }
                } catch (e: Exception) {
                    Result.failure(APIError(e.message ?: "网络错误"))
                }
            }
        }
    }

    suspend fun controlAC(params: Map<String, Any>): Result<CommandResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }

        executeWithRetry {
            requestMutex.withLock {
                try {
                    val timestamp = System.currentTimeMillis().toString()
                    val nonce = generateRandomLetters(10)
                    val headers = buildCommonHeaders(APIConfig.accessToken, timestamp, nonce)

                    val jsonBody = gson.toJson(params)

                    val requestBuilder = Request.Builder()
                        .url("${APIConfig.baseURL}/car/control/acc")
                        .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    headers.forEach { (key, value) -> requestBuilder.header(key, value) }

                    val response = client.newCall(requestBuilder.build()).execute()
                    val body = response.body?.string()

                    if (body != null) {
                        Result.success(gson.fromJson(body, CommandResponse::class.java))
                    } else {
                        Result.failure(APIError("网络错误"))
                    }
                } catch (e: Exception) {
                    Result.failure(APIError(e.message ?: "网络错误"))
                }
            }
        }
    }

    suspend fun checkCarStatus(vin: String): Result<CheckStatusResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }

        executeWithRetry {
            requestMutex.withLock {
                try {
                    val timestamp = System.currentTimeMillis().toString()
                    val nonce = generateRandomLetters(10)
                    val headers = buildCommonHeaders(APIConfig.accessToken, timestamp, nonce)

                    val params = mapOf("vin" to vin)
                    val jsonBody = gson.toJson(params)

                    val requestBuilder = Request.Builder()
                        .url("${APIConfig.baseURL}/car/check/all")
                        .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    headers.forEach { (key, value) -> requestBuilder.header(key, value) }

                    val response = client.newCall(requestBuilder.build()).execute()
                    val body = response.body?.string()

                    if (body != null) {
                        Result.success(gson.fromJson(body, CheckStatusResponse::class.java))
                    } else {
                        Result.failure(APIError("网络错误"))
                    }
                } catch (e: Exception) {
                    Result.failure(APIError(e.message ?: "网络错误"))
                }
            }
        }
    }

    suspend fun authorizeIgnition(vin: String): Result<AuthorizeResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }

        executeWithRetry {
            requestMutex.withLock {
                try {
                    val timestamp = System.currentTimeMillis().toString()
                    val nonce = generateRandomLetters(10)
                    val headers = buildCommonHeaders(APIConfig.accessToken, timestamp, nonce)

                    val params = mapOf("vin" to vin)
                    val jsonBody = gson.toJson(params)

                    val requestBuilder = Request.Builder()
                        .url("${APIConfig.baseURL}/car/control/ignition/authorize")
                        .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    headers.forEach { (key, value) -> requestBuilder.header(key, value) }

                    val response = client.newCall(requestBuilder.build()).execute()
                    val body = response.body?.string()

                    if (body != null) {
                        Result.success(gson.fromJson(body, AuthorizeResponse::class.java))
                    } else {
                        Result.failure(APIError("网络错误"))
                    }
                } catch (e: Exception) {
                    Result.failure(APIError(e.message ?: "网络错误"))
                }
            }
        }
    }

    suspend fun searchCar(vin: String): Result<SearchCarResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }

        executeWithRetry {
            requestMutex.withLock {
                try {
                    val timestamp = System.currentTimeMillis().toString()
                    val nonce = generateRandomLetters(10)
                    val headers = buildCommonHeaders(APIConfig.accessToken, timestamp, nonce)

                    val params = mapOf("vin" to vin)
                    val jsonBody = gson.toJson(params)

                    val requestBuilder = Request.Builder()
                        .url("${APIConfig.baseURL}/car/control/searchCar")
                        .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    headers.forEach { (key, value) -> requestBuilder.header(key, value) }

                    val response = client.newCall(requestBuilder.build()).execute()
                    val body = response.body?.string()

                    if (body != null) {
                        Result.success(gson.fromJson(body, SearchCarResponse::class.java))
                    } else {
                        Result.failure(APIError("网络错误"))
                    }
                } catch (e: Exception) {
                    Result.failure(APIError(e.message ?: "网络错误"))
                }
            }
        }
    }

    suspend fun controlWindow(vin: String, status: Int): Result<WindowControlResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }

        executeWithRetry {
            requestMutex.withLock {
                try {
                    val timestamp = System.currentTimeMillis().toString()
                    val nonce = generateRandomLetters(10)
                    val headers = buildCommonHeaders(APIConfig.accessToken, timestamp, nonce)

                    val params = mapOf(
                        "vin" to vin,
                        "status" to status
                    )
                    val jsonBody = gson.toJson(params)

                    val requestBuilder = Request.Builder()
                        .url("${APIConfig.baseURL}/car/control/window")
                        .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    headers.forEach { (key, value) -> requestBuilder.header(key, value) }

                    val response = client.newCall(requestBuilder.build()).execute()
                    val body = response.body?.string()

                    if (body != null) {
                        Result.success(gson.fromJson(body, WindowControlResponse::class.java))
                    } else {
                        Result.failure(APIError("网络错误"))
                    }
                } catch (e: Exception) {
                    Result.failure(APIError(e.message ?: "网络错误"))
                }
            }
        }
    }

    /**
     * 查询昨日里程
     * 独立接口，因为 queryDefaultCarStatus 的 yesterMileage 字段经常返回 0
     */
    suspend fun fetchYesterdayMileage(vin: String): Result<APIResponse<YesterdayMileageData>> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }

        executeWithRetry {
            requestMutex.withLock {
                try {
                    val timestamp = System.currentTimeMillis().toString()
                    val nonce = generateRandomLetters(10)
                    val headers = buildCommonHeaders(APIConfig.accessToken, timestamp, nonce)

                    val params = mapOf("vin" to vin)
                    val jsonBody = gson.toJson(params)

                    val requestBuilder = Request.Builder()
                        .url("${APIConfig.baseURL}/car/yesterday/mileage")
                        .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    headers.forEach { (key, value) -> requestBuilder.header(key, value) }

                    val response = client.newCall(requestBuilder.build()).execute()
                    val body = response.body?.string()

                    if (body != null) {
                        val type = object : com.google.gson.reflect.TypeToken<APIResponse<YesterdayMileageData>>() {}.type
                        Result.success(gson.fromJson<APIResponse<YesterdayMileageData>>(body, type))
                    } else {
                        Result.failure(APIError("网络错误"))
                    }
                } catch (e: Exception) {
                    Result.failure(APIError(e.message ?: "网络错误"))
                }
            }
        }
    }

    suspend fun queryBleKey(vin: String, userId: String): Result<BleKeyResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }

        executeWithRetry {
            requestMutex.withLock {
                try {
                    val timestamp = System.currentTimeMillis().toString()
                    val nonce = generateRandomLetters(10)
                    val headers = buildCommonHeaders(APIConfig.accessToken, timestamp, nonce)

                    val params = mapOf(
                        "vin" to vin,
                        "userId" to userId
                    )
                    val jsonBody = gson.toJson(params)

                    val requestBuilder = Request.Builder()
                        .url("${APIConfig.baseURL}/car/control/ble/key/query")
                        .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    headers.forEach { (key, value) -> requestBuilder.header(key, value) }

                    val response = client.newCall(requestBuilder.build()).execute()
                    val body = response.body?.string()

                    if (body != null) {
                        Result.success(gson.fromJson(body, BleKeyResponse::class.java))
                    } else {
                        Result.failure(APIError("网络错误"))
                    }
                } catch (e: Exception) {
                    Result.failure(APIError(e.message ?: "网络错误"))
                }
            }
        }
    }

    // ==================== 循环预约充电 (v69) ====================
    // 接口路径来自官方 App dex 静态提取，并用线上只读查询闭环验证过 /car/cycle/charge/query
    // （返回字段与 ReserveChargeStatusInfoBean 完全对应）。
    //
    // 设计约束：chargeLimit / chargeModel / chargeRequest / type 四个字段的枚举语义未知，
    // 因此设置时只提交时间，其余字段**原样回传**服务端当前值（为 null 时传空串），
    // 绝不臆造枚举，避免把车端设成无法预料的状态。

    /** 统一 POST JSON，返回响应体原文（抛出异常交由调用方捕获） */
    private fun postJson(path: String, params: Map<String, Any?>): String {
        val timestamp = System.currentTimeMillis().toString()
        val nonce = generateRandomLetters(10)
        val headers = buildCommonHeaders(APIConfig.accessToken, timestamp, nonce)
        val requestBuilder = Request.Builder()
            .url("${APIConfig.baseURL}$path")
            .post(gson.toJson(params).toRequestBody(JSON_MEDIA_TYPE))
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }
        val response = client.newCall(requestBuilder.build()).execute()
        return response.body?.string() ?: throw APIError("网络错误：响应体为空")
    }

    /** 查询循环预约充电设置：POST /car/cycle/charge/query */
    suspend fun queryReserveCharge(vin: String): Result<APIResponse<ReserveChargeInfo>> =
        withContext(Dispatchers.IO) {
            if (!APIConfig.isConfigured) {
                return@withContext Result.failure(APIError("请先配置 Access Token"))
            }
            executeWithRetry {
                requestMutex.withLock {
                    try {
                        val body = postJson("/car/cycle/charge/query", mapOf("vin" to vin))
                        val type = object : com.google.gson.reflect.TypeToken<APIResponse<ReserveChargeInfo>>() {}.type
                        Result.success(gson.fromJson<APIResponse<ReserveChargeInfo>>(body, type))
                    } catch (e: Exception) {
                        Result.failure(APIError(e.message ?: "网络错误"))
                    }
                }
            }
        }

    /** 设置循环预约充电：POST /car/cycle/charge/reserve */
    suspend fun setReserveCharge(
        vin: String,
        startHour: String,
        startMinute: String,
        endHour: String,
        endMinute: String,
        chargeLimit: String?,
        chargeModel: String?,
        chargeRequest: String?,
        type: String?
    ): Result<APIResponse<Any>> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }
        executeWithRetry {
            requestMutex.withLock {
                try {
                    val body = postJson(
                        "/car/cycle/charge/reserve",
                        mapOf(
                            "vin" to vin,
                            "startHour" to startHour,
                            "startMinute" to startMinute,
                            "endHour" to endHour,
                            "endMinute" to endMinute,
                            // 未知枚举字段原样回传，null → 空串（与服务端下发格式一致）
                            "chargeLimit" to (chargeLimit ?: ""),
                            "chargeModel" to (chargeModel ?: ""),
                            "chargeRequest" to (chargeRequest ?: ""),
                            "type" to (type ?: "")
                        )
                    )
                    val t = object : com.google.gson.reflect.TypeToken<APIResponse<Any>>() {}.type
                    Result.success(gson.fromJson<APIResponse<Any>>(body, t))
                } catch (e: Exception) {
                    Result.failure(APIError(e.message ?: "网络错误"))
                }
            }
        }
    }

    /** 取消循环预约充电：POST /car/cancel/cycle/charge/reserve */
    suspend fun cancelReserveCharge(vin: String): Result<APIResponse<Any>> =
        withContext(Dispatchers.IO) {
            if (!APIConfig.isConfigured) {
                return@withContext Result.failure(APIError("请先配置 Access Token"))
            }
            executeWithRetry {
                requestMutex.withLock {
                    try {
                        val body = postJson("/car/cancel/cycle/charge/reserve", mapOf("vin" to vin))
                        val t = object : com.google.gson.reflect.TypeToken<APIResponse<Any>>() {}.type
                        Result.success(gson.fromJson<APIResponse<Any>>(body, t))
                    } catch (e: Exception) {
                        Result.failure(APIError(e.message ?: "网络错误"))
                    }
                }
            }
        }

    // ==================== MQTT 凭证 (v73) ====================
    // 官方 getMQTTToken 流程：用 accessToken 调 openapi.baojun.net 的凭证接口换取
    // MQTT 登录用的 username/password（可能还有 clientId/topic）。
    //
    // v74 更新：未加固 APK 反编译 + 线上实测已确认响应形态——
    //   POST /junApi/sgmw/base/mqtt/auth  body {"vin":"<VIN>"}
    //   → {"result":true,"data":{"token":"<32位hex>"}}
    // 线上 `data` 实际**只返回 token**（官方数据类 MqttAuthResponse 的 password/clientId 有默认值，可空）。
    // 官方把 token 放在 **username 位置**（写入 wuling_mqtt_v2 时 {"username":<token>,...}），
    // 故此处 username 优先取 token；password/clientId 为空时由上层按官方规则补齐/回退。

    /**
     * 获取 MQTT 登录凭证。
     * @param url 凭证接口地址（默认 MQTT_CREDENTIAL_URL，可被 MqttConfig 覆盖）
     * @return 解析到的三要素（username/password 都为空表示未识别到可用字段）
     */
    suspend fun fetchMqttCredential(
        vin: String,
        url: String = MQTT_CREDENTIAL_URL
    ): Result<com.open.wuling.data.mqtt.MqttCredential> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }
        try {
            val timestamp = System.currentTimeMillis().toString()
            val nonce = generateRandomLetters(10)
            val headers = buildCommonHeaders(APIConfig.accessToken, timestamp, nonce)
            // 请求体：官方接口需要 vin；accessToken 已随 sgmwaccesstoken 头带上
            val body = gson.toJson(mapOf("vin" to vin))
            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
            headers.forEach { (k, v) -> request.header(k, v) }

            val response = client.newCall(request.build()).execute()
            val text = response.body?.string()
            AppLogger.apiResponse("POST /mqtt/auth", response.code, sanitizeForLog(text ?: ""))
            if (text == null) {
                return@withContext Result.failure(APIError("网络错误：响应体为空"))
            }
            if (response.code != 200) {
                return@withContext Result.failure(APIError("MQTT 凭证接口返回 ${response.code}"))
            }

            // 防御式解析：{data:{...}} 或平铺 {...}
            val map = runCatching { gson.fromJson(text, Map::class.java) as? Map<String, Any> }.getOrNull()
            // 网关统一信封：result=false 时给 errorCode/errorMessage（如 20006 方法参数无效 / 400016 无权控制此车辆）
            val resultFlag = map?.get("result")
            if (resultFlag is Boolean && !resultFlag) {
                val code = pickString(map, "errorCode")
                val msg = pickString(map, "errorMessage").ifEmpty { "未知错误" }
                AppLogger.w("WulingAPI", "MQTT 凭证接口业务失败 $code: $msg", text.take(400))
                return@withContext Result.failure(APIError("[$code] $msg"))
            }
            val data = (map?.get("data") as? Map<*, *>) ?: map
            // v74 实证：官方 username 位置存的就是 token
            val mqttToken = pickString(data, "token", "mqttToken", "mqtt_token")
            val username = pickString(data, "username", "mqttUser", "mqttUsername", "user", "userName", "mqtt_user")
                .ifEmpty { mqttToken }
            val password = pickString(data, "password", "mqttPwd", "mqttPassword", "pwd", "pass", "mqtt_pwd")
                .ifEmpty { mqttToken }   // 官方 password 常见为空，先回退成 token 让上层有可用值
            val clientId = pickString(data, "clientId", "client_id", "mqttClientId", "mqtt_client_id")
            val topic = pickString(data, "topic", "topics", "mqttTopic", "mqtt_topic")

            if (username.isEmpty() && password.isEmpty()) {
                AppLogger.w("WulingAPI", "MQTT 凭证响应未识别到 token/username/password 字段", text.take(400))
                return@withContext Result.failure(APIError("凭证响应无可用字段（已写入调试日志）"))
            }
            Result.success(
                com.open.wuling.data.mqtt.MqttCredential(
                    username = username,
                    password = password,
                    clientId = clientId.ifEmpty { null },
                    topic = topic.ifEmpty { null }
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchMqttCredential 异常: ${e.message}")
            Result.failure(APIError(e.message ?: "凭证获取失败"))
        }
    }

    /** 从 Map 里按候选 key 顺序取第一个非空字符串值（Gson 解析出的 Map 值是 Any） */
    private fun pickString(map: Map<*, *>?, vararg keys: String): String {
        if (map == null) return ""
        for (k in keys) {
            val v = map[k] ?: continue
            if (v != null && v.toString().isNotEmpty() && v.toString() != "null") {
                return v.toString()
            }
        }
        return ""
    }

}

/**
 * 充电功率解析（方案 A 兜底）。
 * - 服务端 carStatus.chargePower 为非空正数时直接采用；单位可能是 W 或 kW，
 *   formatChargePower() 已做「>100 视为 W→÷1000」双向防御，无需在此关心。
 * - 缺失/为空时，用 电压(V) × 电流(A) 估算（结果单位 W）。
 * - 剔除「未下发」占位：任一值 <=0，或落在默认占位区间 350V×50A，视为无数据返回 null
 *   （否则会把默认假值算成 17.5kW 的假功率）。
 * ⚠️ 待实测校准：充电时看详情页「电压/电流」真实值，确认字段与单位口径是否正确。
 */
private fun computeChargePower(raw: String?, voltage: Double?, current: Double?): Double? {
    // 1) 服务端直读优先
    raw?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.let { if (it > 0.0) return it }
    // 2) 缺失则用 电压(V) × 电流(A) 估算（单位 W，formatChargePower 负责归一为 kW）
    val v = voltage ?: 0.0
    val c = current ?: 0.0
    if (v <= 0.0 || c <= 0.0) return null
    if (v in 340.0..360.0 && c in 45.0..55.0) return null // 默认占位，视为未下发
    val watts = v * c
    if (watts <= 0.0) return null
    return watts
}

// Extension to convert API response to VehicleStatus
// 可选参数用于传入诊断状态（来自单独的 checkStatus API）
fun CarStatusApi.toVehicleStatus(
    checkEnginePow: Int? = null,
    checkEngineTemp: Int? = null,
    checkAbsio: Int? = null,
    checkPwrStrIo: Int? = null
): VehicleStatus {
    // 门锁状态判断逻辑（已确认值含义）：
    // doorLockStatus: 0=锁定, 1=解锁（可能是中控锁状态，可能不同步）
    // doorXLockStatus: 0=锁定, 1=解锁
    // 优先用单独门锁状态判断，只有当所有单独门锁都无效(null)时才用 doorLockStatus
    
    // 检查各单独门锁是否都有值
    // 直接使用 doorLockStatus 判断整车锁定状态（0=锁定，1=解锁）
    val isVehicleLocked = doorLockStatus == 0
    
    val result = VehicleStatus(
        // 基础
        batteryLevel = batterySoc ?: 0,
        range = leftMileage ?: 0,
        electricRange = leftMileage ?: 0,
        oilRange = oilLeftMileage ?: 0,
        leftFuel = leftFuel?.toIntOrNull() ?: 0,  // 剩余油量百分比（混动车型）
        isLocked = isVehicleLocked,
        isClimateOn = acStatus != 0 && acStatus != null,
        climateMode = when (acStatus) {
            0 -> "off"
            1 -> "cool"
            2 -> "heat"
            else -> "off"
        },
        climateTemperature = interiorTemperature ?: 24,
        mileage = mileage ?: 0,
        isCharging = vecChrgingSts == 1,
        interiorTemperature = interiorTemperature ?: 25,
        exteriorTemperature = accCntTemp ?.toInt() ?: 20,
        gearStatus = autoGearStatus ?: "10",

        // 胎压 - 如果API返回的胎压数据为null，则保持默认值0.0，等待单独的胎压API获取
        tirePressureFL = tirePressureFl?.toDoubleOrNull()?.div(100) ?: 0.0,
        tirePressureFR = tirePressureFr?.toDoubleOrNull()?.div(100) ?: 0.0,
        tirePressureRL = tirePressureRl?.toDoubleOrNull()?.div(100) ?: 0.0,
        tirePressureRR = tirePressureRr?.toDoubleOrNull()?.div(100) ?: 0.0,
        tireTemperature = 0,  // 轮胎温度由单独API获取

        // 电池
        batteryHealth = batSOH ?: batHealth ?: 95,
        batteryTempMin = batMinTemp ?: 20,
        batteryTempMax = batMaxTemp ?: 28,
        batAvgTemp = batAvgTemp ?: 0,
        lowBatVol = lowBatVol ?: 0.0,
        batteryStatus = batteryStatus ?: "0",
        leftBatteryPower = leftBatteryPower ?: 0.0,
        voltage = voltage ?: 0.0,
        current = current ?: 0.0,
        // 方案 A：充电功率解析。服务端 carStatus.chargePower 优先（多数场景不下发空串），
        //   缺失/为空时用 电压(V) × 电流(A) 估算（返回 W，formatChargePower 归一为 kW）。
        //   需实测校准：voltage/current 默认值 350/50 为「未下发」占位，会由 computeChargePower 剔除。
        chargePower = computeChargePower(
            raw = chargePower,
            voltage = voltage,
            current = current
        ),

        // 车门 — 用 doorXOpenStatus 判断是否打开，用 doorXLockStatus 判断是否锁定
        // 门锁状态：0=锁定, 1=解锁
        doors = DoorStatus(
            frontLeft = (door1OpenStatus ?: 0) == 1,
            frontRight = (door2OpenStatus ?: 0) == 1,
            rearLeft = (door3OpenStatus ?: 0) == 1,
            rearRight = (door4OpenStatus ?: 0) == 1,
            trunk = (tailDoorOpenStatus ?: 0) == 1,
            frontLeftLocked = (door1LockStatus ?: doorLockStatus ?: 0) == 0,
            frontRightLocked = (door2LockStatus ?: doorLockStatus ?: 0) == 0,
            rearLeftLocked = (door3LockStatus ?: doorLockStatus ?: 0) == 0,
            rearRightLocked = (door4LockStatus ?: doorLockStatus ?: 0) == 0,
            trunkLocked = (doorLockStatus ?: 0) == 0
        ),

        // 车窗
        windows = WindowStatus(
            frontLeft = (window1Status ?: 0) == 1,
            frontRight = (window2Status ?: 0) == 1,
            rearLeft = (window3Status ?: 0) == 1,
            rearRight = (window4Status ?: 0) == 1
        ),

        // 车窗开度
        window1OpenDegree = window1OpenDegree ?: 0,
        window2OpenDegree = window2OpenDegree ?: 0,
        window3OpenDegree = window3OpenDegree ?: 0,
        window4OpenDegree = window4OpenDegree ?: 0,

        // 灯光
        frontFogLight = frontFogLight == "1",
        leftTurnLight = leftTurnLight == "1",
        positionLight = positionLight == "1",
        rightTurnLight = rightTurnLight == "1",
        dipHeadLight = dipHeadLight == "1",
        lowBeamLight = lowBeamLight == "1",

        // 钥匙 & 档位
        keyStatus = keyStatus ?: "0",
        autoGearStatus = autoGearStatus ?: "10",

        // 电机温度
        tmActTemp = tmActTemp ?: 0,
        invActTemp = invActTemp ?: 0,
        obcOtpCur = obcOtpCur ?: 0.0,

        // 充电
        vecChrgStsIndOn = vecChrgStsIndOn == 1,
        vecChargeSts = vecChargeSts ?: 0,
        chargingTimeRemaining = leftChargeTime,
        chargingRaw = charging ?: "0",

        // 里程
        yesterMileage = yesterMileage ?: 0,
        avgFuel = avgFuel ?: 0.0,
        hybridMileageKm = hybridMileage?.toIntOrNull(),

        // 驾驶状态
        steeringWheelAngle = strWhAng ?: "0",
        brakePedalPosition = brakPedalPos ?: "0",
        accPosition = accActPos ?: "0",
        averageSpeed = vehSpdAvgDrvn ?: "",

        // 安全
        sentinelModeStatus = sentinelModeStatus == "1",
        limitFeedback = limitFeedback ?: "-1",

        // 座椅
        seat1HotStatus = seat1HotStatus ?: "",
        seat2HotStatus = seat2HotStatus ?: "",
        seat3HotStatus = seat3HotStatus ?: "",
        seat4HotStatus = seat4HotStatus ?: "",
        seat1WindStatus = seat1WindStatus ?: "",
        seat2WindStatus = seat2WindStatus ?: "",
        seat3WindStatus = seat3WindStatus ?: "",
        seat4WindStatus = seat4WindStatus ?: "",

        // 其他
        intelligentCarSwitch = intelligentCarSwitch ?: 0,
        collectTime = collectTime ?: "",

        // ====== 诊断状态 (CheckStatus) ======
        // ProblemConv(reverse=True): 值被反转 - 0=异常, 1=正常
        // 例如 enginePow=0 表示"动力系统异常"，enginePow=1 表示"动力系统正常"
        enginePowStatus = checkEnginePow ?: 1,
        engineTempStatus = checkEngineTemp ?: 1,
        // absio, pwrStrIo: BinarySensorConv 无反转 - 0=正常, 1=异常
        absStatus = checkAbsio ?: 0,
        powerSteeringStatus = checkPwrStrIo ?: 0
    )
    return result
}

// Extension to convert CarInfoApi to CarInfo
fun CarInfoApi.toCarInfo(): CarInfo {
    return CarInfo(
        carInfoId = carInfoId ?: 0,
        userId = userId ?: "",
        vin = vin ?: "",
        carName = carName ?: "",
        colorCode = colorCode ?: "",
        colorName = colorName ?: "",
        vsn = vsn ?: "",
        carPlate = carPlate ?: "",
        carTypeName = carTypeName ?: "",
        model = model ?: "",
        level = level ?: "",
        engineType = engineType ?: 0,
        image = image ?: "",
        providerCode = providerCode ?: "",
        carYear = carYear ?: "",
        seriesCode = seriesCode ?: "",
        powerType = powerType ?: "",
        purchaseDate = purchaseDate ?: 0,
        purchaseUserName = purchaseUserName ?: "",
        purchaseShopNum = purchaseShopNum ?: "",
        carOwnerDay = carOwnerDay ?: 0,
        bindCarUserMobile = bindCarUserMobile ?: "",
        finishBind = finishBind ?: false,
        shakeLock = shakeLock ?: 0,
        bluetoothKeyConnectMark = bluetoothKeyConnectMark ?: "",
        supportAutoAir = supportAutoAir ?: 0,
        supportChargeRemain = supportChargeRemain ?: 0,
        supportChargePower = supportChargePower ?: 0,
        supportAvgFuel = supportAvgFuel ?: 0,
        supportHybridMileage = supportHybridMileage ?: 0,
        supportMqtt = supportMqtt ?: 0,
        controlView = controlView ?: 0,
        bleType = bleType ?: 0,
        physicsEngine = physicsEngine ?: 0
    )
}
