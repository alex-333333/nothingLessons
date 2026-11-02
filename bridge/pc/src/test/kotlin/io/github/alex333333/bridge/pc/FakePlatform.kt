package io.github.alex333333.bridge.pc

import io.github.alex333333.bridge.protocol.Key
import io.github.alex333333.bridge.protocol.MediaAction
import io.github.alex333333.bridge.protocol.MouseButton
import io.github.alex333333.bridge.protocol.PowerAction
import java.util.concurrent.CopyOnWriteArrayList

/** Records every call instead of touching the real keyboard, mouse, clipboard or power state. */
class FakePlatform : PlatformHooks {
    val calls = CopyOnWriteArrayList<String>()
    val notifications = CopyOnWriteArrayList<String>()
    @Volatile var clipboard = ""

    override fun mouseMove(dx: Int, dy: Int) { calls += "mouseMove($dx,$dy)" }
    override fun click(button: MouseButton) { calls += "click($button)" }
    override fun scroll(dy: Int) { calls += "scroll($dy)" }
    override fun typeText(text: String) { calls += "typeText($text)" }
    override fun pressKey(key: Key) { calls += "pressKey($key)" }
    override fun media(action: MediaAction) { calls += "media($action)" }
    override fun power(action: PowerAction) { calls += "power($action)" }
    override fun read(): String = clipboard
    override fun write(text: String) { clipboard = text }
    override fun notify(text: String) { notifications += text }
}
