package com.open.wuling.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.open.wuling.AppState
import com.open.wuling.analytics.UmengAnalytics
import com.open.wuling.data.update.UpdateConfig

/**
 * 版本更新弹窗（App 内自动更新）。
 * - 检测到大版本时由 AppState.pendingUpdate 驱动显示
 * - 下载中显示进度条，禁用按钮
 * - 失败时显示错误并提供「改用浏览器下载」兜底
 * - forceUpdate 时不可关闭、无「稍后」按钮
 * - 提供「下载源」手动选择：进入弹窗实时测速各加速源延迟，用户可指定某一源（仅本次生效）
 */
@Composable
fun UpdateDialog(appState: AppState) {
    val context = LocalContext.current
    val info by appState.pendingUpdate.collectAsState()
    val progress by appState.updateProgress.collectAsState()
    val error by appState.updateError.collectAsState()
    val selectedMirror by appState.selectedMirror.collectAsState()
    val mirrorSpeeds by appState.mirrorSpeeds.collectAsState()

    if (info == null) return

    val downloading = progress != null
    val force = info!!.forceUpdate

    // 进入弹窗即测速各加速源延迟（apkUrl 变化时才重新测）
    LaunchedEffect(info!!.apkUrl) {
        appState.measureMirrors(info!!.apkUrl)
    }

    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            if (!force) appState.dismissUpdate()
        },
        confirmButton = {
            Button(
                enabled = !downloading,
                onClick = {
                    UmengAnalytics.event(context, "update_dialog_action", mapOf("result" to if (downloading) "retry" else "confirm"))
                    appState.startUpdateInstall()
                }
            ) {
                Text(if (downloading) "下载中…" else if (error != null) "重试" else "立即更新")
            }
        },
        dismissButton = if (force || downloading) null else {
            {
                TextButton(onClick = {
                    UmengAnalytics.event(context, "update_dialog_action", mapOf("result" to "later"))
                    appState.dismissUpdate()
                }) {
                    Text("稍后")
                }
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("发现新版本 v${info!!.versionName}")
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (info!!.updateLog.isNotBlank()) {
                    Text(info!!.updateLog, fontSize = 14.sp, lineHeight = 20.sp)
                    Spacer(Modifier.height(12.dp))
                }

                // ===== 下载源选择 =====
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("下载源", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "收起" else "选择")
                    }
                }
                if (expanded) {
                    Spacer(Modifier.height(4.dp))
                    MirrorChoiceRow(
                        label = "自动（推荐）",
                        sub = "按实时速度自动择优",
                        subColor = null,
                        selected = selectedMirror == null,
                        onClick = { appState.setSelectedMirror(null) }
                    )
                    UpdateConfig.MIRRORS_PUBLIC.forEach { entry ->
                        val key = if (info!!.apkUrl.startsWith("https://raw.githubusercontent.com/"))
                            "${entry.url}${info!!.apkUrl}" else info!!.apkUrl
                        val ms = mirrorSpeeds[key]
                        MirrorChoiceRow(
                            label = entry.label,
                            sub = formatSpeed(ms),
                            subColor = speedColor(ms),
                            selected = selectedMirror == entry.url,
                            onClick = { appState.setSelectedMirror(entry.url) }
                        )
                    }
                }

                // ===== 下载进度 =====
                if (downloading) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress!! / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("下载中 ${progress}%", fontSize = 13.sp)
                }

                // ===== 错误兜底 =====
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        UmengAnalytics.event(context, "update_dialog_action", mapOf("result" to "browser"))
                        appState.openApkInBrowser()
                    }) {
                        Text("改用浏览器下载")
                    }
                }
            }
        }
    )
}

/**
 * 单个下载源选择行：左侧 RadioButton，右侧名称 + 延迟状态。
 */
@Composable
private fun MirrorChoiceRow(
    label: String,
    sub: String,
    subColor: Color?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp)
            Text(
                sub,
                fontSize = 12.sp,
                color = subColor ?: LocalContentColor.current.copy(alpha = 0.6f)
            )
        }
    }
}

/** 延迟文案：null=不可用，<800ms 快，<3000ms 一般，否则慢 */
private fun formatSpeed(ms: Long?): String = when {
    ms == null -> "不可用"
    ms < 800 -> "快 · ${ms}ms"
    ms < 3000 -> "一般 · ${ms}ms"
    else -> "慢 · ${ms}ms"
}

/** 延迟配色：绿=快，橙=一般，红=慢/不可用 */
private fun speedColor(ms: Long?): Color = when {
    ms == null -> Color(0xFFB00020)
    ms < 800 -> Color(0xFF2E7D32)
    ms < 3000 -> Color(0xFFE65100)
    else -> Color(0xFFB00020)
}
