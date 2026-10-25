package com.vknxworks.elementctrl.ui

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.vknxworks.elementctrl.matrix.mxcAvatar
import coil3.compose.AsyncImage
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.elementctrl.R
import com.vknxworks.elementctrl.matrix.MatrixSession
import com.vknxworks.elementctrl.ui.components.CtrlLabel
import com.vknxworks.elementctrl.ui.components.CtrlSheet
import com.vknxworks.elementctrl.ui.components.CtrlSquareButton
import com.vknxworks.elementctrl.ui.components.CtrlSubBar
import com.vknxworks.elementctrl.ui.components.CtrlSwitch
import com.vknxworks.elementctrl.ui.components.CtrlTopBar
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight

// The app shell shared with the other Ctrl apps ("Ctrl / Signal"): top bars, the profile picture, grouped
// settings rows and the floating tab bar, built on the components in ui/components.

// ---------- Top bars ----------

/** Top bar for pushed screens: the square Ctrl back button and the title in Space Grotesk. */
@Composable
fun ScreenTopBar(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
) {
    CtrlSubBar(onBack = onBack, actions = actions) {
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Main-screen top bar: the "ELEMENT/CTRL" wordmark on the left, the screen's [title] as a big headline under it,
 * and your profile picture at the end.
 */
@Composable
fun TabTitle(
    title: String,
    session: MatrixSession,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        CtrlTopBar(name = "Element", trailing = trailing) { ProfileButton(session, onProfile) }
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = Gutter, end = Gutter, top = 10.dp, bottom = 6.dp),
        )
    }
}

/**
 * Your Matrix profile picture (or initial) in the Ctrl square: 44 dp, 14 dp corners, 1 dp border, with a small
 * accent square at the bottom right while connected. Tapping it opens the You page, the only way into settings.
 */
@Composable
fun ProfileButton(session: MatrixSession, onClick: () -> Unit) {
    val profile by session.profile.collectAsStateWithLifecycle()
    val sync by session.syncState.collectAsStateWithLifecycle()
    val stuck by session.stuck.collectAsStateWithLifecycle()
    val connected = !stuck && sync == org.matrix.rustcomponents.sdk.SyncServiceState.RUNNING
    val label = stringResource(R.string.you_title)
    Box {
        CtrlSquareButton(onClick, label) {
            ProfileAvatar(profile.displayName ?: session.userId, profile.avatarUrl, 42.dp, shape = CtrlShapes.Square, square = true)
        }
        if (connected) {
            Box(
                Modifier.align(Alignment.BottomEnd).offset(x = 3.dp, y = 3.dp).size(10.dp)
                    .border(2.dp, CtrlColors.Background, RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp)).background(CtrlColors.Accent),
            )
        }
    }
}

/**
 * Your own picture, as in every Ctrl app: your Matrix avatar when there is one, otherwise your initial. In the
 * Ctrl square ([square]) the initial sits on the surface colour; the large profile tile uses the accent fill.
 */
@Composable
fun ProfileAvatar(
    name: String,
    url: String?,
    size: Dp,
    initialStyle: TextStyle = MaterialTheme.typography.titleMedium,
    shape: Shape = CtrlShapes.Card,
    square: Boolean = false,
) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val model = remember(url, px) { mxcAvatar(url, (px * 2).coerceAtMost(512)) }
    Box(
        Modifier.size(size).clip(shape).background(if (square) CtrlColors.Surface else CtrlColors.Accent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials(name),
            style = initialStyle.copy(fontWeight = FontWeight.Bold),
            color = if (square) CtrlColors.Text else CtrlColors.OnAccent,
            maxLines = 1,
        )
        if (model != null) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

// ---------- Grouped lists (You page, settings) ----------

/** A Ctrl card of related rows (1 dp dividers between them), with a mono label above it and a note below. */
@Composable
fun Group(
    title: String? = null,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp)) {
        if (title != null) {
            CtrlLabel(title, Modifier.padding(start = 4.dp, bottom = 8.dp))
        }
        Column(
            Modifier.fillMaxWidth().clip(CtrlShapes.Card).background(CtrlColors.Surface).border(1.dp, CtrlColors.Border, CtrlShapes.Card),
        ) {
            // Every row draws a 1 dp divider above itself; the first one is pulled up out of sight.
            Column(
                Modifier.layout { measurable, constraints ->
                    val hidden = 1.dp.roundToPx()
                    val p = measurable.measure(constraints)
                    layout(p.width, (p.height - hidden).coerceAtLeast(0)) { p.place(0, -hidden) }
                },
                content = content,
            )
        }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            )
        }
    }
}

/** The 1 dp divider every group row draws above itself (the group hides the first one). */
@Composable
fun GroupDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(CtrlColors.Border))
}

@Composable
fun GroupRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = CtrlColors.Muted,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    showChevron: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    GroupDivider()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp), tint = iconTint)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        } else if (showChevron) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun GroupSwitch(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    GroupRow(title, subtitle, icon, enabled = enabled, onClick = { onChange(!checked) }) {
        CtrlSwitch(checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** A row showing the current value; tapping opens a sheet to pick another. */
@Composable
fun <T> GroupChoice(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    icon: ImageVector? = null,
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second.orEmpty()
    GroupRow(title, current, icon, showChevron = true, onClick = { open = true })
    if (open) {
        ChoiceSheet(title, options, selected, onDismiss = { open = false }) { onPick(it); open = false }
    }
}

@Composable
fun <T> ChoiceSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    CtrlSheet(onDismissRequest = onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(options, key = { it.second }) { (value, label) ->
                val isSel = value == selected
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(value) }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSel) Icon(Icons.Outlined.Check, stringResource(R.string.selected), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// ---------- Floating tab bar ----------

data class TabItem(@StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

/** The floating Ctrl bottom bar (see [CtrlBottomBar]), 20 dp from the sides. */
@Composable
fun GlassTabBar(
    items: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    CtrlBottomBar(items, selectedIndex, onSelect, modifier.padding(horizontal = 20.dp))
}
