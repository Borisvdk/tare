package app.tare.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AmountInputStateTest {
    private fun AmountInputState.type(keys: String) = keys.forEach { c ->
        press(
            when (c) {
                '.' -> PadKey.Dot
                '<' -> PadKey.Back
                else -> PadKey.Digit(c.digitToInt())
            },
        )
    }

    @Test
    fun firstKeyReplacesInitialValue() {
        val input = AmountInputState(100.0)
        input.type("150")
        assertThat(input.value).isEqualTo(150.0)
    }

    @Test
    fun backspaceEditsInitialValueInsteadOfReplacing() {
        val input = AmountInputState(125.0)
        input.type("<")
        assertThat(input.text).isEqualTo("12")
    }

    @Test
    fun decimalsAreLimited() {
        val input = AmountInputState(null, maxDecimals = 1)
        input.type("1.55")
        assertThat(input.text).isEqualTo("1.5")
        input.type(".")
        assertThat(input.text).isEqualTo("1.5")
    }

    @Test
    fun leadingDotBecomesZeroDot() {
        val input = AmountInputState(null)
        input.type(".5")
        assertThat(input.value).isEqualTo(0.5)
    }

    @Test
    fun noDecimalsWhenDisallowed() {
        val input = AmountInputState(null, maxDecimals = 0)
        input.type("2.5")
        assertThat(input.text).isEqualTo("25")
    }
}
