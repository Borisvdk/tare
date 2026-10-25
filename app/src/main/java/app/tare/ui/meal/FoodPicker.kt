package app.tare.ui.meal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.tare.data.db.Food
import app.tare.data.meal.DraftItem
import app.tare.ui.components.FoodRow
import app.tare.ui.components.RoundIconButton
import app.tare.ui.components.SectionHeader
import app.tare.ui.components.TareTextField
import app.tare.ui.log.AmountMode
import app.tare.ui.log.AmountSheet
import app.tare.ui.log.FoodSearch
import app.tare.ui.log.searchResults
import app.tare.ui.theme.Tare

/** Full-screen search to add an ingredient to a meal or recipe. */
@Composable
fun FoodPickerDialog(
    search: FoodSearch,
    recent: List<Food>,
    onPicked: (DraftItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val results by search.results.collectAsState()
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Food?>(null) }
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(query) { search.query.value = query }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(Modifier.fillMaxSize().background(Tare.colors.background).statusBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(Icons.Rounded.Close, "Close", onDismiss, background = Color.Transparent)
                Spacer(Modifier.width(4.dp))
                TareTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Add an ingredient",
                    imeAction = ImeAction.Search,
                    onIme = { focus.clearFocus() },
                    leading = { Icon(Icons.Rounded.Search, null, tint = Tare.colors.textFaint, modifier = Modifier.size(20.dp)) },
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                )
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
                if (!results.isSearching) {
                    if (recent.isNotEmpty()) item(key = "h") { SectionHeader("Recent") }
                    items(recent, key = { it.id }) { food ->
                        FoodRow(food, onClick = {
                            focus.clearFocus()
                            picked = food
                        })
                    }
                } else {
                    searchResults(results, onFood = {
                        focus.clearFocus()
                        picked = it
                    }, onQuickAdd = null)
                }
            }
        }

        picked?.let { food ->
            AmountSheet(
                food = food,
                mode = AmountMode.PICK,
                onConfirm = { result ->
                    picked = null
                    onPicked(DraftItem(food, result.grams, result.portion))
                },
                onDismiss = { picked = null },
            )
        }
    }
}
