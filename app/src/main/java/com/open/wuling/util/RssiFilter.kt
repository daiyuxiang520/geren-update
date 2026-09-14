package com.open.wuling.util

/**
 * RSSI 滑动滤波工具类
 * 用于平滑蓝牙信号强度，消除抖动
 */
class RssiFilter(private val windowSize: Int = 10) {
    private val values = mutableListOf<Int>()

    /**
     * 添加新的 RSSI 值并返回滤波后的结果
     * @param rssi 原始 RSSI 值
     * @return 滤波后的平均 RSSI 值
     */
    fun addAndFilter(rssi: Int): Int {
        values.add(rssi)
        if (values.size > windowSize) {
            values.removeAt(0)
        }
        return getFilteredValue()
    }

    /**
     * 获取当前滤波后的 RSSI 值
     */
    fun getFilteredValue(): Int {
        if (values.isEmpty()) return 0
        return values.average().toInt()
    }

    /**
     * 重置滤波器
     */
    fun reset() {
        values.clear()
    }

    /**
     * 检查是否有足够的数据进行滤波
     */
    fun hasEnoughData(): Boolean {
        return values.size >= windowSize / 2
    }
}
