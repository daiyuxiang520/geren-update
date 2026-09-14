package com.open.wuling

import android.app.Application
import android.os.Build
import android.util.Log
import com.umeng.analytics.MobclickAgent
import com.umeng.commonsdk.UMConfigure
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WulingApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 友盟+ 移动统计（U-App）轻量直连初始化
        // 合规提示：工信部要求「用户同意隐私政策前不得采集个人信息」。
        // 此处采用轻量直连（preInit + init 直接调用），未做运行时同意门，
        // 仅个人使用可接受；若上架国内应用商店需补齐隐私同意弹窗后再 init。
        val umengAppKey = BuildConfig.UMENG_APPKEY
        if (umengAppKey.isNotBlank()) {
            // preInit 不采集、不上报；正式 init 开始统计
            UMConfigure.preInit(this, umengAppKey, "Umeng")
            UMConfigure.init(
                this,
                umengAppKey,
                "Umeng",
                UMConfigure.DEVICE_TYPE_PHONE,
                null
            )
            UMConfigure.setEncryptEnabled(true) // 加密传输
            // 页面统计走手动模式（Compose 单 Activity，由 UmengPageView 埋点）
            MobclickAgent.setPageCollectionMode(MobclickAgent.PageMode.MANUAL)
        }

        // Set crash handler for non-recovery process
        if (!isRecoveryProcess()) {
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                Log.e("WulingApp", "Uncaught exception in thread ${thread.name}", throwable)
            }
        }
    }

    private fun isRecoveryProcess(): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= 28) {
            android.os.Process.myProcessName()
        } else {
            packageName
        }
        return processName?.endsWith(":recovery") == true
    }
}
