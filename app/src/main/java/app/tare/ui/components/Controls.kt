package app.tare.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tare.ui.theme.Radii
import app.tare.ui.theme.Tare

/** Scales down slightly while pressed — the app's universal press feedback. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.96f): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, label = "press")
    return this.scale(scale)
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Tare.colors.kcal,
    contentColor: Color = Tare.colors.onAccent,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val bg by animateColorAsState(if (enabled) color else Tare.colors.surfaceRaised, label = "btn")
    Row(
        modifier
            .pressScale(interaction)
            .heightIn(min = 54.dp)
            .clip(Radii.pill)
            .background(bg)
            .clickable(interaction, indication = null, enabled = enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val fg = if (enabled) contentColor else Tare.colors.textFaint
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Tare.type.title, color = fg, maxLines = 1)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentColor: Color = Tare.colors.text,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(interaction)
            .heightIn(min = 48.dp)
            .clip(Radii.pill)
            .background(Tare.colors.surfaceRaised)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val fg = if (enabled) contentColor else Tare.colors.textFaint
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Tare.type.bodyStrong, color = fg, maxLines = 1)
    }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Tare.colors.kcal) {
    Text(
        text,
        style = Tare.type.bodyStrong,
        color = color,
        modifier = modifier
            .clip(Radii.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    background: Color = Tare.colors.surfaceRaised,
    tint: Color = Tare.colors.text,
    onLongClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(interaction, 0.9f)
            .size(size)
            .clip(CircleShape)
            .background(background)
            .combinedClickableCompat(interaction, onClick, onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Chip used for portion units, ranges and filters. */
@Composable
fun SelectChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Tare.colors.text,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = Tare.colors
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) accent else colors.surfaceRaised, label = "chip")
    val fg by animateColorAsState(if (selected) colors.background else colors.textMuted, label = "chipText")
    Text(
        text,
        style = Tare.type.label,
        color = fg,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .pressScale(interaction, 0.95f)
            .clip(Radii.pill)
            .background(bg)
            .combinedClickableCompat(interaction, onClick, onLongClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

/** Segmented control: a pill with sliding selection. */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Tare.colors
    Row(
        modifier
            .clip(Radii.pill)
            .background(colors.surfaceRaised)
            .padding(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val bg by animateColorAsState(if (isSelected) colors.surface else Color.Transparent, label = "seg")
            Box(
                Modifier
                    .weight(1f)
                    .clip(Radii.pill)
                    .background(bg)
                    .then(if (isSelected && !colors.isDark) Modifier.border(BorderStroke(1.dp, colors.outline), Radii.pill) else Modifier)
                    .clickable { onSelect(option) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = Tare.type.label,
                    color = if (isSelected) colors.text else colors.textMuted,
                )
            }
        }
    }
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .then(if (onClick != null) Modifier.pressScale(interaction, 0.98f) else Modifier)
            .clip(Radii.card)
            .background(Tare.colors.surface)
            .then(if (!Tare.colors.isDark) Modifier.border(1.dp, Tare.colors.outline, Radii.card) else Modifier)
            .then(if (onClick != null) Modifier.clickable(interaction, indication = null, onClick = onClick) else Modifier)
            .padding(padding),
    ) {
        content()
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title.uppercase(), style = Tare.type.overline, color = Tare.colors.textFaint, modifier = Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

/** Rounded single-line text input used across editors. */
@Composable
fun TareTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onIme: () -> Unit = {},
    singleLine: Boolean = true,
    minLines: Int = 1,
    textStyle: TextStyle = Tare.type.body.copy(fontSize = Tare.type.bodyStrong.fontSize),
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    suffix: String? = null,
) {
    val colors = Tare.colors
    Column(modifier) {
        if (label != null) {
            Text(label, style = Tare.type.label, color = colors.textMuted, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = textStyle.copy(color = colors.text),
            cursorBrush = SolidColor(colors.kcal),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onIme() }),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(Radii.row)
                        .background(colors.surfaceRaised)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                ) {
                    if (leading != null) {
                        leading()
                        Spacer(Modifier.width(10.dp))
                    }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) Text(placeholder, style = textStyle, color = colors.textFaint)
                        inner()
                    }
                    if (suffix != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(suffix, style = Tare.type.body, color = colors.textMuted)
                    }
                    if (trailing != null) {
                        Spacer(Modifier.width(8.dp))
                        trailing()
                    }
                }
            },
        )
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            title,
            style = Tare.type.headline.copy(fontSize = Tare.type.headline.fontSize * 0.75f),
            color = Tare.colors.textMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(body, style = Tare.type.body, color = Tare.colors.textFaint, textAlign = TextAlign.Center)
    }
}
