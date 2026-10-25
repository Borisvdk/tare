package app.tare.ui.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DictationTest {
    @Test
    fun spokenPhrasesJoinWhatsTypedWithASpace() {
        assertThat(appendSpoken("", "twee eieren")).isEqualTo("twee eieren")
        assertThat(appendSpoken("twee eieren", "en een boterham met kaas")).isEqualTo("twee eieren en een boterham met kaas")
        assertThat(appendSpoken("2 eggs ", " and toast ")).isEqualTo("2 eggs and toast")
    }

    @Test
    fun silenceAddsNothing() {
        assertThat(appendSpoken("2 eggs", "  ")).isEqualTo("2 eggs")
    }
}
