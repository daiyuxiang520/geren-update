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
                                "500009" -> Result.failure(APIError("登录已失效，请重新配置 Token"))
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
        chargePower = chargePower?.toDoubleOrNull() ?: 0.0,

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
