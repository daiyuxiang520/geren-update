package com.open.wuling.ui.screens

import com.open.wuling.analytics.UmengPageView

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.open.wuling.data.local.AmapGeoResolver
import com.open.wuling.data.local.AmapKeyManager
import com.open.wuling.data.local.CoordConverter
import com.open.wuling.data.local.OpenMeteoResolver
import com.open.wuling.data.local.WeatherCodeMap
import com.open.wuling.data.local.WeatherInfo
import com.open.wuling.data.model.Vehicle
import com.open.wuling.ui.components.AmapView
import com.open.wuling.ui.components.CollectTimeText
import com.open.wuling.ui.components.reloadMap
import com.open.wuling.ui.theme.*
import com.open.wuling.ui.theme.LocalCardAlpha
import com.open.wuling.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationScreen(
    modifier: Modifier = Modifier,
    vehicle: Vehicle?,
    // ===== 状态提升（v37）=====
    // 本页位于 MainActivity 的 when(selectedTab) 分支内，父级重组会整体重建它，
    // remember 状态随之丢失 —— 日志里表现为「刚拿到数据就被清空」
    // （41.454 地址来源: 无 → 41.463 又变回 高德逆地理，9ms 内靠缓存回填）。
    // 提升到调用方后，跨重组/切 Tab 都保留，彻底消除该问题。
    weatherState: WeatherInfo? = null,
    onWeatherChange: (WeatherInfo?) -> Unit = {},
    addressState: String? = null,
    onAddressChange: (String?) -> Unit = {}
) {
    UmengPageView("位置")

    val context = LocalContext.current

    // Key 配置状态
    var amapKey by remember { mutableStateOf(AmapKeyManager.getKey()) }
    var inputKey by remember { mutableStateOf("") }
    var showKeyInput by remember { mutableStateOf(false) }
    var keyVisible by remember { mutableStateOf(false) }

    // 天气 Key 状态（高德「Web 服务」类型，与地图 Key 不通用）
    var weatherKey by remember { mutableStateOf(AmapKeyManager.getWeatherKey()) }
    var inputWeatherKey by remember { mutableStateOf("") }
    var showWeatherKeyInput by remember { mutableStateOf(false) }
    var weatherKeyVisible by remember { mutableStateOf(false) }

    // 车辆所在地区天气（高德优先，失败自动降级 Open-Meteo）
    // 状态由调用方持有（见函数参数说明），此处只读取与写回
    val weather: WeatherInfo? = weatherState
    val setWeather: (WeatherInfo?) -> Unit = onWeatherChange
    // 失败提示（避免静默失败让用户无从排查）：地址失败 / 天气失败分别记录
    var geoError by remember { mutableStateOf<AmapGeoResolver.GeoError?>(null) }
    var weatherError by remember { mutableStateOf<AmapGeoResolver.GeoError?>(null) }
    // 加载中标记（用于 UI 显示「查询中…」，避免看起来像坏了）
    var loading by remember { mutableStateOf(false) }

    // WebView 引用，用于"回到车辆位置"
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // 刷新 Key 状态
    LaunchedEffect(Unit) {
        amapKey = AmapKeyManager.getKey()
        weatherKey = AmapKeyManager.getWeatherKey()
    }

    val location = vehicle?.location
    val hasValidLocation = location?.latitude != null && location.longitude != null
    val hasAmapKey = amapKey.isNotEmpty()

    // ===== 坐标纠偏（WGS84 → GCJ-02）：固定开启，v50 起不再提供 UI 开关 =====
    //
    // TBOX 上报的是 WGS84 原始 GPS，高德地图用 GCJ-02，直接投点会偏 200~600 米，
    // 因此纠偏是**必需**步骤而非可选项：普通用户没有判断基准，开关留在界面上
    // 只会被误关（关了之后车点反而更偏），属于纯风险项。
    // 现在恒定纠偏，境外坐标由 CoordConverter 内部原样返回，无需特判。
    val displayLocation = location?.let { loc ->
        val (lat, lon) = CoordConverter.convert(context, loc.latitude, loc.longitude)
        loc.copy(latitude = lat, longitude = lon)
    }

    // ===== 坐标兜底（v36）=====
    //
    // 车辆状态每 30 秒刷新一次，而 VehicleRepository 里 location 是**条件赋值**：
    // 接口任何一次没返回经纬度，location 就会变成 null。
    // 若直接拿它当 effect key，null 抖动会重启协程并清空已拿到的地址/天气，
    // 表现正是「日志里数据拿到了、界面上却空白」。
    //
    // 这里用「最后一次有效坐标」兜底：location 短暂为 null 时 key 保持不变，
    // 协程不重启、数据不丢失；只有坐标真正变化时才重新解析。
    var lastValidCoord by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    LaunchedEffect(displayLocation) {
        displayLocation?.let { lastValidCoord = it.latitude to it.longitude }
    }
    val activeCoord = displayLocation?.let { it.latitude to it.longitude } ?: lastValidCoord

    // v63：停车记录（本地存储）。进入页面时读一次即可——写入发生在 AppState 每次状态刷新，
    //      用户从别的 Tab 切回来就能看到最新记录，无需在本页做定时轮询。
    var parkingPoints by remember {
        mutableStateOf(emptyList<com.open.wuling.data.local.ParkingHistoryStore.Point>())
    }
    LaunchedEffect(Unit) {
        parkingPoints = com.open.wuling.data.local.ParkingHistoryStore.load(context)
        // 懒加载补全地名：仅对"停过且缺地名"的点逆地理，写回后重新读取刷新列表
        withContext(Dispatchers.IO) {
            val todo = parkingPoints.filter { com.open.wuling.data.local.ParkingHistoryStore.shouldEnrich(it) }
            if (todo.isNotEmpty()) {
                todo.forEach { p ->
                    com.open.wuling.data.local.ParkingHistoryStore.enrichPoint(context, p)
                        ?.let { com.open.wuling.data.local.ParkingHistoryStore.saveName(context, p, it) }
                }
                parkingPoints = com.open.wuling.data.local.ParkingHistoryStore.load(context)
            }
        }
    }

    // ===== 地址 + 天气：单协程串行解析（v33 重构）=====
    //
    // 背景：v31/v32 中地址与天气各用一个 LaunchedEffect（key 相同），30 秒车辆刷新时
    // 两个 effect 会被同时 cancel/restart 并互相干扰；天气链路更长（regeo + weather 两次
    // 串行请求），更容易在刷新周期内被取消 → 表现为「地址能显示、天气不显示」。
    //
    // 现在：合并为**单个** LaunchedEffect，串行执行一次 regeo（同时拿下地址与 adcode）
    // 再查一次天气；key 使用「坐标 4 位小数 + 天气 Key」的稳定字符串，车辆原地刷新时
    // key 不变、effect 不会被无谓重启（配合内部缓存，原地不动几乎零请求）。
    // 解析出的地址（同样提升到调用方持有）
    val resolvedAddress: String? = addressState
    val setAddress: (String?) -> Unit = onAddressChange
    val geoKey = activeCoord?.let { (lat, lon) ->
        String.format(Locale.US, "%.4f,%.4f|%s", lat, lon, weatherKey)
    }
    LaunchedEffect(geoKey) {
        val coord = activeCoord
        if (coord == null) {
            // 从未拿到过坐标：本就没有数据可清，保持静默即可。
            // ⚠️ 不要在这里清空 weather / resolvedAddress —— 那正是 v35 及以前
            //    「数据拿到了却显示空白」的根因。
            loading = false
            return@LaunchedEffect
        }
        if (weatherKey.isBlank()) {
            setAddress(null)
            setWeather(null)
            geoError = AmapGeoResolver.GeoError.NO_KEY
            weatherError = null
            loading = false
            return@LaunchedEffect
        }

        loading = true
        geoError = null
        weatherError = null
        val result = withContext(Dispatchers.IO) {
            AmapGeoResolver.resolve(weatherKey, coord.first, coord.second)
        }
        // 单协程内串行更新，不存在两个 effect 竞态覆盖
        setAddress(result.address)
        geoError = result.geoError

        // ===== 天气双保险 =====
        // 高德没拿到天气（未配置 Key / 服务未开通 10002 / 配额超限 / 网络异常）时，
        // 自动降级到 Open-Meteo：它按经纬度直查、无需任何 Key，
        // 保证「只要有坐标就有天气」，不再依赖控制台能否配置成功。
        val w = result.weather ?: withContext(Dispatchers.IO) {
            AppLogger.i("LocationScreen", "高德天气不可用，降级 Open-Meteo")
            OpenMeteoResolver.query(coord.first, coord.second)
        }
        setWeather(w)
        // 两个源都失败时才提示错误（已降级成功则不再报红，避免误导）
        weatherError = if (w == null) result.weatherError else null
        loading = false
    }
    // 优先用服务端自带地址（若有），否则用本地解析结果。
    // ⚠️ 注意：地址能显示**不等于**高德 Key 有效——它可能来自服务端。
    //    因此把来源写进调试日志，排查天气问题时不再被这个表象误导。
    val serverAddress = location?.address?.takeIf { it.isNotEmpty() }
    val displayAddress = serverAddress ?: resolvedAddress
    LaunchedEffect(serverAddress, resolvedAddress) {
        AppLogger.d(
            "LocationScreen",
            "地址来源: ${if (serverAddress != null) "服务端" else if (resolvedAddress != null) "高德逆地理" else "无"}"
        )
    }
    // 是否需要提示用户去配置/检查高德 Key（未配置 Key 或 Key 无效时）
    val showAddressHint = displayAddress.isNullOrEmpty()

    // ===== 整页可滚动（v38 根因修复）=====
    //
    // 这一行就是「天气看不见」「小区两个字下面缺一点」的真正原因，与 Key、网络、
    // 状态、缓存全都无关：
        // 本页内容 = Header(~44dp) + Key 配置区(展开约 400dp) + 地图(300dp)
        //          + 位置卡片(~210dp) + 操作按钮(~48dp) ≈ 700dp 以上，
    // 而最外层 Column 用 fillMaxSize() 被锁死在屏幕高度、且**没有 verticalScroll** ——
    // 超出屏幕的部分既渲染不出来也滑不到。位置卡片正好被屏幕底边/底部导航栏切掉，
    // 于是「小区」只露出上半截，排在卡片最后的天气行则完全落在屏幕外。
    //
    // 项目里 Home / Detail / Energy / Profile 都写了 verticalScroll，唯独这里漏了，
    // 这也是 v33~v37 一连串数据层、状态层修复都「没有变化」的原因：数据早就到了，
    // 只是被一屏装不下又滚不动的布局挡在了视野之外。
    val scrollState = rememberScrollState()

    // 全屏地图（v40）：小地图只作静态预览，平移/缩放都在这个独立页面里做
    var showFullscreenMap by remember { mutableStateOf(false) }

    // 地图高度随屏幕自适应：小屏上不至于把下方的位置卡片整个挤出首屏
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val mapHeight = (screenHeightDp * 0.28f).dp.coerceIn(170.dp, 260.dp)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "车辆位置",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Key 配置按钮
            TextButton(
                onClick = { showKeyInput = !showKeyInput }
            ) {
                Icon(
                    imageVector = Icons.Filled.Key,
                    contentDescription = "配置Key",
                    modifier = Modifier.size(20.dp),
                    tint = PrimaryOrange
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (hasAmapKey) "Key已配置" else "配置Key",
                    fontSize = 14.sp,
                    color = if (hasAmapKey) PrimaryGreen else PrimaryOrange
                )
            }        }

        // Key 输入区域
        AnimatedVisibility(visible = showKeyInput) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "高德地图 Key",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputKey,
                        onValueChange = { inputKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("请输入高德地图Key") },
                        singleLine = true,
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                AmapKeyManager.saveToPrefs(context, inputKey)
                                inputKey = ""
                                showKeyInput = false
                                amapKey = AmapKeyManager.getKey()
                            }
                        ),
                        trailingIcon = {
                            IconButton(onClick = { keyVisible = !keyVisible }) {
                                Icon(
                                    imageVector = if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (keyVisible) "隐藏" else "显示"
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { inputKey = "" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("清空")
                        }
                        Button(
                            onClick = {
                                AmapKeyManager.saveToPrefs(context, inputKey)
                                inputKey = ""
                                showKeyInput = false
                                amapKey = AmapKeyManager.getKey()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("保存")
                        }
                    }
                    if (hasAmapKey) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                AmapKeyManager.clearPrefs(context)
                                amapKey = ""
                            }
                        ) {
                            Text("清除已保存的Key", color = PrimaryRed, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // 申请地址（可点击复制）
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("高德地图申请地址", "https://lbs.amap.com/")
                            clipboard.setPrimaryClip(clip)
                        },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text(
                            text = "📋 点击复制申请地址",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "⚠️ 请申请「Web JS API」类型 Key",
                        fontSize = 12.sp,
                        color = PrimaryOrange
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "💡 输入Key后地图将立即显示",
                        fontSize = 12.sp,
                        color = PrimaryGreen
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    // ===== 天气 Key（可选）=====
                    Text(
                        text = "高德天气 Key（可选）",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "配置后在「车辆位置」显示所在地区天气；需要「Web 服务」类型 Key（与上面的地图 Key 不通用）",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputWeatherKey,
                        onValueChange = { inputWeatherKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("请输入高德「Web 服务」Key") },
                        singleLine = true,
                        visualTransformation = if (weatherKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                AmapKeyManager.saveWeatherKeyToPrefs(context, inputWeatherKey)
                                inputWeatherKey = ""
                                showWeatherKeyInput = false
                                weatherKey = AmapKeyManager.getWeatherKey()
                            }
                        ),
                        trailingIcon = {
                            IconButton(onClick = { weatherKeyVisible = !weatherKeyVisible }) {
                                Icon(
                                    imageVector = if (weatherKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (weatherKeyVisible) "隐藏" else "显示"
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { inputWeatherKey = "" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("清空输入")
                        }
                        Button(
                            onClick = {
                                AmapKeyManager.saveWeatherKeyToPrefs(context, inputWeatherKey)
                                inputWeatherKey = ""
                                showWeatherKeyInput = false
                                weatherKey = AmapKeyManager.getWeatherKey()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("保存")
                        }
                    }
                    if (weatherKey.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                AmapKeyManager.clearWeatherKeyPrefs(context)
                                AmapGeoResolver.clearCache()
                                weatherKey = ""
                                setWeather(null)
                                geoError = null
                                weatherError = null
                            }
                        ) {
                            Text("清除已保存的天气 Key", color = PrimaryRed, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "⚠️ 请申请「Web 服务」类型 Key 并绑定「天气查询」服务",
                        fontSize = 12.sp,
                        color = PrimaryOrange
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 高德地图
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(mapHeight),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (hasValidLocation && hasAmapKey) {
                    // 小地图 = 静态预览（interactive=false）。
                    // v38 加整页滚动后，Compose 会截走触摸事件，WebView 收不到 MOVE
                    // → 「地图不动、页面在滚」。与其和页面抢手势，不如让小地图彻底
                    // 不参与：页面一路滑到底，要看/要操作就点右上角「全屏」。
                    //
                    // v52：预览图也改为 3D（与全屏一致）。因为不参与手势，这里只取
                    //      俯仰角带来的立体观感，不带旋转/倾斜交互（也带不了）。
                    //      尺寸小，俯仰角相应调小，避免楼块在窄框里糊成一团。
                    AmapView(
                        longitude = displayLocation!!.longitude,
                        latitude = displayLocation.latitude,
                        modifier = Modifier.fillMaxSize(),
                        zoomLevel = 16,
                        showMarker = true,
                        interactive = false,
                        key = amapKey,
                        use3D = true,
                        pitch3D = 35
                    )

                    // 全屏入口（右上角，避开右下角高德工具条）
                    Surface(
                        onClick = { showFullscreenMap = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        contentColor = Color.White
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Fullscreen,
                                contentDescription = "全屏",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "全屏", fontSize = 12.sp)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (!hasAmapKey) {
                                Text(
                                    text = "请先配置高德地图 Key",
                                    fontSize = 14.sp,
                                    color = PrimaryOrange
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            Text(
                                text = if (hasValidLocation) "正在加载地图..." else "暂无位置信息",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ===== 位置信息卡片（v37）=====
        // v50：页面底部的「坐标纠偏」开关已移除（纠偏恒开），卡片整体再上移。
        //
        // ⚠️ 原来写成 location?.let { ... } ?: run { 暂无位置信息 }：
        //    location 会随 30 秒刷新短暂变 null，整张卡片会被替换成「暂无车辆位置信息」
        //    再重建，视觉上闪烁。
        //    （v37 曾据此猜测「高度算错导致截断」，v38 已证伪：真正的截断原因是
        //     整页不可滚动，见本文件顶部说明。此处的结构稳定性依然值得保留。）
        //
        // 改为：用「兜底坐标」判断是否有过位置，卡片结构保持稳定；内部字段各自判空。
        // 同时给 Column 加 animateContentSize，内容增减时高度平滑过渡，避免裁剪残影。
        val cardCoord = activeCoord
        if (cardCoord != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .animateContentSize()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.DirectionsCar,
                            contentDescription = null,
                            tint = PrimaryGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = vehicle?.displayName ?: "车辆",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 用兜底坐标：坐标短暂为 null 时仍显示上一次的值，不整块消失
                        LocationInfoItem(
                            label = "经度",
                            value = String.format("%.6f", cardCoord.second)
                        )
                        LocationInfoItem(
                            label = "纬度",
                            value = String.format("%.6f", cardCoord.first)
                        )
                    }

                    // v62：官方下发时间。collectTime 与经纬度同包返回，即「位置下发时间」；
                    //      带「N 分钟前」相对时间，超 10 分钟橙色提示车辆可能离线（TBox 休眠）。
                    Spacer(modifier = Modifier.height(10.dp))
                    CollectTimeText(
                        collectTime = vehicle?.status?.collectTime,
                        prefix = "位置更新于"
                    )

                    if (!displayAddress.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.MyLocation,
                                contentDescription = null,
                                tint = PrimaryOrange,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = displayAddress,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (showAddressHint) {
                        // 地址解析失败/未配置 Key：给出明确提示，避免「什么都没有」让用户困惑
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Text(text = "⚠️", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = geoError?.message("逆地理编码")
                                    ?: if (loading) "正在解析地址…" else "未能解析地址，请稍后重试",
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = PrimaryOrange
                            )
                        }
                    }

                    // ===== 天气行（v37：无条件占位，永远可见）=====
                    //
                    // 原先是 weather?.let { ... } —— 没数据时整行消失，
                    // 用户无法区分「没渲染」和「渲染了但没数据」，排查全靠猜。
                    // 现在这行**始终存在**：有数据显示天气，否则显示加载中/错误/未配置，
                    // 一眼就能看出卡在哪一层。
                    Spacer(modifier = Modifier.height(10.dp))
                    when {
                        weather != null -> {
                            val w = weather!!
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = WeatherCodeMap.icon(w.weather),
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = buildWeatherText(w),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                // 标注数据来源：高德 / Open-Meteo（降级时会看到来源变化）
                                text = buildString {
                                    if (w.reportTime.isNotEmpty()) append("更新于 ${w.reportTime} · ")
                                    append("来源 ${w.source.label}")
                                },
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        weatherKey.isBlank() -> {
                            Text(
                                text = "🌡️ 配置「高德天气 Key」后显示天气",
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        loading -> {
                            Text(
                                text = "🌡️ 天气加载中…",
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        weatherError != null -> {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(text = "⚠️", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = weatherError!!.message("天气查询"),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = PrimaryOrange
                                )
                            }
                        }
                        else -> {
                            Text(
                                text = "🌡️ 暂无天气数据",
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无车辆位置信息",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 操作按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 导航找车按钮（使用纠偏后的坐标，dev=0 表示传入 GCJ-02 坐标）
            Button(
                onClick = {
                    displayLocation?.let { loc -> navigateTo(context, loc.latitude, loc.longitude) }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(14.dp),
                enabled = hasValidLocation
            ) {
                Icon(
                    imageVector = Icons.Filled.Navigation,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "导航找车")
            }

            // 分享位置按钮（使用纠偏后的坐标）
            Button(
                onClick = {
                    displayLocation?.let { loc ->
                        val shareText = buildString {
                            append("我的车辆位置\n")
                            append("经度: ${loc.longitude}\n")
                            append("纬度: ${loc.latitude}\n")
                            if (!displayAddress.isNullOrEmpty()) append("地址: $displayAddress")
                            append("\nhttps://m.amap.com/?q=${loc.latitude},${loc.longitude}")
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "分享车辆位置"))
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                shape = RoundedCornerShape(14.dp),
                enabled = hasValidLocation
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "分享位置")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ===== 停车记录（v63）=====
        //
        // 官方网关没有行程接口，这里是本地观测式记录：车辆在同一个地方停留会合并成一条，
        // 移动过（>50m）才新增。存的是原始 WGS84 坐标，导航前统一纠偏。
        ParkingHistoryCard(
            points = parkingPoints,
            onNavigate = { lat, lon ->
                val (cLat, cLon) = CoordConverter.convert(context, lat, lon)
                navigateTo(context, cLat, cLon)
            }
        )

        // v50：原先这里的「坐标纠偏」开关卡片已移除 —— 纠偏改为恒定开启，
        //      界面上不再暴露开关。少掉约 76dp 高度，滚动距离同步缩短。

        // 底部留白：paddingValues 已经为底部导航让过位，这里只需一点呼吸空间。
        // ⚠️ 原来是 100.dp —— 在不可滚动的 Column 里它只是一块纯浪费的空白，
        //    还把本就超屏的内容又往下推了 100dp。
        Spacer(modifier = Modifier.height(24.dp))

        // ===== 全屏地图（v40）=====
        //
        // 这是「滑动地图」这个坑的正面解法：小地图让位给页面滚动（静态预览），
        // 要看、要平移、要缩放就进这个独立页面 —— 里面没有父级滚动容器抢事件，
        // 地图手势完整。Dialog 不占用本页布局空间，放在 Column 末尾是安全的。
        if (showFullscreenMap) {
            val fsCoord = activeCoord
            Dialog(
                onDismissRequest = { showFullscreenMap = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    if (fsCoord != null && hasAmapKey) {
                        AmapView(
                            longitude = fsCoord.second,
                            latitude = fsCoord.first,
                            modifier = Modifier.fillMaxSize(),
                            zoomLevel = 16,
                            showMarker = true,
                            interactive = true,   // 全屏：完整手势（平移 / 缩放）
                            key = amapKey,
                            // v51：全屏地图启用 3D（楼块 + 俯仰/旋转 + 罗盘控制盘）。
                            // 车机 WebView 不支持 WebGL 时，组件内部会自动回落 2D，不会白图。
                            use3D = true
                        )
                        // v51：记录 3D 实际是否生效 —— 有的车机 WebGL 不可用会被静默回落成 2D，
                        // 只有把探测结果打出来，排查时才知道用户到底看到的是哪种视图。
                        LaunchedEffect(fsCoord, amapKey) {
                            delay(2500)  // 等地图脚本初始化完成后再读探测标记
                            webViewRef?.evaluateJavascript(
                                "(window.__wulingMap3D === undefined ? -1 : window.__wulingMap3D)"
                            ) { r ->
                                val v = r?.trim()?.removePrefix("\"")?.removeSuffix("\"")
                                AppLogger.i(
                                    "LocationScreen",
                                    "全屏地图 3D 探测结果: " + when (v) {
                                        "1" -> "3D 已启用"
                                        "0" -> "WebGL 不可用，已回落 2D"
                                        else -> "未取到标记（脚本可能未执行）"
                                    }
                                )
                            }
                        }
                    } else {
                        Text(
                            text = if (!hasAmapKey) "请先配置高德地图 Key" else "暂无位置信息",
                            color = Color.White,
                            fontSize = 15.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    // 顶部标题栏 + 关闭按钮（半透明底，避免压在地图上辨认不清）
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = vehicle?.displayName ?: "车辆位置",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { showFullscreenMap = false }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "关闭",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationInfoItem(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 调起导航到指定坐标（坐标需已纠偏为 GCJ-02，dev=0）。
 * 高德 App 优先，未安装则回退高德网页版。
 */
private fun navigateTo(context: android.content.Context, lat: Double, lon: Double) {
    val uri = Uri.parse(
        "androidamap://route?sourceApplication=五菱智驾&slat=&slon=&sname=我的位置" +
            "&dlat=$lat&dlon=$lon&dname=车辆位置&dev=0&t=2"
    )
    val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.autonavi.minimap") }
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        val webUri = Uri.parse("https://uri.amap.com/navigation?to=$lon,$lat,车辆位置&mode=car&src=车上")
        context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
    }
}

/** 停车记录卡片（v63）：最近 8 条，点右侧「导航」可直接过去 */
@Composable
private fun ParkingHistoryCard(
    points: List<com.open.wuling.data.local.ParkingHistoryStore.Point>,
    onNavigate: (Double, Double) -> Unit
) {
    if (points.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "停车记录",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "本地记录：同一地点（50 米内）合并为一条，车辆移动后新增",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            points.take(8).forEachIndexed { index, p ->
                if (index > 0) {
                    Divider(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                        thickness = 1.dp
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${formatParkingTime(p.firstSeen)} · ${formatStayDuration(p)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = p.name ?: String.format(Locale.US, "%.4f, %.4f", p.lat, p.lon),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { onNavigate(p.lat, p.lon) }) {
                        Text("导航", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun formatParkingTime(epochMs: Long): String = try {
    java.text.SimpleDateFormat("MM-dd HH:mm", Locale.US).format(java.util.Date(epochMs))
} catch (_: Exception) {
    "--"
}

private fun formatStayDuration(p: com.open.wuling.data.local.ParkingHistoryStore.Point): String {
    val ms = p.lastSeen - p.firstSeen
    if (ms < 60_000) return "途经"
    val minutes = ms / 60_000
    return when {
        minutes < 60 -> "停留 ${minutes} 分钟"
        minutes < 1440 -> {
            val h = minutes / 60
            val m = minutes % 60
            if (m > 0) "停留 ${h} 小时 ${m} 分" else "停留 ${h} 小时"
        }
        else -> "停留 ${minutes / 1440} 天"
    }
}

/**
 * 拼接天气展示文案，如「晴 21°C · 湿度 53% · 东南风 3级」。
 * 缺失字段自动跳过，避免出现空占位。
 */
private fun buildWeatherText(w: WeatherInfo): String {
    val parts = mutableListOf<String>()
    if (w.weather.isNotEmpty()) parts.add(w.weather)
    if (w.temperature.isNotEmpty()) parts.add("${w.temperature}°C")
    if (w.humidity.isNotEmpty()) parts.add("湿度 ${w.humidity}%")
    // 风向：跳过「无风向」；风力统一追加「级」
    val dir = if (w.windDirection == "无风向") "" else w.windDirection
    val power = if (w.windPower.isNotEmpty()) "${w.windPower}级" else ""
    val wind = "$dir$power".trim()
    if (wind.isNotEmpty()) parts.add("${wind}风")
    return parts.joinToString(" · ")
}
