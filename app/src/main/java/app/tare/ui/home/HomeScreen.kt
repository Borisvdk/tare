package app.tare.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.LocalDining
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import app.tare.ui.components.combinedClickableCompat
import app.tare.ui.components.pressScale
import app.tare.ui.library.LibraryScreen
import app.tare.ui.navigation.LocalNavigator
import app.tare.ui.navigation.Route
import app.tare.ui.navigation.tareViewModel
import app.tare.ui.progress.ProgressScreen
import app.tare.ui.theme.Radii
import app.tare.ui.theme.Tare
import app.tare.ui.today.TodayScreen
import app.tare.ui.today.TodayViewModel
import java.time.LocalDate

enum class HomeTab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Rounded.WbSunny),
    LIBRARY("Library", Icons.Rounded.LocalDining),
    PROGRESS("Progress", Icons.Rounded.BarChart),
}

@Composable
fun HomeScreen() {
    val nav = LocalNavigator.current
    var tab by rememberSaveable { mutableStateOf(HomeTab.TODAY) }
    val todayViewModel = tareViewModel { TodayViewModel(it) }
    var day by remember { mutableStateOf(LocalDate.now()) }
    val bottomInset = PaddingValues(bottom = 96.dp)

    BackHandler(enabled = tab != HomeTab.TODAY) { tab = HomeTab.TODAY }

    Box(Modifier.fillMaxSize().background(Tare.colors.background)) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "tab",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            when (current) {
                HomeTab.TODAY -> TodayScreen(todayViewModel, onDayChange = { day = it }, contentPadding = bottomInset)
                HomeTab.LIBRARY -> LibraryScreen(contentPadding = bottomInset)
                HomeTab.PROGRESS -> ProgressScreen(contentPadding = bottomInset)
            }
        }
        BottomBar(
            tab = tab,
            onTab = { tab = it },
            onAdd = { nav.go(Route.LogFood(day.toEpochDay())) },
            onScan = { nav.go(Route.Scan(day.toEpochDay())) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun BottomBar(
    tab: HomeTab,
    onTab: (HomeTab) -> Unit,
    onAdd: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Tare.colors
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, colors.background.copy(alpha = 0.92f), colors.background)))
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .height(64.dp)
                    .shadow(if (colors.isDark) 0.dp else 12.dp, Radii.pill, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
                    .clip(Radii.pill)
                    .background(colors.surface)
                    .border(1.dp, colors.outline, Radii.pill)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                HomeTab.entries.forEach { t ->
                    TabItem(t, selected = t == tab, onClick = { onTab(t) }, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.width(12.dp))
            AddButton(onClick = onAdd, onLongClick = onScan)
        }
    }
}

@Composable
private fun TabItem(tab: HomeTab, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Tare.colors
    val bg by animateColorAsState(if (selected) colors.surfaceRaised else Color.Transparent, label = "tabBg")
    val fg by animateColorAsState(if (selected) colors.text else colors.textFaint, label = "tabFg")
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .fillMaxSize()
            .pressScale(interaction, 0.94f)
            .clip(Radii.pill)
            .background(bg)
            .combinedClickableCompat(interaction, onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        Text(tab.label, style = Tare.type.caption, color = fg)
    }
}

@Composable
private fun AddButton(onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = Tare.colors
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressScale(interaction, 0.9f)
            .size(64.dp)
            .shadow(16.dp, CircleShape, ambientColor = colors.kcal.copy(alpha = 0.5f), spotColor = colors.kcal.copy(alpha = 0.5f))
            .clip(CircleShape)
            .background(colors.kcal)
            .combinedClickableCompat(
                interaction,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = "Log food (hold to scan)", tint = colors.onAccent, modifier = Modifier.size(30.dp))
    }
}
