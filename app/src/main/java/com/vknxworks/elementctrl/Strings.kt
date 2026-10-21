package com.vknxworks.elementctrl

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/** Text from strings.xml for code that has no Context of its own (Matrix layer, notifications, enums, errors). */
object Strings {
    private lateinit var context: Context

    fun init(ctx: Context) {
        context = ctx.applicationContext
    }

    fun str(@StringRes id: Int, vararg args: Any?): String = context.getString(id, *args)

    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any?): String = context.resources.getQuantityString(id, count, *args)
}
