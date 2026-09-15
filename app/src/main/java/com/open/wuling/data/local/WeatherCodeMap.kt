package com.open.wuling.data.local

/**
 * 高德天气现象文字 → emoji 图标映射。
 *
 * 高德 `weather` 字段返回中文天气现象（见官方「天气现象表」），
 * 这里做一层 emoji 映射，让位置页的天气行更直观。
 *
 * 匹配策略：优先精确匹配；未命中则按关键字包含匹配（如「小雨-中雨」「雷阵雨并伴有冰雹」）；
 * 仍未知则返回默认图标。
 */
object WeatherCodeMap {

    private const val DEFAULT = "🌡️"

    /** 精确映射表（覆盖高德天气现象表全部取值） */
    private val EXACT = mapOf(
        // 晴 / 云
        "晴" to "☀️",
        "少云" to "🌤️",
        "晴间多云" to "⛅",
        "多云" to "⛅",
        "阴" to "☁️",
        // 风
        "有风" to "🌬️",
        "平静" to "🌬️",
        "微风" to "🌬️",
        "和风" to "🌬️",
        "清风" to "🌬️",
        "强风/劲风" to "💨",
        "疾风" to "💨",
        "大风" to "💨",
        "烈风" to "💨",
        "风暴" to "🌪️",
        "狂爆风" to "🌪️",
        "飓风" to "🌀",
        "热带风暴" to "🌀",
        // 霾
        "霾" to "😷",
        "中度霾" to "😷",
        "重度霾" to "😷",
        "严重霾" to "😷",
        // 雨
        "阵雨" to "🌦️",
        "雷阵雨" to "⛈️",
        "雷阵雨并伴有冰雹" to "⛈️",
        "小雨" to "🌧️",
        "中雨" to "🌧️",
        "大雨" to "🌧️",
        "暴雨" to "🌧️",
        "大暴雨" to "🌧️",
        "特大暴雨" to "🌧️",
        "强阵雨" to "🌧️",
        "强雷阵雨" to "⛈️",
        "极端降雨" to "🌧️",
        "毛毛雨/细雨" to "🌦️",
        "雨" to "🌧️",
        "小雨-中雨" to "🌧️",
        "中雨-大雨" to "🌧️",
        "大雨-暴雨" to "🌧️",
        "暴雨-大暴雨" to "🌧️",
        "大暴雨-特大暴雨" to "🌧️",
        // 雪
        "雨雪天气" to "🌨️",
        "雨夹雪" to "🌨️",
        "阵雨夹雪" to "🌨️",
        "冻雨" to "🌨️",
        "雪" to "❄️",
        "阵雪" to "🌨️",
        "小雪" to "🌨️",
        "中雪" to "❄️",
        "大雪" to "❄️",
        "暴雪" to "❄️",
        "小雪-中雪" to "🌨️",
        "中雪-大雪" to "❄️",
        "大雪-暴雪" to "❄️",
        // 沙尘
        "浮尘" to "🌫️",
        "扬沙" to "🌫️",
        "沙尘暴" to "🌪️",
        "强沙尘暴" to "🌪️",
        "龙卷风" to "🌪️",
        // 雾
        "雾" to "🌫️",
        "浓雾" to "🌫️",
        "强浓雾" to "🌫️",
        "轻雾" to "🌫️",
        "大雾" to "🌫️",
        "特强浓雾" to "🌫️",
        // 其他
        "热" to "🔥",
        "冷" to "🥶",
        "未知" to DEFAULT
    )

    /**
     * 天气文字 → emoji。
     * 先精确匹配，再按关键字包含匹配（处理「雷阵雨并伴有冰雹」等长组合词）。
     */
    fun icon(weather: String?): String {
        val w = weather?.trim().orEmpty()
        if (w.isEmpty()) return DEFAULT
        EXACT[w]?.let { return it }

        // 关键字包含匹配（从强到弱，避免「小雨」误判「雷阵雨」中的「雨」）
        return when {
            w.contains("冰雹") -> "🌨️"
            w.contains("雷") -> "⛈️"
            w.contains("雪") -> "❄️"
            w.contains("雾") || w.contains("霾") || w.contains("尘") || w.contains("沙") -> "🌫️"
            w.contains("风") || w.contains("风暴") || w.contains("飓风") -> "💨"
            w.contains("雨") -> "🌧️"
            w.contains("云") -> "⛅"
            w.contains("晴") -> "☀️"
            w.contains("阴") -> "☁️"
            w.contains("热") -> "🔥"
            w.contains("冷") -> "🥶"
            else -> DEFAULT
        }
    }
}
