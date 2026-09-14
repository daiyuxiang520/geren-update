package com.open.wuling.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.open.wuling.AppState

/**
 * 版本更新弹窗（App 内自动更新）。
 * - 检测到大版本时由 AppState.pendingUpdate 驱动显示
 * - 下载中显示进度条，禁用按钮
 * - 失败时显示错误并提供「改用浏览器下载」兜底
 * - forceUpdate 时不可关闭、无「稍后」按钮
 */
@Composable
fun UpdateDialog(appState: AppState) {
    val info by appState.pendingUpdate.collectAsState()
    val progress by appState.updateProgress.collectAsState()
    val error by appState.updateError.collectAsState()

    if (info == null) return

    val downloading = progress != null
    val force = info!!.forceUpdate

    AlertDialog(
        onDismissRequest = {
            if (!force) appState.dismissUpdate()
        },
        confirmButton = {
            Button(
                enabled = !downloading,
                onClick = { appState.startUpdateInstall() }
            ) {
                Text(if (downloading) "下载中…" else if (error != null) "重试" else "立即更新")
            }
        },
        dismissButton = if (force || downloading) null else {
            {
                TextButton(onClick = { appState.dismissUpdate() }) {
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
                if (downloading) {
                    LinearProgressIndicator(
                        progress = { progress!! / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("下载中 ${progress}%", fontSize = 13.sp)
                }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { appState.openApkInBrowser() }) {
                        Text("改用浏览器下载")
                    }
                }
            }
        }
    )
}
