package io.github.alex333333.bridge.pc

import io.github.alex333333.bridge.protocol.Key
import io.github.alex333333.bridge.protocol.MediaAction
import io.github.alex333333.bridge.protocol.MouseButton
import io.github.alex333333.bridge.protocol.PowerAction
import java.awt.MouseInfo
import java.awt.Robot
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.concurrent.TimeUnit

/**
 * The real Windows implementation: `java.awt.Robot` for mouse and keys, the AWT clipboard, PowerShell for media keys,
 * `rundll32` and `shutdown` for power. [onNotify] shows a tray balloon (wired up by the tray program).
 * Not unit-tested: it needs a Windows desktop.
 */
class WindowsPlatform(private val onNotify: (String) -> Unit = {}) : PlatformHooks {
    private val robot: Robot by lazy { Robot() }
    private val clipboard get() = Toolkit.getDefaultToolkit().systemClipboard
    private val typeLock = Any()

    override fun mouseMove(dx: Int, dy: Int) {
        val p = MouseInfo.getPointerInfo()?.location ?: return
        robot.mouseMove(p.x + dx, p.y + dy)
    }

    override fun click(button: MouseButton) {
        val mask = when (button) {
            MouseButton.Left -> InputEvent.BUTTON1_DOWN_MASK
            MouseButton.Right -> InputEvent.BUTTON3_DOWN_MASK
            MouseButton.Middle -> InputEvent.BUTTON2_DOWN_MASK
        }
        robot.mousePress(mask)
        robot.mouseRelease(mask)
    }

    override fun scroll(dy: Int) {
        robot.mouseWheel(dy)
    }

    /** Robot cannot type Cyrillic or emoji, so the text goes through the clipboard and Ctrl+V; the old text is put back. */
    override fun typeText(text: String) {
        synchronized(typeLock) {
            val previous = try {
                if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) clipboard.getData(DataFlavor.stringFlavor) as? String else null
            } catch (_: Exception) {
                null
            }
            clipboard.setContents(StringSelection(text), null)
            robot.keyPress(KeyEvent.VK_CONTROL)
            robot.keyPress(KeyEvent.VK_V)
            robot.keyRelease(KeyEvent.VK_V)
            robot.keyRelease(KeyEvent.VK_CONTROL)
            Thread.sleep(150) // let the target window read the clipboard before it is replaced
            if (previous != null) clipboard.setContents(StringSelection(previous), null)
        }
    }

    override fun pressKey(key: Key) {
        val code = when (key) {
            Key.Enter -> KeyEvent.VK_ENTER
            Key.Backspace -> KeyEvent.VK_BACK_SPACE
            Key.Escape -> KeyEvent.VK_ESCAPE
            Key.Tab -> KeyEvent.VK_TAB
            Key.Left -> KeyEvent.VK_LEFT
            Key.Right -> KeyEvent.VK_RIGHT
            Key.Up -> KeyEvent.VK_UP
            Key.Down -> KeyEvent.VK_DOWN
        }
        robot.keyPress(code)
        robot.keyRelease(code)
    }

    override fun media(action: MediaAction) {
        val code = when (action) {
            MediaAction.VolumeUp -> 175
            MediaAction.VolumeDown -> 174
            MediaAction.Mute -> 173
            MediaAction.PlayPause -> 179
            MediaAction.Next -> 176
            MediaAction.Previous -> 177
        }
        run("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", "(New-Object -ComObject WScript.Shell).SendKeys([char]$code)")
    }

    override fun power(action: PowerAction) {
        when (action) {
            PowerAction.Lock -> run("rundll32.exe", "user32.dll,LockWorkStation")
            PowerAction.Shutdown -> run("shutdown", "/s", "/t", "0")
        }
    }

    override fun read(): String = withClipboardRetry {
        if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) clipboard.getData(DataFlavor.stringFlavor) as? String ?: "" else ""
    }

    override fun write(text: String) {
        withClipboardRetry { clipboard.setContents(StringSelection(text), null) }
    }

    override fun notify(text: String) = onNotify(text)

    /** Another program may hold the clipboard for a moment; try a few times. */
    private fun <T> withClipboardRetry(block: () -> T): T {
        var last: IllegalStateException? = null
        repeat(5) {
            try {
                return block()
            } catch (e: IllegalStateException) {
                last = e
                Thread.sleep(50)
            }
        }
        throw last!!
    }

    private fun run(vararg command: String) {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        process.outputStream.close()
        if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly()
    }
}
