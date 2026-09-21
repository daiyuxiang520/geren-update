package com.open.wuling.data.mqtt

import android.util.Log
import com.google.gson.Gson
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscribe
import com.open.wuling.data.api.APIError
import com.open.wuling.data.api.WulingAPI
import com.open.wuling.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MqttRepository"

/**
 * v73：MQTT 连接仓库（可配置、容错不崩）。
 *
 * 职责：
 *  - 按 MqttConfig 连接 broker（HiveMQ 纯 JVM 客户端，支持非 TLS TCP 1883）；
 *  - 需要时用官方凭证接口（或手动账号密码）做 simpleAuth 登录；
 *  - 订阅可配置 topic 列表，收到报文后回调 onMessage；
 *  - 内置自动重连（断线指数退避）；
 *  - 所有异常局限在协程内，绝不让连接异常冒泡到 UI / 主线程导致崩溃。
 *
 * 设计要点：本类不解析 protobuf(SgmwAppCarStatus)——字段顺序被加固壳藏住，无法静态还原。
 * 收到的原始报文一律写调试日志（tag=MQTT），由 AppState 决定如何处理：
 * 默认「收到任意推送即触发一次已验证的 REST 车况刷新」即可实现「车况一变立刻变」，
 * 无需理解报文内容。后续用户可在设置页照真机抓包补全直解析。
 */
@Singleton
class MqttRepository @Inject constructor(
    private val wulingAPI: WulingAPI,
    private val configStore: MqttConfigStore,
    @ApplicationContext private val context: android.content.Context
) {
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<MqttConnectionState>(MqttConnectionState.DISABLED)
    val connectionState: StateFlow<MqttConnectionState> = _state.asStateFlow()

    /** 最近一次错误描述（设置页展示） */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** 最近一次收到报文的时刻（ms），供 UI 显示「最后推送 x 秒前」 */
    private val _lastMessageAt = MutableStateFlow(0L)
    val lastMessageAt: StateFlow<Long> = _lastMessageAt.asStateFlow()

    private var client: com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient? = null
    /** 主动断开标志：避免 graceful disconnect 触发 disconnected 监听误判为「重连中」 */
    private var closing = false
    private var connectJob: Job? = null

    /**
     * 建立（或重建）连接。
     * @param config   本次连接使用的配置（调用方负责从 configStore 读取最新值）
     * @param accessToken 当前 accessToken（用于凭证接口鉴权）
     * @param vin      当前车辆 VIN（用于展开 {vin} 占位符）
     * @param onMessage 收到推送时的回调（在 IO 协程执行，调用方需自行节流）
     */
    fun connect(
        config: MqttConfig,
        accessToken: String,
        vin: String,
        /** v74：登录手机号（用于按官方规则拼 clientId `{vin}_{手机号后4位}`，可空） */
        phone: String = "",
        onMessage: (MqttMessage) -> Unit
    ) {
        if (!config.enabled) {
            Log.d(TAG, "MQTT 未启用，跳过连接")
            _state.value = MqttConnectionState.DISABLED
            return
        }
        closing = false
        connectJob?.cancel()
        connectJob = scope.launch {
            try {
                teardownClient()
                _state.value = MqttConnectionState.CONNECTING
                _lastError.value = null

                // ① 解析登录凭证（优先官方接口，失败回退手动）
                val cred = resolveCredentials(config, accessToken, vin)
                val user = cred.username.ifEmpty { config.username }
                val pass = cred.password.ifEmpty { config.password }
                // v74：clientId 按官方规则 {vin}_{手机号后4位}；接口给了就直接用
                val clientId = (cred.clientId?.takeIf { it.isNotEmpty() }
                    ?: config.resolvedClientId(vin, phone))
                val (host, port, useTls) = config.brokerHostPort()

                AppLogger.i(
                    TAG,
                    "正在连接 broker $host:$port (tls=$useTls, user=${user.ifEmpty { "<空>" }}, clientId=$clientId)",
                    "topic=${config.resolvedTopics(vin, phone)}"
                )

                // ①' 分段诊断：先解析 DNS、再探 TCP，把「卡在哪一层」直接打出来
                //     有此两步日志，就不会再出现「只看到正在连接、后面什么都没有」的盲区。
                val resolvedIp = runCatching {
                    java.net.InetAddress.getByName(host).hostAddress
                }.getOrNull()
                if (resolvedIp == null) {
                    _state.value = MqttConnectionState.ERROR
                    _lastError.value = "DNS 解析失败：$host"
                    AppLogger.e(TAG, "MQTT 连接失败：DNS 解析失败", "host=$host")
                    return@launch
                }
                AppLogger.i(TAG, "DNS 解析成功：$host → $resolvedIp")
                val tcpOk = runCatching {
                    java.net.Socket().use { s ->
                        s.connect(java.net.InetSocketAddress(resolvedIp, port), 8000)
                    }
                    true
                }.getOrElse { false }
                if (!tcpOk) {
                    _state.value = MqttConnectionState.ERROR
                    _lastError.value = "TCP 连接失败：$resolvedIp:$port（端口不通/被拦截）"
                    AppLogger.e(TAG, "MQTT 连接失败：TCP 不通", "addr=$resolvedIp:$port")
                    return@launch
                }
                AppLogger.i(TAG, "TCP 连通：$resolvedIp:$port，开始 MQTT CONNECT 握手")

                // ② 构建 HiveMQ 异步客户端
                // 注意：HiveMQ builder 是不可变的，每个方法都返回新实例，条件分支必须重新赋值
                var builder = MqttClient.builder()
                    .serverHost(host)
                    .serverPort(port)
                    .useMqttVersion3()
                    .identifier(clientId)
                if (useTls) builder = builder.useSslWithDefaultConfig()
                if (user.isNotEmpty()) {
                    builder = builder.simpleAuth().username(user).password(pass.toByteArray()).applySimpleAuth()
                }
                if (config.reconnectEnabled) {
                    builder = builder.automaticReconnect()
                        .initialDelay(1, TimeUnit.SECONDS)
                        .maxDelay(30, TimeUnit.SECONDS)
                        .applyAutomaticReconnect()
                }
                builder = builder.addDisconnectedListener {
                    if (!closing) {
                        _state.value = if (config.reconnectEnabled) MqttConnectionState.RECONNECTING
                        else MqttConnectionState.DISCONNECTED
                    }
                }
                val c = builder.buildAsync()
                client = c

                // ③ 建连（带硬超时：HiveMQ 默认无 MQTT 层超时，broker 不回应时会永久挂起，
                //    此前「只看到正在连接」正是卡在这里 → 现在最多等 connectionTimeoutSeconds 秒必出结论）
                val timeoutMs = (config.connectionTimeoutSeconds.coerceAtLeast(5)) * 1000L
                val connFuture = c.connectWith()
                    .keepAlive(config.keepAliveSeconds)
                    .send()
                try {
                    // 同步等待 CONNECT 结果：成功直接返回（后续订阅已在 whenComplete 中挂好），
                    // 失败会把 Mqtt3ConnAckException / Mqtt3AuthException 抛出来，据此解析 rc。
                    connFuture.get(timeoutMs, TimeUnit.MILLISECONDS)
                } catch (e: java.util.concurrent.TimeoutException) {
                    _state.value = MqttConnectionState.ERROR
                    _lastError.value = "MQTT CONNECT 超时（${config.connectionTimeoutSeconds}s 内 broker 未回应）" +
                        "——TCP 已通但无 CONNACK，通常是 broker 侧对该来源 IP/网络做了限制或静默丢包"
                    AppLogger.e(TAG, "MQTT 连接失败：CONNECT 超时（无 CONNACK）", _lastError.value)
                    teardownClient()
                    return@launch
                } catch (e: java.util.concurrent.ExecutionException) {
                    // 关键分支：拿到了 CONNACK 但被拒绝（或握手异常）
                    val cause = e.cause ?: e
                    val rc = extractConnAckCode(cause)
                    _state.value = MqttConnectionState.ERROR
                    _lastError.value = "CONNECT 被拒绝" +
                        if (rc != null) "：CONNACK rc=$rc ${connAckMeaning(rc)}"
                        else "（无 CONNACK 返回码，可能为非协议层的连接中断：${cause.javaClass.simpleName}）"
                    AppLogger.e(
                        TAG,
                        "MQTT 连接失败" + if (rc != null) "（CONNACK rc=$rc）" else "（无 CONNACK 码）",
                        "err=${cause.javaClass.simpleName}: ${cause.message}\n${_lastError.value}"
                    )
                    teardownClient()
                    return@launch
                }
                // 成功：whenComplete 中已处理状态流转与订阅，这里只补一条确认日志
                connFuture.whenComplete { _, err ->
                    if (err != null) {
                        _state.value = MqttConnectionState.ERROR
                        val rc = extractConnAckCode(err)
                        _lastError.value = (err.message ?: "连接失败") +
                            if (rc != null) " [CONNACK rc=$rc ${connAckMeaning(rc)}]" else ""
                        AppLogger.e(TAG, "MQTT 连接失败（异步回调）", _lastError.value)
                        return@whenComplete
                    }
                    _state.value = MqttConnectionState.CONNECTED
                    AppLogger.i(TAG, "MQTT 已连接（CONNACK rc=0 接受）")
                    // ④ 订阅
                    val topics = config.resolvedTopics(vin, phone)
                    if (topics.isEmpty()) {
                        AppLogger.i(TAG, "MQTT 已连接，但未配置订阅（请在设置页填写 topic）")
                        return@whenComplete
                    }
                    var okCount = 0
                    topics.forEach { t ->
                        val sub = Mqtt3Subscribe.builder()
                            .topicFilter(t)
                            .qos(qosFromCode(config.qos))
                            .build()
                        c.subscribe(sub) { pub ->
                            val payload: ByteArray = pub.payloadAsBytes
                            handlePublish(pub.topic.toString(), payload, config, onMessage)
                        }.whenComplete { _, e ->
                            if (e != null) {
                                AppLogger.w(TAG, "MQTT 订阅失败: $t", e.message)
                            } else {
                                okCount++
                                AppLogger.i(TAG, "MQTT 订阅成功: $t")
                                if (okCount == topics.size) {
                                    _state.value = MqttConnectionState.SUBSCRIBED
                                    AppLogger.i(TAG, "MQTT 全部订阅就绪（${topics.size} 个 topic）")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _state.value = MqttConnectionState.ERROR
                _lastError.value = e.message ?: "连接异常"
                AppLogger.e(TAG, "MQTT 连接异常", _lastError.value)
            }
        }
    }

    /** 断开并清理（幂等） */
    fun disconnect() {
        closing = true
        connectJob?.cancel()
        scope.launch { teardownClient() }
        _state.value = MqttConnectionState.DISABLED
    }

    private fun teardownClient() {
        closing = true
        // v84：先取出并清空引用，再断开——避免断连回调期间新连接写入 client 后被旧引用覆盖。
        val old = client
        client = null
        runCatching { old?.disconnect() }   // graceful disconnect 不触发自动重连
    }

    /**
     * 解析凭证：优先官方接口；任何失败都回退到「空凭证」（再回退手动账号密码）。
     */
    private suspend fun resolveCredentials(
        config: MqttConfig,
        accessToken: String,
        vin: String
    ): MqttCredential {
        if (!config.useCredentialApi || accessToken.isEmpty()) return MqttCredential()
        return runCatching {
            wulingAPI.fetchMqttCredential(vin, config.credentialApiUrl).getOrNull()
        }.getOrNull() ?: MqttCredential().also {
            AppLogger.w(TAG, "MQTT 凭证接口未返回可用凭证，回退手动账号密码")
        }
    }

    /** 收到一条推送：写原始日志 + 回调调用方 */
    private fun handlePublish(
        topic: String,
        payload: ByteArray?,
        config: MqttConfig,
        onMessage: (MqttMessage) -> Unit
    ) {
        val bytes = payload ?: ByteArray(0)
        val text = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull()
        val json = text?.let {
            runCatching { gson.fromJson(it, Map::class.java) as? Map<String, Any> }.getOrNull()
        }
        if (config.logRawPayload) {
            val hex = bytes.joinToString("") { "%02x".format(it) }.take(240)
            AppLogger.d(
                TAG,
                "收到推送 topic=$topic",
                "hex=${hex}" + (text?.let { "\ntext=$it" } ?: "")
            )
        }
        _lastMessageAt.value = System.currentTimeMillis()
        runCatching { onMessage(MqttMessage(topic, bytes, text, json)) }
            .onFailure { AppLogger.e(TAG, "MQTT 消息回调异常", it.message) }
    }

    private fun qosFromCode(code: Int): MqttQos = MqttQos.fromCode(code) ?: MqttQos.AT_LEAST_ONCE

    /**
     * 从异常链中挖掘 CONNACK 返回码（rc）。
     *
     * HiveMQ 会把 CONNACK rc 包在异常里：常见为 `Mqtt3ConnAckException`
     * （携带 `Mqtt3ConnAckReturnCode`），或 `Mqtt3AuthException`（认证被拒 rc=4/5）。
     * 逐层遍历 cause，优先读 `returnCode.code` 字段，其次用类名兜底判断。
     */
    private fun extractConnAckCode(err: Throwable): Int? {
        var t: Throwable? = err
        var depth = 0
        while (t != null && depth < 8) {
            runCatching {
                val m = t.javaClass.getMethod("getReturnCode")
                val rc = m.invoke(t)
                if (rc != null) {
                    val codeField = runCatching {
                        rc.javaClass.getMethod("getCode").invoke(rc) as? Int
                    }.getOrNull()
                    if (codeField != null) return codeField
                }
            }
            val name = t.javaClass.simpleName
            if (name.contains("Auth")) return 5
            t = t.cause
            depth++
        }
        return null
    }

    /** CONNACK 返回码语义（MQTT 3.1.1 规范） */
    private fun connAckMeaning(rc: Int): String = when (rc) {
        0 -> "连接已接受"
        1 -> "拒绝：协议版本不支持"
        2 -> "拒绝：clientId 不合法"
        3 -> "拒绝：服务不可用"
        4 -> "拒绝：用户名或密码错误"
        5 -> "拒绝：未授权（账号无权限 / 来源受限）"
        else -> "拒绝：未知码 $rc"
    }

    fun destroy() {
        teardownClient()
        scope.cancel()
    }
}

/**
 * 一条 MQTT 推送事件的承载（不解析业务字段，原样交给调用方）。
 */
class MqttMessage(
    val topic: String,
    val payload: ByteArray,
    val text: String?,
    val json: Map<String, Any>?
)
