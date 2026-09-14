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
fun AmapView(
    longitude: Double,
    latitude: Double,
    modifier: Modifier = Modifier,
    zoomLevel: Int = 16,
    showMarker: Boolean = true,
    key: String = ""
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
    DisposableEffect(longitude, latitude, zoomLevel, showMarker, key) {
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
                        if (typeof AMap !== 'undefined') {
                            var map = new AMap.Map('container', {
                                zoom: $zoomLevel,
                                center: [$longitude, $latitude],
                                viewMode: '2D'
                            });

                            AMap.plugin(['AMap.ToolBar', 'AMap.Marker'], function() {
                                map.addControl(new AMap.ToolBar({ position: 'RB' }));
                                
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
