package com.open.wuling.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.vehicleAlertDataStore: DataStore<Preferences> by preferencesDataStore(name = "vehicle_alert_settings")

/**
 * 「离车提醒」偏好持久化（v61）。
 *
 * 控制 App 内车辆状态检测提醒：
 * - 总开关 + 三类异常项目（车窗 / 车门锁 / 后备箱）各自可关；
 * - 默认全部开启——该功能的意图就是"忘了关窗锁门时能被提醒"，关闭需要用户主动操作；
 * - 仅控制系统通知与详情页提醒条的数据来源开关，桌面小组件的异常角标不在此列
 *   （小组件是常驻桌面的被动展示，跟随最新状态显示，无需开关）。
 */
@Singleton
class VehicleAlertPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        val ALERT_ENABLED = booleanPreferencesKey("alert_enabled")
        val ALERT_WINDOWS = booleanPreferencesKey("alert_windows")
        val ALERT_DOORS = booleanPreferencesKey("alert_doors")
        val ALERT_TRUNK = booleanPreferencesKey("alert_trunk")

        /** 写盘用独立 scope（设置项低频写入，fire-and-forget 足够） */
        private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private val store get() = context.vehicleAlertDataStore

    /** 总开关（默认开） */
    val enabledFlow: Flow<Boolean> = store.data.map { it[ALERT_ENABLED] ?: true }

    /** 车窗未关提醒（默认开） */
    val windowsFlow: Flow<Boolean> = store.data.map { it[ALERT_WINDOWS] ?: true }

    /** 车门未锁提醒（默认开） */
    val doorsFlow: Flow<Boolean> = store.data.map { it[ALERT_DOORS] ?: true }

    /** 后备箱未关提醒（默认开） */
    val trunkFlow: Flow<Boolean> = store.data.map { it[ALERT_TRUNK] ?: true }

    fun setEnabled(v: Boolean) = writeScope.launch { store.edit { it[ALERT_ENABLED] = v } }

    fun setWindows(v: Boolean) = writeScope.launch { store.edit { it[ALERT_WINDOWS] = v } }

    fun setDoors(v: Boolean) = writeScope.launch { store.edit { it[ALERT_DOORS] = v } }

    fun setTrunk(v: Boolean) = writeScope.launch { store.edit { it[ALERT_TRUNK] = v } }
}
