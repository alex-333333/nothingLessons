package io.github.alex333333.bridge.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayNameTest {
    @Test
    fun `display name prefers the file name`() {
        assertEquals("photo.jpg", displayName("photo.jpg", "12345"))
    }

    @Test
    fun `display name falls back to the last uri segment`() {
        assertEquals("12345", displayName(null, "12345"))
        assertEquals("12345", displayName("  ", "12345"))
    }

    @Test
    fun `display name is never blank`() {
        assertEquals("file", displayName(null, null))
        assertEquals("file", displayName("", " "))
    }

    @Test
    fun `display name is made safe`() {
        assertEquals("a_b.txt", displayName("a:b.txt", null))
        assertEquals("passwd", displayName("../etc/passwd", null))
    }
}
