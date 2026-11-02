package io.github.alex333333.bridge.pc

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Font
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.WindowConstants
import javax.swing.border.EmptyBorder

/** What the PC's user can do from the tray menu (or from the plain window when there is no tray). */
class UiActions(
    /** Makes a fresh one-shot pairing code every time. */
    val newCode: () -> PairingCode,
    val sendToPhone: (Path) -> Unit,
    val downloadsDir: Path,
    val unpairAll: () -> Unit,
    val exit: () -> Unit,
)

/** The PC program's whole user interface; the wiring lives in Main.kt. */
interface Ui {
    fun show(actions: UiActions)
    /** A short message, e.g. "Телефон подключён". */
    fun notify(text: String)
    /** A problem the user must see; the program exits afterwards. Works before [show]. */
    fun error(text: String)
    fun dispose()

    companion object {
        fun create(): Ui = if (GraphicsEnvironment.isHeadless()) ConsoleUi() else SwingUi()
    }
}

private object Texts {
    const val TITLE = "Мост"
    const val SHOW_CODE = "Показать код привязки"
    const val SEND_FILE = "Отправить файл на телефон…"
    const val OPEN_FOLDER = "Открыть папку Мост"
    const val UNPAIR = "Отвязать все телефоны"
    const val EXIT = "Выход"
}

/** No display at all (development on a server, CI): print what the dialogs would say. */
internal class ConsoleUi : Ui {
    override fun show(actions: UiActions) {
        val code = actions.newCode()
        println("${Texts.TITLE}: код привязки ${formatCode(code.code)}, отпечаток ${code.fingerprintShort}. Нет экрана, меню недоступно; Ctrl+C для выхода.")
        Runtime.getRuntime().addShutdownHook(Thread { actions.exit() })
    }

    override fun notify(text: String) = println("${Texts.TITLE}: $text")
    override fun error(text: String) = System.err.println("${Texts.TITLE}: $text")
    override fun dispose() {}
}

internal fun formatCode(code: String): String = if (code.length == 6) "${code.take(3)} ${code.drop(3)}" else code

internal class SwingUi : Ui {
    private var trayIcon: TrayIcon? = null
    private var statusLabel: JLabel? = null
    private var window: JFrame? = null

    init {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (_: Exception) {
            // The default look is fine.
        }
    }

    override fun show(actions: UiActions) {
        SwingUtilities.invokeLater {
            if (SystemTray.isSupported()) installTray(actions) else installWindow(actions)
        }
    }

    override fun notify(text: String) {
        SwingUtilities.invokeLater {
            trayIcon?.displayMessage(Texts.TITLE, text, TrayIcon.MessageType.INFO)
            statusLabel?.text = text
        }
    }

    override fun error(text: String) {
        JOptionPane.showMessageDialog(null, text, Texts.TITLE, JOptionPane.ERROR_MESSAGE)
    }

    /** Waits for the removal: the process exits right after, and a tray icon left behind lingers on Windows. */
    override fun dispose() {
        val remove = Runnable {
            trayIcon?.let { SystemTray.getSystemTray().remove(it) }
            window?.dispose()
        }
        try {
            if (SwingUtilities.isEventDispatchThread()) remove.run() else SwingUtilities.invokeAndWait(remove)
        } catch (_: Exception) {
            // Exiting anyway.
        }
    }

    // ---- the two shells around the same five actions --------------------------------------------------------------

    private fun installTray(actions: UiActions) {
        val menu = PopupMenu()
        fun item(label: String, run: () -> Unit) = menu.add(MenuItem(label).also { it.addActionListener { run() } })
        item(Texts.SHOW_CODE) { showCode(actions) }
        item(Texts.SEND_FILE) { sendFile(actions) }
        item(Texts.OPEN_FOLDER) { openFolder(actions) }
        item(Texts.UNPAIR) { unpair(actions) }
        menu.addSeparator()
        item(Texts.EXIT) { actions.exit() }
        val icon = TrayIcon(drawIcon(32), Texts.TITLE, menu).also { it.isImageAutoSize = true }
        icon.addActionListener { showCode(actions) } // double click
        trayIcon = icon
        SystemTray.getSystemTray().add(icon)
    }

    private fun installWindow(actions: UiActions) {
        val frame = JFrame(Texts.TITLE)
        frame.iconImage = drawIcon(32)
        val panel = JPanel().also { it.layout = BoxLayout(it, BoxLayout.Y_AXIS); it.border = EmptyBorder(12, 16, 12, 16) }
        fun button(label: String, run: () -> Unit) {
            val b = JButton(label)
            b.alignmentX = 0.5f
            b.maximumSize = java.awt.Dimension(Int.MAX_VALUE, b.preferredSize.height)
            b.addActionListener { run() }
            panel.add(b)
            panel.add(javax.swing.Box.createVerticalStrut(6))
        }
        button(Texts.SHOW_CODE) { showCode(actions) }
        button(Texts.SEND_FILE) { sendFile(actions) }
        button(Texts.OPEN_FOLDER) { openFolder(actions) }
        button(Texts.UNPAIR) { unpair(actions) }
        button(Texts.EXIT) { actions.exit() }
        val status = JLabel(" ")
        statusLabel = status
        frame.add(panel, BorderLayout.CENTER)
        frame.add(status.also { it.border = EmptyBorder(0, 16, 8, 16) }, BorderLayout.SOUTH)
        frame.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        frame.addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosing(e: java.awt.event.WindowEvent) = actions.exit()
        })
        frame.pack()
        frame.setLocationRelativeTo(null)
        window = frame
        frame.isVisible = true
    }

    // ---- actions ------------------------------------------------------------------------------------------------------

    private fun showCode(actions: UiActions) {
        val code = actions.newCode()
        val digits = JLabel(formatCode(code.code)).also { it.font = it.font.deriveFont(Font.BOLD, 40f); it.alignmentX = 0.5f }
        val panel = JPanel().also { it.layout = BoxLayout(it, BoxLayout.Y_AXIS) }
        panel.add(JLabel("Введите этот код в приложении на телефоне:").also { it.alignmentX = 0.5f })
        panel.add(digits)
        panel.add(JLabel("Отпечаток сертификата: ${code.fingerprintShort}").also { it.alignmentX = 0.5f })
        panel.add(JLabel("Сверьте это с экраном телефона").also { it.alignmentX = 0.5f })
        JOptionPane.showMessageDialog(null, panel, Texts.SHOW_CODE, JOptionPane.PLAIN_MESSAGE)
    }

    private fun sendFile(actions: UiActions) {
        val dialog = FileDialog(null as Frame?, "Отправить файл на телефон", FileDialog.LOAD)
        dialog.isVisible = true
        val name = dialog.file ?: return
        val file = java.nio.file.Paths.get(dialog.directory ?: "", name)
        try {
            actions.sendToPhone(file)
            notify("Файл «$name» ждёт на телефоне")
        } catch (e: Exception) {
            error("Не удалось отправить файл: ${e.message ?: e}")
        }
    }

    private fun openFolder(actions: UiActions) {
        try {
            check(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) { "Проводник недоступен" }
            Desktop.getDesktop().open(actions.downloadsDir.toFile())
        } catch (e: Exception) {
            error("Не удалось открыть папку ${actions.downloadsDir}: ${e.message ?: e}")
        }
    }

    private fun unpair(actions: UiActions) {
        val answer = JOptionPane.showConfirmDialog(
            null,
            "Все телефоны придётся привязать заново. Отвязать?",
            Texts.UNPAIR,
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE,
        )
        if (answer == JOptionPane.YES_OPTION) actions.unpairAll()
    }
}

/** A dot-matrix "М" on a blue disc, drawn in code so that no resource file is needed. */
internal fun drawIcon(size: Int): Image {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0x1F, 0x5F, 0xBF)
        g.fillOval(0, 0, size, size)
        g.color = Color.WHITE
        val rows = arrayOf("X...X", "XX.XX", "X.X.X", "X...X", "X...X")
        val cell = size / 7.0
        val dot = cell * 0.8
        val origin = cell * 1.0 + (size - cell * 7) / 2
        for ((r, row) in rows.withIndex()) for ((c, ch) in row.withIndex()) {
            if (ch == 'X') g.fill(java.awt.geom.Ellipse2D.Double(origin + c * cell + (cell - dot) / 2, origin + r * cell + (cell - dot) / 2, dot, dot))
        }
    } finally {
        g.dispose()
    }
    return image
}
