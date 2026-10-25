package app.tare.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import app.tare.data.db.Food
import app.tare.domain.FoodSource
import app.tare.ui.Fmt
import app.tare.ui.theme.Radii
import app.tare.ui.theme.Tare
import kotlin.math.roundToInt

/** Generic list row: title, subtitle, trailing kcal figure. */
@Composable
fun ListRow(
    title: String,
    subtitle: String?,
    kcal: Double?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    titleColor: Color = Tare.colors.text,
    leading: (@Composable () -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    val colors = Tare.colors
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) colors.surfaceRaised else Color.Transparent, label = "rowBg")
    Row(
        modifier
            .fillMaxWidth()
            .clip(Radii.row)
            .background(bg)
            .combinedClickableCompat(interaction, onClick, onLongClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(Icons.Rounded.CheckCircle, null, tint = colors.kcal, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
        } else if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Tare.type.bodyStrong, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, style = Tare.type.caption, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (kcal != null) {
            Spacer(Modifier.width(12.dp))
            KcalFigure(kcal)
        }
        if (trailingAction != null) {
            Spacer(Modifier.width(10.dp))
            trailingAction()
        }
    }
}

@Composable
fun KcalFigure(kcal: Double, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Tare.colors.text)) { append(Fmt.kcal(kcal)) }
            withStyle(SpanStyle(color = Tare.colors.textFaint, fontSize = Tare.type.caption.fontSize)) { append(" kcal") }
        },
        style = Tare.type.figure,
        modifier = modifier,
        maxLines = 1,
    )
}

/** Subtitle for a food in search results: brand or source, plus the default amount. */
fun foodSubtitle(food: Food, grams: Double, portionLabel: String?): String {
    val origin = when (food.source) {
        FoodSource.RECIPE -> "Recipe"
        FoodSource.AI -> "Estimated"
        FoodSource.CUSTOM -> food.brand ?: "My food"
        FoodSource.GENERIC -> null
        else -> food.brand
    }
    val amount = if (portionLabel != null) {
        val portion = food.portions.firstOrNull { it.label == portionLabel }
        if (portion != null) "${Fmt.amount(grams / portion.grams)} × $portionLabel" else "${grams.roundToInt()} ${food.unit}"
    } else {
        "${Fmt.amount(grams)} ${food.unit}"
    }
    return listOfNotNull(origin, amount).joinToString(" · ")
}

/** The amount a food is offered at by default: what you had last time, else one portion, else 100 g. */
fun defaultAmount(food: Food): Pair<Double, String?> {
    food.lastGrams?.let { grams ->
        return grams to food.lastPortionLabel?.takeIf { label -> food.portions.any { it.label == label } }
    }
    val preferred = food.portions.firstOrNull { it.label == "serving" } ?: food.portions.firstOrNull()
    return if (preferred != null && food.source != FoodSource.RECIPE) {
        preferred.grams to preferred.label
    } else {
        100.0 to null
    }
}

@Composable
fun FoodRow(
    food: Food,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onQuickAdd: (() -> Unit)? = null,
    showImage: Boolean = false,
) {
    val (grams, portion) = defaultAmount(food)
    ListRow(
        title = food.name,
        subtitle = foodSubtitle(food, grams, portion),
        kcal = food.per100.forGrams(grams).kcal,
        modifier = modifier,
        leading = when {
            showImage -> {
                { FoodImage(food.imageUrl, size = 40.dp) }
            }
            food.favorite -> {
                { Icon(Icons.Rounded.Star, null, tint = Tare.colors.fat, modifier = Modifier.size(16.dp)) }
            }
            else -> null
        },
        trailingAction = onQuickAdd?.let { add ->
            {
                RoundIconButton(
                    Icons.Rounded.Add,
                    contentDescription = "Log ${food.name}",
                    onClick = add,
                    size = 36.dp,
                    iconSize = 20.dp,
                )
            }
        },
        onClick = onClick,
    )
}

/** Product photo thumbnail; a quiet placeholder when there's no image. */
@Composable
fun FoodImage(url: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(Radii.chip).background(Tare.colors.surfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        // Shown when there's no photo or it can't load; a loaded photo covers it.
        Icon(Icons.Rounded.Inventory2, null, tint = Tare.colors.textFaint.copy(alpha = 0.6f), modifier = Modifier.size(size * 0.42f))
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
fun Spaced(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
}
