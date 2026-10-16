package com.vknxworks.elementctrl.ui.room

import androidx.lifecycle.ViewModel
import com.vknxworks.elementctrl.matrix.MatrixSession
import com.vknxworks.elementctrl.matrix.RoomController
import com.vknxworks.elementctrl.matrix.TimelineController
import com.vknxworks.elementctrl.ui.Notifications

/**
 * Keeps an open room's SDK objects alive for as long as its screen is on the back stack, so going to the
 * details screen and back doesn't reload the timeline.
 */
class RoomViewModel(
    val session: MatrixSession,
    val roomId: String,
    val threadRootId: String? = null,
    withTimeline: Boolean = true,
) : ViewModel() {
    private val sdkRoom = session.getRoom(roomId)
    val room: RoomController? = sdkRoom?.let { RoomController(session, it) }
    val timeline: TimelineController? = if (withTimeline) sdkRoom?.let { TimelineController(session, it, threadRootId) } else null

    init {
        room?.start()
        timeline?.start()
        if (withTimeline) Notifications.clear(roomId)
    }

    override fun onCleared() {
        timeline?.close()
        room?.close()
        runCatching { sdkRoom?.close() }
    }
}
