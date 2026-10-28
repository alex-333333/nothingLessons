package io.github.alex333333.lessons.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CountdownTest {

    @Test
    fun `under an hour shows plain minutes`() {
        assertEquals(Countdown("7", CountdownUnit.Minutes), Countdown.of(7))
        assertEquals(Countdown("59", CountdownUnit.Minutes), Countdown.of(59))
    }

    @Test
    fun `an hour or more shows hours and minutes`() {
        assertEquals(Countdown("1:00", CountdownUnit.Hours), Countdown.of(60))
        assertEquals(Countdown("8:05", CountdownUnit.Hours), Countdown.of(485))
    }
}
