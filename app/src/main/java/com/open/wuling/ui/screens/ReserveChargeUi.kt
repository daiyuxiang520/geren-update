package com.open.wuling.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.open.wuling.data.api.ReserveChargeInfo
import com.open.wuling.ui.theme.PrimaryOrange
import com.open.wuling.ui.theme.PrimaryRed

// ====== 循环预约充电 (v69 引入，v72 改为底部弹窗) ======
// 接口：/car/cycle/charge/query（查询）、/car/cycle/charge/reserve（设置）、
//      /car/cancel/cycle/charge/reserve（取消）
// 说明：官方 App 内部叫「循环预约充电」。chargeLimit/chargeModel/chargeRequest/type
//      的枚举语义官方未公开，本 App 只在设置时**原样回传**服务端当前值，不臆造枚举。
// 入口：主页快捷控制区的「预约充电」按钮点击后弹出本弹窗（不再是主页常驻区块）。

/**
 * 循环预约充电底部弹窗：展示当前预约时段 / 限值 / 同步时间，支持设置、修改、取消。
 * 由主页 quick control 的「预约充电」按钮触发，数据与回调沿用 AppState 同一套状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReserveChargeSheet(
    reserveCharge: ReserveChargeInfo?,
    isLoading: Boolean,
    onSet: (Int, Int, Int, Int) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // 标题
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = PrimaryOrange
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "预约充电",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when {
                isLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "正在查询预约设置…",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                reserveCharge != null && reserveCharge.hasReservation -> {
                    ReserveRow("预约时段", "${reserveCharge.startTimeText()} - ${reserveCharge.endTimeText()}")
                    if (!reserveCharge.chargeLimit.isNullOrBlank()) {
                        ReserveRow("充电限值", reserveCharge.chargeLimit!!)
                    }
                    if (!reserveCharge.collectTime.isNullOrBlank()) {
                        ReserveRow("同步时间", reserveCharge.collectTime!!)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { showDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("修改", fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text("取消预约", fontSize = 14.sp)
                        }
                    }
                }
                else -> {
                    Text(
                        text = "当前未设置预约充电",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("设置预约充电", fontSize = 14.sp)
                    }
                }
            }
        }
    }

    if (showDialog) {
        val start = reserveCharge?.takeIf { it.hasReservation }
        ReserveChargeTimeDialog(
            initialStartHour = start?.startHour?.trim()?.toIntOrNull() ?: 22,
            initialStartMinute = start?.startMinute?.trim()?.toIntOrNull() ?: 0,
            initialEndHour = start?.endHour?.trim()?.toIntOrNull() ?: 8,
            initialEndMinute = start?.endMinute?.trim()?.toIntOrNull() ?: 0,
            onDismiss = { showDialog = false },
            onConfirm = { sh, sm, eh, em ->
                showDialog = false
                onSet(sh, sm, eh, em)
            }
        )
    }
}

/**
 * 两步式时间选择：先开始时间，再结束时间（24 小时制）。
 * 只提交「时/分」，与服务端 startHour/startMinute/endHour/endMinute 字段一一对应。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReserveChargeTimeDialog(
    initialStartHour: Int,
    initialStartMinute: Int,
    initialEndHour: Int,
    initialEndMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int, Int) -> Unit
) {
    var pickingEnd by remember { mutableStateOf(false) }
    val startState = rememberTimePickerState(
        initialHour = initialStartHour,
        initialMinute = initialStartMinute,
        is24Hour = true
    )
    val endState = rememberTimePickerState(
        initialHour = initialEndHour,
        initialMinute = initialEndMinute,
        is24Hour = true
    )
    val state = if (pickingEnd) endState else startState

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (pickingEnd) "选择结束时间" else "选择开始时间",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (pickingEnd) {
                    onConfirm(startState.hour, startState.minute, endState.hour, endState.minute)
                } else {
                    pickingEnd = true
                }
            }) {
                Text(if (pickingEnd) "确定" else "下一步")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (pickingEnd) pickingEnd = false else onDismiss()
            }) {
                Text(if (pickingEnd) "上一步" else "取消")
            }
        }
    )
}

/** 键值行（与详情页风格一致） */
@Composable
private fun ReserveRow(label: String, value: String, isWarning: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = if (isWarning) PrimaryRed else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
    Divider(
        color = MaterialTheme.colorScheme.surfaceVariant,
        thickness = 0.5.dp
    )
}
