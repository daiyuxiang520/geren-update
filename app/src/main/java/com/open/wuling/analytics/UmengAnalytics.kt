package com.open.wuling.analytics

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.umeng.analytics.MobclickAgent

/**
 * 友盟+ 页面统计埋点（手动模式）。
 *
 * 在 Compose 屏幕 composable 内调用一次即可：屏幕进入时 onPageStart，
 * 离开（dispose）时 onPageEnd。因单 Activity + 底部 tab 切换时屏幕 composable
 * 会真实 dispose/mount，可正确统计页面浏览与时长。
 *
 * @param pageName 页面名称（友盟后台展示用，建议与 tab/页面一一对应）
 */
@Composable
fun UmengPageView(pageName: String) {
    DisposableEffect(pageName) {
        MobclickAgent.onPageStart(pageName)
        onDispose {
            MobclickAgent.onPageEnd(pageName)
        }
    }
}

/**
 * 友盟事件与错误上报封装（档位1：错误日志 + 关键节点埋点，零服务端）。
 *
 * 统一入口的好处：
 * 1. **未初始化即静默跳过**——用户未同意隐私政策时友盟未 init，此时上报不能崩；
 * 2. **异常吞掉**——统计 SDK 任何问题都不能影响主流程；
 * 3. **参数脱敏**——手机号等敏感信息统一走 [maskPhone]。
 */
object UmengAnalytics {

    /** 通用事件上报（带 KV 参数）。友盟未初始化时静默跳过。 */
    fun event(context: Context, eventId: String, params: Map<String, String> = emptyMap()) {
        if (!UmengInitializer.isInited()) return
        try {
            MobclickAgent.onEvent(context, eventId, params)
        } catch (t: Throwable) {
            // 统计失败不影响业务
        }
    }

    /** 计数型事件（无参数） */
    fun event(context: Context, eventId: String) {
        if (!UmengInitializer.isInited()) return
        try {
            MobclickAgent.onEvent(context, eventId)
        } catch (t: Throwable) {
            // 统计失败不影响业务
        }
    }

    /**
     * 错误上报：进入友盟「错误分析」。
     * 只上报**摘要文本**（友盟对错误信息有长度限制），详细堆栈交给友盟自带的崩溃采集。
     */
    fun reportError(context: Context, summary: String) {
        if (!UmengInitializer.isInited()) return
        try {
            MobclickAgent.reportError(context, summary.take(512))
        } catch (t: Throwable) {
            // 统计失败不影响业务
        }
    }

    /** 手机号脱敏：138****8000；非 11 位则只留首尾 */
    fun maskPhone(phone: String?): String {
        if (phone.isNullOrBlank()) return ""
        return if (phone.length == 11) {
            "${phone.take(3)}****${phone.takeLast(4)}"
        } else {
            phone.take(1) + "***" + phone.takeLast(1)
        }
    }
}
