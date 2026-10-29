package io.github.alex333333.lessons.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigCodecTest {

    private val config = Config(
        bells = listOf(
            Bell(LocalTime.of(8, 30), LocalTime.of(9, 15)),
            Bell(LocalTime.of(9, 25), LocalTime.of(10, 10)),
        ),
        sundayEnabled = true,
        font = FontChoice.RobotoSerif,
        weight = FontWeightChoice.Bold,
    ).withSubjects(DayOfWeek.MONDAY, listOf("Алгебра", ""))
        .withSubjects(DayOfWeek.SUNDAY, listOf("Кружок"))

    @Test
    fun `round trip keeps everything`() {
        assertEquals(config, ConfigCodec.decode(ConfigCodec.encode(config)))
    }

    @Test
    fun `times are stored as HH mm`() {
        assertTrue(ConfigCodec.encode(config).contains("\"08:30\""))
    }

    @Test
    fun `missing or broken json falls back to an empty config`() {
        assertEquals(Config(), ConfigCodec.decode(null))
        assertEquals(Config(), ConfigCodec.decode(""))
        assertEquals(Config(), ConfigCodec.decode("{not json"))
    }

    @Test
    fun `unknown fields are ignored`() {
        val json = """{"bells":[{"start":"08:30","end":"09:15"}],"future":42}"""
        assertEquals(1, ConfigCodec.decode(json).bells.size)
    }

    @Test
    fun `week is always padded to seven days`() {
        val json = """{"bells":[],"week":[["Алгебра"]]}"""
        val decoded = ConfigCodec.decode(json)
        assertEquals(7, decoded.week.size)
        assertEquals(listOf("Алгебра"), decoded.subjects(DayOfWeek.MONDAY))
        assertEquals(emptyList<String>(), decoded.subjects(DayOfWeek.FRIDAY))
    }

    @Test
    fun `appearance defaults to regular Noto Serif`() {
        assertEquals(FontChoice.NotoSerif, Config().font)
        assertEquals(FontWeightChoice.Regular, Config().weight)
    }

    @Test
    fun `an unknown font from a newer version keeps the schedule`() {
        val json = """{"bells":[{"start":"08:30","end":"09:15"}],"font":"Comic","weight":"Heavy"}"""
        val decoded = ConfigCodec.decode(json)
        assertEquals(1, decoded.bells.size)
        assertEquals(FontChoice.NotoSerif, decoded.font)
        assertEquals(FontWeightChoice.Regular, decoded.weight)
    }

    @Test
    fun `old json without calendar fields decodes unchanged`() {
        val old = """{"bells":[{"start":"08:30","end":"09:15"}],"week":[["Алгебра"],[],[],[],[],[],[]],""" +
            """"sundayEnabled":false,"font":"Inter","weight":"Medium"}"""
        val expected = Config(
            bells = listOf(Bell(LocalTime.of(8, 30), LocalTime.of(9, 15))),
            font = FontChoice.Inter,
            weight = FontWeightChoice.Medium,
        ).withSubjects(DayOfWeek.MONDAY, listOf("Алгебра"))
        val decoded = ConfigCodec.decode(old)
        assertEquals(expected, decoded)
        assertEquals(emptyList<DayOverride>(), decoded.overrides)
        assertEquals(emptyList<Holiday>(), decoded.holidays)
    }

    private val calendar = config
        .withCancelled(LocalDate.of(2026, 10, 27), true)
        .withOverride(
            DayOverride(
                LocalDate.of(2026, 10, 28),
                bells = listOf(Bell(LocalTime.of(9, 0), LocalTime.of(9, 30))),
                subjects = listOf("Экскурсия"),
            ),
        )
        .withHoliday(Holiday(LocalDate.of(2026, 12, 29), LocalDate.of(2027, 1, 11)))

    @Test
    fun `round trip keeps overrides and holidays`() {
        assertEquals(calendar, ConfigCodec.decode(ConfigCodec.encode(calendar)))
    }

    @Test
    fun `dates are stored as ISO yyyy-MM-dd`() {
        val json = ConfigCodec.encode(calendar)
        assertTrue(json.contains("\"2026-10-27\""))
        assertTrue(json.contains("\"2027-01-11\""))
    }

    @Test
    fun `a broken date falls back to an empty config instead of crashing`() {
        assertEquals(Config(), ConfigCodec.decode("""{"holidays":[{"start":"2026-13-45","end":"2027-01-01"}]}"""))
        assertEquals(Config(), ConfigCodec.decode("""{"bells":[{"start":"25:99","end":"09:15"}]}"""))
    }
}
