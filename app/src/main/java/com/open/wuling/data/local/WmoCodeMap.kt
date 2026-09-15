package com.open.wuling.data.local

/**
 * WMO 天气代码 → 中文天气现象 + emoji 映射。
 *
 * Open-Meteo 的 `current.weather_code` 采用 **WMO 4677 标准代码**（纯数字），
 * 与高德直接返回中文不同，因此单独维护这张表。
 *
 * 映射成中文后，即可复用 [WeatherCodeMap.icon] 取 emoji，
 * 保证两个数据源的展示风格一致。
 *
 * 参考：https://open-meteo.com/en/docs  (Weather variable documentation)
 */
object WmoCodeMap {

    private val MAP = mapOf(
        // 晴 / 云
        0 to ("晴" to "☀️"),
        1 to ("少云" to "🌤️"),
        2 to ("晴间多云" to "⛅"),
        3 to ("阴" to "☁️"),
        // 雾
        45 to ("雾" to "🌫️"),
        48 to ("雾凇" to "🌫️"),
        // 毛毛雨
        51 to ("小毛毛雨" to "🌦️"),
        53 to ("毛毛雨" to "🌦️"),
        55 to ("大毛毛雨" to "🌧️"),
        // 冻毛毛雨
        56 to ("轻冻毛毛雨" to "🌧️"),
        57 to ("浓冻毛毛雨" to "🌧️"),
        // 雨
        61 to ("小雨" to "🌦️"),
        63 to ("中雨" to "🌧️"),
        65 to ("大雨" to "🌧️"),
        // 冻雨
        66 to ("轻冻雨" to "🌧️"),
        67 to ("强冻雨" to "🌧️"),
        // 雪
        71 to ("小雪" to "🌨️"),
        73 to ("中雪" to "❄️"),
        75 to ("大雪" to "❄️"),
        77 to ("米雪" to "🌨️"),
        // 阵雨
        80 to ("小阵雨" to "🌦️"),
        81 to ("中阵雨" to "🌧️"),
        82 to ("强阵雨" to "⛈️"),
        // 阵雪
        85 to ("小阵雪" to "🌨️"),
        86 to ("大阵雪" to "❄️"),
        // 雷暴
        95 to ("雷阵雨" to "⛈️"),
        96 to ("雷阵雨伴冰雹" to "⛈️"),
        99 to ("强雷阵雨伴冰雹" to "⛈️")
    )

    /** WMO 代码 → 中文天气现象（未知码返回「未知」） */
    fun text(code: Int): String = MAP[code]?.first ?: "未知"

    /** WMO 代码 → emoji（未知码返回温度计兜底图标） */
    fun icon(code: Int): String = MAP[code]?.second ?: "🌡️"
}
