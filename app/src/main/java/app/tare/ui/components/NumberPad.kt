package app.tare.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tare.ui.Fmt
import app.tare.ui.theme.Radii
import app.tare.ui.theme.Tare

sealed interface PadKey {
    data class Digit(val value: Int) : PadKey
    data object Dot : PadKey
    data object Back : PadKey
}

/**
 * Text state for a number typed on [NumberPad]. The first key press after a value is set
 * replaces it (like a selected text field), so changing "100" to "150" is three taps.
 */
@Stable
class AmountInputState(initial: Double?, private val maxDecimals: Int = 1) {
    var text by mutableStateOf(initial?.let(Fmt::amount).orEmpty())
        private set
    private var fresh by mutableStateOf(true)

    val value: Double? get() = text.toDoubleOrNull()
    val isFresh: Boolean get() = fresh

    fun set(value: Double?) {
        text = value?.let(Fmt::amount).orEmpty()
        fresh = true
    }

    fun press(key: PadKey) {
        val base = if (fresh && key !is PadKey.Back) "" else text
        fresh = false
        text = when (key) {
            is PadKey.Digit -> {
                val decimals = base.substringAfter('.', "")
                when {
                    base.contains('.') && decimals.length >= maxDecimals -> base
                    base.replace(".", "").length >= 6 -> base
                    base == "0" -> key.value.toString()
                    else -> base + key.value
                }
            }
            PadKey.Dot -> when {
                maxDecimals == 0 || base.contains('.') -> base
                base.isEmpty() -> "0."
                else -> "$base."
            }
            PadKey.Back -> base.dropLast(1)
        }
    }
}

@Composable
fun rememberAmountInput(initial: Double?, key: Any? = null, maxDecimals: Int = 1): AmountInputState =
    remember(key) { AmountInputState(initial, maxDecimals) }

@Composable
fun NumberPad(
    onKey: (PadKey) -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 56.dp,
    allowDecimal: Boolean = true,
) {
    val rows = listOf(
        listOf(PadKey.Digit(1), PadKey.Digit(2), PadKey.Digit(3)),
        listOf(PadKey.Digit(4), PadKey.Digit(5), PadKey.Digit(6)),
        listOf(PadKey.Digit(7), PadKey.Digit(8), PadKey.Digit(9)),
        listOf(if (allowDecimal) PadKey.Dot else null, PadKey.Digit(0), PadKey.Back),
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    Box(Modifier.weight(1f).height(keyHeight)) {
                        if (key != null) PadButton(key, onKey)
                    }
                }
            }
        }
    }
}

@Composable
private fun PadButton(key: PadKey, onKey: (PadKey) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier
            .testTag(
                when (key) {
                    is PadKey.Digit -> "pad-${key.value}"
                    PadKey.Dot -> "pad-dot"
                    PadKey.Back -> "pad-back"
                },
            )
            .pressScale(interaction, 0.92f)
            .fillMaxWidth()
            .height(56.dp)
            .clip(Radii.row)
            .combinedClickableCompat(
                interaction,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                    onKey(key)
                },
                onLongClick = if (key is PadKey.Back) {
                    { repeat(8) { onKey(PadKey.Back) } }
                } else {
                    null
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (key) {
            is PadKey.Digit -> Text(key.value.toString(), style = Tare.type.number.copy(fontSize = 26.sp), color = Tare.colors.text)
            PadKey.Dot -> Text(".", style = Tare.type.number.copy(fontSize = 26.sp), color = Tare.colors.text)
            PadKey.Back -> Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete", tint = Tare.colors.textMuted)
        }
    }
}
