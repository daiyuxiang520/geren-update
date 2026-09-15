package com.open.wuling.data.local

/**
 * 统一的实况天气模型。
 *
 * 高德（[AmapGeoResolver]，需「Web 服务」Key）与 Open-Meteo（[OpenMeteoResolver]，无需 Key）
 * 都产出这个结构，好处是：
 *  - UI 无差别渲染，高德降级到 Open-Meteo 时展示不跳变；
 *  - 来源由 [source] 标记，界面可标注数据出处，便于排查。
 *
 * 字段口径与高德 `lives[0]` 对齐（温度 ℃、湿度 %、风力为级别数字）。
 */
data class WeatherInfo(
    val weather: String,        // 天气现象（中文），如「晴」「多云」
    val temperature: String,    // 实时气温，℃
    val humidity: String,       // 空气湿度，%
    val windDirection: String,  // 风向，如「东北风」
    val windPower: String,      // 风力级别（数字），如「3」
    val reportTime: String = "",// 数据发布时间
    val city: String = "",      // 城市名（高德有；Open-Meteo 不返回，为空）
    val source: Source = Source.AMAP
) {
    /** 数据来源 */
    enum class Source(val label: String) {
        AMAP("高德"),
        OPEN_METEO("Open-Meteo")
    }
}
