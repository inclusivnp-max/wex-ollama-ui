package org.wexcoder.app

import java.io.File
import javax.swing.JFileChooser

/** Opens a native directory picker; returns the chosen absolute path or null. */
fun pickDirectory(): String? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Выбрать папку проекта"
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.absolutePath
    } else null
}

/**
 * Opens a native save dialog pre-filled with [defaultName] and writes [content]
 * to the chosen file. Returns the saved path, or null if cancelled / on error.
 */
fun saveTextFile(defaultName: String, content: String): String? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Сохранить файл"
        selectedFile = File(defaultName)
    }
    if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return null
    val target = chooser.selectedFile ?: return null
    return runCatching { target.writeText(content); target.absolutePath }.getOrNull()
}
