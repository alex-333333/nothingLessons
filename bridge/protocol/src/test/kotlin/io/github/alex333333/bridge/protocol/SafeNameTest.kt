package io.github.alex333333.bridge.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeNameTest {
    @Test fun stripsWindowsPathTraversal() = assertEquals("evil.exe", SafeName.of("..\\..\\evil.exe"))

    @Test fun stripsUnixPath() = assertEquals("c.txt", SafeName.of("a/b/c.txt"))

    @Test fun prefixesReservedNames() {
        assertEquals("_CON", SafeName.of("CON"))
        assertEquals("_nul.txt", SafeName.of("nul.txt"))
        assertEquals("_COM1", SafeName.of("COM1"))
        assertEquals("_lpt9.log", SafeName.of("lpt9.log"))
    }

    @Test fun doesNotPrefixNamesThatOnlyStartWithReservedWord() {
        assertEquals("console.txt", SafeName.of("console.txt"))
        assertEquals("COM10", SafeName.of("COM10"))
    }

    @Test fun emptyAndDotsFallBackToFile() {
        assertEquals("file", SafeName.of(""))
        assertEquals("file", SafeName.of("..."))
        assertEquals("file", SafeName.of("   "))
        assertEquals("file", SafeName.of(".."))
    }

    @Test fun replacesForbiddenCharacters() = assertEquals("a_b__c.txt", SafeName.of("a<b>:c.txt"))

    @Test fun replacesControlCharacters() = assertEquals("a_b.txt", SafeName.of("a\u0001b.txt"))

    @Test fun longNamesKeepExtensionAndFitLimit() {
        val n = SafeName.of("x".repeat(300) + ".png")
        assertTrue(n.length <= 120)
        assertTrue(n.endsWith(".png"))
    }

    @Test fun trailingDotsAndSpacesAreTrimmed() {
        assertEquals("a.txt", SafeName.of("a.txt. . "))
        assertEquals("report", SafeName.of("report..."))
    }

    @Test fun cyrillicAndEmojiArePreserved() {
        assertEquals("отчёт 📄.txt", SafeName.of("отчёт 📄.txt"))
    }

    @Test fun uniqueKeepsFreeName() = assertEquals("a.txt", SafeName.unique("a.txt") { false })

    @Test fun uniqueAddsCounter() = assertEquals("a (1).txt", SafeName.unique("a.txt") { it == "a.txt" })

    @Test fun uniqueSkipsTakenCounters() {
        val taken = setOf("a.txt", "a (1).txt")
        assertEquals("a (2).txt", SafeName.unique("a.txt") { it in taken })
    }

    @Test fun uniqueWorksWithoutExtension() = assertEquals("a (1)", SafeName.unique("a") { it == "a" })
}
