package io.github.alex333333.bridge.pc

import io.github.alex333333.bridge.protocol.Key
import io.github.alex333333.bridge.protocol.MediaAction
import io.github.alex333333.bridge.protocol.MouseButton
import io.github.alex333333.bridge.protocol.PowerAction

/** Everything the phone can do to the PC's keyboard, mouse, player and power state. */
interface InputDriver {
    fun mouseMove(dx: Int, dy: Int)
    fun click(button: MouseButton)
    /** Positive [dy] scrolls down. */
    fun scroll(dy: Int)
    fun typeText(text: String)
    fun pressKey(key: Key)
    fun media(action: MediaAction)
    fun power(action: PowerAction)
}

interface ClipboardAccess {
    fun read(): String
    fun write(text: String)
}

interface PlatformHooks : InputDriver, ClipboardAccess {
    /** A short pop-up message (tray balloon). */
    fun notify(text: String)
}
