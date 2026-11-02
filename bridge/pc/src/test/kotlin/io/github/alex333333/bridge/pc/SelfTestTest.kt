package io.github.alex333333.bridge.pc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfTestTest {
    @Test fun selfTestPassesAndReportsEveryStep() {
        val lines = mutableListOf<String>()
        val code = runSelfTest { lines += it }
        assertEquals(lines.toString(), 0, code)
        assertTrue(lines.toString(), lines.first().startsWith("OK server started"))
        assertEquals("SELFTEST PASSED", lines.last())
        assertTrue(lines.toString(), lines.any { it.startsWith("OK hello") })
        assertTrue(lines.toString(), lines.any { it.startsWith("OK pair") })
        assertTrue(lines.toString(), lines.any { it.startsWith("OK signed request") })
    }
}
