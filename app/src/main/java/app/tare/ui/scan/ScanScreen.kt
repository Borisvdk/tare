package app.tare.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import app.tare.data.diagnostics.CrashReports
import app.tare.ui.Fmt
import app.tare.ui.components.PrimaryButton
import app.tare.ui.components.RoundIconButton
import app.tare.ui.components.SecondaryButton
import app.tare.ui.components.TareSheet
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
fun ScanScreen(route: Route.Scan) {
    val day = LocalDate.ofEpochDay(route.day)
    val viewModel = tareViewModel(key = "scan-${route.day}-${route.part}") { ScanViewModel(it, day, route.part) }
    val state by viewModel.state.collectAsState()
    val remaining by viewModel.remainingKcal.collectAsState()
    val nav = LocalNavigator.current
    val notify = LocalNotifier.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    // Google's scanner takes over when our camera scanner crashed or failed on this phone, or
    // when it's chosen in Settings.
    var useGoogle by remember { mutableStateOf(CameraScannerGuard.isBroken(context)) }
    // "Active" while this screen is in the foreground, so any crash or kill in that window
    // (not a background kill) switches this phone to Google's scanner.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> CameraScannerGuard.active(context, true)
                Lifecycle.Event.ON_STOP -> CameraScannerGuard.active(context, false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            CameraScannerGuard.active(context, false)
        }
    }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var googleError by remember { mutableStateOf<String?>(null) }
    var preparingGoogle by remember { mutableStateOf(false) }

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(useGoogle) { if (!useGoogle && !hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }
    var torch by remember { mutableStateOf(false) }
    var logging by remember { mutableStateOf(false) }

    // Google's scanner is a full-screen activity: open it whenever we're ready for a barcode,
    // including after "Log & next". Backing out of it leaves the scanner.
    LaunchedEffect(useGoogle, state is ScanState.Scanning, googleError) {
        if (!useGoogle || state !is ScanState.Scanning || googleError != null) return@LaunchedEffect
        try {
            preparingGoogle = true
            val ready = prepareGoogleScanner(context)
            preparingGoogle = false
            if (!ready) {
                googleError = "Google's scanner couldn't be downloaded. Check your connection and try again."
                return@LaunchedEffect
            }
            when (val result = scanWithGoogle(context)) {
                is GoogleScan.Code -> viewModel.onBarcode(result.value)
                GoogleScan.Cancelled -> nav.back()
                is GoogleScan.Failed -> googleError = result.message
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            preparingGoogle = false
            CrashReports.record(context, "Google's scanner failed", e)
            googleError = e.message ?: e.javaClass.simpleName
        }
    }

    LaunchedEffect(state) {
        if (state is ScanState.Looking) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (useGoogle) {
            ScannerMessage(
                title = when {
                    googleError != null -> "Scanner unavailable"
                    preparingGoogle -> "Getting Google's scanner ready…"
                    else -> "Using Google's scanner"
                },
                body = googleError ?: "The camera scanner stopped working on this phone last time, so Tare opens Google's instead.",
            ) {
                if (googleError != null) PrimaryButton("Try again", onClick = { googleError = null })
                SecondaryButton("Try Tare's camera again", onClick = {
                    CameraScannerGuard.reset(context)
                    googleError = null
                    useGoogle = false
                })
                SecondaryButton("Search instead", onClick = { nav.replace(Route.LogFood(route.day, focusSearch = true, part = route.part)) })
            }
        } else if (cameraError != null) {
            ScannerMessage(title = "The camera didn't start", body = cameraError.orEmpty()) {
                PrimaryButton("Use Google's scanner", onClick = { useGoogle = true })
                SecondaryButton("Search instead", onClick = { nav.replace(Route.LogFood(route.day, focusSearch = true, part = route.part)) })
            }
        } else if (hasPermission) {
            BarcodeCamera(
                onBarcode = viewModel::onBarcode,
                onError = { error ->
                    CrashReports.record(context, "Camera didn't start", error)
                    cameraError = error.message ?: error.javaClass.simpleName
                },
                torch = torch,
                modifier = Modifier.fillMaxSize(),
            )
            Viewfinder(busy = state is ScanState.Looking)
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Camera access needed", style = Tare.type.title, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text("Tare uses the camera only to read barcodes.", style = Tare.type.body, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Allow camera", onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) })
            }
        }

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(Icons.Rounded.Close, "Close", { nav.back() }, background = Color.Black.copy(alpha = 0.45f), tint = Color.White)
            Spacer(Modifier.weight(1f))
            if (!useGoogle && cameraError == null) {
                RoundIconButton(
                    if (torch) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                    if (torch) "Torch off" else "Torch on",
                    { torch = !torch },
                    background = Color.Black.copy(alpha = 0.45f),
                    tint = if (torch) Tare.colors.fat else Color.White,
                )
            }
        }

        if (cameraError == null && (!useGoogle || state is ScanState.Looking)) Text(
            when (val s = state) {
                is ScanState.Looking -> "Looking up ${s.barcode}…"
                else -> "Point at a barcode"
            },
            style = Tare.type.bodyStrong,
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 48.dp),
        )
    }

    when (val s = state) {
        is ScanState.Found -> AmountSheet(
            food = s.food,
            mode = AmountMode.LOG,
            remainingKcal = remaining,
            initialTime = viewModel.sessionTime(),
            onConfirm = { result ->
                if (!logging) {
                    logging = true
                    scope.launch {
                        val entry = viewModel.log(s.food, result.grams, result.portion, result.time)
                        nav.back()
                        notify.show("Logged ${entry.name} · ${Fmt.kcal(entry.nutrients.kcal)} kcal")
                    }
                }
            },
            onDismiss = viewModel::reset,
            onEditFood = { nav.replace(Route.FoodEditor(foodId = s.food.id)) },
            onConfirmAndNext = { result ->
                if (!logging) {
                    logging = true
                    scope.launch {
                        val entry = viewModel.log(s.food, result.grams, result.portion, result.time)
                        viewModel.reset()
                        logging = false
                        notify.show("Logged ${entry.name} · ${Fmt.kcal(entry.nutrients.kcal)} kcal · scan the next one", actionLabel = "Undo") {
                            viewModel.undo(entry)
                        }
                    }
                }
            },
        )
        is ScanState.Missing -> TareSheet(onDismiss = viewModel::reset) {
            Text(s.title, style = Tare.type.title, color = Tare.colors.text)
            Spacer(Modifier.height(4.dp))
            Text(s.message, style = Tare.type.body, color = Tare.colors.textMuted)
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                "Read the label with Claude",
                icon = Icons.Rounded.AutoAwesome,
                onClick = { nav.replace(Route.FoodEditor(barcode = s.barcode, logDay = route.day, name = s.name.orEmpty(), readLabel = true)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                "Enter nutrition manually",
                icon = Icons.Rounded.Edit,
                onClick = { nav.replace(Route.FoodEditor(barcode = s.barcode, logDay = route.day, name = s.name.orEmpty())) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Scan again", onClick = viewModel::rescan, modifier = Modifier.fillMaxWidth())
        }
        else -> Unit
    }
}

/** Centred explanation with actions, shown instead of the camera. */
@Composable
private fun BoxScope.ScannerMessage(title: String, body: String, actions: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.align(Alignment.Center).padding(32.dp).widthIn(max = 360.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = Tare.type.title, color = Color.White, textAlign = TextAlign.Center)
        Text(body, style = Tare.type.body, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        actions()
    }
}

/** Dimmed overlay with a rounded cut-out and a sweeping line while a lookup runs. */
@Composable
private fun Viewfinder(busy: Boolean) {
    val accent = Tare.colors.kcal
    val sweep = rememberInfiniteTransition(label = "sweep")
    val progress by sweep.animateFloat(0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "line")
    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val w = size.width * 0.78f
        val h = w * 0.58f
        val topLeft = Offset((size.width - w) / 2f, size.height * 0.40f - h / 2f)
        val radius = CornerRadius(28.dp.toPx())
        drawRect(Color.Black.copy(alpha = 0.55f))
        drawRoundRect(Color.Transparent, topLeft, Size(w, h), radius, blendMode = BlendMode.Clear)
        drawRoundRect(
            color = if (busy) accent else Color.White.copy(alpha = 0.9f),
            topLeft = topLeft,
            size = Size(w, h),
            cornerRadius = radius,
            style = Stroke(width = 3.dp.toPx()),
        )
        if (busy) {
            val y = topLeft.y + 16.dp.toPx() + (h - 32.dp.toPx()) * progress
            drawLine(accent, Offset(topLeft.x + 20.dp.toPx(), y), Offset(topLeft.x + w - 20.dp.toPx(), y), 3.dp.toPx(), StrokeCap.Round)
        }
    }
}
