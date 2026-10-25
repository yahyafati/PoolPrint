package com.yahyafati.poolprint.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yahyafati.poolprint.core.model.PrinterType
import org.jetbrains.compose.resources.stringResource
import poolprint.shared.generated.resources.*

/** The 24dp side margin every screen uses. */
fun Modifier.gutter(): Modifier = padding(horizontal = 24.dp)

/**
 * A full screen: a top bar, scrolling content and a bottom action area that stays above the keyboard and the
 * navigation bar. On a wide desktop window it is a centred, phone-width column. [overlay] draws over everything
 * (confirmation panels). With [spaceBetween] the content fills at least the visible height and its children are
 * spread out, so the last one sits at the bottom until the content is taller than the screen.
 */
@Composable
fun PhoneScreen(
    topBar: @Composable () -> Unit = {},
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    overlay: @Composable () -> Unit = {},
    spaceBetween: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(PoolTheme.colors.bg)) {
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxHeight().widthIn(max = 520.dp).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            topBar()
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .then(if (spaceBetween) Modifier.heightIn(min = maxHeight) else Modifier)
                        .padding(bottom = 16.dp),
                    verticalArrangement = if (spaceBetween) Arrangement.SpaceBetween else Arrangement.Top,
                    content = content,
                )
            }
            if (bottomBar != null) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = bottomBar,
                )
            }
        }
        overlay()
    }
}

/** A 2dp ink rule between sections, or ([strong] false) the 1dp divider between rows. */
@Composable
fun Rule(modifier: Modifier = Modifier, strong: Boolean = true, color: Color? = null) {
    val c = PoolTheme.colors
    Box(modifier.fillMaxWidth().height(if (strong) 2.dp else 1.dp).background(color ?: if (strong) c.rule else c.outlineVariant))
}

/** A 48dp tap target showing a text glyph (←, ×, ⋮), announced as [description]. */
@Composable
fun GlyphButton(glyph: String, description: String, onClick: () -> Unit, enabled: Boolean = true, highlighted: Boolean = false) {
    val c = PoolTheme.colors
    Box(
        Modifier.size(48.dp).background(if (highlighted) c.surfaceHigh else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (enabled) c.on else c.outline, modifier = Modifier.clearAndSetSemantics {})
    }
}

@Composable
fun IconButton48(icon: ImageVector, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    val c = PoolTheme.colors
    Box(
        Modifier.size(48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(22.dp), tint = if (enabled) c.on else c.outline)
    }
}

/** The 64dp title bar of a secondary screen, with ← and a strong rule under it. */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(start = if (onBack != null) 12.dp else 24.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) GlyphButton("←", stringResource(Res.string.action_back), onBack)
        Text(
            title,
            style = PoolTheme.type.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = if (onBack != null) 4.dp else 0.dp),
        )
        actions()
    }
    Rule()
}

/** The four-segment onboarding progress, with "← Back" and "Step n of 4". */
@Composable
fun StepHeader(step: Int, onBack: (() -> Unit)?, total: Int = 4) {
    val c = PoolTheme.colors
    Column(Modifier.fillMaxWidth().gutter().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 1..total) Box(Modifier.weight(1f).height(4.dp).background(if (i <= step) c.primary else c.outlineVariant))
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Text(
                    "← " + stringResource(Res.string.action_back),
                    style = PoolTheme.type.label,
                    color = c.onVariant,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onBack).padding(vertical = 8.dp).padding(end = 16.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(stringResource(Res.string.onboarding_step, step, total), style = PoolTheme.type.label, color = c.onVariant)
        }
    }
}

enum class ButtonKind { Primary, Outline, Ink, Danger }

/**
 * The design's rectangular button: label flush left, an optional trailing glyph (→, ↻) at the right edge.
 * Disabled buttons drop to a neutral fill.
 */
@Composable
fun BlockButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    height: Dp = 56.dp,
    trailing: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    textStyle: TextStyle? = null,
) {
    val c = PoolTheme.colors
    val t = PoolTheme.type
    val (fill, ink) = when {
        !enabled && kind != ButtonKind.Outline -> c.surfaceHigh to c.onVariant
        kind == ButtonKind.Primary -> c.lime to c.onLime
        kind == ButtonKind.Ink -> c.on to c.bg
        kind == ButtonKind.Danger -> c.error to c.bg
        else -> Color.Transparent to (if (enabled) c.on else c.outline)
    }
    val style = textStyle ?: when {
        height < 50.dp -> t.buttonSmall.copy(fontWeight = if (kind == ButtonKind.Outline) FontWeight.Bold else FontWeight.ExtraBold)
        kind == ButtonKind.Outline -> t.button.copy(fontWeight = FontWeight.Bold)
        else -> t.button
    }
    Row(
        modifier.fillMaxWidth().height(height).alpha(if (enabled) 1f else 0.7f).background(fill)
            .then(if (kind == ButtonKind.Outline) Modifier.border(2.dp, if (enabled) c.on else c.outline) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (height < 50.dp) 14.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon?.let { Icon(it, null, Modifier.size(18.dp), tint = ink) }
        Text(text, style = style, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, style = style, color = ink) }
    }
}

/** A bold inline action in the accent colour ("Save as preset", "Test again"). */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = PoolTheme.colors.primary,
    enabled: Boolean = true,
    style: TextStyle = PoolTheme.type.action,
) {
    Text(
        text,
        style = style,
        color = if (enabled) color else PoolTheme.colors.outline,
        modifier = modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
    )
}

/** A row's text block: an optional small label above, the main line, an optional line below. */
@Composable
fun RowText(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    subtitle: String? = null,
    titleStyle: TextStyle = PoolTheme.type.rowTitle,
    titleColor: Color = PoolTheme.colors.on,
    subtitleStyle: TextStyle = PoolTheme.type.caption,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        overline?.let { Text(it, style = PoolTheme.type.label.copy(fontSize = 12.sp), color = PoolTheme.colors.onVariant) }
        Text(title, style = titleStyle, color = titleColor)
        subtitle?.let { Text(it, style = subtitleStyle, color = PoolTheme.colors.onVariant) }
    }
}

/** A list row with a minimum height and an optional trailing slot; dividers are placed by the caller. */
@Composable
fun ListRow(
    modifier: Modifier = Modifier,
    minHeight: Dp = 64.dp,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = minHeight)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
        trailing()
    }
}

@Composable
fun Chevron(glyph: String = "›") {
    Text(glyph, fontSize = 22.sp, color = PoolTheme.colors.on, modifier = Modifier.clearAndSetSemantics {})
}

/** An uppercase section label. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = PoolTheme.type.caps, color = PoolTheme.colors.onVariant, modifier = modifier)
}

/** The design's text field: a label above, a filled box with a 2dp underline, and a helper or error line below. */
@Composable
fun PoolTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    labelColor: Color? = null,
    placeholder: String? = null,
    helper: String? = null,
    error: String? = null,
    textStyle: TextStyle = PoolTheme.type.mono.copy(fontSize = 18.sp),
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null,
) {
    val c = PoolTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        label?.let {
            Text(it, style = PoolTheme.type.label, color = labelColor ?: if (error != null) c.error else if (focused) c.primary else c.onVariant)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
            textStyle = textStyle.copy(color = c.on),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            minLines = minLines,
            interactionSource = interaction,
            cursorBrush = SolidColor(c.primary),
            decorationBox = { inner ->
                FieldBox(focused = focused, isError = error != null) {
                    if (value.isEmpty() && placeholder != null) Text(placeholder, style = textStyle, color = c.outline)
                    inner()
                }
            },
        )
        when {
            error != null -> ErrorLine(error)
            helper != null -> Text(helper, style = PoolTheme.type.caption, color = c.onVariant)
        }
    }
}

/** The filled box and underline shared by the text fields. */
@Composable
fun FieldBox(focused: Boolean, isError: Boolean, inner: @Composable () -> Unit) {
    val c = PoolTheme.colors
    Column {
        Box(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 14.dp, vertical = 14.dp)) { inner() }
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (isError) c.error else if (focused) c.primary else c.outline))
    }
}

@Composable
fun ErrorLine(text: String) {
    val c = PoolTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("⚠", style = PoolTheme.type.label, color = c.error)
        Text(text, style = PoolTheme.type.label, color = c.error)
    }
}

/** The square on/off switch. */
@Composable
fun SquareSwitch(checked: Boolean) {
    val c = PoolTheme.colors
    if (checked) {
        Box(Modifier.size(52.dp, 32.dp).background(c.primary).padding(4.dp), contentAlignment = Alignment.CenterEnd) {
            Box(Modifier.size(24.dp).background(c.onPrimary), contentAlignment = Alignment.Center) {
                Icon(PoolIcons.Check, null, Modifier.size(14.dp), tint = c.primary)
            }
        }
    } else {
        Box(Modifier.size(52.dp, 32.dp).border(2.dp, c.outline).padding(6.dp), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.size(16.dp).background(c.outline))
        }
    }
}

/** A whole row that toggles: title, optional subtitle, and the switch at the end. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    minHeight: Dp = 64.dp,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = minHeight).toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RowText(title, Modifier.weight(1f), subtitle = subtitle)
        SquareSwitch(checked)
    }
}

/** Equal-width cells in a 2dp ink frame; the selected one is inverted and ticked. */
@Composable
fun <T> Segmented(items: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = PoolTheme.colors
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).border(2.dp, c.on).selectableGroup()) {
        items.forEachIndexed { index, item ->
            if (index > 0) Box(Modifier.fillMaxHeight().width(2.dp).background(c.on))
            val on = item == selected
            Text(
                (if (on) "✓ " else "") + label(item),
                style = PoolTheme.type.buttonSmall,
                color = if (on) c.bg else c.on,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).fillMaxHeight().background(if (on) c.on else Color.Transparent)
                    .selectable(on, role = Role.RadioButton) { onSelect(item) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

/** The printer type badge: a half-filled square for b/w, process colours for colour. */
@Composable
fun TypeBadge(type: PrinterType) {
    val c = PoolTheme.colors
    val ink = c.on
    Row(
        Modifier.border(1.5.dp, ink).padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (type == PrinterType.BW) {
            Box(Modifier.size(9.dp).border(1.dp, ink).drawBehind { drawRect(ink, size = Size(size.width / 2, size.height)) })
        } else {
            Row(Modifier.size(9.dp)) {
                for (colour in listOf(Color(0xFF00A0DC), Color(0xFFE4007C), Color(0xFFFFD400))) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(colour))
                }
            }
        }
        Text(printerTypeBadge(type).uppercase(), style = PoolTheme.type.badge, color = ink)
    }
}

@Composable
fun printerTypeBadge(type: PrinterType): String = stringResource(
    when (type) {
        PrinterType.BW -> Res.string.print_badge_bw
        PrinterType.COLOR -> Res.string.print_badge_colour
        PrinterType.UNKNOWN -> Res.string.print_badge_unknown
    },
)

/** An outlined label such as YOU. */
@Composable
fun OutlineTag(text: String, color: Color) {
    Text(
        text.uppercase(),
        style = PoolTheme.type.badge,
        color = color,
        modifier = Modifier.border(1.5.dp, color).padding(horizontal = 5.dp),
    )
}

/**
 * A catalogue error: a 3dp red rule, the warning icon and headline, the next step, inline actions, and the raw
 * stderr behind "Details".
 */
@Composable
fun ErrorBlock(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    details: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = PoolTheme.colors
    var showDetails by remember(details) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(c.error))
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(PoolIcons.Alert, null, Modifier.size(22.dp), tint = c.error)
                Text(title, style = PoolTheme.type.alert, color = c.error)
            }
            body?.let { Text(it, style = PoolTheme.type.bodySmall, modifier = Modifier.padding(start = 32.dp)) }
            Row(
                Modifier.fillMaxWidth().padding(start = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions()
                if (details != null) {
                    Spacer(Modifier.weight(1f))
                    TextAction(
                        stringResource(Res.string.action_details) + if (showDetails) " ▴" else " ▾",
                        onClick = { showDetails = !showDetails },
                        color = c.onVariant,
                        style = PoolTheme.type.buttonSmall,
                    )
                }
            }
            if (showDetails && details != null) {
                SelectionContainer(Modifier.padding(start = 32.dp)) {
                    Text(
                        details,
                        style = PoolTheme.type.monoSmall,
                        color = c.onVariant,
                        modifier = Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/**
 * A confirmation over a scrim: a top rule (red when [danger]), the title, then [content] (body and buttons).
 * Tapping the scrim dismisses it.
 */
@Composable
fun ConfirmPanel(title: String, onDismiss: () -> Unit, danger: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val c = PoolTheme.colors
    Box(
        Modifier.fillMaxSize().background(c.scrim).clickable(interactionSource = null, indication = null, onClick = onDismiss)
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth().background(c.bg)
                .clickable(interactionSource = null, indication = null, onClick = {}),
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).background(if (danger) c.error else c.rule))
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(title, style = PoolTheme.type.title)
                content()
            }
        }
    }
}

/** The two stacked buttons of a [ConfirmPanel]. */
@Composable
fun ConfirmButtons(confirm: String, onConfirm: () -> Unit, dismiss: String, onDismiss: () -> Unit, danger: Boolean = true) {
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BlockButton(confirm, onConfirm, kind = if (danger) ButtonKind.Danger else ButtonKind.Primary, height = 52.dp, textStyle = PoolTheme.type.button.copy(fontSize = 16.sp))
        BlockButton(dismiss, onDismiss, kind = ButtonKind.Outline, height = 52.dp, textStyle = PoolTheme.type.buttonSmall.copy(fontSize = 16.sp))
    }
}

/** The inverse bar at the bottom of a screen (a snackbar that stays until the next action). */
@Composable
fun InverseBar(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    val c = PoolTheme.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).background(c.inverse).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, style = PoolTheme.type.bodySmall, color = c.onInverse, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
        action?.let { TextAction(it, onAction, color = c.inversePrimary) }
    }
}

/** A flat bar: determinate when [progress] is given, otherwise a segment sliding across. */
@Composable
fun BarProgress(progress: Float?, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    val c = PoolTheme.colors
    Box(modifier.fillMaxWidth().height(height).background(c.surfaceHigh).clipToBounds()) {
        if (progress != null) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(c.primary))
        } else {
            val transition = rememberInfiniteTransition(label = "bar")
            val position by transition.animateFloat(-0.4f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "bar")
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(0.4f).graphicsLayer { translationX = position * size.width / 0.4f }
                    .background(c.primary),
            )
        }
    }
}

/** A numbered step marker: filled for the current step, outlined for the rest. */
@Composable
fun StepNumber(number: Int, filled: Boolean) {
    val c = PoolTheme.colors
    Box(
        Modifier.size(28.dp).then(if (filled) Modifier.background(c.on) else Modifier.border(2.dp, c.on)),
        contentAlignment = Alignment.Center,
    ) {
        Text(number.toString(), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = if (filled) c.bg else c.on)
    }
}

/** The app mark: a lime square with a printer outline. */
@Composable
fun AppMark() {
    val c = PoolTheme.colors
    val ink = c.onLime
    Column(
        Modifier.size(72.dp).background(c.lime).padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Box(Modifier.fillMaxWidth().height(5.dp).background(ink))
        Box(
            Modifier.padding(horizontal = 6.dp).fillMaxWidth().height(22.dp).drawBehind {
                val w = 3.dp.toPx()
                drawRect(ink, Offset.Zero, Size(w, size.height))
                drawRect(ink, Offset(size.width - w, 0f), Size(w, size.height))
                drawRect(ink, Offset(0f, size.height - w), Size(size.width, w))
            },
        )
    }
}

/** The lime square with a check mark, for "You're set." and "Sent". */
@Composable
fun SuccessMark() {
    val c = PoolTheme.colors
    Box(Modifier.size(72.dp).background(c.lime), contentAlignment = Alignment.Center) {
        Icon(PoolIcons.Check, null, Modifier.size(40.dp), tint = c.onLime)
    }
}

/** A stylised first page of the document (it is not rendered from the PDF). */
@Composable
fun DocThumb(colour: Boolean = false) {
    val c = PoolTheme.colors
    val ink = Color(0xFF1A1C16)
    val line = Color(0xFFA9AB9F)
    Column(
        Modifier.size(52.dp, 72.dp).background(Color.White).border(1.dp, c.outlineVariant).padding(horizontal = 7.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.fillMaxWidth(0.7f).height(4.dp).background(ink))
        if (colour) Box(Modifier.fillMaxWidth().height(18.dp).background(Color(0xFF9BC4C0)))
        Box(Modifier.fillMaxWidth().height(2.dp).background(line))
        Box(Modifier.fillMaxWidth().height(2.dp).background(line))
        Box(Modifier.fillMaxWidth(0.8f).height(2.dp).background(line))
        if (!colour) Box(Modifier.fillMaxWidth().height(14.dp).background(Color(0xFFD9DBCF)))
    }
}

/** Selectable monospace text on a filled box (keys, commands). */
@Composable
fun MonoBox(text: String, modifier: Modifier = Modifier, style: TextStyle = PoolTheme.type.monoSmall) {
    SelectionContainer(modifier.fillMaxWidth()) {
        Text(text, style = style, modifier = Modifier.fillMaxWidth().background(PoolTheme.colors.surface).padding(12.dp))
    }
}

/** A square dropdown menu on the raised surface. */
@Composable
fun PoolMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = PoolTheme.colors.surfaceHigh,
        content = content,
    )
}

@Composable
fun PoolMenuItem(text: String, onClick: () -> Unit, color: Color = PoolTheme.colors.on, selected: Boolean = false) {
    DropdownMenuItem(
        text = { Text((if (selected) "✓ " else "") + text, style = if (selected) PoolTheme.type.rowTitle else PoolTheme.type.body, color = color) },
        onClick = onClick,
    )
}
