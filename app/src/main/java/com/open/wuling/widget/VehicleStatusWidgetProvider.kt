package com.open.wuling.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.open.wuling.MainActivity
import com.open.wuling.R
import com.open.wuling.data.api.APIConfig
import com.open.wuling.data.api.WulingAPI
import com.open.wuling.data.model.Vehicle
import com.open.wuling.data.repository.VehicleRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 「车辆状态」桌面小组件（2×3）
 *
 * 数据链路:
 * - App 内每次车辆状态刷新成功（含 30 秒自动刷新）→ saveCacheAndPush 写入缓存并即时刷新桌面
 * - 系统每 30 分钟定时 onUpdate → 用缓存重绘
 * - 点击卡片 → 打开 App 主页
 *
 * 渲染: RemoteViews，无数据一律显示 --；
 * 状态行带图标: ⚡上电/🔌下电、🔒已锁/🔓未锁、🪟车窗已关/⚠️车窗未关，
 * 正常绿 #2E9E44 / 提示黄 #C88A00。
 */
class VehicleStatusWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "VehicleStatusWidget"
        private const val PREFS = "widget_vehicle_status"
        private const val KEY_DATA = "data"
        private const val KEY_CAR_URL = "car_url"
        private const val KEY_VIN = "vin"
        private const val CAR_IMG_FILE = "widget_car.png"

        private const val COLOR_OK = -0xd161bc      // 绿 #2E9E44
        private const val COLOR_WARN = -0x377600    // 黄 #C88A00
        private const val COLOR_NONE = -0x656058    // 灰 #9A9FA8
        private const val COLOR_RED = 0xFFE53935.toInt()   // 红 #E53935（v61 异常角标/异常状态行）

        /** 缓存快照（SharedPreferences JSON） */
        private data class Snapshot(
            val totalRange: Int?,
            val socPct: Int?,
            val fuelPct: Int?,
            val hasFuel: Boolean,
            val powerOn: Boolean?,
            val locked: Boolean?,
            val windowsClosed: Boolean?,
            val trunkClosed: Boolean? = null
        )

        // ============== 供 AppState 调用：App 内刷新成功后同步到桌面 ==============

        fun saveCacheAndPush(context: Context, vehicle: Vehicle) {
            try {
                val s = vehicle.status
                val showFuel = vehicle.hasFuel && s.leftFuel > 0
                val total = s.range + if (showFuel) s.oilRange else 0
                val json = JSONObject()
                    .put("totalRange", if (total > 0) total else -1)
                    .put("soc", s.batteryLevel)
                    .put("fuel", if (showFuel) s.leftFuel else -1)
                    .put("powerOn", s.keyStatus == "2")
                    .put("locked", s.isLocked)
                    .put("windowsClosed", !(
                            s.windows.frontLeft || s.windows.frontRight ||
                                    s.windows.rearLeft || s.windows.rearRight))
                    .put("trunkClosed", !s.doors.trunk)
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_DATA, json.toString())
                    .putString(KEY_CAR_URL, vehicle.carInfo?.image ?: "")
                    // v63：快捷控制按钮需要 vin，一并落到小组件自己的缓存里
                    //      （小组件是独立进程入口，不能依赖 App 内存里的 vehicle）
                    .putString(KEY_VIN, vehicle.vin)
                    .apply()
                ensureCarImage(context, vehicle.carInfo?.image ?: "")
                pushAll(context)
            } catch (e: Exception) {
                android.util.Log.d(TAG, "saveCacheAndPush failed: ${e.message}")
            }
        }

        /**
         * 车图下载：URL 变化或本地文件缺失时后台下载，
         * 采样解码到 ~256px 后写入 filesDir/widget_car.png，完成即重绘桌面。
         */
        private fun ensureCarImage(context: Context, url: String) {
            if (url.isBlank()) return
            val f = File(context.filesDir, CAR_IMG_FILE)
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val savedUrl = prefs.getString(KEY_CAR_URL, "")
            if (f.exists() && savedUrl == url) return
            Thread {
                try {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    conn.instanceFollowRedirects = true
                    val bytes = conn.inputStream.use { it.readBytes() }
                    // 两段解码，按目标 256px 采样，避免大图占用内存
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    var sample = 1
                    while (opts.outWidth / sample > 256 || opts.outHeight / sample > 256) sample *= 2
                    val bmp = BitmapFactory.decodeByteArray(
                        bytes, 0, bytes.size,
                        BitmapFactory.Options().apply { inSampleSize = sample }
                    )
                    if (bmp != null) {
                        f.outputStream().use { out -> bmp.compress(
                            android.graphics.Bitmap.CompressFormat.PNG, 90, out) }
                        prefs.edit().putString(KEY_CAR_URL, url).apply()
                        pushAll(context)
                    }
                } catch (e: Exception) {
                    android.util.Log.d(TAG, "car image download failed: ${e.message}")
                }
            }.start()
        }

        /** 重新渲染桌面上所有本小组件实例（用缓存数据） */
        fun pushAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, VehicleStatusWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                render(context, manager, ids)
            }
        }

        /**
         * 请求一次「联网刷新 + 重绘」（v63）。
         * 快捷控制按钮执行完指令后调用：onReceive 收到标准 update 广播会走 refreshFromNetwork，
         * 不必等系统 30 分钟的定时周期，桌面状态能尽快跟上刚下发的操作。
         */
        fun requestRefresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, VehicleStatusWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val intent = Intent(context, VehicleStatusWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }

        private fun readSnapshot(context: Context): Snapshot? = try {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DATA, null)
            if (raw != null) {
                val j = JSONObject(raw)
                Snapshot(
                    totalRange = j.optInt("totalRange", -1).takeIf { it >= 0 },
                    socPct = j.optInt("soc", -1).takeIf { it in 0..100 },
                    fuelPct = j.optInt("fuel", -1).takeIf { it in 0..100 },
                    hasFuel = j.optInt("fuel", -1) >= 0,
                    powerOn = if (j.has("powerOn")) j.optBoolean("powerOn") else null,
                    locked = if (j.has("locked")) j.optBoolean("locked") else null,
                    windowsClosed = if (j.has("windowsClosed")) j.optBoolean("windowsClosed") else null,
                    trunkClosed = if (j.has("trunkClosed")) j.optBoolean("trunkClosed") else null
                )
            } else null
        } catch (e: Exception) {
            null
        }

        // ============== 渲染 ==============

        private fun render(
            context: Context,
            manager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val snap = readSnapshot(context)
            for (id in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_vehicle_status)

                // 总续航
                views.setTextViewText(
                    R.id.widget_range_value,
                    if (snap?.totalRange != null) "总续航 ${snap.totalRange} km" else "总续航 -- km"
                )

                // 车图：本地缓存存在则显示图片，否则回退 emoji
                val carImg = File(context.filesDir, CAR_IMG_FILE)
                if (carImg.exists()) {
                    try {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(carImg.absolutePath, opts)
                        var sample = 1
                        while (opts.outWidth / sample > 128 || opts.outHeight / sample > 128) sample *= 2
                        val bmp = BitmapFactory.decodeFile(
                            carImg.absolutePath,
                            BitmapFactory.Options().apply { inSampleSize = sample }
                        )
                        if (bmp != null) {
                            views.setImageViewBitmap(R.id.widget_car_image, bmp)
                            views.setViewVisibility(R.id.widget_car_emoji, View.GONE)
                            views.setViewVisibility(R.id.widget_car_image, View.VISIBLE)
                        }
                    } catch (e: Exception) {
                        android.util.Log.d(TAG, "car image decode failed: ${e.message}")
                    }
                }

                // 电量
                val soc = snap?.socPct
                views.setTextViewText(R.id.widget_soc_text, if (soc != null) "电量 $soc%" else "电量 --")
                views.setProgressBar(R.id.widget_soc_bar, 100, soc ?: 0, false)

                // 油量（纯电车型无油量数据 → --）
                val fuel = if (snap?.hasFuel == true) snap.fuelPct else null
                views.setTextViewText(R.id.widget_fuel_text, if (fuel != null) "油量 $fuel%" else "油量 --")
                views.setProgressBar(R.id.widget_fuel_bar, 100, fuel ?: 0, false)

                // 右侧三行状态（带图标）: 上电/下电、已锁/未锁、车窗
                val (powerText, powerColor) = when (snap?.powerOn) {
                    true -> "⚡ 上电" to COLOR_OK
                    false -> "🔌 下电" to COLOR_WARN
                    null -> "--" to COLOR_NONE
                }
                views.setTextViewText(R.id.widget_power_text, powerText)
                views.setTextColor(R.id.widget_power_text, powerColor)

                // v61：未锁/车窗未关升级为红色（此前黄色，与「下电」同色分不出轻重）
                val (lockText, lockColor) = when (snap?.locked) {
                    true -> "🔒 已锁" to COLOR_OK
                    false -> "🔓 未锁" to COLOR_RED
                    null -> "--" to COLOR_NONE
                }
                views.setTextViewText(R.id.widget_lock_text, lockText)
                views.setTextColor(R.id.widget_lock_text, lockColor)

                val (winText, winColor) = when (snap?.windowsClosed) {
                    true -> "✅ 车窗已关" to COLOR_OK
                    false -> "⚠️ 车窗未关" to COLOR_RED
                    null -> "--" to COLOR_NONE
                }
                views.setTextViewText(R.id.widget_window_text, winText)
                views.setTextColor(R.id.widget_window_text, winColor)

                // v61：车图右上角红色角标，汇总当前异常项（锁=车门未锁，窗=车窗未关，箱=后备箱未关）
                val badgeParts = buildList {
                    if (snap?.locked == false) add("锁")
                    if (snap?.windowsClosed == false) add("窗")
                    if (snap?.trunkClosed == false) add("箱")
                }
                if (badgeParts.isNotEmpty()) {
                    views.setTextViewText(R.id.widget_alert_badge, "⚠" + badgeParts.joinToString("/"))
                    views.setViewVisibility(R.id.widget_alert_badge, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widget_alert_badge, View.GONE)
                }

                // 点击卡片 → App 主页
                views.setOnClickPendingIntent(R.id.widget_root, mainPendingIntent(context))

                // v63：快捷控制按钮。指令由 VehicleActionReceiver 在后台执行，
                //      完成后它会回调 requestRefresh 让本组件立刻拉一次新状态。
                val vin = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_VIN, "") ?: ""
                if (vin.isNotBlank()) {
                    views.setViewVisibility(R.id.widget_actions, View.VISIBLE)
                    views.setOnClickPendingIntent(
                        R.id.widget_btn_lock,
                        com.open.wuling.receiver.VehicleActionReceiver.createPendingIntent(
                            context, com.open.wuling.receiver.VehicleActionReceiver.CMD_LOCK, vin, 9201
                        )
                    )
                    views.setOnClickPendingIntent(
                        R.id.widget_btn_window,
                        com.open.wuling.receiver.VehicleActionReceiver.createPendingIntent(
                            context, com.open.wuling.receiver.VehicleActionReceiver.CMD_CLOSE_WINDOW, vin, 9202
                        )
                    )
                    views.setOnClickPendingIntent(
                        R.id.widget_btn_find,
                        com.open.wuling.receiver.VehicleActionReceiver.createPendingIntent(
                            context, com.open.wuling.receiver.VehicleActionReceiver.CMD_FIND_CAR, vin, 9203
                        )
                    )
                } else {
                    views.setViewVisibility(R.id.widget_actions, View.GONE)
                }

                manager.updateAppWidget(id, views)
            }
        }

        private fun mainPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // 先用缓存立刻渲染一次，保证桌面立即有内容（避免白屏/--）
        render(context, appWidgetManager, appWidgetIds)

        // 再主动联网拉取最新状态（不依赖 App 进程存活），拉成功后重绘
        val pending = goAsync()
        Thread {
            try {
                refreshFromNetwork(context)
            } catch (e: Exception) {
                android.util.Log.d(TAG, "onUpdate refresh failed: ${e.message}")
            } finally {
                try {
                    pending.finish()
                } catch (e: Exception) {
                    android.util.Log.d(TAG, "pending.finish failed: ${e.message}")
                }
            }
        }.start()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // 系统重绘广播兜底：同样尝试拉一次最新数据
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val pending = goAsync()
            Thread {
                try {
                    refreshFromNetwork(context)
                } catch (_: Exception) {
                } finally {
                    try {
                        pending.finish()
                    } catch (_: Exception) {
                    }
                }
            }.start()
        }
    }

    /**
     * 主动联网拉取车辆状态并更新小组件（不依赖 App 进程存活）。
     *
     * - 未登录 / 无 Token：直接返回，保留缓存渲染结果（显示 --），不报错
     * - 拉取失败（断网等）：保留旧缓存，不覆盖
     * - 超时保护 15 秒，避免系统唤醒后长时间占线程
     */
    private fun refreshFromNetwork(context: Context) {
        // 1) 读取已保存的 Token（DataStore），写入 APIConfig 供请求使用
        val token = readSavedToken(context)
        if (token.isBlank()) {
            android.util.Log.d(TAG, "no token, keep cached render")
            return
        }
        APIConfig.setAccessToken(token)

        // 2) 拉取车辆状态（协程 + 超时），成功则写缓存并重绘
        val vehicle = runBlocking {
            withTimeoutOrNull(15_000L) {
                try {
                    VehicleRepository(WulingAPI())
                        .fetchDefaultVehicleStatusQuick()
                        .getOrNull()
                } catch (e: Exception) {
                    android.util.Log.d(TAG, "fetch status failed: ${e.message}")
                    null
                }
            }
        }

        if (vehicle != null) {
            saveCacheAndPush(context, vehicle)
        } else {
            android.util.Log.d(TAG, "network refresh returned null, keep cached render")
        }
    }

    /** 从 DataStore（wuling_token）读取 access_token，失败返回空串 */
    private fun readSavedToken(context: Context): String = try {
        runBlocking {
            withTimeoutOrNull(5_000L) {
                WidgetTokenStore.read(context).data.first()[WidgetTokenStore.TOKEN_KEY] ?: ""
            }
        } ?: ""
    } catch (e: Exception) {
        android.util.Log.d(TAG, "readSavedToken failed: ${e.message}")
        ""
    }
}

/**
 * 小组件专用的 Token 读取器。
 *
 * 注意：TokenStore 使用 `preferencesDataStore(name="wuling_token")` 委托，
 * 同一进程内不能对同名文件再建第二个委托（会抛 IllegalStateException），
 * 因此这里用 PreferenceDataStoreFactory 直接指向同一落盘文件，并用进程级缓存复用它。
 */
internal object WidgetTokenStore {
    val TOKEN_KEY = stringPreferencesKey("access_token")

    @Volatile
    private var instance: DataStore<Preferences>? = null

    fun read(context: Context): DataStore<Preferences> {
        instance?.let { return it }
        synchronized(this) {
            instance?.let { return it }
            val ds = PreferenceDataStoreFactory.create(
                produceFile = { File(context.applicationContext.filesDir, "datastore/wuling_token.preferences_pb") }
            )
            instance = ds
            return ds
        }
    }
}
