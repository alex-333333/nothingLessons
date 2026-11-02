package io.github.alex333333.bridge.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatSizeTest {
    @Test
    fun `small sizes are in bytes`() {
        assertEquals("0 Б", formatSize(0))
        assertEquals("1023 Б", formatSize(1023))
    }

    @Test
    fun `larger sizes get one decimal with a comma`() {
        assertEquals("1,5 КБ", formatSize(1536))
        assertEquals("5,0 МБ", formatSize(5L * 1024 * 1024))
        assertEquals("2,0 ГБ", formatSize(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun `a negative size is shown as zero`() {
        assertEquals("0 Б", formatSize(-5))
    }
}
