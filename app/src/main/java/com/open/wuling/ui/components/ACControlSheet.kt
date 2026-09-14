package com.open.wuling.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.open.wuling.ui.theme.*

/**
 * 空调控制底部弹窗
 * @param isOpen 当前空调是否开启
 * @param currentTemp 当前设定温度
 * @param currentFanLevel 当前风速等级
 * @param onClose 关闭弹窗回调
 * @param onQuickCool 快速制冷回调
 * @param onQuickHeat 快速制热回调
 * @param onCustomControl 自定义控制回调(温度, 风速等级, 是否开启)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ACControlSheet(
    isOpen: Boolean,
    currentTemp: Int = 24,
    currentFanLevel: Int = 3,
    onClose: () -> Unit,
    onQuickCool: () -> Unit,
    onQuickHeat: () -> Unit,
    onCustomControl: (temperature: Int, fanLevel: Int, turnOn: Boolean) -> Unit
) {
    if (!isOpen) return

    var temperature by remember { mutableIntStateOf(currentTemp) }
    var fanLevel by remember { mutableIntStateOf(currentFanLevel) }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "空调控制",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 快速模式按钮
            Text(
                text = "快速模式",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 快速制冷按钮
                QuickModeButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.AcUnit,
                    label = "快速制冷",
                    description = "17°C · 最大风速",
                    color = PrimaryBlue,
                    onClick = onQuickCool
                )
                // 快速制热按钮
                QuickModeButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Whatshot,
                    label = "快速制热",
                    description = "33°C · 最大风速",
                    color = PrimaryOrange,
                    onClick = onQuickHeat
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 开启/关闭按钮 - 放在显眼位置
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onCustomControl(temperature, fanLevel, false)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = PrimaryRed
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("关闭空调")
                }

                Button(
                    onClick = {
                        onCustomControl(temperature, fanLevel, true)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("开启空调")
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            Divider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(28.dp))

            // 温度控制
            Text(
                text = "温度调节",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { if (temperature > 17) temperature-- },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Remove,
                        contentDescription = "降低温度",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$temperature",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (temperature <= 22) PrimaryBlue else if (temperature >= 28) PrimaryOrange else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "°C",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { if (temperature < 33) temperature++ },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "升高温度",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 温度快捷按钮
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf(18, 22, 26, 30).forEach { temp ->
                    Text(
                        text = "${temp}°C",
                        fontSize = 14.sp,
                        color = if (temperature == temp) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { temperature = temp }
                            .background(
                                if (temperature == temp) PrimaryBlue.copy(alpha = 0.1f) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            Divider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(28.dp))

            // 风速控制
            Text(
                text = "风速调节",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(7) { index ->
                    val level = index + 1
                    val isActive = fanLevel >= level
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) PrimaryBlue else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { fanLevel = level },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$level",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "风速: $fanLevel 档",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun QuickModeButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
