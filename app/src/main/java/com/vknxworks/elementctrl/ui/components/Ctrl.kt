package com.vknxworks.elementctrl.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.vknxworks.elementctrl.R
import com.vknxworks.elementctrl.ui.CtrlColors
import com.vknxworks.elementctrl.ui.CtrlShapes
import com.vknxworks.elementctrl.ui.CtrlType
import com.vknxworks.elementctrl.ui.Gutter
import com.vknxworks.elementctrl.ui.TabItem

// The "Ctrl / Signal" building blocks shared by every Ctrl app: the wordmark top bar, square back button,
// cards, the accent hero card, the floating bottom bar, the rounded-rect switch, buttons, fields, dialogs and sheets.

// ---------- Top bars ----------

/** "ELEMENT/CTRL": JetBrains Mono, wide tracking, the slash in the accent colour. */
@Composable
fun CtrlWordmark(name: String, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append(name.uppercase())
            withStyle(SpanStyle(color = CtrlColors.AccentText)) { append("/") }
            append("CTRL")
        },
        style = CtrlType.Wordmark,
        color = CtrlColors.Text,
        maxLines = 1,
        modifier = modifier,
    )
}

/** Main-screen top bar: wordmark on the left, [trailing] actions and then the profile button on the right. */
@Composable
fun CtrlTopBar(
    name: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
    profile: @Composable () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().background(CtrlColors.Background).statusBarsPadding()
            .padding(start = Gutter, end = Gutter, top = 12.dp, bottom = 8.dp)
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CtrlWordmark(name, Modifier.weight(1f))
        trailing()
        Spacer(Modifier.width(4.dp))
        profile()
    }
}

/** A 44 dp square control (surface fill, 1 dp border, 14 dp corners): the back button and the profile button. */
@Composable
fun CtrlSquareButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .clip(CtrlShapes.Square)
            .background(CtrlColors.Surface)
            .border(1.dp, CtrlColors.ControlBorder, CtrlShapes.Square)
            .clickable(role = Role.Button, onClick = onClick)
            .then(contentDescription?.let { label -> Modifier.semantics { this.contentDescription = label } } ?: Modifier),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The square back button of detail screens. */
@Composable
fun CtrlBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.back)
    CtrlSquareButton(onBack, label, modifier) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, Modifier.size(20.dp), tint = CtrlColors.Text)
    }
}

/** Detail-screen top bar: square back button, a title slot and actions. No Material colour fill. */
@Composable
fun CtrlSubBar(
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    title: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth().background(CtrlColors.Background).statusBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            CtrlBackButton(onBack)
            Spacer(Modifier.width(12.dp))
        } else {
            Spacer(Modifier.width(4.dp))
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, content = title)
        actions()
    }
}

// ---------- Labels, cards ----------

/** Section label: JetBrains Mono 11 sp, upper case, wide tracking, muted. */
@Composable
fun CtrlLabel(text: String, modifier: Modifier = Modifier, color: Color = CtrlColors.Muted) {
    Text(text.uppercase(), style = CtrlType.Label, color = color, modifier = modifier, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/** An 8 dp status square (2 dp corners). */
@Composable
fun CtrlStatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).clip(CtrlShapes.Dot).background(color))
}

/** Secondary card: surface fill, 1 dp border, 22 dp corners. */
@Composable
fun CtrlCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(18.dp),
    shape: Shape = CtrlShapes.Card,
    color: Color = CtrlColors.Surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(shape)
            .background(color)
            .border(1.dp, CtrlColors.Border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/**
 * The hero card: the one most important thing on a main screen. Accent fill, #08080A content, a mono label row on
 * top and a giant number below it.
 */
@Composable
fun CtrlHeroCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trailingLabel: String? = null,
    unit: String? = null,
    footer: String? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CtrlShapes.Hero)
            .background(CtrlColors.Accent)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), style = CtrlType.Label.copy(fontWeight = FontWeight.SemiBold), color = CtrlColors.OnAccent, modifier = Modifier.weight(1f))
            if (trailingLabel != null) {
                Text(trailingLabel.uppercase(), style = CtrlType.Label.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp), color = CtrlColors.OnAccent)
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = CtrlType.Number, color = CtrlColors.OnAccent, maxLines = 1)
            if (unit != null) {
                Spacer(Modifier.width(6.dp))
                Text(unit, style = CtrlType.Meta.copy(fontSize = 14.sp), color = CtrlColors.OnAccent, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
        if (footer != null) Text(footer, style = CtrlType.Meta, color = CtrlColors.OnAccent)
    }
}

// ---------- Bottom bar ----------

/**
 * Floating bottom bar: 64 dp tall, 22 dp corners, surface at 92 % with a 1 dp border. The selected section is an
 * accent rounded rect with its icon and label; the others are muted icons with 48 dp touch targets.
 */
@Composable
fun CtrlBottomBar(
    items: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(CtrlShapes.Card)
            .background(CtrlColors.Surface.copy(alpha = 0.92f))
            .border(1.dp, CtrlColors.ControlBorder, CtrlShapes.Card)
            .padding(horizontal = 8.dp)
            .selectableGroup(),
        horizontalArrangement = if (items.size < 4) Arrangement.SpaceEvenly else Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { i, item ->
            val selected = i == selectedIndex
            val label = stringResource(item.label)
            val bg by animateColorAsState(if (selected) CtrlColors.Accent else Color.Transparent, label = "tab-bg")
            val fg by animateColorAsState(if (selected) CtrlColors.OnAccent else CtrlColors.Muted, label = "tab-fg")
            Row(
                Modifier
                    .animateContentSize()
                    .height(48.dp)
                    .widthIn(min = 48.dp)
                    .clip(CtrlShapes.Control)
                    .background(bg)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = {
                            if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(i)
                        },
                    )
                    .padding(horizontal = if (selected) 18.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(item.icon, contentDescription = if (selected) null else label, tint = fg, modifier = Modifier.size(if (selected) 18.dp else 20.dp))
                if (selected) {
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), fontFamily = com.vknxworks.elementctrl.ui.SpaceGrotesk, color = fg, maxLines = 1)
                }
            }
        }
    }
}

// ---------- Controls ----------

/**
 * The Ctrl switch: a 52×32 dp rounded rect (10 dp corners, 4 dp padding) with a 24 dp knob (7 dp corners).
 * On: accent track, dark knob. Off: the palette's off track and grey knob. Announced as a switch.
 */
@Composable
fun CtrlSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val track by animateColorAsState(if (checked) CtrlColors.Accent else CtrlColors.SwitchTrackOff, label = "switch-track")
    val knob by animateColorAsState(if (checked) CtrlColors.OnAccent else CtrlColors.SwitchKnobOff, label = "switch-knob")
    val knobX by animateDpAsState(if (checked) 20.dp else 0.dp, label = "switch-x")
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    } else Modifier
    Box(
        modifier
            .then(toggle)
            .minimumInteractiveComponentSize()
            .size(width = 52.dp, height = 32.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(RoundedCornerShape(10.dp))
            .background(track)
            .padding(4.dp),
    ) {
        Box(Modifier.offset(x = knobX).size(24.dp).clip(RoundedCornerShape(7.dp)).background(knob))
    }
}

private val ButtonPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)

@Composable
fun ctrlButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = CtrlColors.Accent,
    contentColor = CtrlColors.OnAccent,
    disabledContainerColor = CtrlColors.Raised,
    disabledContentColor = CtrlColors.Disabled,
)

/** Primary button: accent fill, dark text, 16 dp corners. Give main calls to action a 56 dp height. */
@Composable
fun CtrlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ctrlButtonColors(),
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = CtrlShapes.Button,
        colors = colors,
        contentPadding = ButtonPadding,
        content = content,
    )
}

/** Secondary button: surface fill with a 1 dp border. */
@Composable
fun CtrlSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(containerColor = CtrlColors.Surface, contentColor = CtrlColors.Text),
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = CtrlShapes.Button,
        colors = colors,
        border = BorderStroke(1.dp, CtrlColors.ControlBorder),
        contentPadding = ButtonPadding,
        content = content,
    )
}

@Composable
fun ctrlFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = CtrlColors.Surface,
    unfocusedContainerColor = CtrlColors.Surface,
    disabledContainerColor = CtrlColors.Surface,
    errorContainerColor = CtrlColors.Surface,
    focusedBorderColor = CtrlColors.AccentText,
    unfocusedBorderColor = CtrlColors.ControlBorder,
    disabledBorderColor = CtrlColors.Border,
    cursorColor = CtrlColors.AccentText,
    focusedLabelColor = CtrlColors.AccentText,
    unfocusedLabelColor = CtrlColors.Muted,
    focusedLeadingIconColor = CtrlColors.AccentText,
    unfocusedLeadingIconColor = CtrlColors.Muted,
    focusedTextColor = CtrlColors.Text,
    unfocusedTextColor = CtrlColors.Text,
    unfocusedPlaceholderColor = CtrlColors.Muted,
    focusedPlaceholderColor = CtrlColors.Muted,
)

/** Text field: surface fill, 1 dp control border, 16 dp corners; accent border and cursor when focused. */
@Composable
fun CtrlTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = CtrlShapes.Field,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        shape = shape,
        colors = ctrlFieldColors(),
    )
}

/** Progress bar: 6 dp tall, 3 dp corners, #26262C track. */
@Composable
fun CtrlProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = CtrlColors.ProgressFill,
    trackColor: Color = CtrlColors.ProgressTrack,
) {
    LinearProgressIndicator(
        progress = progress,
        modifier = modifier.height(6.dp).clip(RoundedCornerShape(3.dp)),
        color = color,
        trackColor = trackColor,
        strokeCap = StrokeCap.Butt,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

// ---------- Dialogs and sheets ----------

/** Alert dialog on the surface colour with 26 dp corners and a 1 dp border. */
@Composable
fun CtrlDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.border(1.dp, CtrlColors.Border, CtrlShapes.Dialog),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = CtrlShapes.Dialog,
        containerColor = CtrlColors.Surface,
        iconContentColor = CtrlColors.AccentText,
        titleContentColor = CtrlColors.Text,
        textContentColor = CtrlColors.Muted,
        tonalElevation = 0.dp,
        properties = properties,
    )
}

private val SheetShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)

/** Bottom sheet on the surface colour with 26 dp top corners and a 1 dp border. */
@Composable
fun CtrlSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.border(1.dp, CtrlColors.Border, SheetShape),
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = CtrlColors.Surface,
        contentColor = CtrlColors.Text,
        tonalElevation = 0.dp,
        content = content,
    )
}

// ---------- Lists ----------

/** A plain list row (min 52 dp) with a 1 dp top divider, as in the Ctrl lists. */
@Composable
fun CtrlListRow(
    modifier: Modifier = Modifier,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (divider) Box(Modifier.fillMaxWidth().padding(horizontal = Gutter).height(1.dp).background(CtrlColors.Divider))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = Gutter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/** Big icon tile used by empty states and the lock screen: 72 dp, 24 dp corners. */
@Composable
fun CtrlIconTile(icon: ImageVector, modifier: Modifier = Modifier, filled: Boolean = false) {
    Box(
        modifier
            .size(72.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (filled) CtrlColors.Accent else CtrlColors.Surface)
            .then(if (filled) Modifier else Modifier.border(1.dp, CtrlColors.Border, RoundedCornerShape(24.dp))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(30.dp), tint = if (filled) CtrlColors.OnAccent else CtrlColors.AccentText)
    }
}
