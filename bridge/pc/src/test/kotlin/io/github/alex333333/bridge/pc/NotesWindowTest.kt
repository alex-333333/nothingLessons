package io.github.alex333333.bridge.pc

import io.github.alex333333.bridge.protocol.Note
import org.junit.Assert.assertEquals
import org.junit.Test

class NotesWindowTest {
    @Test fun titleIsTheFirstNonBlankLine() {
        assertEquals("Привет 😀", noteTitle("\n  \nПривет 😀\nвторая"))
        assertEquals("one", noteTitle("one"))
    }

    @Test fun blankAndLongTitles() {
        assertEquals("(пусто)", noteTitle(" \n\t"))
        assertEquals("а".repeat(80) + "…", noteTitle("а".repeat(200)))
    }

    @Test fun newestNotesComeFirst() {
        val a = Note("a", "x", 1)
        val b = Note("b", "y", 3)
        val c = Note("c", "z", 2)
        assertEquals(listOf(b, c, a), newestFirst(listOf(a, b, c)))
    }

    @Test fun addressLineListsEveryAddress() {
        assertEquals("Адрес ПК: 192.168.1.25", addressLine(listOf("192.168.1.25")))
        assertEquals("Адрес ПК: 192.168.1.25, 10.0.0.4", addressLine(listOf("192.168.1.25", "10.0.0.4")))
        assertEquals("Адрес ПК: не найден (нет сети)", addressLine(emptyList()))
    }
}
