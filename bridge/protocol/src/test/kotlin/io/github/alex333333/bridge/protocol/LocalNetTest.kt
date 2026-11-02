package io.github.alex333333.bridge.protocol

import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetTest {
    private fun local(a: String) = isLocalAddress(InetAddress.getByName(a))

    @Test fun privateAndLocalAddressesAreLocal() {
        for (a in listOf("192.168.1.5", "10.0.0.1", "172.16.0.1", "172.31.255.254", "169.254.1.1", "127.0.0.1", "::1", "fe80::1", "fd00::1")) {
            assertTrue(a, local(a))
        }
    }

    @Test fun publicAddressesAreNotLocal() {
        for (a in listOf("8.8.8.8", "172.32.0.1", "11.0.0.1", "2001:4860:4860::8888")) {
            assertFalse(a, local(a))
        }
    }
}
