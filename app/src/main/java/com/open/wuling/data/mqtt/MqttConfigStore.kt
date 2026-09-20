package com.open.wuling.data.mqtt

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.mqttDataStore: DataStore<Preferences> by preferencesDataStore(name = "wuling_mqtt")

/**
 * v73：MQTT 配置持久化（DataStore Preferences）。
 * 全部参数可持久化，App 重启后自动恢复，用户调过的 topic / 凭证接口都留得住。
 */
@Singleton
class MqttConfigStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("mqtt_enabled")
        val BROKER_URL = stringPreferencesKey("mqtt_broker_url")
        val USE_CREDENTIAL_API = booleanPreferencesKey("mqtt_use_credential_api")
        val CREDENTIAL_API_URL = stringPreferencesKey("mqtt_credential_api_url")
        val USERNAME = stringPreferencesKey("mqtt_username")
        val PASSWORD = stringPreferencesKey("mqtt_password")
        val CLIENT_ID_TEMPLATE = stringPreferencesKey("mqtt_client_id_template")
        val SUBSCRIPTIONS = stringPreferencesKey("mqtt_subscriptions")
        val QOS = intPreferencesKey("mqtt_qos")
        val KEEP_ALIVE = intPreferencesKey("mqtt_keep_alive")
        val RECONNECT = booleanPreferencesKey("mqtt_reconnect")
        val FORCE_REFRESH = booleanPreferencesKey("mqtt_force_refresh")
        val LOG_RAW = booleanPreferencesKey("mqtt_log_raw")
    }

    val flow: Flow<MqttConfig> = context.mqttDataStore.data.map { p ->
        // v74：订阅主题与 clientId 的旧默认值（v73 预填的「无 VIN 前缀 topic」和「wuling_{uuid}」）
        // 会覆盖新默认值，导致升级后仍用错规则；命中旧默认值即视为「用户没改过」，回落到新默认。
        val savedSubs = p[Keys.SUBSCRIPTIONS] ?: ""
        val savedClientId = p[Keys.CLIENT_ID_TEMPLATE] ?: MqttConfig.DEFAULTS.clientIdTemplate
        val legacySubs = V73_LEGACY_SUBSCRIPTIONS
        MqttConfig(
            enabled = p[Keys.ENABLED] ?: false,
            brokerUrl = p[Keys.BROKER_URL] ?: MqttConfig.DEFAULTS.brokerUrl,
            useCredentialApi = p[Keys.USE_CREDENTIAL_API] ?: true,
            credentialApiUrl = p[Keys.CREDENTIAL_API_URL] ?: MqttConfig.DEFAULTS.credentialApiUrl,
            username = p[Keys.USERNAME] ?: "",
            password = p[Keys.PASSWORD] ?: "",
            clientIdTemplate = if (savedClientId == V73_LEGACY_CLIENT_ID) MqttConfig.DEFAULTS.clientIdTemplate
                               else savedClientId,
            subscriptions = if (savedSubs.isBlank() || savedSubs == legacySubs) MqttConfig.DEFAULTS.subscriptions
                            else savedSubs,
            qos = p[Keys.QOS] ?: 1,
            keepAliveSeconds = p[Keys.KEEP_ALIVE] ?: MqttConfig.DEFAULTS.keepAliveSeconds,
            connectionTimeoutSeconds = MqttConfig.DEFAULTS.connectionTimeoutSeconds,
            reconnectEnabled = p[Keys.RECONNECT] ?: true,
            forceRefreshOnMessage = p[Keys.FORCE_REFRESH] ?: true,
            logRawPayload = p[Keys.LOG_RAW] ?: true
        )
    }

    companion object {
        /** v73 的 clientId 默认值（无 VIN 前缀，官方实测为非法） */
        private const val V73_LEGACY_CLIENT_ID = "wuling_{uuid}"

        /** v73 的订阅默认值（缺 `{vin}/` 前缀，官方实证需带） */
        private val V73_LEGACY_SUBSCRIPTIONS =
            "/prod/sgmw/vehicle/app/status\n" +
            "/prod/sgmw/vehicle/control\n" +
            "/prod/sgmw/vehicle/car_check_authorize/business\n" +
            "/prod/sgmw/vehicle/car_parking_notify/business"
    }

    suspend fun get(): MqttConfig = flow.first()

    suspend fun save(cfg: MqttConfig) {
        context.mqttDataStore.edit { p ->
            p[Keys.ENABLED] = cfg.enabled
            p[Keys.BROKER_URL] = cfg.brokerUrl
            p[Keys.USE_CREDENTIAL_API] = cfg.useCredentialApi
            p[Keys.CREDENTIAL_API_URL] = cfg.credentialApiUrl
            p[Keys.USERNAME] = cfg.username
            p[Keys.PASSWORD] = cfg.password
            p[Keys.CLIENT_ID_TEMPLATE] = cfg.clientIdTemplate
            p[Keys.SUBSCRIPTIONS] = cfg.subscriptions
            p[Keys.QOS] = cfg.qos
            p[Keys.KEEP_ALIVE] = cfg.keepAliveSeconds
            p[Keys.RECONNECT] = cfg.reconnectEnabled
            p[Keys.FORCE_REFRESH] = cfg.forceRefreshOnMessage
            p[Keys.LOG_RAW] = cfg.logRawPayload
        }
    }

    /** 仅更新总开关（on/off 即时生效） */
    suspend fun setEnabled(enabled: Boolean) {
        save(get().copy(enabled = enabled))
    }
}
