package com.open.wuling.data.mqtt

import com.open.wuling.data.api.APIConfig
import java.util.UUID

/**
 * v74：MQTT 实时推送「可配置框架」的核心配置。
 *
 * 情报来源（v74 反编译**未加固** APK 得到完整业务源码，已固化在 v74-mqtt-情报与实测结论.md）：
 * 官方实现用 Eclipse Paho mqttv3，broker / 凭证接口 / topic 结构 / clientId 规则 / 连接选项
 * **全部已 100% 静态确认**，无需再靠真机抓包反推。
 *
 * 已确认的硬参数：
 *   broker       tcp://parkingdata.sgmwcloud.com.cn:1883（明文 TCP，非 TLS）
 *   凭证接口     POST https://openapi.baojun.net/junApi/sgmw/base/mqtt/auth
 *                body {"vin":"<VIN>"} → {"result":true,"data":{"token":"<32位hex>"}}
 *   clientId     {vin}_{手机号后4位}
 *   topic        {vin}/prod/sgmw/vehicle/app/status
 *                {vin}/prod/sgmw/vehicle/control
 *                {vin}/prod/sgmw/vehicle/car_check_authorize/business
 *                {vin}/prod/sgmw/vehicle/car_parking_notify/business
 *   QoS          {1,1,1,1}
 *   连接选项     cleanSession=true / autoReconnect=false / connTimeout=15 /
 *                keepAlive=60 / MQTT 3.1.1
 *   凭证持久化   SharedPreferences "wuling_mqtt_v2"，key=VIN，
 *                value={"username":<token>,"password":...,"clientId":...}
 *
 * ⚠️ 已知阻塞：实测 broker 对任意凭证（含空认证）均返回 CONNACK rc=5 未授权，
 *   疑似来源白名单/地域限制或账号无车控权限。故仍保留「手动填凭证」兜底。
 */
data class MqttConfig(
    /** 总开关：关闭则完全不连 broker，回退到原有 30s 轮询 */
    val enabled: Boolean = false,

    /** broker 地址，支持 tcp://host:port / ssl://host:port / host:port */
    val brokerUrl: String = "tcp://parkingdata.sgmwcloud.com.cn:1883",

    /** 是否调用官方凭证接口换取 MQTT 用户名/密码（true 时忽略下方手动 username/password） */
    val useCredentialApi: Boolean = true,

    /**
     * 官方 MQTT 凭证接口。
     * v73 修正：反编译确认路径为 `/junApi/sgmw` + `/base/mqtt/auth`（此前误用 /sgmw/base/...）。
     */
    val credentialApiUrl: String = "https://openapi.baojun.net/junApi/sgmw/base/mqtt/auth",

    /** 手动用户名（useCredentialApi=false 时生效） */
    val username: String = "",

    /** 手动密码（useCredentialApi=false 时生效） */
    val password: String = "",

    /**
     * clientId 模板，支持占位符：
     *   {vin}    当前车辆 VIN
     *   {phone4} 登录手机号后 4 位（官方规则：{vin}_{手机号后4位}）
     *   {uuid}   每次连接生成的 8 位随机串（避免 broker 拒绝重复 clientId）
     *   {imei}   设备 IMEI（APIConfig.deviceImei）
     *   {random} 8 位随机字母数字
     * v74：默认值已改为官方实证规则 `{vin}_{phone4}`。
     */
    val clientIdTemplate: String = "{vin}_{phone4}",

    /**
     * 订阅主题，每行一个，支持 {vin} 占位符。
     * v74：已按未加固 APK 的 `MqttConfig.topicXxx(vin)` 结果填入带 VIN 前缀的真实 topic。
     */
    val subscriptions: String =
        "{vin}/prod/sgmw/vehicle/app/status\n" +
        "{vin}/prod/sgmw/vehicle/control\n" +
        "{vin}/prod/sgmw/vehicle/car_check_authorize/business\n" +
        "{vin}/prod/sgmw/vehicle/car_parking_notify/business",

    /** 订阅 QoS（0/1/2）。v74：官方 topicQos 数组实测为 {1,1,1,1} */
    val qos: Int = 1,

    /** keep-alive 秒数。v74：官方 setKeepAliveInterval(60) */
    val keepAliveSeconds: Int = 60,

    /** 连接超时秒数。v74：官方 setConnectionTimeout(15) */
    val connectionTimeoutSeconds: Int = 15,

    /**
     * HiveMQ 内置自动重连（断线后指数退避重连）。
     * 注：官方 Paho 侧是 setAutomaticReconnect(false) + 自研 scheduleReconnect 重试，
     * 我方用 HiveMQ 的自动重连更省事，语义等价。
     */
    val reconnectEnabled: Boolean = true,

    /**
     * 收到任意推送即强制刷新车况（走已验证的 REST 轮询链路把整车状态拉全）。
     * 这是「车况一变立刻变」的最稳做法——无需解析 protobuf 即可秒级感知变化。
     * 配合 logRawPayload 可在调试日志里看到原始报文，后续据此补全 protobuf 直解析。
     */
    val forceRefreshOnMessage: Boolean = true,

    /** 把收到的原始报文（hex + 尝试 UTF-8 文本）写入「调试日志」，供真机调参 */
    val logRawPayload: Boolean = true
) {
    companion object {
        val DEFAULTS = MqttConfig()

        // ── v74 未加固 APK 确认的官方 topic 后缀（前面需拼 `{vin}/`） ──
        /** 车况推送（对应官方 MqttConfig.topicStatus(vin)） */
        const val TOPIC_STATUS_SUFFIX = "/prod/sgmw/vehicle/app/status"

        /** 控制指令回执（对应 topicControl(vin)） */
        const val TOPIC_CONTROL_SUFFIX = "/prod/sgmw/vehicle/control"

        /** 授权校验（对应 topicAuthorize(vin)，一键启动等需先授权） */
        const val TOPIC_AUTHORIZE_SUFFIX = "/prod/sgmw/vehicle/car_check_authorize/business"

        /** 停车通知（对应 topicParking(vin)） */
        const val TOPIC_PARKING_SUFFIX = "/prod/sgmw/vehicle/car_parking_notify/business"

        // ── 完整 topic 构造（与官方 MqttConfig.topicXxx(vin) 逐字一致） ──
        fun topicStatus(vin: String) = "$vin$TOPIC_STATUS_SUFFIX"
        fun topicControl(vin: String) = "$vin$TOPIC_CONTROL_SUFFIX"
        fun topicAuthorize(vin: String) = "$vin$TOPIC_AUTHORIZE_SUFFIX"
        fun topicParking(vin: String) = "$vin$TOPIC_PARKING_SUFFIX"

        /** 官方全部 4 个 topic（按官方 topicQos 顺序，均为 QoS 1） */
        fun allTopics(vin: String) =
            listOf(topicStatus(vin), topicControl(vin), topicAuthorize(vin), topicParking(vin))

        /** 默认订阅串（带 {vin} 占位符，供设置页展示） */
        val DEFAULT_SUBSCRIPTIONS =
            "{vin}$TOPIC_STATUS_SUFFIX\n" +
            "{vin}$TOPIC_CONTROL_SUFFIX\n" +
            "{vin}$TOPIC_AUTHORIZE_SUFFIX\n" +
            "{vin}$TOPIC_PARKING_SUFFIX"
    }

    /**
     * 展开模板中的占位符。
     * {uuid}/{random} 每次调用都会重新生成，保证 clientId 唯一；
     * {phone4} 取登录手机号后 4 位（取不到则退化为空串，clientId 变成 `{vin}_`）。
     */
    fun expand(template: String, vin: String, phone: String = ""): String {
        val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val rnd = (1..8).joinToString("") { letters.random().toString() }
        val phone4 = phone.filter { it.isDigit() }.takeLast(4)
        return template
            .replace("{vin}", vin)
            .replace("{phone4}", phone4)
            .replace("{uuid}", UUID.randomUUID().toString().replace("-", "").take(8))
            .replace("{random}", rnd)
            .replace("{imei}", APIConfig.deviceImei)
    }

    /** 解析 broker 地址为 (host, port, useTls) */
    fun brokerHostPort(): Triple<String, Int, Boolean> {
        var s = brokerUrl.trim()
        var useTls = false
        when {
            s.startsWith("ssl://") || s.startsWith("tls://") -> {
                useTls = true
                s = s.removePrefix("ssl://").removePrefix("tls://")
            }
            s.startsWith("tcp://") -> s = s.removePrefix("tcp://")
        }
        val idx = s.lastIndexOf(':')
        val host = if (idx > 0) s.substring(0, idx) else s
        val port = if (idx > 0) s.substring(idx + 1).toIntOrNull() ?: 1883 else 1883
        return Triple(host, port, useTls)
    }

    /** 按官方规则生成 clientId（{vin}_{手机号后4位}） */
    fun resolvedClientId(vin: String, phone: String = ""): String =
        expand(clientIdTemplate, vin, phone)

    /** 把 subscriptions 文本解析为去重后的 topic 列表（展开 {vin}） */
    fun resolvedTopics(vin: String, phone: String = ""): List<String> =
        subscriptions.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { expand(it, vin, phone) }
            .distinct()
            .toList()
}

/**
 * 从官方凭证接口 / 手动配置解析出的 MQTT 登录三要素。
 *
 * v74 未加固 APK 实证（`MqttAuthResponse(token, password, clientId)`）：
 *   - 凭证接口线上实际只返回 `token`（`password`/`clientId` 为空，第 3 参有默认值）
 *   - 官方把 **token 放在 username 位置**：写入 `wuling_mqtt_v2` 时
 *     `{"username":<token>, "password":..., "clientId":...}`
 *   - 故此处 `username` 默认取 token；password/clientId 为空时由上层按官方规则补齐。
 */
data class MqttCredential(
    val username: String = "",
    val password: String = "",
    val clientId: String? = null,
    val topic: String? = null
)

/**
 * MQTT 连接状态（供设置页 UI 与 AppState 展示）。
 */
enum class MqttConnectionState {
    DISABLED,       // 功能关闭
    DISCONNECTED,   // 未连接
    CONNECTING,     // 建连中
    CONNECTED,      // 已连接（已建连，可能尚未订阅）
    SUBSCRIBED,     // 已连接且至少一个 topic 订阅成功
    ERROR,          // 连接/订阅出错（detail 含原因）
    RECONNECTING;   // 自动重连中

    val isActive: Boolean get() = this == CONNECTED || this == SUBSCRIBED
    val isError: Boolean get() = this == ERROR
}
