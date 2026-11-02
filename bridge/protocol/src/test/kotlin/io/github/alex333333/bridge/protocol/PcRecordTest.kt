package io.github.alex333333.bridge.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PcRecordTest {
    private val record = PcRecord(
        name = "Дом",
        host = "192.168.1.5",
        port = 48750,
        token = "tok",
        deviceId = "dev-1",
        fingerprintHex = "a3f109bc",
        macs = listOf("AA-BB-CC-DD-EE-FF"),
    )

    @Test
    fun `a record survives a round trip through json`() {
        assertEquals(record, PcRecord.fromJson(record.toJson()))
    }

    @Test
    fun `missing or damaged text gives no record`() {
        assertNull(PcRecord.fromJson(null))
        assertNull(PcRecord.fromJson(""))
        assertNull(PcRecord.fromJson("{not json"))
        assertNull(PcRecord.fromJson("{\"name\":\"x\"}"))
    }

    @Test
    fun `host and port are parsed with the default port`() {
        assertEquals("192.168.1.5" to Api.PORT, parseHostPort("192.168.1.5"))
        assertEquals("192.168.1.5" to 1234, parseHostPort(" 192.168.1.5:1234 "))
        assertEquals("pc.local" to Api.PORT, parseHostPort("pc.local"))
    }

    @Test
    fun `a bad host or port is rejected`() {
        assertNull(parseHostPort(""))
        assertNull(parseHostPort("   "))
        assertNull(parseHostPort("host:abc"))
        assertNull(parseHostPort("host:0"))
        assertNull(parseHostPort("host:70000"))
        assertNull(parseHostPort(":1234"))
        assertNull(parseHostPort("a:1:2"))
        assertNull(parseHostPort("bad host"))
    }
}
