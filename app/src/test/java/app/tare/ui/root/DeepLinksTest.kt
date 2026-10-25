package app.tare.ui.root

import android.app.Application
import android.content.Intent
import android.net.Uri
import app.tare.ui.navigation.Route
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class DeepLinksTest {
    private val today = LocalDate.of(2026, 10, 25)
    private val day = today.toEpochDay()

    @Test
    fun shortcutsOpenTheirScreens() {
        assertThat(DeepLinks.routeFor(Intent(DeepLinks.ACTION_SCAN), today)).isEqualTo(Route.Scan(day))
        assertThat(DeepLinks.routeFor(Intent(DeepLinks.ACTION_DESCRIBE), today)).isEqualTo(Route.Describe(day))
        assertThat(DeepLinks.routeFor(Intent(DeepLinks.ACTION_LOG), today)).isEqualTo(Route.LogFood(day, focusSearch = true))
    }

    @Test
    fun sharedTextPrefillsDescribe() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "  2 eggs and toast ")
        assertThat(DeepLinks.routeFor(intent, today)).isEqualTo(Route.Describe(day, prefill = "2 eggs and toast"))
    }

    @Test
    fun sharedPhotoOpensDescribeWithIt() {
        val uri = Uri.parse("content://media/external/images/media/42")
        val intent = Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uri)
        assertThat(DeepLinks.routeFor(intent, today)).isEqualTo(Route.Describe(day, photoUri = uri.toString()))
    }

    @Test
    fun launcherAndUnknownIntentsOpenNothingExtra() {
        assertThat(DeepLinks.routeFor(Intent(Intent.ACTION_MAIN), today)).isNull()
        assertThat(DeepLinks.routeFor(null, today)).isNull()
        assertThat(DeepLinks.routeFor(Intent(Intent.ACTION_SEND).setType("application/pdf"), today)).isNull()
    }
}
