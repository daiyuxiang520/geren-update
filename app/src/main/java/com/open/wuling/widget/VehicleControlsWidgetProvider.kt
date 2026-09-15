package com.open.wuling.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.open.wuling.R
import com.open.wuling.receiver.VehicleActionReceiver

/**
 * 车辆快捷控制条（4×1，v64）。
 *
 * 与 2×3 状态卡[VehicleStatusWidgetProvider]分工：状态卡只负责"看"，控制条只负责"点"。
 * 分开的原因很实际——状态卡内容高度已经接近 2 行格子的物理上限，再塞按钮必被裁掉
 * （v63 试过，按钮只露出上缘）。独立成 4×1 后两边都不挤。
 *
 * VIN 从 AppState 落盘的车辆缓存（vehicle_cache/last_vehicle）里读：
 * 小组件是独立入口，进程冷启动时拿不到 App 内存里的 vehicle，只能读持久化缓存。
 * 因此用户打开一次 App 并完成刷新后，控制条才真正可用（之前点击会提示未就绪）。
 */
class VehicleControlsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        render(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        private const val TAG = "VehicleControlsWidget"

        /** 读取当前车辆 VIN（缓存缺失时返回空串） */
        fun readVin(context: Context): String = try {
            val raw = context.getSharedPreferences("vehicle_cache", Context.MODE_PRIVATE)
                .getString("last_vehicle", null) ?: ""
            if (raw.isBlank()) "" else org.json.JSONObject(raw).optString("vin", "")
        } catch (e: Exception) {
            android.util.Log.d(TAG, "readVin failed: ${e.message}")
            ""
        }

        /** 重新绑定所有控制条实例（App 内刷新成功后调用，让新 VIN 生效） */
        fun pushAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, VehicleControlsWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val vin = readVin(context)
            val views = RemoteViews(context.packageName, R.layout.widget_vehicle_controls)
            val r = VehicleActionReceiver

            views.setOnClickPendingIntent(
                R.id.widget_ctrl_lock,
                r.createPendingIntent(context, r.CMD_LOCK, vin, 9301)
            )
            views.setOnClickPendingIntent(
                R.id.widget_ctrl_window,
                r.createPendingIntent(context, r.CMD_CLOSE_WINDOW, vin, 9302)
            )
            views.setOnClickPendingIntent(
                R.id.widget_ctrl_find,
                r.createPendingIntent(context, r.CMD_FIND_CAR, vin, 9303)
            )

            ids.forEach { manager.updateAppWidget(it, views) }
        }
    }
}
