package io.github.alex333333.lessons.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigCalendarTest {

    private fun t(hour: Int, minute: Int) = LocalTime.of(hour, minute)

    private val bells = listOf(Bell(t(8, 30), t(9, 15)), Bell(t(9, 25), t(10, 10)), Bell(t(10, 20), t(11, 5)))

    private val config = Config(bells = bells)
        .withSubjects(DayOfWeek.MONDAY, listOf("Математика", "Русский"))
        .withSubjects(DayOfWeek.SUNDAY, listOf("Кружок"))

    // 2026-10-26 is a Monday, 2026-11-01 a Sunday.
    private val monday = LocalDate.of(2026, 10, 26)
    private val tuesday = monday.plusDays(1)
    private val sunday = LocalDate.of(2026, 11, 1)

    private val short = DayOverride(monday, bells = listOf(Bell(t(8, 30), t(9, 0))), subjects = listOf("Физика"))

    @Test
    fun `regular date follows the week`() {
        assertEquals(config.slots(DayOfWeek.MONDAY), config.slots(monday))
        assertEquals(emptyList<Slot>(), config.slots(tuesday))
        assertEquals(emptyList<Slot>(), config.slots(sunday))
        assertEquals(1, config.copy(sundayEnabled = true).slots(sunday).size)
    }

    @Test
    fun `changed date uses its own bells and subjects`() {
        val changed = config.withOverride(short)
        assertEquals(listOf(Slot(1, "Физика", t(8, 30), t(9, 0))), changed.slots(monday))
        assertTrue(changed.isChanged(monday))
        assertFalse(changed.isChanged(tuesday))
        // The weekly view is untouched.
        assertEquals(config.slots(DayOfWeek.MONDAY), changed.slots(DayOfWeek.MONDAY))
    }

    @Test
    fun `a change on a disabled sunday still has lessons`() {
        val changed = config.withOverride(short.copy(date = sunday))
        assertEquals(1, changed.slots(sunday).size)
    }

    @Test
    fun `cancelled date and holiday have no lessons`() {
        assertEquals(emptyList<Slot>(), config.withCancelled(monday, true).slots(monday))
        assertFalse(config.withCancelled(monday, true).isChanged(monday))
        val holiday = config.withOverride(short).withHoliday(Holiday(monday, monday))
        assertEquals(emptyList<Slot>(), holiday.slots(monday))
        assertFalse(holiday.isChanged(monday))
    }

    @Test
    fun `withCancelled toggles a cancel-only override`() {
        val cancelled = config.withCancelled(monday, true)
        assertEquals(DayOverride(monday, cancelled = true), cancelled.override(monday))
        assertEquals(config, cancelled.withCancelled(monday, false))
        // Cancelling twice keeps a single override.
        assertEquals(1, cancelled.withCancelled(monday, true).overrides.size)
    }

    @Test
    fun `cancelling a changed day replaces the change`() {
        val cancelled = config.withOverride(short).withCancelled(monday, true)
        assertEquals(listOf(DayOverride(monday, cancelled = true)), cancelled.overrides)
    }

    @Test
    fun `un-cancelling leaves a changed day alone`() {
        val changed = config.withOverride(short)
        assertEquals(changed, changed.withCancelled(monday, false))
    }

    @Test
    fun `withOverride replaces the same date and keeps dates sorted`() {
        val other = short.copy(subjects = listOf("Химия"))
        val result = config.withOverride(short.copy(date = tuesday)).withOverride(short).withOverride(other)
        assertEquals(listOf(other, short.copy(date = tuesday)), result.overrides)
        assertEquals(listOf(short.copy(date = tuesday)), result.withoutOverride(monday).overrides)
        assertNull(result.withoutOverride(monday).override(monday))
    }

    @Test
    fun `holidays are added once, sorted, and removed`() {
        val winter = Holiday(LocalDate.of(2026, 12, 29), LocalDate.of(2027, 1, 11))
        val autumn = Holiday(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 11, 1))
        val result = config.withHoliday(winter).withHoliday(autumn).withHoliday(winter)
        assertEquals(listOf(autumn, winter), result.holidays)
        assertEquals(listOf(winter), result.withoutHoliday(autumn).holidays)
        assertEquals(autumn, result.holidayOn(monday))
        assertEquals(winter, result.holidayOn(LocalDate.of(2027, 1, 11)))
        assertNull(result.holidayOn(LocalDate.of(2027, 1, 12)))
    }

    @Test
    fun `holiday range is inclusive and tolerates swapped ends`() {
        val h = Holiday(LocalDate.of(2026, 11, 1), monday)
        assertTrue(monday in h)
        assertTrue(sunday in h)
        assertFalse(monday.minusDays(1) in h)
        assertFalse(sunday.plusDays(1) in h)
        assertEquals(monday, h.first)
        assertEquals(sunday, h.last)
    }

    @Test
    fun `regularDay is pre-filled and padded to the bells`() {
        val day = config.regularDay(monday)
        assertEquals(DayOverride(monday, cancelled = false, bells = bells, subjects = listOf("Математика", "Русский", "")), day)
        assertEquals(config.slots(monday), config.withOverride(day).slots(monday))
        assertEquals(listOf("", "", ""), config.regularDay(tuesday).subjects)
        // A disabled Sunday has no regular lessons to copy.
        assertEquals(listOf("", "", ""), config.regularDay(sunday).subjects)
    }

    @Test
    fun `regularDay drops subjects beyond the last bell`() {
        val long = config.withSubjects(DayOfWeek.MONDAY, listOf("a", "b", "c", "d"))
        assertEquals(listOf("a", "b", "c"), long.regularDay(monday).subjects)
    }

    @Test
    fun `pruned drops past overrides and finished holidays`() {
        val past = Holiday(monday.minusDays(10), monday.minusDays(1))
        val current = Holiday(monday.minusDays(2), monday)
        val future = Holiday(sunday, sunday)
        val full = config
            .withCancelled(monday.minusDays(1), true)
            .withCancelled(monday, true)
            .withOverride(short.copy(date = tuesday))
            .withHoliday(past).withHoliday(current).withHoliday(future)
        val pruned = full.pruned(monday)
        assertEquals(listOf(monday, tuesday), pruned.overrides.map { it.date })
        assertEquals(listOf(current, future), pruned.holidays)
        assertEquals(pruned, pruned.pruned(monday))
    }

    @Test
    fun `a change with lessons counts as having lessons`() {
        assertFalse(Config(bells = bells).hasLessons())
        assertTrue(Config().withOverride(short).hasLessons())
        assertFalse(Config().withCancelled(monday, true).hasLessons())
        assertFalse(Config().withOverride(short.copy(subjects = listOf(" "))).hasLessons())
    }
}
