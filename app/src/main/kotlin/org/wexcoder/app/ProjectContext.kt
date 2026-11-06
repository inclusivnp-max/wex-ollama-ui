package org.wexcoder.app

import java.io.File

/**
 * Builds a compact, text-only digest of a project directory so the model can
 * reason about the whole codebase: a file listing plus as many file contents as
 * fit into a character budget. VCS / build / dependency dirs and binary files
 * are skipped. All work is blocking — call it off the UI thread.
 */
object ProjectContext {

    private val IGNORE_DIRS = setOf(
        ".git", ".hg", ".svn", "node_modules", "build", "dist", "out", "target",
        ".gradle", ".idea", ".kotlin", ".vscode", "vendor", "__pycache__",
        ".venv", "venv", ".next", ".nuxt", ".cache", "bin", "obj", "coverage",
        ".mvn", ".settings", "Pods", "DerivedData",
    )

    private val BINARY_EXT = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "svg", "pdf",
        "zip", "gz", "tar", "7z", "rar", "jar", "war", "class", "so", "dll",
        "dylib", "exe", "bin", "o", "a", "obj", "lib", "wasm",
        "mp3", "mp4", "mov", "avi", "mkv", "wav", "flac", "ogg",
        "ttf", "otf", "woff", "woff2", "eot",
        "db", "sqlite", "sqlite3", "lock", "keystore", "jks",
    )

    /** Per-file cap so one huge file can't eat the whole budget. */
    private const val MAX_FILE_CHARS = 48_000

    /** Safety cap on how many entries we walk, for pathological trees. */
    private const val MAX_FILES_SCANNED = 6_000

    /**
     * @return a digest string, or "" if [rootPath] is not a readable directory.
     */
    fun build(rootPath: String, charBudget: Int = 60_000): String {
        val root = File(rootPath)
        if (!root.isDirectory) return ""

        val files = ArrayList<File>()
        root.walkTopDown()
            .onEnter { dir -> dir == root || (dir.name !in IGNORE_DIRS && !dir.name.startsWith(".")) }
            .forEach { f ->
                if (files.size >= MAX_FILES_SCANNED) return@forEach
                if (f.isFile) files.add(f)
            }
        files.sortBy { it.relativeTo(root).path }

        val rel = { f: File -> f.relativeTo(root).path.replace(File.separatorChar, '/') }

        val sb = StringBuilder()
        sb.append("PROJECT: ").append(root.name).append(" (").append(root.absolutePath).append(")\n")
        sb.append("FILES (").append(files.size).append("):\n")
        files.forEach { sb.append(rel(it)).append('\n') }
        sb.append("\nFILE CONTENTS:\n")

        var used = sb.length
        var includedFiles = 0
        var skipped = 0
        for (f in files) {
            val ext = f.extension.lowercase()
            if (ext in BINARY_EXT) { skipped++; continue }
            val text = readTextSafe(f) ?: run { skipped++; continue }

            val header = "\n===== ${rel(f)} =====\n"
            if (used + header.length + text.length > charBudget && includedFiles > 0) {
                skipped++
                continue
            }
            sb.append(header).append(text).append('\n')
            used += header.length + text.length + 1
            includedFiles++
            if (used >= charBudget) { skipped = files.size - includedFiles; break }
        }

        if (skipped > 0) {
            sb.append("\n[").append(skipped)
                .append(" файл(ов) не показано из-за лимита размера или бинарного типа]\n")
        }
        return sb.toString()
    }

    /** A cheap file-listing (paths only) — used to orient the agent. */
    fun fileTree(rootPath: String, limit: Int = 1_500): String {
        val root = File(rootPath)
        if (!root.isDirectory) return ""
        val paths = ArrayList<String>()
        root.walkTopDown()
            .onEnter { it == root || (it.name !in IGNORE_DIRS && !it.name.startsWith(".")) }
            .forEach { if (it.isFile && paths.size < limit) paths.add(it.relativeTo(root).path.replace(File.separatorChar, '/')) }
        paths.sort()
        return paths.joinToString("\n")
    }

    /** Reads a text file, returning null for binary or oversized content. */
    private fun readTextSafe(f: File): String? = runCatching {
        if (f.length() > MAX_FILE_CHARS.toLong() * 4) return@runCatching null
        val bytes = f.readBytes()
        if (bytes.any { it == 0.toByte() }) return@runCatching null   // NUL ⇒ binary
        val s = bytes.decodeToString()
        if (s.length > MAX_FILE_CHARS) s.take(MAX_FILE_CHARS) + "\n…(файл обрезан)" else s
    }.getOrNull()
}
