package com.open.wuling.analytics

import android.content.Context
import android.util.Log
import com.open.wuling.BuildConfig
import com.umeng.analytics.MobclickAgent
import com.umeng.commonsdk.UMConfigure
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 友盟+ U-App 初始化器（合规延迟初始化）。
 *
 * ## 为什么单独抽出来
 * 原实现把 `UMConfigure.init` 放在 `Application.onCreate` 里**无条件立即执行**，
 * 这在「用户同意隐私政策前不得初始化采集 SDK」的合规要求下是不允许的。
 * 现改为：由 UI 层在用户点「同意」后调用 [initIfAgreed]，或冷启动时若已同意
 * 则自动补一次 init。
 *
 * ## 崩溃上报说明
 * 友盟 `UMConfigure.init` 内部会注册自己的 `UncaughtExceptionHandler` 用于崩溃
 * 采集。若业务方在 init **之后**再 `Thread.setDefaultUncaughtExceptionHandler`，
 * 会把友盟的处理器**覆盖掉**，导致崩溃上报失效。因此这里在 init 后**主动把
 * 友盟的处理器作为「下游」串联**，保证既保留业务日志、又不丢友盟崩溃上报。
 */
object UmengInitializer {

    private const val TAG = "UmengInitializer"
    private val inited = AtomicBoolean(false)

    /** 友盟初始化后实际生效的默认异常处理器（init 时抓取，作为链路末端） */
    @Volatile
    private var umengHandler: Thread.UncaughtExceptionHandler? = null

    /**
     * 若用户已同意隐私政策且配置了 AppKey，则初始化友盟。
     * 幂等：重复调用只会真正初始化一次。
     *
     * @param agreedByUser 本次是否由用户在弹窗中刚点击「同意」
     */
    fun initIfAgreed(context: Context, agreedByUser: Boolean = false) {
        val appKey = BuildConfig.UMENG_APPKEY
        if (appKey.isBlank()) {
            Log.i(TAG, "未配置 UMENG_APPKEY，跳过友盟初始化")
            return
        }
        if (!inited.compareAndSet(false, true)) {
            Log.d(TAG, "友盟已初始化，忽略重复调用")
            return
        }

        try {
            // preInit 不采集、不上报；正式 init 开始统计
            UMConfigure.preInit(context, appKey, "Umeng")
            UMConfigure.init(
                context,
                appKey,
                "Umeng",
                UMConfigure.DEVICE_TYPE_PHONE,
                null
            )
            UMConfigure.setEncryptEnabled(true) // 加密传输
            // 页面统计走手动模式（Compose 单 Activity，由 UmengPageView 埋点）
            MobclickAgent.setPageCollectionMode(MobclickAgent.PageMode.MANUAL)

            // 抓取友盟注册的崩溃处理器，用于串联（避免被业务处理器覆盖）
            umengHandler = Thread.getDefaultUncaughtExceptionHandler()

            Log.i(TAG, "友盟初始化完成（agreedByUser=$agreedByUser）")
        } catch (t: Throwable) {
            // 统计 SDK 问题绝不能拖垮 App
            inited.set(false)
            Log.e(TAG, "友盟初始化失败：${t.message}", t)
        }
    }

    /** 友盟是否已初始化（未初始化时事件上报应静默跳过） */
    fun isInited(): Boolean = inited.get()

    /**
     * 把「业务日志处理器」与「友盟崩溃处理器」串联成一条链：
     * 业务处理器先执行（本地打印），随后转发给友盟处理器（崩溃采集）。
     *
     * 必须在 [initIfAgreed] **之后**调用，才能拿到友盟的处理器。
     */
    fun chainCrashHandler(local: Thread.UncaughtExceptionHandler): Thread.UncaughtExceptionHandler =
        Thread.UncaughtExceptionHandler { thread, throwable ->
            try {
                local.uncaughtException(thread, throwable)
            } catch (t: Throwable) {
                Log.e(TAG, "本地崩溃处理器异常：${t.message}", t)
            }
            // 转发给友盟（若已抓取到），保证崩溃上报不丢失
            try {
                umengHandler?.uncaughtException(thread, throwable)
            } catch (t: Throwable) {
                Log.e(TAG, "转发友盟崩溃处理器异常：${t.message}", t)
            }
        }

    /** 直接上抛给友盟处理器（供 Application 未初始化友盟时兜底） */
    fun forwardToUmeng(thread: Thread, throwable: Throwable) {
        umengHandler?.uncaughtException(thread, throwable)
    }
}
