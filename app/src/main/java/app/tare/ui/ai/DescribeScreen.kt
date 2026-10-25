package app.tare.ui.ai

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.tare.ui.Fmt
import app.tare.ui.components.Card
import app.tare.ui.components.KcalFigure
import app.tare.ui.components.ListRow
import app.tare.ui.components.MacroLine
import app.tare.ui.components.PrimaryButton
import app.tare.ui.components.RoundIconButton
import app.tare.ui.components.SecondaryButton
import app.tare.ui.components.SectionHeader
import app.tare.ui.components.TareTextField
import app.tare.ui.log.AmountMode
import app.tare.ui.log.AmountSheet
import app.tare.ui.navigation.LocalNavigator
import app.tare.ui.navigation.Route
import app.tare.ui.navigation.tareViewModel
import app.tare.ui.root.LocalSnackbar
import app.tare.ui.theme.Radii
import app.tare.ui.theme.Tare
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun DescribeScreen(route: Route.Describe) {
    val day = LocalDate.ofEpochDay(route.day)
    val viewModel = tareViewModel(key = "describe-${route.day}") { DescribeViewModel(it, day) }
    val state by viewModel.state.collectAsState()
    val photo by viewModel.photo.collectAsState()
    val hasKey by viewModel.hasApiKey.collectAsState()
    val remaining by viewModel.remainingKcal.collectAsState()
    val nav = LocalNavigator.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    var text by rememberSaveable { mutableStateOf(route.prefill) }
    var editing by remember { mutableStateOf<EstimateRow?>(null) }
    val media = rememberMediaInput(
        onPhoto = { viewModel.photo.value = it },
        onSpeech = { spoken -> text = if (text.isBlank()) spoken else "$text, $spoken" },
    )
    LaunchedEffect(Unit) { if (route.openCamera) media.takePhoto() }

    Column(Modifier.fillMaxSize().background(Tare.colors.background).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", { nav.back() }, background = Color.Transparent)
            Spacer(Modifier.width(8.dp))
            Text("Describe", style = Tare.type.headline.copy(fontSize = Tare.type.headline.fontSize * 0.8f), color = Tare.colors.text)
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        ) {
            if (!hasKey) {
                item(key = "nokey") { NoKeyCard { nav.go(Route.Settings) } }
            }
            item(key = "input") {
                Column {
                    TareTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = "2 eggs, toast with peanut butter and a flat white",
                        singleLine = false,
                        minLines = 3,
                        imeAction = ImeAction.Default,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoundIconButton(Icons.Rounded.Mic, "Dictate", media.dictate)
                        RoundIconButton(Icons.Rounded.PhotoCamera, "Take photo", media.takePhoto)
                        RoundIconButton(Icons.Rounded.Image, "Choose photo", media.pickPhoto)
                        photo?.let { bytes -> PhotoThumb(bytes, onRemove = { viewModel.photo.value = null }) }
                        Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        "Estimate",
                        icon = Icons.Rounded.AutoAwesome,
                        onClick = {
                            focus.clearFocus()
                            viewModel.estimate(text)
                        },
                        enabled = hasKey && (text.isNotBlank() || photo != null) && state !is DescribeState.Working,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item(key = "result") {
                AnimatedContent(
                    targetState = state,
                    contentKey = { it::class },
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                    label = "describe",
                ) { s ->
                    when (s) {
                        DescribeState.Idle -> Text(
                            "Say it like you'd tell a friend. Amounts help (“200 g chicken”, “a big bowl”), brands too. A photo of your plate works on its own.",
                            style = Tare.type.caption,
                            color = Tare.colors.textFaint,
                            modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp),
                        )
                        DescribeState.Working -> Thinking()
                        DescribeState.NoApiKey -> NoKeyCard { nav.go(Route.Settings) }
                        is DescribeState.Error -> Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            Column {
                                Text("Couldn't estimate", style = Tare.type.title, color = Tare.colors.text)
                                Text(s.message, style = Tare.type.body, color = Tare.colors.textMuted)
                                Spacer(Modifier.height(12.dp))
                                SecondaryButton("Try again", onClick = { viewModel.estimate(text) })
                            }
                        }
                        is DescribeState.Ready -> Column(Modifier.padding(top = 8.dp)) {
                            SectionHeader(s.title)
                            if (s.rows.isEmpty()) {
                                Text("Nothing left to log.", style = Tare.type.body, color = Tare.colors.textMuted)
                            }
                            s.rows.forEach { row ->
                                ListRow(
                                    title = row.food.name,
                                    subtitle = "${Fmt.amount(row.grams)} ${row.food.unit}" + if (row.lowConfidence) " · rough guess, check amount" else "",
                                    kcal = row.nutrients.kcal,
                                    onClick = { editing = row },
                                    trailingAction = {
                                        RoundIconButton(Icons.Rounded.Close, "Remove", { viewModel.remove(row.key) }, size = 32.dp, iconSize = 16.dp)
                                    },
                                )
                            }
                            if (s.note.isNotBlank()) {
                                Text(s.note, style = Tare.type.caption, color = Tare.colors.textMuted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                            Card(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        KcalFigure(s.total.kcal)
                                        MacroLine(s.total)
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            PrimaryButton(
                                "Log all · ${Fmt.kcal(s.total.kcal)} kcal",
                                onClick = {
                                    scope.launch {
                                        val entries = viewModel.logAll()
                                        nav.back()
                                        snackbar.showSnackbar("Logged ${s.title} · ${Fmt.kcal(entries.sumOf { it.nutrients.kcal })} kcal")
                                    }
                                },
                                enabled = s.rows.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { row ->
        AmountSheet(
            food = row.food,
            mode = AmountMode.PICK,
            initialGrams = row.grams,
            remainingKcal = remaining,
            onConfirm = { result ->
                viewModel.setGrams(row.key, result.grams)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun PhotoThumb(bytes: ByteArray, onRemove: () -> Unit) {
    val bitmap = remember(bytes) {
        val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
    }
    Box(Modifier.size(44.dp).clip(Radii.chip).clickable(onClick = onRemove)) {
        if (bitmap != null) {
            Image(bitmap, "Attached photo, tap to remove", contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(3.dp).size(16.dp).clip(Radii.pill).background(Tare.colors.text),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, null, tint = Tare.colors.background, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun Thinking() {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "alpha")
    Column(Modifier.padding(top = 24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.AutoAwesome, null, tint = Tare.colors.kcal, modifier = Modifier.size(28.dp).alpha(alpha))
        Spacer(Modifier.height(8.dp))
        Text("Claude is estimating…", style = Tare.type.bodyStrong, color = Tare.colors.textMuted)
        Spacer(Modifier.height(16.dp))
        repeat(3) { i ->
            Box(
                Modifier
                    .fillMaxWidth(if (i == 1) 0.7f else 0.9f)
                    .padding(vertical = 5.dp)
                    .height(44.dp)
                    .alpha(alpha * 0.6f)
                    .clip(Radii.row)
                    .background(Tare.colors.surfaceRaised),
            )
        }
    }
}

@Composable
private fun NoKeyCard(onOpenSettings: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), onClick = onOpenSettings) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Key, null, tint = Tare.colors.kcal)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Add your Claude API key", style = Tare.type.title, color = Tare.colors.text)
                Text("Describing meals and reading labels uses your own key. It stays encrypted on this phone.", style = Tare.type.caption, color = Tare.colors.textMuted)
            }
        }
    }
}

