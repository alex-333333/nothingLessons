package io.github.alex333333.bridge.pc

import io.github.alex333333.bridge.protocol.Api
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MainArgsTest {
    @Test fun noArgumentsMeansDefaults() {
        val args = parseArgs(emptyArray())
        assertFalse(args.selftest)
        assertEquals(Api.PORT, args.port)
        assertNull(args.out)
    }

    @Test fun selftestFlagIsRead() {
        assertTrue(parseArgs(arrayOf("--selftest")).selftest)
    }

    @Test fun portIsRead() {
        assertEquals(5000, parseArgs(arrayOf("--port", "5000")).port)
    }

    @Test fun outFileIsRead() {
        assertEquals("x.log", parseArgs(arrayOf("--out", "x.log")).out)
    }

    @Test fun flagsCombine() {
        val args = parseArgs(arrayOf("--selftest", "--out", "r.txt", "--port", "0"))
        assertTrue(args.selftest)
        assertEquals("r.txt", args.out)
        assertEquals(0, args.port)
    }

    @Test fun invalidPortsAreRejected() {
        for (bad in listOf("abc", "-1", "65536", "")) rejects("--port", bad)
        rejects("--port") // value missing
    }

    @Test fun missingOutValueAndUnknownFlagsAreRejected() {
        rejects("--out")
        rejects("--bogus")
    }

    private fun rejects(vararg args: String) {
        try {
            parseArgs(arrayOf(*args))
            fail("Expected ${args.toList()} to be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
