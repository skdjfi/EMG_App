package com.knee.emgapp.theme

import android.content.Context
import android.content.res.ColorStateList
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

/** 从当前主题读取颜色属性的小工具。 */
object Theme {

    @ColorInt
    fun color(context: Context, @AttrRes attr: Int): Int {
        val ta = context.obtainStyledAttributes(intArrayOf(attr))
        try {
            return ta.getColor(0, 0xFF000000.toInt())
        } finally {
            ta.recycle()
        }
    }

    fun tint(@ColorInt value: Int): ColorStateList = ColorStateList.valueOf(value)
}