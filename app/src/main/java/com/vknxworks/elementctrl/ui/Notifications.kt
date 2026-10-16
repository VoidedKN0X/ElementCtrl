package com.vknxworks.elementctrl.ui

import com.vknxworks.elementctrl.ElementCtrlApp
import com.vknxworks.elementctrl.notify.Notifier

/** Small bridge so screens can dismiss a room's notification when it is opened. */
object Notifications {
    fun clear(roomId: String) = Notifier.clear(ElementCtrlApp.instance, roomId)
}
