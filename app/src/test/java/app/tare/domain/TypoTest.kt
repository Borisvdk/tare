package app.tare.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TypoTest {
    @Test
    fun editDistanceCountsSwapsAsOneEdit() {
        assertThat(editDistance("kipfilet", "kipfilet", 2)).isEqualTo(0)
        assertThat(editDistance("kipfilt", "kipfilet", 2)).isEqualTo(1)
        assertThat(editDistance("bnaan", "banaan", 2)).isEqualTo(1)
        assertThat(editDistance("brocolli", "broccoli", 2)).isEqualTo(2)
        assertThat(editDistance("kpi", "kip", 2)).isEqualTo(1)
    }

    @Test
    fun editDistanceStopsPastTheBudget() {
        assertThat(editDistance("appel", "banaan", 1)).isEqualTo(2)
        assertThat(editDistance("a", "abcdef", 2)).isEqualTo(3)
    }

    @Test
    fun shortWordsAndNumbersAreNeverCorrected() {
        assertThat(typoBudget("ei")).isEqualTo(0)
        assertThat(typoBudget("liga")).isEqualTo(0)
        assertThat(typoBudget("zalm")).isEqualTo(0)
        assertThat(typoBudget("banan")).isEqualTo(1)
        assertThat(typoBudget("havermout")).isEqualTo(2)
        assertThat(typoBudget("150gram")).isEqualTo(0)
    }

    private val words = mapOf("banaan" to 3, "naan" to 1, "kipfilet" to 4, "havermout" to 2, "volle" to 2, "yoghurt" to 6)

    @Test
    fun fixesOnlyTheTokensThatMatchNothing() {
        assertThat(correctTypos(listOf("volle", "yoghrt"), words)).containsExactly("volle", "yoghurt").inOrder()
        assertThat(correctTypos(listOf("kipfilt"), words)).containsExactly("kipfilet")
    }

    @Test
    fun aMatchingFirstLetterBreaksTies() {
        // "naan" is as close to "bnaan" as "banaan" is, but typos rarely hit the first letter.
        assertThat(correctTypos(listOf("bnaan"), words)).containsExactly("banaan")
    }

    @Test
    fun givesUpWhenNothingIsCloseOrNothingNeedsFixing() {
        assertThat(correctTypos(listOf("pizza"), words)).isNull()
        assertThat(correctTypos(listOf("volle"), words)).isNull()
        assertThat(correctTypos(listOf("kip", "xyzq"), words)).isNull()
        // Half-typed words aren't guessed at; the full word will be.
        assertThat(correctTypos(listOf("havrmo"), words)).isNull()
    }

    @Test
    fun matchesAllNeedsEveryToken() {
        assertThat(matchesAll("fristi aardbei drinkyoghurt", listOf("fristi"))).isTrue()
        assertThat(matchesAll("fristi aardbei", listOf("fristi", "rood"))).isFalse()
    }
}
