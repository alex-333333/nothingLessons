package io.github.alex333333.bridge.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignerTest {
    private val now = 1_000_000L

    private fun sig(
        token: String = "tok",
        method: String = "GET",
        path: String = "/notes?x=1",
        time: Long = now,
        body: String = Signer.sha256Hex(ByteArray(0)),
    ) = Signer.sign(token, method, path, time, body)

    private fun verify(
        signature: String,
        token: String = "tok",
        method: String = "GET",
        path: String = "/notes?x=1",
        time: Long = now,
        body: String = Signer.sha256Hex(ByteArray(0)),
        nowMillis: Long = now,
    ) = Signer.verify(token, method, path, time, body, signature, nowMillis)

    @Test fun signatureIsDeterministic() = assertEquals(sig(), sig())

    @Test fun signatureIsBase64UrlWithoutPadding() {
        val s = sig()
        assertTrue(s, s.matches(Regex("[A-Za-z0-9_-]+")))
    }

    @Test fun verifyAcceptsSameRequest() = assertTrue(verify(sig()))

    @Test fun verifyRejectsOtherPath() = assertFalse(verify(sig(), path = "/files"))

    @Test fun verifyRejectsOtherQuery() = assertFalse(verify(sig(), path = "/notes?x=2"))

    @Test fun verifyRejectsOtherMethod() = assertFalse(verify(sig(), method = "POST"))

    @Test fun verifyRejectsOtherToken() = assertFalse(verify(sig(), token = "other"))

    @Test fun verifyRejectsOtherBody() = assertFalse(verify(sig(), body = Signer.sha256Hex("x".toByteArray())))

    @Test fun verifyRejectsOtherTimestamp() = assertFalse(verify(sig(), time = now + 1))

    @Test fun verifyRejectsOldTimestamp() {
        val old = now - 60_001
        assertFalse(verify(sig(time = old), time = old))
    }

    @Test fun verifyRejectsFutureTimestamp() {
        val future = now + 60_001
        assertFalse(verify(sig(time = future), time = future))
    }

    @Test fun verifyAcceptsTimestampAtSkewLimit() {
        assertTrue(verify(sig(time = now - 60_000), time = now - 60_000))
        assertTrue(verify(sig(time = now + 60_000), time = now + 60_000))
    }

    @Test fun corruptedSignatureGivesFalseNotException() {
        assertFalse(verify(""))
        assertFalse(verify("!!!not base64!!!"))
        assertFalse(verify("AAAA"))
        assertFalse(verify(sig().dropLast(1)))
        assertFalse(verify(sig() + "A"))
    }

    @Test fun sha256HexOfAbc() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Signer.sha256Hex("abc".toByteArray()))
    }
}
