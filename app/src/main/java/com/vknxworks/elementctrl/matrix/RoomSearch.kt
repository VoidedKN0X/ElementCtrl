package com.vknxworks.elementctrl.matrix

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.SearchService
import org.matrix.rustcomponents.sdk.SearchServicePaginationStateListener
import org.matrix.rustcomponents.sdk.SearchServiceResult
import org.matrix.rustcomponents.sdk.SearchServiceResultsListener
import org.matrix.rustcomponents.sdk.SearchServiceResultsUpdate
import org.matrix.rustcomponents.sdk.TaskHandle
import uniffi.matrix_sdk.SearchBackfillStrategy
import uniffi.matrix_sdk_ui.SearchServicePaginationState

data class SearchHit(
    val roomId: String,
    val eventId: String,
    val sender: String,
    val senderName: String,
    val senderAvatar: String?,
    val text: String,
    val timestamp: Long,
)

/**
 * Full-text search over the on-device index the SDK keeps (so it works in encrypted rooms too). While the
 * search screen is open, older history is fetched into the index in the background.
 */
class RoomSearch(private val session: MatrixSession, private val roomId: String?) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val service: SearchService = session.client.searchService()
    private val handles = mutableListOf<TaskHandle>()
    private val updates = Channel<List<SearchServiceResultsUpdate>>(Channel.UNLIMITED)
    // Index-parallel to the SDK's list; filtering by room happens only when publishing.
    private val all = ArrayList<SearchHit?>()
    private var subscribed = false
    private var backfill: TaskHandle? = null

    private val _results = MutableStateFlow<List<SearchHit>>(emptyList())
    val results: StateFlow<List<SearchHit>> = _results.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _endReached = MutableStateFlow(false)
    val endReached: StateFlow<Boolean> = _endReached.asStateFlow()

    init {
        handles += service.subscribeToPaginationStateUpdates(object : SearchServicePaginationStateListener {
            override fun onUpdate(paginationState: SearchServicePaginationState) {
                when (paginationState) {
                    is SearchServicePaginationState.Idle -> { _loading.value = false; _endReached.value = paginationState.endReached }
                    else -> _loading.value = true
                }
            }
        })
        scope.launch { for (batch in updates) apply(batch) }
        // Index older messages while searching, gently, since the app is in the foreground.
        runCatching { backfill = session.client.runSearchBackfill(SearchBackfillStrategy.FOREGROUND) }
    }

    fun query(text: String) {
        scope.launch {
            runCatching {
                if (!subscribed) {
                    handles += service.subscribeToResults(object : SearchServiceResultsListener {
                        override fun onUpdate(updates: List<SearchServiceResultsUpdate>) { this@RoomSearch.updates.trySend(updates) }
                    })
                    subscribed = true
                }
                _endReached.value = false
                service.setQuery(escape(text))
                if (text.isNotBlank()) service.paginate()
            }
        }
    }

    fun more() {
        if (_loading.value || _endReached.value) return
        scope.launch { runCatching { service.paginate() } }
    }

    fun close() {
        handles.forEach { runCatching { it.cancel() } }
        runCatching { backfill?.cancel() }
        updates.close()
        scope.cancel()
    }

    private fun map(r: SearchServiceResult): SearchHit? {
        val m = r as? SearchServiceResult.Message ?: return null
        val msg = m.result
        return SearchHit(
            roomId = m.roomId,
            eventId = msg.eventId,
            sender = msg.sender,
            senderName = msg.senderProfile.nameOr(msg.sender),
            senderAvatar = msg.senderProfile.avatarOrNull(),
            text = msg.content.previewText(),
            timestamp = msg.timestamp.toLong(),
        )
    }

    private fun apply(batch: List<SearchServiceResultsUpdate>) {
        for (u in batch) when (u) {
            is SearchServiceResultsUpdate.Append -> all.addAll(u.values.map(::map))
            is SearchServiceResultsUpdate.PushBack -> all.add(map(u.value))
            is SearchServiceResultsUpdate.PushFront -> all.add(0, map(u.value))
            is SearchServiceResultsUpdate.Insert -> all.add(u.index.toInt().coerceAtMost(all.size), map(u.value))
            is SearchServiceResultsUpdate.Set -> if (u.index.toInt() < all.size) all[u.index.toInt()] = map(u.value)
            is SearchServiceResultsUpdate.Remove -> if (u.index.toInt() < all.size) all.removeAt(u.index.toInt())
            is SearchServiceResultsUpdate.Truncate -> while (all.size > u.length.toInt()) all.removeAt(all.lastIndex)
            is SearchServiceResultsUpdate.Reset -> { all.clear(); all.addAll(u.values.map(::map)) }
            SearchServiceResultsUpdate.Clear -> all.clear()
            SearchServiceResultsUpdate.PopBack -> all.removeLastOrNull()
            SearchServiceResultsUpdate.PopFront -> all.removeFirstOrNull()
        }
        _results.value = all.filterNotNull().filter { roomId == null || it.roomId == roomId }
    }

    /** The index uses a query language where ":" and friends are syntax; search the words literally instead. */
    private fun escape(q: String): String = q.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ") { token ->
        val escaped = buildString { token.forEach { c -> if (c in "+-&|!(){}[]^\"~*?:\\/'<>=") append('\\'); append(c) } }
        if (token.any(Char::isLetterOrDigit)) "+$escaped" else escaped
    }
}
