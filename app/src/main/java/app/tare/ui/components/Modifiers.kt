package app.tare.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier

/** Click + optional long-click without the default ripple (press feedback comes from [pressScale]). */
fun Modifier.combinedClickableCompat(
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier = combinedClickable(
    interactionSource = interaction,
    indication = null,
    onClick = onClick,
    onLongClick = onLongClick,
)
