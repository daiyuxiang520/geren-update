package com.open.wuling

import android.app.Application
import android.os.Build
import android.util.Log
import com.open.wuling.analytics.UmengInitializer
import com.open.wuling.data.local.PrivacyPreferences
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class WulingApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        val privacy = PrivacyPreferences(this)

        // 让 AppLogger 的 WARN/ERROR 具备上报友盟的通道（上报时会自行判断友盟是否已 init）
        com.open.wuling.util.AppLogger.attachContext(this)

        // 1) 崩溃处理器：本地日志 + 转发友盟（串联，不覆盖）
        //    注意：必须在友盟 init 之前设置本地部分，init 之后由 UmengInitializer
        //    抓取友盟处理器并串联，见下方 init 调用。
        if (!isRecoveryProcess()) {
            val local = Thread.UncaughtExceptionHandler { thread, throwable ->
                Log.e("WulingApp", "Uncaught exception in thread ${thread.name}", throwable)
            }
            Thread.setDefaultUncaughtExceptionHandler(local)

            // 2) 友盟延迟初始化：仅当用户已同意隐私政策时才 init
            //    合规要求：同意前不得初始化任何采集 SDK。
            appScope.launch {
                val agreed = try {
                    privacy.isAgreed()
                } catch (t: Throwable) {
                    Log.e("WulingApp", "读取隐私同意状态失败：${t.message}", t)
                    false
                }
                if (agreed) {
                    UmengInitializer.initIfAgreed(this@WulingApplication, agreedByUser = false)
                    // 串联：本地日志 + 友盟崩溃采集
                    Thread.setDefaultUncaughtExceptionHandler(
                        UmengInitializer.chainCrashHandler(local)
                    )
                } else {
                    Log.i("WulingApp", "用户尚未同意隐私政策，跳过友盟初始化")
                }
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
