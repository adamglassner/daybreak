package com.adamglassner.larksong.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglassner.larksong.data.ClockStyle

// Windows 11-style controls for the desktop app.

/** A hover-tinted overlay color for the current light/dark mode. */
@Composable
fun hoverTint(): Color = if (LocalDb.current.p.dark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)

@Composable
fun FButton(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val p = LocalDb.current.p
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    if (LocalDb.current.style == ClockStyle.CLASSIC) {
        // Windows 95-style push button: gray, beveled, a black frame on the default button.
        Row(
            modifier
                .heightIn(min = 30.dp)
                .then(if (accent) Modifier.border(BorderStroke(1.dp, if (p.dark) Color.White else Color.Black)) else Modifier)
                .padding(if (accent) 1.dp else 0.dp)
                .background(p.surface)
                .bevel(p.dark)
                .hoverable(src)
                .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            val fg = if (enabled) p.text else p.muted
            if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
            DText(text, 14.sp, fg, if (accent) FontWeight.Bold else FontWeight.Normal, family = FontFamilyForUi(), maxLines = 1)
        }
        return
    }
    val shape = RoundedCornerShape(4.dp)
    val bg = when {
        !enabled -> p.surface2
        accent -> if (hovered) p.accent.copy(alpha = 0.9f) else p.accent
        else -> p.surface2
    }
    val fg = when {
        !enabled -> p.muted
        accent -> p.onAccent
        else -> p.text
    }
    Row(
        modifier
            .heightIn(min = 32.dp)
            .clip(shape)
            .background(bg)
            .then(if (!accent && hovered) Modifier.background(hoverTint()) else Modifier)
            .border(BorderStroke(1.dp, if (accent) Color.Transparent else p.border), shape)
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        DText(text, 14.sp, fg, FontWeight.Medium, family = FontFamilyForUi(), maxLines = 1)
    }
}

/** Body font for UI chrome: Segoe UI for Stock, otherwise the style's body face. */
@Composable
fun FontFamilyForUi() = LocalDb.current.f.body

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tooltip(text: String, content: @Composable () -> Unit) {
    val p = LocalDb.current.p
    TooltipArea(
        tooltip = {
            Box(
                Modifier.clip(RoundedCornerShape(4.dp)).background(p.surface)
                    .border(BorderStroke(1.dp, p.border), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
            ) { DText(text, 12.sp, p.text) }
        },
        delayMillis = 600,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 18.dp)),
    ) { content() }
}

@Composable
fun FIconButton(icon: ImageVector, tooltip: String, modifier: Modifier = Modifier, tint: Color? = null, size: Dp = 36.dp, onClick: () -> Unit) {
    val p = LocalDb.current.p
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    Tooltip(tooltip) {
        Box(
            modifier.size(size).clip(RoundedCornerShape(4.dp))
                .background(if (hovered) hoverTint() else Color.Transparent)
                .hoverable(src)
                .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick)
                .pointerHoverIcon(PointerIcon.Hand)
                .semantics { contentDescription = tooltip },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint ?: p.text, modifier = Modifier.size(18.dp))
        }
    }
}

/** Windows toggle switch (40×20). */
@Composable
fun FToggle(checked: Boolean, onChange: (Boolean) -> Unit, description: String, enabled: Boolean = true) {
    val p = LocalDb.current.p
    if (LocalDb.current.style == ClockStyle.CLASSIC) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            ClassicCheckbox(checked, { if (enabled) onChange(it) }, description)
        }
        return
    }
    val knob by animateDpAsState(if (checked) 23.dp else 4.dp, label = "toggle")
    val shape = RoundedCornerShape(10.dp)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DText(if (checked) "On" else "Off", 14.sp, if (enabled) p.text else p.muted)
        Box(
            Modifier.size(40.dp, 20.dp).clip(shape)
                .background(if (checked && enabled) p.accent else Color.Transparent)
                .border(BorderStroke(1.dp, if (checked && enabled) p.accent else p.muted), shape)
                .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
                .pointerHoverIcon(PointerIcon.Hand)
                .semantics { contentDescription = description; stateDescription = if (checked) "On" else "Off" },
        ) {
            Box(
                Modifier.offset(x = knob, y = 4.dp).size(12.dp).clip(RoundedCornerShape(6.dp))
                    .background(if (checked && enabled) p.onAccent else p.muted),
            )
        }
    }
}

/** A card: 1px border, small radius, optional hover + click. */
@Composable
fun FCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    color: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val db = LocalDb.current
    val p = db.p
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val shape = RoundedCornerShape(db.radius)
    var m = modifier.clip(shape).background(color ?: p.surface)
    if (onClick != null && hovered) m = m.background(hoverTint())
    m = if (db.style == ClockStyle.CLASSIC) m.bevel(p.dark)
    else m.border(BorderStroke(1.dp, if (db.style == ClockStyle.RADIO) p.panelBorder else p.border), shape)
    if (onClick != null) {
        m = m.hoverable(src).clickable(interactionSource = src, indication = null, onClick = onClick).pointerHoverIcon(PointerIcon.Hand)
    }
    Column(m.padding(padding), content = content)
}

/** A Windows Settings-style row: icon, title, description, control on the right. */
@Composable
fun SettingsCard(
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val p = LocalDb.current.p
    FCard(Modifier.fillMaxWidth(), onClick = onClick, padding = 0.dp) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, tint = p.text, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                DText(title, 14.sp, p.text)
                if (description != null) DText(description, 12.sp, p.muted)
            }
            Spacer(Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = trailing)
        }
        if (below != null) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
            Column(Modifier.fillMaxWidth().padding(16.dp), content = below)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    DText(text, 14.sp, LocalDb.current.p.text, FontWeight.SemiBold, modifier = modifier.padding(top = 20.dp, bottom = 8.dp))
}

/** Page title with a command area on the right. */
@Composable
fun PageHeader(title: String, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val db = LocalDb.current
    if (db.style == ClockStyle.CLASSIC) {
        Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp).background(db.p.surface).bevel(db.p.dark).padding(3.dp)) {
            ClassicTitleBar("Larksong - $title")
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { DText(subtitle ?: title, 14.sp, db.p.text) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
        return
    }
    Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            DText(
                if (db.f.caps) title.uppercase() else title, 28.sp, db.p.text, FontWeight.SemiBold,
                family = db.f.display, italic = db.style == ClockStyle.RADIO,
            )
            if (subtitle != null) DText(subtitle, 14.sp, db.p.muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** A scrolling page with a Windows-style scrollbar. */
@Composable
fun ScrollPage(state: ScrollState = rememberScrollState(), maxWidth: Dp = 1200.dp, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(state).padding(start = 36.dp, end = 36.dp, bottom = 36.dp)) {
            Column(Modifier.widthIn(max = maxWidth).fillMaxWidth(), content = content)
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp, horizontal = 2.dp))
    }
}

/** Windows ContentDialog: a centered card over a dimmed window, with a footer of buttons. */
@Composable
fun ContentDialog(
    title: String,
    onDismiss: () -> Unit,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String = "Cancel",
    width: Dp = 520.dp,
    footerStart: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val db = LocalDb.current
    val p = db.p
    val classic = db.style == ClockStyle.CLASSIC
    val dShape = RoundedCornerShape(if (classic) 0.dp else 8.dp)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (classic) 0.25f else 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(width).padding(24.dp).clip(dShape).background(p.surface)
                .then(if (classic) Modifier.bevel(p.dark).padding(3.dp) else Modifier.border(BorderStroke(1.dp, p.border), dShape))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            if (classic) ClassicTitleBar(title)
            val scroll = rememberScrollState()
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(scroll).padding(if (classic) 16.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (!classic) DText(title, 20.sp, p.text, FontWeight.SemiBold, family = db.f.display)
                content()
            }
            Row(
                Modifier.fillMaxWidth().background(if (classic) p.surface else p.bg).padding(if (classic) 16.dp else 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                footerStart()
                Spacer(Modifier.weight(1f))
                FButton(primary, Modifier.widthIn(min = 110.dp), accent = true, onClick = onPrimary)
                FButton(secondary, Modifier.widthIn(min = 110.dp), onClick = onDismiss)
            }
        }
    }
}

/** Windows ComboBox: shows the current choice, opens a menu of options. */
@Composable
fun ComboBox(value: String, options: List<String>, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val p = LocalDb.current.p
    val classic = LocalDb.current.style == ClockStyle.CLASSIC
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        val shape = RoundedCornerShape(if (classic) 0.dp else 4.dp)
        Row(
            Modifier.widthIn(min = 160.dp).heightIn(min = 32.dp).clip(shape).background(p.surface2)
                .then(if (classic) Modifier.bevel(p.dark, sunken = true) else Modifier.border(BorderStroke(1.dp, p.border), shape))
                .clickable(role = Role.Button) { open = true }
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DText(value, 14.sp, p.text, maxLines = 1)
            Spacer(Modifier.width(16.dp))
            Icon(DbIcons.Down, null, tint = p.muted, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = p.surface) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(text = { DText(o, 14.sp, if (o == value) p.link else p.text) }, onClick = { open = false; onSelect(i) })
            }
        }
    }
}

/** Segmented choice (like Windows' radio-style option groups). */
@Composable
fun Segments(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val p = LocalDb.current.p
    val classic = LocalDb.current.style == ClockStyle.CLASSIC
    val shape = RoundedCornerShape(if (classic) 0.dp else 4.dp)
    Row(
        if (classic) modifier.background(p.surface).padding(1.dp)
        else modifier.clip(shape).border(BorderStroke(1.dp, p.border), shape).background(p.surface2),
    ) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 32.dp)
                    .background(if (on) p.accent else Color.Transparent)
                    .then(if (classic) Modifier.bevel(p.dark, sunken = on) else Modifier)
                    .clickable(role = Role.RadioButton) { onSelect(i) }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                DText(o, 13.sp, if (on) p.onAccent else p.text, if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, align = TextAlign.Center)
            }
        }
    }
}

/** A number with ▲▼ arrows that also responds to the mouse wheel and arrow keys. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NumberSpinner(value: Int, range: IntRange, label: String, modifier: Modifier = Modifier, width: Dp = 92.dp, format: (Int) -> String = { "%02d".format(it) }, onChange: (Int) -> Unit) {
    val db = LocalDb.current
    val p = db.p
    fun wrap(v: Int): Int {
        val size = range.last - range.first + 1
        return ((v - range.first) % size + size) % size + range.first
    }
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier.width(width).clip(shape).background(p.surface2).border(BorderStroke(1.dp, p.border), shape)
            .onPointerEvent(PointerEventType.Scroll) { e ->
                val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (dy < 0) onChange(wrap(value + 1)) else if (dy > 0) onChange(wrap(value - 1))
            }
            .semantics { contentDescription = "$label ${format(value)}" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FIconButton(DbIcons.Up, "Increase $label", size = 30.dp) { onChange(wrap(value + 1)) }
        DText(format(value), 40.sp, if (db.style == ClockStyle.RADIO) p.led else p.text, FontWeight.SemiBold, family = db.f.display)
        FIconButton(DbIcons.Down, "Decrease $label", size = 30.dp) { onChange(wrap(value - 1)) }
    }
}

/** Hour and minute spinners, plus AM/PM when using 12-hour time. */
@Composable
fun TimeSpinner(hour: Int, minute: Int, use24: Boolean, onChange: (Int, Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        if (use24) {
            NumberSpinner(hour, 0..23, "hour") { onChange(it, minute) }
        } else {
            val h12 = if (hour % 12 == 0) 12 else hour % 12
            NumberSpinner(h12, 1..12, "hour", format = { it.toString() }) { v ->
                val pm = hour >= 12
                onChange((v % 12) + if (pm) 12 else 0, minute)
            }
        }
        DText(":", 36.sp, LocalDb.current.p.muted, FontWeight.SemiBold)
        NumberSpinner(minute, 0..59, "minute") { onChange(hour, it) }
        if (!use24) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Segments(listOf("AM", "PM"), if (hour < 12) 0 else 1, Modifier.width(110.dp)) { i ->
                    val base = hour % 12
                    onChange(if (i == 0) base else base + 12, minute)
                }
            }
        }
    }
}

/** Round day toggles (S M T W T F S), like the Windows Clock app. */
@Composable
fun DayToggles(days: Set<Int>, onToggle: (Int) -> Unit) {
    val p = LocalDb.current.p
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DAY_LETTERS.forEach { (d, letter) ->
            val on = d in days
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (on) p.accent else p.surface2)
                    .border(BorderStroke(1.dp, if (on) p.accent else p.border), RoundedCornerShape(18.dp))
                    .clickable(role = Role.Checkbox) { onToggle(d) }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .semantics { contentDescription = DAY_NAMES.getValue(d); stateDescription = if (on) "Selected" else "Not selected" },
                contentAlignment = Alignment.Center,
            ) {
                DText(letter, 13.sp, if (on) p.onAccent else p.text, FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    val p = LocalDb.current.p
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.surface2)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0.01f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.accent))
    }
}
