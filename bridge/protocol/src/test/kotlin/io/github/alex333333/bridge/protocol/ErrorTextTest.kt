package io.github.alex333333.bridge.protocol

import io.github.alex333333.bridge.protocol.client.BridgeHttpException
import io.github.alex333333.bridge.protocol.client.errorText
import java.io.IOException
import java.net.ConnectException
import javax.net.ssl.SSLException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorTextTest {
    @Test
    fun `an unpaired phone is explained`() {
        assertEquals("Телефон не привязан к ПК", errorText(NotPairedException()))
    }

    @Test
    fun `a refused pairing code is explained`() {
        assertTrue(errorText(BridgeHttpException(403, "x")).contains("код"))
    }

    @Test
    fun `other statuses show the status`() {
        assertTrue(errorText(BridgeHttpException(500, "x")).contains("500"))
    }

    @Test
    fun `a changed certificate is explained`() {
        assertTrue(errorText(SSLException("x")).contains("сертификат"))
    }

    @Test
    fun `an unreachable PC is explained`() {
        assertEquals("Нет связи с ПК", errorText(ConnectException("refused")))
        assertEquals("Нет связи с ПК", errorText(IOException("timeout")))
    }

    @Test
    fun `anything else is never blank`() {
        assertTrue(errorText(IllegalStateException()).isNotBlank())
    }
}
