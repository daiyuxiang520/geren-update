package com.open.wuling.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.open.wuling.AppState
import com.open.wuling.data.mqtt.MqttConfig
import com.open.wuling.data.mqtt.MqttConnectionState
import com.open.wuling.ui.theme.PrimaryGreen
import com.open.wuling.ui.theme.PrimaryOrange
import com.open.wuling.ui.theme.PrimaryRed

/**
 * v73：MQTT 实时推送设置弹层（底部弹窗）。
 *
 * 全部参数可编辑并即时持久化；未启用时只显示总开关，启用后展开全部高级项。
 * 连接状态实时展示，便于用户在真机上调 topic / 凭证接口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MqttSettingsSheet(
    appState: AppState,
    onClose: () -> Unit
) {
    val config by appState.mqttConfig.collectAsState()
    val state by appState.mqttConnectionState.collectAsState()
    val lastError by appState.mqttLastError.collectAsState()
    val lastMessageAt by appState.mqttLastMessageAt.collectAsState()

    // 外部配置变化时（冷启动载入 / 保存后回写）同步到草稿
    var draft by remember(config) { mutableStateOf(config) }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "MQTT 实时推送",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                MqttStateBadge(state)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 总开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("启用实时推送", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "关闭则回退到原有 30 秒轮询；开启后车况变化经 MQTT 秒级刷新",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = draft.enabled,
                    onCheckedChange = { draft = draft.copy(enabled = it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                )
            }

            if (draft.enabled) {
                Spacer(modifier = Modifier.height(16.dp))

                // Broker 地址
                LabeledTextField(
                    label = "Broker 地址",
                    value = draft.brokerUrl,
                    onValueChange = { draft = draft.copy(brokerUrl = it) },
                    hint = "tcp://host:port 或 ssl://host:port"
                )

                // 凭证来源
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("使用官方凭证接口", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("用 accessToken 换取 MQTT 账号密码；关闭则用手动账号", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = draft.useCredentialApi,
                        onCheckedChange = { draft = draft.copy(useCredentialApi = it) }
                    )
                }

                if (draft.useCredentialApi) {
                    LabeledTextField(
                        label = "凭证接口地址",
                        value = draft.credentialApiUrl,
                        onValueChange = { draft = draft.copy(credentialApiUrl = it) },
                        hint = "openapi.baojun.net 的 getMQTTToken 接口"
                    )
                } else {
                    LabeledTextField(
                        label = "用户名",
                        value = draft.username,
                        onValueChange = { draft = draft.copy(username = it) }
                    )
                    LabeledTextField(
                        label = "密码",
                        value = draft.password,
                        onValueChange = { draft = draft.copy(password = it) },
                        isPassword = true
                    )
                }

                // clientId 模板
                LabeledTextField(
                    label = "ClientID 模板",
                    value = draft.clientIdTemplate,
                    onValueChange = { draft = draft.copy(clientIdTemplate = it) },
                    hint = "官方规则：{vin}_{phone4}（手机号后4位）；另支持 {uuid} {imei} {random}"
                )

                // 订阅 topic
                LabeledTextField(
                    label = "订阅主题（每行一个，支持 {vin}）",
                    value = draft.subscriptions,
                    onValueChange = { draft = draft.copy(subscriptions = it) },
                    hint = "官方实证：{vin}/prod/sgmw/vehicle/app/status 等 4 个 topic（已预填）",
                    minLines = 3
                )

                // QoS
                Text("订阅 QoS", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 1, 2).forEach { q ->
                        FilterChip(
                            selected = draft.qos == q,
                            onClick = { draft = draft.copy(qos = q) },
                            label = { Text("QoS $q", fontSize = 12.sp) }
                        )
                    }
                }

                // keep-alive
                LabeledTextField(
                    label = "Keep-Alive（秒）",
                    value = draft.keepAliveSeconds.toString(),
                    onValueChange = {
                        draft = draft.copy(keepAliveSeconds = it.toIntOrNull()?.coerceIn(5, 3600) ?: 30)
                    },
                    keyboardType = KeyboardType.Number
                )

                // 开关组
                ToggleRow("断线自动重连", draft.reconnectEnabled) { draft = draft.copy(reconnectEnabled = it) }
                ToggleRow("收到推送即刷新车况", draft.forceRefreshOnMessage) { draft = draft.copy(forceRefreshOnMessage = it) }
                ToggleRow("记录原始报文到调试日志", draft.logRawPayload) { draft = draft.copy(logRawPayload = it) }

                Spacer(modifier = Modifier.height(12.dp))

                // 连接状态
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MqttStateBadge(state)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stateText(state), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = stateColor(state))
                        }
                        lastError?.takeIf { it.isNotEmpty() }?.let { err ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("错误：$err", fontSize = 12.sp, color = PrimaryRed)
                        }
                        if (lastMessageAt > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val secs = (System.currentTimeMillis() - lastMessageAt) / 1000
                            Text("最后推送：${if (secs < 60) "${secs}秒前" else "${secs / 60}分前"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "topic 拼法 / 凭证字段被官方加固壳藏住，只能真机拿。连上后到「我的 → 调试日志」(tag=MQTT) 看原始报文，再把真实 topic 填进上方订阅框。",
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 保存 / 取消
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onClose) { Text("取消") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        appState.setMqttConfig(draft)
                        onClose()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("保存")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String? = null,
    isPassword: Boolean = false,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = hint?.let { { Text(it, fontSize = 12.sp) } },
            singleLine = minLines <= 1,
            minLines = minLines,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
        )
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MqttStateBadge(state: MqttConnectionState) {
    val (icon, color) = when (state) {
        MqttConnectionState.CONNECTED, MqttConnectionState.SUBSCRIBED -> Icons.Filled.CloudDone to PrimaryGreen
        MqttConnectionState.ERROR -> Icons.Filled.CloudOff to PrimaryRed
        MqttConnectionState.CONNECTING, MqttConnectionState.RECONNECTING -> Icons.Filled.Sync to PrimaryOrange
        else -> Icons.Filled.Cloud to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
}

private fun stateText(state: MqttConnectionState): String = when (state) {
    MqttConnectionState.DISABLED -> "未启用"
    MqttConnectionState.DISCONNECTED -> "未连接"
    MqttConnectionState.CONNECTING -> "连接中…"
    MqttConnectionState.CONNECTED -> "已连接"
    MqttConnectionState.SUBSCRIBED -> "已订阅"
    MqttConnectionState.ERROR -> "连接错误"
    MqttConnectionState.RECONNECTING -> "重连中…"
}

@Composable
private fun stateColor(state: MqttConnectionState): Color = when (state) {
    MqttConnectionState.CONNECTED, MqttConnectionState.SUBSCRIBED -> PrimaryGreen
    MqttConnectionState.ERROR -> PrimaryRed
    MqttConnectionState.CONNECTING, MqttConnectionState.RECONNECTING -> PrimaryOrange
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
