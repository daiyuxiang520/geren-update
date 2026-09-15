package com.open.wuling.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 隐私政策同意门（首启必弹，不可点击外部/返回键关闭）。
 *
 * 合规依据：工信部《APP 用户权益保护测评规范》要求「用户同意隐私政策前，
 * App 不得初始化采集类 SDK」。因此本弹窗必须在友盟 `init` **之前**展示，
 * 用户点「同意并继续」后才初始化友盟并持久化同意状态。
 *
 * @param onAgree   用户同意回调（写 DataStore + 触发友盟 init）
 * @param onDisagree 用户选择「暂不同意」（可退出或仅关闭，由调用方决定）
 */
@Composable
fun PrivacyConsentDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    Dialog(
        onDismissRequest = { /* 隐私门不允许点外部/返回键直接关闭 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "隐私政策与用户协议",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = PRIVACY_TEXT,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = onAgree,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("同意并继续", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(Modifier.height(4.dp))

                TextButton(onClick = onDisagree, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "暂不同意",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private const val PRIVACY_TEXT = """欢迎使用「五菱车控」。

为保障你的知情权，请在开始使用前阅读以下说明：

一、我们收集哪些信息
1. 设备信息：设备型号、系统版本、应用版本号，用于兼容性适配与统计分析。
2. 账号信息：你主动填写的手机号与登录凭证，仅保存在本机与官方服务端，用于车辆远程控制。
3. 车辆信息：你绑定车辆的 VIN、车型与状态数据，用于展示与控制。
4. 运行日志：应用运行中的错误与调试日志，用于问题排查。

二、我们如何使用这些信息
1. 提供车辆状态查询、远程控制、蓝牙无感控车等核心功能。
2. 通过第三方统计服务（友盟+ U-App）进行匿名的安装量、版本分布与崩溃分析，以改进产品质量。

三、第三方 SDK 说明
本应用集成了友盟+ 移动统计 SDK（U-App），用于匿名统计与崩溃上报。在你点击「同意并继续」之前，该 SDK 不会初始化、不会采集任何信息。

四、你的权利
你可以随时在「我的」页面退出登录以清除本机凭证；卸载应用即可清除本机全部数据。

五、特别说明
本应用为个人学习与自用项目，非官方应用。车辆控制存在网络与设备限制，请以官方 App 为准。

点击「同意并继续」即表示你已阅读并同意上述内容。"""
