package com.open.wuling.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * 高德地图 WebView 组件
 * 使用高德 JS API，支持动态 Key
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
/**
 * @param interactive 是否允许手势交互（拖拽 / 缩放）。
 *  - `true`（默认）：可自由平移缩放，用于**全屏地图**。
 *  - `false`：**静态预览**，地图自身不响应任何手势。用于嵌在可滚动页面里的小地图 ——
 *    v38 给位置页加了整页 verticalScroll 后，Compose 的 scrollable 会截走触摸事件，
 *    WebView 只收到 DOWN 收不到 MOVE，导致「地图不动、页面在滚」。
 *    与其两边抢，不如让小地图彻底不参与手势：页面滑动一路顺畅，
 *    要看/要操作地图就点「全屏」进独立页面（那里没有父级滚动容器，手势完整）。
 * @param use3D 是否使用 3D 视图（v51 新增）。
 *  - 全屏地图与位置页预览小图均已开启（v52 起预览图也上 3D）。
 *  - 3D 依赖 WebGL，部分老旧车机 WebView 不满足条件。高德官方说明此时**自动回落 2D**，
 *    我们额外在 JS 侧探测 WebGL，不可用时直接按 2D 初始化，避免出现空白地图。
 * @param pitch3D 3D 俯仰角（0-83）。
 *  - 全屏地图用默认 50：视野开阔、楼块立体感强，且用户可自行转动调整。
 *  - 预览小图建议调小（如 35）：尺寸只有一两百 dp，俯仰角过大会让楼块挤压、
 *    画面发糊，透视太强反而看不出车在哪。
 */
fun AmapView(
    longitude: Double,
    latitude: Double,
    modifier: Modifier = Modifier,
    zoomLevel: Int = 16,
    showMarker: Boolean = true,
    interactive: Boolean = true,
    key: String = "",
    use3D: Boolean = false,
    pitch3D: Int = 50
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 加载状态
    var isLoading by remember { mutableStateOf(true) }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                allowFileAccess = true
                allowContentAccess = true
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    isLoading = false
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        isLoading = false
                    }
                }
            }
            webChromeClient = WebChromeClient()
        }
    }

    // 生命周期管理
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                Lifecycle.Event.ON_PAUSE -> webView.onPause()
                Lifecycle.Event.ON_DESTROY -> webView.destroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView.destroy()
        }
    }

    // 更新地图内容
    DisposableEffect(longitude, latitude, zoomLevel, showMarker, interactive, key, use3D, pitch3D) {
        isLoading = true

        if (key.isNotEmpty()) {
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
                    <style>
                        * { margin: 0; padding: 0; }
                        html, body, #container { width: 100%; height: 100%; }
                    </style>
                    <script src="https://webapi.amap.com/maps?v=2.0&key=$key"></script>
                </head>
                <body>
                    <div id="container"></div>
                    <script>
                        // ===== 3D 能力探测（v51）=====
                        // 3D 视图走 WebGL 渲染，部分老旧车机 WebView / GPU 驱动不满足条件。
                        // 高德官方说明此时「仍然使用原有 2D 视图绘制」，但我们还额外自己探一次：
                        // 拿不到 WebGL 上下文就**根本不传 3D 参数**，省掉一次无谓的 3D 初始化，
                        // 也避免个别机型在回落过程中闪一下白图。
                        function supportsWebGL() {
                            try {
                                var canvas = document.createElement('canvas');
                                var gl = canvas.getContext('webgl') ||
                                         canvas.getContext('experimental-webgl');
                                if (!gl) return false;
                                // 部分车机有上下文但无实际渲染能力，再确认一个基础参数
                                var ok = !!gl.getParameter(gl.VERSION);
                                var lose = gl.getExtension('WEBGL_lose_context');
                                if (lose) lose.loseContext();  // 探测完及时释放，不占 GPU 资源
                                return ok;
                            } catch (e) {
                                return false;
                            }
                        }

                        var want3D = $use3D;
                        var can3D = want3D && supportsWebGL();
                        // 探测结果挂到全局，供 Android 侧通过 evaluateJavascript 读取（排查用）
                        window.__wulingMap3D = can3D ? 1 : 0;

                        if (typeof AMap !== 'undefined') {
                            var mapOptions = {
                                zoom: $zoomLevel,
                                center: [$longitude, $latitude],
                                viewMode: can3D ? '3D' : '2D',
                                // interactive=false → 静态预览：地图完全不响应手势，
                                // 触摸事件不会被 WebView 吃掉，页面可一路顺畅滑动。
                                dragEnable: $interactive,
                                zoomEnable: $interactive,
                                scrollWheel: $interactive,
                                doubleClickZoom: $interactive,
                                touchZoom: $interactive,
                                keyboardEnable: $interactive,
                                // 3D 专属：俯仰角 + 旋转/倾斜手势（2D 下这些参数被高德忽略）
                                // 旋转/倾斜交互仅在可交互时开启；静态预览图只保留俯仰立体感。
                                pitch: can3D ? $pitch3D : 0,
                                rotation: 0,
                                rotateEnable: can3D && $interactive,
                                pitchEnable: can3D && $interactive,
                                showBuildingBlock: can3D,   // 3D 楼块，城市立体感的主要来源
                                buildingAnimation: can3D    // 楼块生长动画
                            };
                            var map = new AMap.Map('container', mapOptions);

                            AMap.plugin(['AMap.ToolBar', 'AMap.Marker', 'AMap.ControlBar'], function() {
                                // 缩放工具条只在可交互时才有意义（小地图禁用缩放，按钮会失效）
                                ${if (interactive) "map.addControl(new AMap.ToolBar({ position: 'RB' }));" else ""}

                                // v51：3D 全屏地图加罗盘控制盘 —— 否则用户根本不知道地图可以转。
                                // ControlBar 提供旋转/倾斜控制，只在 3D 且可交互时挂载。
                                ${if (interactive) """
                                if (can3D) {
                                    map.addControl(new AMap.ControlBar({
                                        position: { right: '10px', top: '80px' },
                                        showZoomBar: false,
                                        showControlButton: true
                                    }));
                                }
                                """ else ""}

                                ${if (showMarker) """
                                var marker = new AMap.Marker({
                                    position: [$longitude, $latitude],
                                    title: '车辆位置',
                                    icon: 'https://webapi.amap.com/theme/v1.3/markers/n/mark_r.png',
                                    offset: new AMap.Pixel(-13, -34)
                                });
                                marker.setMap(map);
                                """ else ""}
                            });
                        }
                    </script>
                </body>
                </html>
            """.trimIndent()
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }

        onDispose { }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )

        // 加载中遮罩
        if (isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = Color(0xFF2196F3)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "正在加载地图...",
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * 重新加载地图（用于"回到车辆位置"功能）
 */
fun WebView.reloadMap(longitude: Double, latitude: Double, zoomLevel: Int = 16) {
    val js = """
        if (typeof map !== 'undefined') {
            map.setCenter([$longitude, $latitude]);
            map.setZoom($zoomLevel);
        }
    """.trimIndent()
    evaluateJavascript(js, null)
}
