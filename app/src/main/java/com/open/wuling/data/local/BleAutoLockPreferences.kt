package com.open.wuling.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ble_auto_lock")

/**
 * BLE 无感控车配置管理
 */
class BleAutoLockPreferences(private val context: Context) {
    companion object {
        private val KEY_ENABLED = booleanPreferencesKey("enabled")
        private val KEY_BLE_MAC = stringPreferencesKey("ble_mac")
        private val KEY_USER_ID = stringPreferencesKey("ble_user_id")
        private val KEY_COLLECT_TIME = stringPreferencesKey("ble_collect_time")
        private val KEY_KEY_ID = stringPreferencesKey("ble_key_id")
        private val KEY_KEY_TYPE = stringPreferencesKey("ble_key_type")
        private val KEY_KEY_MASTER_RANDOM = stringPreferencesKey("ble_key_master_random")
        private val KEY_END_TIME = stringPreferencesKey("ble_end_time")
        private val KEY_MASTER_KEY = stringPreferencesKey("ble_master_key")
        private val KEY_VIN = stringPreferencesKey("ble_vin")
        private val KEY_UNLOCK_RSSI = intPreferencesKey("unlock_rssi")
        private val KEY_UNLOCK_DURATION = intPreferencesKey("unlock_duration")
        private val KEY_LOCK_RSSI = intPreferencesKey("lock_rssi")
        private val KEY_LOCK_DURATION = intPreferencesKey("lock_duration")
        private val KEY_COOLDOWN_TIME = intPreferencesKey("cooldown_time")
        private val KEY_CONNECT_TIMEOUT = intPreferencesKey("connect_timeout")
        private val KEY_AUTH_TIMEOUT = intPreferencesKey("auth_timeout")
        private val KEY_LOG_ENABLED = booleanPreferencesKey("log_enabled")
        private val KEY_FOREGROUND_SERVICE_ENABLED = booleanPreferencesKey("foreground_service_enabled")

        // 默认值
        const val DEFAULT_UNLOCK_RSSI = -60
        const val DEFAULT_UNLOCK_DURATION = 2
        const val DEFAULT_LOCK_RSSI = -75
        const val DEFAULT_LOCK_DURATION = 3
        const val DEFAULT_COOLDOWN_TIME = 10
        const val DEFAULT_CONNECT_TIMEOUT = 12
        const val DEFAULT_AUTH_TIMEOUT = 10

        /** 全局禁用 v1.0.0 BLE（避免干扰linkey） */
        suspend fun disableGlobally(context: Context) {
            context.dataStore.edit { it[KEY_ENABLED] = false }
        }
    }

    val enabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ENABLED] ?: false
    }

    val bleMac: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_BLE_MAC] ?: ""
    }

    val bleUserId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_USER_ID] ?: ""
    }

    val bleCollectTime: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_COLLECT_TIME] ?: ""
    }

    val bleKeyId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_KEY_ID] ?: ""
    }

    val bleKeyType: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_KEY_TYPE] ?: ""
    }

    val bleKeyMasterRandom: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_KEY_MASTER_RANDOM] ?: ""
    }

    val bleEndTime: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_END_TIME] ?: ""
    }

    val bleMasterKey: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_MASTER_KEY] ?: ""
    }

    val bleVin: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_VIN] ?: ""
    }

    val unlockRssi: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_UNLOCK_RSSI] ?: DEFAULT_UNLOCK_RSSI
    }

    val unlockDuration: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_UNLOCK_DURATION] ?: DEFAULT_UNLOCK_DURATION
    }

    val lockRssi: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_LOCK_RSSI] ?: DEFAULT_LOCK_RSSI
    }

    val lockDuration: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_LOCK_DURATION] ?: DEFAULT_LOCK_DURATION
    }

    val cooldownTime: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_COOLDOWN_TIME] ?: DEFAULT_COOLDOWN_TIME
    }

    val connectTimeout: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_CONNECT_TIMEOUT] ?: DEFAULT_CONNECT_TIMEOUT
    }

    val authTimeout: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTH_TIMEOUT] ?: DEFAULT_AUTH_TIMEOUT
    }

    val logEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_LOG_ENABLED] ?: false
    }

    val foregroundServiceEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_FOREGROUND_SERVICE_ENABLED] ?: false
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ENABLED] = enabled
        }
    }

    suspend fun setBleMac(mac: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BLE_MAC] = mac
        }
    }

    suspend fun setBleKeyData(
        bleMac: String,
        userId: String,
        collectTime: String,
        keyId: String,
        keyType: String,
        keyMasterRandom: String,
        endTime: String,
        masterKey: String,
        vin: String
    ) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BLE_MAC] = bleMac
            preferences[KEY_USER_ID] = userId
            preferences[KEY_COLLECT_TIME] = collectTime
            preferences[KEY_KEY_ID] = keyId
            preferences[KEY_KEY_TYPE] = keyType
            preferences[KEY_KEY_MASTER_RANDOM] = keyMasterRandom
            preferences[KEY_END_TIME] = endTime
            preferences[KEY_MASTER_KEY] = masterKey
            preferences[KEY_VIN] = vin
        }
    }

    suspend fun setUnlockRssi(rssi: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_UNLOCK_RSSI] = rssi
        }
    }

    suspend fun setUnlockDuration(duration: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_UNLOCK_DURATION] = duration
        }
    }

    suspend fun setLockRssi(rssi: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LOCK_RSSI] = rssi
        }
    }

    suspend fun setLockDuration(duration: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LOCK_DURATION] = duration
        }
    }

    suspend fun setCooldownTime(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_COOLDOWN_TIME] = seconds
        }
    }

    suspend fun setConnectTimeout(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CONNECT_TIMEOUT] = seconds
        }
    }

    suspend fun setAuthTimeout(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTH_TIMEOUT] = seconds
        }
    }

    suspend fun setLogEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LOG_ENABLED] = enabled
        }
    }

    suspend fun setForegroundServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FOREGROUND_SERVICE_ENABLED] = enabled
        }
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { preferences ->
            preferences[KEY_UNLOCK_RSSI] = DEFAULT_UNLOCK_RSSI
            preferences[KEY_UNLOCK_DURATION] = DEFAULT_UNLOCK_DURATION
            preferences[KEY_LOCK_RSSI] = DEFAULT_LOCK_RSSI
            preferences[KEY_LOCK_DURATION] = DEFAULT_LOCK_DURATION
            preferences[KEY_COOLDOWN_TIME] = DEFAULT_COOLDOWN_TIME
        }
    }
}
