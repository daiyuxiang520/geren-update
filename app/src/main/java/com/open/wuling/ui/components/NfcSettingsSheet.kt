package com.open.wuling.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.open.wuling.nfc.NfcCarController
import com.open.wuling.nfc.NfcOutcome
import com.open.wuling.ui.theme.PrimaryGreen
import com.open.wuling.ui.theme.PrimaryOrange
import kotlinx.coroutines.launch

/**
 * 「NFC 车控」设置底部弹层（v67）。
 *
 * 内容：启用总开关 / 绑定与重绑标签（写入随机密钥）/ 切换模式说明 /
 * 最近动作状态 / 无标签模拟切换 / 安全说明。
 *
 * 绑定流程：点「绑定」→ [NfcCarController.startBinding] 生成 16 位随机密钥 →
 * 提示用户贴标签 → 系统 NDEF 派发拉起 [com.open.wuling.nfc.NfcTriggerActivity]
 * 写入并回显成功。本层全部状态来自 controller 的 StateFlow：
 * 触发页写完标签后，绑定引导自动切回「已绑定」卡片，无需手动刷新。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcSettingsSheet(
    isOpen: Boolean,
    controller: NfcCarController,
    onClose: () -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val enabled by controller.enabledFlow.collectAsState()
    val bound by controller.boundFlow.collectAsState()
    val lastAction by controller.lastActionFlow.collectAsState()
    val bindingPending by controller.bindingPendingFlow.collectAsState()

    var testing by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = {
            if (bindingPending) controller.cancelBinding()
            onClose()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "NFC 车控",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "碰一下 NFC 标签即可解锁 / 锁车",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            // ===== 启用总开关 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "启用 NFC 车控",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "锁车状态碰=解锁，解锁状态碰=锁车",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = { controller.setEnabled(it) })
            }

            Spacer(Modifier.height(16.dp))

            // ===== 绑定卡（绑定引导 / 未绑定 / 已绑定 三态） =====
            if (bindingPending) {
                val pulse = rememberInfiniteTransition(label = "nfcPulse")
                val alpha by pulse.animateFloat(
                    0.35f, 1f,
                    infiniteRepeatable(tween(900, easing = LinearEasing)),
                    label = "nfcAlpha"
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Nfc,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "请将手机背面贴近 NFC 标签",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "贴近后自动写入密钥并返回（需亮屏）",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { controller.cancelBinding() }) {
                        Text("取消绑定")
                    }
                }
            } else if (!bound) {
                Button(
                    onClick = { controller.startBinding() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("绑定 NFC 标签")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = PrimaryGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "已绑定标签",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "密钥 ••${controller.prefs.boundSecret.takeLast(4)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { controller.startBinding() }) { Text("重绑") }
                    TextButton(onClick = { controller.unbind() }) { Text("解绑") }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ===== 切换模式 + 最近动作 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "切换模式",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "单个标签切换",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "上次操作",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = when (lastAction) {
                        NfcCarController.ACTION_LOCK -> "已锁车"
                        NfcCarController.ACTION_UNLOCK -> "已解锁"
                        else -> "尚未执行"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when (lastAction) {
                        NfcCarController.ACTION_LOCK -> PrimaryOrange
                        NfcCarController.ACTION_UNLOCK -> PrimaryGreen
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            // ===== 模拟切换（无标签调试） =====
            OutlinedButton(
                enabled = !testing,
                onClick = {
                    scope.launch {
                        testing = true
                        when (val o = controller.executeToggle()) {
                            is NfcOutcome.Success ->
                                Toast.makeText(
                                    context,
                                    if (o.unlocked) "已解锁（状态来源：${sourceLabel(o.source)}）"
                                    else "已锁车（状态来源：${sourceLabel(o.source)}）",
                                    Toast.LENGTH_SHORT
                                ).show()
                            is NfcOutcome.Failure ->
                                Toast.makeText(context, "失败：${o.message}", Toast.LENGTH_SHORT).show()
                        }
                        testing = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (testing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("模拟一次切换（无需标签）")
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "安全说明：绑定时会向标签写入一段随机密钥，只有本机能识别；" +
                        "碰标签还需 App 已配置 Access Token 才会真正下发指令。" +
                        "标签请贴在不显眼的位置，丢失后可在此「解绑」让旧标签立即失效。",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun sourceLabel(source: String): String = when (source) {
    "live" -> "实时"
    "cache" -> "缓存"
    "last_action" -> "上次记录"
    else -> "默认"
}
