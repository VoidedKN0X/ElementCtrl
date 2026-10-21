package com.vknxworks.elementctrl.ui.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.elementctrl.matrix.MatrixSession
import com.vknxworks.elementctrl.matrix.RoomSearch
import com.vknxworks.elementctrl.ui.Avatar
import com.vknxworks.elementctrl.ui.EmptyState
import com.vknxworks.elementctrl.ui.shortTime
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import com.vknxworks.elementctrl.R

/** Search the messages of one chat, including encrypted ones, and jump to a result. */
@Composable
fun SearchScreen(session: MatrixSession, roomId: String, roomName: String?, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val search = remember(roomId) { RoomSearch(session, roomId) }
    DisposableEffect(search) { onDispose { search.close() } }
    val results by search.results.collectAsStateWithLifecycle()
    val loading by search.loading.collectAsStateWithLifecycle()
    val endReached by search.endReached.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val atEnd by remember { derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= results.size - 4 } }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query) {
        delay(300)
        search.query(query)
    }
    // The index keeps growing while history is fetched, so keep asking for more while scrolled to the end.
    LaunchedEffect(atEnd, results.size, loading) { if (atEnd && query.isNotBlank() && !loading && !endReached) search.more() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.search, roomName ?: "this chat")) },
                        singleLine = true,
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, stringResource(R.string.clear)) } },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).imePadding()) {
            when {
                query.isBlank() -> EmptyState(
                    Icons.Default.Search, stringResource(R.string.search_messages),
                    stringResource(R.string.searches_messages_stored_on_this),
                )
                results.isEmpty() && !loading -> EmptyState(Icons.Default.Search, stringResource(R.string.no_results), stringResource(R.string.older_messages_may_still_be_indexing))
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(results, key = { it.eventId }) { hit ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(hit.eventId) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Avatar(hit.senderName, hit.senderAvatar, hit.sender, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(hit.senderName, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(shortTime(hit.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(highlight(hit.text, query), style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (loading && query.isNotBlank()) {
                    item("loading") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun highlight(text: String, query: String): AnnotatedString {
    val words = query.split(Regex("\\s+")).filter { it.length >= 2 }
    val color = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        append(text)
        words.forEach { w ->
            var i = text.indexOf(w, ignoreCase = true)
            while (i >= 0) {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color), i, i + w.length)
                i = text.indexOf(w, i + w.length, ignoreCase = true)
            }
        }
    }
}
