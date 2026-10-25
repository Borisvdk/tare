package app.tare.ui.food

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.tare.data.db.Food
import app.tare.domain.FoodSource
import app.tare.ui.Fmt
import app.tare.ui.ai.rememberMediaInput
import app.tare.ui.components.MacroLine
import app.tare.ui.components.PrimaryButton
import app.tare.ui.components.RoundIconButton
import app.tare.ui.components.SecondaryButton
import app.tare.ui.components.SectionHeader
import app.tare.ui.components.Segmented
import app.tare.ui.components.TareTextField
import app.tare.ui.log.AmountMode
import app.tare.ui.log.AmountSheet
import app.tare.ui.navigation.LocalNavigator
import app.tare.ui.navigation.Route
import app.tare.ui.navigation.tareViewModel
import app.tare.ui.root.LocalNotifier
import app.tare.ui.theme.Tare
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun FoodEditorScreen(route: Route.FoodEditor) {
    val viewModel = tareViewModel(key = "food-${route.foodId}-${route.barcode}") {
        FoodEditorViewModel(it, route.foodId, route.barcode, route.name)
    }
    val nav = LocalNavigator.current
    val notify = LocalNotifier.current
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf<Food?>(null) }
    var busy by remember { mutableStateOf(false) }
    val media = rememberMediaInput(onPhoto = viewModel::readLabel)
    LaunchedEffect(Unit) { if (route.readLabel) media.takePhoto() }

    val unit = if (viewModel.isLiquid) "ml" else "g"
    val forkingProduct = viewModel.original?.let { it.source != FoodSource.CUSTOM } == true

    Column(Modifier.fillMaxSize().background(Tare.colors.background).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", { nav.back() }, background = Color.Transparent)
            Spacer(Modifier.width(8.dp))
            Text(
                if (route.foodId == null) "New food" else "Edit food",
                style = Tare.type.headline.copy(fontSize = Tare.type.headline.fontSize * 0.8f),
                color = Tare.colors.text,
                modifier = Modifier.weight(1f),
            )
            if (viewModel.original?.source == FoodSource.CUSTOM) {
                RoundIconButton(Icons.Rounded.DeleteOutline, "Delete food", {
                    scope.launch {
                        viewModel.delete()
                        nav.back()
                    }
                }, tint = Tare.colors.over)
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecondaryButton(
                if (viewModel.readingLabel) "Reading label…" else "Fill in from a label photo",
                icon = Icons.Rounded.AutoAwesome,
                onClick = media.takePhoto,
                enabled = !viewModel.readingLabel,
                contentColor = Tare.colors.kcal,
                modifier = Modifier.fillMaxWidth(),
            )
            if (viewModel.readingLabel) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Tare.colors.kcal)
                }
            }
            viewModel.labelError?.let { Text(it, style = Tare.type.caption, color = Tare.colors.over) }
            if (forkingProduct) {
                Text(
                    "Saving creates your own copy of this product. Future scans of its barcode use your version.",
                    style = Tare.type.caption,
                    color = Tare.colors.textMuted,
                )
            }

            TareTextField(viewModel.name, { viewModel.name = it }, label = "Name", placeholder = "e.g. Skyr naturel", imeAction = ImeAction.Next)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TareTextField(viewModel.brand, { viewModel.brand = it }, label = "Brand", placeholder = "Optional", modifier = Modifier.weight(1f), imeAction = ImeAction.Next)
                TareTextField(
                    viewModel.barcode,
                    { viewModel.barcode = it.filter(Char::isDigit) },
                    label = "Barcode",
                    placeholder = "Optional",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                    imeAction = ImeAction.Next,
                )
            }

            SectionHeader("Nutrition")
            Segmented(
                options = listOf(false, true),
                selected = viewModel.isLiquid,
                label = { if (it) "Drink (ml)" else "Food (g)" },
                onSelect = { viewModel.isLiquid = it },
                modifier = Modifier.fillMaxWidth(),
            )
            Segmented(
                options = Basis.entries,
                selected = viewModel.basis,
                label = { if (it == Basis.PER_100) "Per 100 $unit" else "Per serving" },
                onSelect = { viewModel.basis = it },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("Calories", viewModel.kcal, { viewModel.kcal = it }, "kcal", Modifier.weight(1f))
                NumberField("Protein", viewModel.protein, { viewModel.protein = it }, "g", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("Carbs", viewModel.carbs, { viewModel.carbs = it }, "g", Modifier.weight(1f))
                NumberField("Fat", viewModel.fat, { viewModel.fat = it }, "g", Modifier.weight(1f))
            }
            NumberField("Fibre (optional)", viewModel.fiber, { viewModel.fiber = it }, "g", Modifier.fillMaxWidth())

            SectionHeader("Serving")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TareTextField(viewModel.servingLabel, { viewModel.servingLabel = it }, label = "Name", placeholder = "serving", modifier = Modifier.weight(1f), imeAction = ImeAction.Next)
                NumberField("Size", viewModel.servingGrams, { viewModel.servingGrams = it }, unit, Modifier.weight(1f))
            }
            if (viewModel.basis == Basis.PER_SERVING && viewModel.serving == null) {
                Text("Enter the serving size to convert per-serving values.", style = Tare.type.caption, color = Tare.colors.over)
            }

            val per100 = viewModel.per100
            if (per100.kcal > 0) {
                Column {
                    Text("Per 100 $unit: ${Fmt.kcal(per100.kcal)} kcal", style = Tare.type.caption, color = Tare.colors.textMuted)
                    MacroLine(per100)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        PrimaryButton(
            text = if (route.logDay != null) "Save and log" else "Save",
            onClick = {
                if (busy) return@PrimaryButton
                busy = true
                scope.launch {
                    val food = viewModel.save()
                    if (route.logDay != null) {
                        saved = food
                        busy = false
                    } else {
                        nav.back()
                        notify.show("Saved ${food.name}")
                    }
                }
            },
            enabled = viewModel.canSave && !busy,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
        )
    }

    saved?.let { food ->
        val day = LocalDate.ofEpochDay(route.logDay ?: return@let)
        AmountSheet(
            food = food,
            mode = AmountMode.LOG,
            onConfirm = { result ->
                if (busy) return@AmountSheet
                busy = true
                scope.launch {
                    val entry = viewModel.log(food, day, result.grams, result.portion, result.time)
                    nav.back()
                    notify.show("Logged ${entry.name} · ${Fmt.kcal(entry.nutrients.kcal)} kcal")
                }
            },
            onDismiss = {
                saved = null
                nav.back()
            },
        )
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, suffix: String, modifier: Modifier = Modifier) {
    TareTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter { it.isDigit() || it == '.' || it == ',' }) },
        label = label,
        placeholder = "0",
        keyboardType = KeyboardType.Decimal,
        imeAction = ImeAction.Next,
        suffix = suffix,
        modifier = modifier,
    )
}
