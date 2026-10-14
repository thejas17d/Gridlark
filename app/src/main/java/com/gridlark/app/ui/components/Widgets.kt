package com.gridlark.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gridlark.app.ui.theme.Grid

/** 1dp hairline rule. */
@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Grid.colors.rule) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** Small spaced caps label above a section. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = Grid.colors.inkMuted,
    )
}

/** Primary ink button (or outlined when [primary] is false). Presses scale down slightly. */
@Composable
fun PaperButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val c = Grid.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(14.dp)
    val bg = when {
        !enabled -> c.sunken
        primary -> c.ink
        else -> Color.Transparent
    }
    val fg = when {
        !enabled -> c.inkFaint
        primary -> c.paper
        else -> c.ink
    }
    Row(
        modifier
            .heightIn(min = 52.dp)
            .scale(if (pressed && enabled) 0.97f else 1f)
            .background(bg, shape)
            .then(if (!primary) Modifier.border(BorderStroke(1.5.dp, if (enabled) c.ink else c.rule), shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** Icon with a caption underneath; used for the puzzle toolbar. */
@Composable
fun ToolButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    badge: String? = null,
) {
    val c = Grid.colors
    val tint = when {
        !enabled -> c.inkFaint
        active -> c.accentText
        else -> c.ink
    }
    Column(
        modifier
            .sizeIn(minWidth = 64.dp, minHeight = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            if (badge != null) {
                Text(
                    badge,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(start = 18.dp)
                        .background(c.accent, RoundedCornerShape(50))
                        .padding(horizontal = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.onAccent,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/** Plain 48dp icon button with an accessibility label. */
@Composable
fun BareIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Grid.colors.ink) {
    Box(
        modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { this.role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/** A settings row with a title, optional explanation and a switch. */
@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Grid.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.ink)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.paper, checkedTrackColor = c.ink, checkedBorderColor = c.ink,
                uncheckedThumbColor = c.inkMuted, uncheckedTrackColor = c.sunken, uncheckedBorderColor = c.rule,
            ),
        )
    }
}

/** A tappable settings row with a trailing value. */
@Composable
fun ValueRow(title: String, value: String?, subtitle: String? = null, onClick: () -> Unit) {
    val c = Grid.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.ink)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
        }
        if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = c.inkMuted)
    }
}

/** Haptics that respect the in-app toggle. Uses the semantic feedback constants only. */
class Haptics(private val enabled: Boolean, private val perform: (HapticFeedbackType) -> Unit) {
    fun tick() { if (enabled) perform(HapticFeedbackType.SegmentTick) }
    fun drag() { if (enabled) perform(HapticFeedbackType.SegmentFrequentTick) }
    fun on() { if (enabled) perform(HapticFeedbackType.ToggleOn) }
    fun off() { if (enabled) perform(HapticFeedbackType.ToggleOff) }
    fun key() { if (enabled) perform(HapticFeedbackType.KeyboardTap) }
    fun reject() { if (enabled) perform(HapticFeedbackType.Reject) }
    fun success() { if (enabled) perform(HapticFeedbackType.Confirm) }
}

@Composable
fun rememberHaptics(enabled: Boolean): Haptics {
    val h = LocalHapticFeedback.current
    return remember(enabled, h) { Haptics(enabled) { h.performHapticFeedback(it) } }
}
