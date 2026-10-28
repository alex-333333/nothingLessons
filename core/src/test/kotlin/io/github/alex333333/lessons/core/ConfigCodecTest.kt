package io.github.alex333333.lessons.core

import java.time.DayOfWeek
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
}
