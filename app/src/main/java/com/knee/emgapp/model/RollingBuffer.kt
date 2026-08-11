package com.knee.emgapp.model

import java.util.ArrayDeque

/**
 * 环形滚动缓冲, 保存每通道最近 N 个采样点, 供波形绘制。
 */
class RollingBuffer(private val capacity: Int) {

    private val points = ArrayDeque<Float>(capacity)

    @Synchronized
    fun push(value: Float) {
        points.addLast(value)
        while (points.size > capacity) {
            points.removeFirst()
        }
    }

    @Synchronized
    fun toList(): List<Float> = points.toList()

    @Synchronized
    fun clear() = points.clear()

    val size get() = points.size
}