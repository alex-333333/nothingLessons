package io.github.alex333333.lessons.core

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class BellValidationTest {

    private fun bell(sh: Int, sm: Int, eh: Int, em: Int) = Bell(LocalTime.of(sh, sm), LocalTime.of(eh, em))

    @Test
    fun `valid bells have no errors`() {
        assertEquals(emptyMap<Int, BellError>(), validateBells(listOf(bell(8, 30, 9, 15), bell(9, 15, 10, 0))))
    }

    @Test
    fun `end must be after start`() {
        assertEquals(mapOf(0 to BellError.EndBeforeStart), validateBells(listOf(bell(9, 15, 9, 15))))
    }

    @Test
    fun `lesson must not start before the previous one ends`() {
        assertEquals(
            mapOf(1 to BellError.OverlapsPrevious),
            validateBells(listOf(bell(8, 30, 9, 15), bell(9, 0, 9, 45))),
        )
    }

    @Test
    fun `suggested next bell follows the last one`() {
        assertEquals(bell(8, 30, 9, 15), suggestNextBell(emptyList()))
        assertEquals(bell(9, 25, 10, 10), suggestNextBell(listOf(bell(8, 30, 9, 15))))
    }

    @Test
    fun `suggested bell never wraps past midnight`() {
        assertEquals(bell(23, 59, 23, 59).start, suggestNextBell(listOf(bell(23, 0, 23, 50))).start)
    }
}
