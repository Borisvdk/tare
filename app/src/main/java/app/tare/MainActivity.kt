package app.tare

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.tare.ui.navigation.Route
import app.tare.ui.root.DeepLinks
import app.tare.ui.root.TareRoot
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Shortcut/share routes waiting to be opened; TareRoot consumes them. */
    private val deepLinks = MutableStateFlow<Route?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as TareApp).graph
        // On recreation the back stack is restored instead; don't open the link twice.
        if (savedInstanceState == null) deepLinks.value = DeepLinks.routeFor(intent)
        setContent { TareRoot(graph, deepLinks = deepLinks) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLinks.routeFor(intent)?.let { deepLinks.value = it }
    }
}
