package io.github.alex333333.bridge.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class FingerprintTest {
    @Test fun sha256OfEmptyArray() =
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Fingerprint.sha256Hex(ByteArray(0)))

    @Test fun shortFormatsFirstEightHexCharsUppercase() = assertEquals("A3F1-09BC", Fingerprint.short("a3f109bcdead"))
}
