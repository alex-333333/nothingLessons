package io.github.alex333333.bridge.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingSessionTest {
    private var time = 0L
    private fun session() = PairingSession("123456", { time })

    @Test fun correctCodeWorksOnce() {
        val s = session()
        assertTrue(s.tryCode("123456"))
        assertFalse(s.tryCode("123456"))
    }

    @Test fun wrongCodeIsRejected() = assertFalse(session().tryCode("000000"))

    @Test fun expiresAfterTtl() {
        val s = session()
        time = 120_001
        assertFalse(s.tryCode("123456"))
    }

    @Test fun stillValidAtExactlyTtl() {
        val s = session()
        time = 120_000
        assertTrue(s.tryCode("123456"))
    }

    @Test fun fiveWrongAttemptsLockTheSession() {
        val s = session()
        repeat(5) { assertFalse(s.tryCode("000000")) }
        assertFalse(s.tryCode("123456"))
    }

    @Test fun fourWrongAttemptsThenCorrectStillWorks() {
        val s = session()
        repeat(4) { assertFalse(s.tryCode("000000")) }
        assertTrue(s.tryCode("123456"))
    }

    @Test fun newCodeIsAlwaysSixDigits() {
        repeat(1000) {
            val c = PairingSession.newCode()
            assertEquals(c, 6, c.length)
            assertTrue(c, c.all { it in '0'..'9' })
        }
    }

    @Test fun newCodeKeepsLeadingZeros() {
        val fixed = object : java.util.Random() {
            override fun nextInt(bound: Int): Int = 42
        }
        assertEquals("000042", PairingSession.newCode(fixed))
    }
}
