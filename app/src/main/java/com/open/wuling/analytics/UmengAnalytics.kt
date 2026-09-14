package com.open.wuling.analytics

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
