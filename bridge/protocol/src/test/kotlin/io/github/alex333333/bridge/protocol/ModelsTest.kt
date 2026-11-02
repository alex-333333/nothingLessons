package io.github.alex333333.bridge.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    private inline fun <reified T> roundTrip(value: T) {
        val text = BridgeJson.encodeToString(value)
        assertEquals(value, BridgeJson.decodeFromString<T>(text))
    }

    @Test fun allInputEventsRoundTrip() {
        val events = listOf<InputEvent>(
            InputEvent.MouseMove(-3, 7),
            InputEvent.Click(MouseButton.Right),
            InputEvent.Scroll(-120),
            InputEvent.TypeText("Привет 😀\nвторая строка\t\"кавычки\""),
            InputEvent.PressKey(Key.Backspace),
            InputEvent.Media(MediaAction.PlayPause),
            InputEvent.Power(PowerAction.Shutdown, confirmed = true),
            InputEvent.Power(PowerAction.Lock),
        )
        events.forEach { roundTrip(it) }
    }

    @Test fun inputEventUsesTypeDiscriminator() {
        val text = BridgeJson.encodeToString<InputEvent>(InputEvent.Scroll(5))
        assertTrue(text, text.contains("\"type\""))
    }

    @Test fun serverEventsRoundTrip() {
        listOf<ServerEvent>(ServerEvent.OutboxChanged, ServerEvent.NotesChanged, ServerEvent.Unpaired).forEach { roundTrip(it) }
    }

    @Test fun noteRoundTripsWithCyrillicEmojiAndNewlines() {
        roundTrip(Note("id-1", "Заметка 📝\nстрока 2\r\nстрока 3", 1_700_000_000_000L))
    }

    @Test fun otherModelsRoundTrip() {
        roundTrip(Hello("pc-1", "Рабочий ПК"))
        roundTrip(PairRequest("123456", "dev", "Pixel 😀"))
        roundTrip(PairResponse("tok", "PC", listOf("AA-BB-CC-DD-EE-FF")))
        roundTrip(ClipText("текст\nещё"))
        roundTrip(FileInfo("f1", "файл.txt", 42))
    }

    @Test fun helloDefaults() {
        val h = BridgeJson.decodeFromString<Hello>("""{"pcId":"a","name":"b"}""")
        assertEquals("bridge", h.app)
        assertEquals(1, h.version)
    }

    @Test fun unknownKeysAreIgnored() {
        assertEquals(ClipText("x"), BridgeJson.decodeFromString<ClipText>("""{"text":"x","extra":1}"""))
    }

    @Test(expected = SerializationException::class)
    fun unknownInputEventTypeThrows() {
        BridgeJson.decodeFromString<InputEvent>("""{"type":"Nope"}""")
    }

    @Test(expected = SerializationException::class)
    fun unknownServerEventTypeThrows() {
        BridgeJson.decodeFromString<ServerEvent>("""{"type":"Nope"}""")
    }
}
