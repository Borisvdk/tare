package app.tare.ui.log

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import app.tare.data.db.Food
import app.tare.data.db.MealWithItems
import app.tare.ui.Fmt
import app.tare.ui.components.FoodRow
import app.tare.ui.components.ListRow
import app.tare.ui.components.SectionHeader
import app.tare.ui.theme.Tare

/**
 * Search results as lazy-list items, shared by the log hub and the ingredient picker.
 * [onAskAi]/[onCreate] add the "can't find it" escape hatches at the end.
 */
fun LazyListScope.searchResults(
    results: SearchResults,
    onFood: (Food) -> Unit,
    onQuickAdd: ((Food) -> Unit)?,
    onMeal: ((MealWithItems) -> Unit)? = null,
    onAskAi: ((String) -> Unit)? = null,
    onCreate: ((String) -> Unit)? = null,
) {
    if (results.meals.isNotEmpty() && onMeal != null) {
        item(key = "h-meals") { SectionHeader("Meals") }
        items(results.meals, key = { "m-" + it.meal.id }) { meal ->
            ListRow(
                title = meal.meal.name,
                subtitle = "Saved meal · ${Fmt.items(meal.items.size)}",
                kcal = meal.nutrients.kcal,
                onClick = { onMeal(meal) },
                modifier = Modifier.animateItem(),
            )
        }
    }
    if (results.local.isNotEmpty()) {
        item(key = "h-local") { SectionHeader("Foods") }
        items(results.local, key = { "l-" + it.id }) { food ->
            FoodRow(food, onClick = { onFood(food) }, onQuickAdd = onQuickAdd?.let { { it(food) } }, modifier = Modifier.animateItem())
        }
    }
    when (val remote = results.remote) {
        RemoteState.Idle -> Unit
        RemoteState.Loading -> item(key = "remote-loading") {
            SectionHeader("Products") {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Tare.colors.textFaint)
            }
        }
        RemoteState.Offline -> item(key = "remote-offline") {
            SectionHeader("Products")
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CloudOff, null, tint = Tare.colors.textFaint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Can't reach Open Food Facts right now", style = Tare.type.caption, color = Tare.colors.textMuted)
            }
        }
        is RemoteState.Loaded -> if (remote.foods.isNotEmpty()) {
            item(key = "h-remote") { SectionHeader("Products") }
            items(remote.foods, key = { "r-" + it.id }) { food ->
                FoodRow(food, onClick = { onFood(food) }, modifier = Modifier.animateItem(), showImage = true)
            }
        }
    }
    if (results.isSearching && (onAskAi != null || onCreate != null)) {
        item(key = "fallbacks") {
            SectionHeader(if (results.local.isEmpty() && (results.remote as? RemoteState.Loaded)?.foods.isNullOrEmpty()) "No match" else "Not it?")
        }
        if (onAskAi != null) {
            item(key = "ask-ai") {
                FallbackRow(Icons.Rounded.AutoAwesome, "Estimate “${results.query}” with Claude", "Works for whole meals too", Tare.colors.kcal) {
                    onAskAi(results.query)
                }
            }
        }
        if (onCreate != null) {
            item(key = "create") {
                FallbackRow(Icons.Rounded.AddCircleOutline, "Create “${results.query}”", "Enter nutrition from the label", Tare.colors.textMuted) {
                    onCreate(results.query)
                }
            }
        }
    }
}

@Composable
private fun FallbackRow(icon: ImageVector, title: String, subtitle: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    ListRow(
        title = title,
        subtitle = subtitle,
        kcal = null,
        leading = {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
        },
        onClick = onClick,
    )
}
