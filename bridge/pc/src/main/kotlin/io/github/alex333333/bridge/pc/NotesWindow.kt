package io.github.alex333333.bridge.pc

import io.github.alex333333.bridge.protocol.Note
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import javax.swing.border.EmptyBorder

private const val MAX_TITLE_CHARS = 80

/** The first non-blank line of a note, cut to a list-friendly length. */
internal fun noteTitle(text: String): String {
    val line = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return "(пусто)"
    val points = line.codePointCount(0, line.length)
    if (points <= MAX_TITLE_CHARS) return line
    return line.substring(0, line.offsetByCodePoints(0, MAX_TITLE_CHARS)) + "…"
}

internal fun newestFirst(notes: List<Note>): List<Note> = notes.sortedByDescending { it.updatedAt }

/** The PC's side of the shared notes: a list with add, edit, delete and copy. Reused (hidden, not disposed) between openings. */
internal class NotesWindow(private val actions: UiActions) {
    private val model = DefaultListModel<Note>()
    private val list = JList(model)
    private val frame = JFrame("Заметки")

    init {
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean,
            ) = super.getListCellRendererComponent(list, (value as? Note)?.let { noteTitle(it.text) }, index, isSelected, cellHasFocus)
        }

        val buttons = JPanel(FlowLayout(FlowLayout.CENTER, 8, 8))
        fun button(label: String, run: () -> Unit) = buttons.add(JButton(label).also { it.addActionListener { run() } })
        button("Добавить") { add() }
        button("Изменить") { edit() }
        button("Удалить") { delete() }
        button("Копировать") { copy() }

        frame.iconImage = drawIcon(32)
        frame.contentPane.add(JScrollPane(list).also { it.border = EmptyBorder(8, 8, 0, 8) }, BorderLayout.CENTER)
        frame.contentPane.add(buttons, BorderLayout.SOUTH)
        frame.defaultCloseOperation = WindowConstants.HIDE_ON_CLOSE
        frame.preferredSize = Dimension(460, 360)
        // Changes made on the phone while the window is open, and anything missed while it was hidden.
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowActivated(e: WindowEvent) = refresh()
        })
        actions.onNotesChanged { SwingUtilities.invokeLater { refresh() } }
        frame.pack()
        frame.setLocationRelativeTo(null)
    }

    fun dispose() = frame.dispose()

    fun open() {
        refresh()
        frame.isVisible = true
        frame.state = JFrame.NORMAL
        frame.toFront()
    }

    private fun refresh() {
        val selected = list.selectedValue?.id
        model.clear()
        newestFirst(actions.listNotes()).forEach { model.addElement(it) }
        selected?.let { id -> (0 until model.size()).firstOrNull { model[it].id == id }?.let(list::setSelectedIndex) }
    }

    private fun add() {
        val text = editText("Новая заметка", "") ?: return
        if (text.isNotBlank()) actions.saveNote(null, text)
        refresh()
    }

    private fun edit() {
        val note = list.selectedValue ?: return
        val text = editText("Изменить заметку", note.text) ?: return
        if (text.isNotBlank() && text != note.text) actions.saveNote(note.id, text)
        refresh()
    }

    private fun delete() {
        val note = list.selectedValue ?: return
        val answer = JOptionPane.showConfirmDialog(
            frame, "Удалить заметку «${noteTitle(note.text)}»?", "Заметки", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
        )
        if (answer == JOptionPane.YES_OPTION) actions.deleteNote(note.id)
        refresh()
    }

    private fun copy() {
        val note = list.selectedValue ?: return
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(note.text), null)
    }

    /** A modal text-area dialog; null if cancelled. */
    private fun editText(title: String, initial: String): String? {
        val area = JTextArea(initial, 12, 40).also { it.lineWrap = true; it.wrapStyleWord = true; it.caretPosition = 0 }
        val answer = JOptionPane.showConfirmDialog(
            frame, JScrollPane(area), title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE,
        )
        return if (answer == JOptionPane.OK_OPTION) area.text else null
    }
}
