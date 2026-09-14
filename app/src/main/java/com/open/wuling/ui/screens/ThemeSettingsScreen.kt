package com.open.wuling.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.open.wuling.MainViewModel
import com.open.wuling.data.local.ThemePreferences
import com.open.wuling.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream

/**
 * 主题设置底部弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsSheet(
    onDismiss: () -> Unit
) {
    val viewModel: MainViewModel = hiltViewModel()
    val themePrefs = viewModel.themePreferences
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 当前设置值
    val themeMode by themePrefs.themeModeFlow.collectAsState(initial = 0)
    val useCustomColors by themePrefs.useCustomColorsFlow.collectAsState(initial = false)
    val useCustomBg by themePrefs.useCustomBackgroundFlow.collectAsState(initial = false)
    val bgPath by themePrefs.backgroundImagePathFlow.collectAsState(initial = null)
    val bgBlur by themePrefs.backgroundBlurFlow.collectAsState(initial = 0f)
    val bgDimEnabled by themePrefs.backgroundDimEnabledFlow.collectAsState(initial = true)
    val cardAlpha by themePrefs.cardAlphaFlow.collectAsState(initial = 0.95f)
    val customPrimary by themePrefs.customPrimaryColorFlow.collectAsState(initial = 0xFF2D7AF6.toInt())
    val customBgColor by themePrefs.customBackgroundColorFlow.collectAsState(initial = 0xFF0A0A0C.toInt())
    val customCardColor by themePrefs.customCardColorFlow.collectAsState(initial = 0xFF1A1A1E.toInt())
    val customTextPrimary by themePrefs.customTextPrimaryColorFlow.collectAsState(initial = 0xFFFFFFFF.toInt())
    val customTextSecondary by themePrefs.customTextSecondaryColorFlow.collectAsState(initial = 0xFFB0B0B0.toInt())
    val customIconColor by themePrefs.customIconColorFlow.collectAsState(initial = 0xFF2D7AF6.toInt())

    // UI 状态
    var showColorPicker by remember { mutableStateOf<String?>(null) }

    // 图片选择器
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val savedPath = saveImageToInternal(context, it)
                if (savedPath != null) {
                    themePrefs.setBackgroundImagePath(savedPath)
                    themePrefs.setUseCustomBackground(true)
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Palette, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("主题设置", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("关闭", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // === 1. 主题模式 ===
            SectionTitle("主题模式")
            Spacer(modifier = Modifier.height(8.dp))
            ThemeModeSelector(
                currentMode = themeMode,
                onModeChange = { scope.launch { themePrefs.setThemeMode(it) } }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // === 2. 自定义颜色总开关 ===
            SectionTitle("自定义颜色")
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("启用自定义颜色", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("替代默认主题配色", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = useCustomColors,
                        onCheckedChange = { scope.launch { themePrefs.setUseCustomColors(it) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            if (useCustomColors) {
                Spacer(modifier = Modifier.height(12.dp))

                // 颜色选择条目
                ColorPickerItem(
                    label = "主色调",
                    color = Color(customPrimary),
                    onClick = { showColorPicker = "primary" }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ColorPickerItem(
                    label = "背景色",
                    color = Color(customBgColor),
                    onClick = { showColorPicker = "background" }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ColorPickerItem(
                    label = "卡片色",
                    color = Color(customCardColor),
                    onClick = { showColorPicker = "card" }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ColorPickerItem(
                    label = "主要文字",
                    color = Color(customTextPrimary),
                    onClick = { showColorPicker = "textPrimary" }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ColorPickerItem(
                    label = "次要文字",
                    color = Color(customTextSecondary),
                    onClick = { showColorPicker = "textSecondary" }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ColorPickerItem(
                    label = "图标颜色",
                    color = Color(customIconColor),
                    onClick = { showColorPicker = "icon" }
                )

                // 预设配色方案
                Spacer(modifier = Modifier.height(16.dp))
                SectionTitle("预设配色")
                Spacer(modifier = Modifier.height(8.dp))
                PresetColorSchemes(
                    themePrefs = themePrefs,
                    scope = scope
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // === 3. 背景图片 ===
            SectionTitle("背景图片")
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("自定义背景图", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                if (bgPath != null) "已设置背景" else "选择图片作为应用背景",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useCustomBg,
                            onCheckedChange = { scope.launch { themePrefs.setUseCustomBackground(it) } },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                        )
                    }

                    // 背景预览
                    if (bgPath != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = File(bgPath),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { imagePicker.launch("image/*") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(Icons.Filled.Image, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("更换图片", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        themePrefs.setBackgroundImagePath(null)
                                        themePrefs.setUseCustomBackground(false)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, PrimaryRed.copy(alpha = 0.3f))
                            ) {
                                Text("移除", color = PrimaryRed, fontSize = 13.sp)
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { imagePicker.launch("image/*") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Filled.Image, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("选择图片", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // 模糊效果
            if (useCustomBg && bgPath != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("模糊效果", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("${bgBlur.toInt()}dp", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = bgBlur,
                            onValueChange = { scope.launch { themePrefs.setBackgroundBlur(it) } },
                            valueRange = 0f..25f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("清晰", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            Text("模糊", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                }
            }

            // 暗色遮罩开关
            if (useCustomBg && bgPath != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("暗色遮罩", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("背景图上方添加半透明黑色遮罩", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = bgDimEnabled,
                            onCheckedChange = { scope.launch { themePrefs.setBackgroundDimEnabled(it) } },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }

            // 卡片透明度
            if (useCustomBg && bgPath != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("卡片透明度", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("${(cardAlpha * 100).toInt()}%", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = cardAlpha,
                            onValueChange = { scope.launch { themePrefs.setCardAlpha(it) } },
                            valueRange = 0.1f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("透明", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            Text("不透明", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // === 恢复默认 ===
            OutlinedButton(
                onClick = {
                    scope.launch {
                        themePrefs.clearCustomSettings()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, PrimaryRed.copy(alpha = 0.3f))
            ) {
                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp), tint = PrimaryRed)
                Spacer(modifier = Modifier.width(4.dp))
                Text("恢复默认主题", color = PrimaryRed)
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // 颜色选择器弹窗
    showColorPicker?.let { type ->
        ColorPickerDialog(
            currentColor = when (type) {
                "primary" -> Color(customPrimary)
                "background" -> Color(customBgColor)
                "card" -> Color(customCardColor)
                "textPrimary" -> Color(customTextPrimary)
                "textSecondary" -> Color(customTextSecondary)
                "icon" -> Color(customIconColor)
                else -> Color(customPrimary)
            },
            onColorSelected = { color ->
                val argb = color.toArgb()
                scope.launch {
                    when (type) {
                        "primary" -> themePrefs.setCustomPrimaryColor(argb)
                        "background" -> themePrefs.setCustomBackgroundColor(argb)
                        "card" -> themePrefs.setCustomCardColor(argb)
                        "textPrimary" -> themePrefs.setCustomTextPrimaryColor(argb)
                        "textSecondary" -> themePrefs.setCustomTextSecondaryColor(argb)
                        "icon" -> themePrefs.setCustomIconColor(argb)
                    }
                }
                showColorPicker = null
            },
            onDismiss = { showColorPicker = null }
        )
    }
}

// ====== 子组件 ======

@Composable
private fun SectionTitle(title: String) {
    Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ThemeModeSelector(currentMode: Int, onModeChange: (Int) -> Unit) {
    val modes = listOf(
        Triple(0, "跟随系统", "自动切换亮色/暗色"),
        Triple(1, "浅色模式", "始终使用亮色主题"),
        Triple(2, "深色模式", "始终使用暗色主题")
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            modes.forEachIndexed { index, (mode, title, subtitle) ->
                if (index > 0) {
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onModeChange(mode) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (currentMode == mode) {
                        Icon(
                            Icons.Filled.Check, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorPickerItem(label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        // 显示 HEX 值
        Text(
            "#${color.toArgb().toUInt().toString(16).uppercase().takeLast(6)}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorPickerDialog(
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var red by remember { mutableIntStateOf(android.graphics.Color.red(currentColor.toArgb())) }
    var green by remember { mutableIntStateOf(android.graphics.Color.green(currentColor.toArgb())) }
    var blue by remember { mutableIntStateOf(android.graphics.Color.blue(currentColor.toArgb())) }

    val previewColor = Color(red, green, blue)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择颜色", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                // 颜色预览
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(previewColor)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // RGB 滑块
                ColorSliderRow(label = "R", value = red, onValueChange = { red = it }, color = Color.Red)
                Spacer(modifier = Modifier.height(8.dp))
                ColorSliderRow(label = "G", value = green, onValueChange = { green = it }, color = Color.Green)
                Spacer(modifier = Modifier.height(8.dp))
                ColorSliderRow(label = "B", value = blue, onValueChange = { blue = it }, color = Color.Blue)

                Spacer(modifier = Modifier.height(12.dp))

                // HEX 显示
                Text(
                    text = "#${previewColor.toArgb().toUInt().toString(16).uppercase().takeLast(6)}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // 快捷预设颜色
                Spacer(modifier = Modifier.height(12.dp))
                Text("快捷选色", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(
                        Color(0xFF2D7AF6), Color(0xFF00C853), Color(0xFFFF9500),
                        Color(0xFFFF3B30), Color(0xFFAF52DE), Color(0xFF5856D6),
                        Color(0xFFFFFFFF), Color(0xFF1A1A1E)
                    ).forEach { preset ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(preset)
                                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .clickable {
                                    red = android.graphics.Color.red(preset.toArgb())
                                    green = android.graphics.Color.green(preset.toArgb())
                                    blue = android.graphics.Color.blue(preset.toArgb())
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onColorSelected(previewColor) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Filled.Save, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("应用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun ColorSliderRow(label: String, value: Int, onValueChange: (Int) -> Unit, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.width(20.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color
            )
        )
        Text(
            "$value", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(30.dp), textAlign = TextAlign.End
        )
    }
}

@Composable
private fun PresetColorSchemes(
    themePrefs: ThemePreferences,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val presets = listOf(
        Triple("默认蓝", 0xFF2D7AF6, 0xFF0A0A0C),
        Triple("翡翠绿", 0xFF00C853, 0xFF0A0C0A),
        Triple("活力橙", 0xFFFF9500, 0xFF0C0A08),
        Triple("赛博紫", 0xFFAF52DE, 0xFF0A080C),
        Triple("极光蓝", 0xFF5856D6, 0xFF08080C),
    )

    presets.forEach { (name, primaryArgb, bgArgb) ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable {
                    scope.launch {
                        themePrefs.setCustomPrimaryColor(primaryArgb.toInt())
                        themePrefs.setCustomBackgroundColor(bgArgb.toInt())
                        themePrefs.setCustomCardColor(
                            if (primaryArgb == 0xFF2D7AF6) 0xFF1A1A1E.toInt() else bgArgb.toInt().let {
                                android.graphics.Color.rgb(
                                    Math.min(255, android.graphics.Color.red(it) + 16),
                                    Math.min(255, android.graphics.Color.green(it) + 16),
                                    Math.min(255, android.graphics.Color.blue(it) + 16)
                                )
                            }
                        )
                        themePrefs.setCustomIconColor(primaryArgb.toInt())
                    }
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(primaryArgb))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(name, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
}

// ====== 工具函数 ======

private fun saveImageToInternal(context: Context, uri: Uri): String? {
    return try {
        val dir = File(context.filesDir, "theme_backgrounds")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "background_${System.currentTimeMillis()}.jpg")
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        if (inputStream != null) {
            file.outputStream().use { out ->
                inputStream.copyTo(out)
            }
            inputStream.close()
            file.absolutePath
        } else null
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
