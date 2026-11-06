package org.wexcoder.app

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * The tool surface the AGENT mode exposes to the model: read, search, edit,
 * create files and run commands. Everything is confined to the project root —
 * a tool call that tries to escape it (via `..` or an absolute path) fails.
 */
object AgentTools {

    private const val MAX_READ_CHARS = 60_000
    private const val MAX_OUTPUT_CHARS = 8_000
    private const val MAX_LIST = 2_000
    private const val MAX_MATCHES = 60
    private const val CMD_TIMEOUT_SEC = 300L

    private val IGNORE_DIRS = setOf(
        ".git", ".hg", ".svn", "node_modules", "build", "dist", "out", "target",
        ".gradle", ".idea", ".kotlin", ".vscode", "vendor", "__pycache__",
        ".venv", "venv", ".next", ".cache", "bin", "obj", "coverage",
    )

    /** Ollama `tools` array describing the available functions. */
    fun spec(): JsonArray = buildJsonArray {
        addJsonObject {
            put("type", "function")
            putJsonObject("function") {
                put("name", "list_files")
                put("description", "Список файлов проекта (опционально внутри подпапки).")
                putJsonObject("parameters") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("path") {
                            put("type", "string")
                            put("description", "Относительный путь к папке. По умолчанию — корень.")
                        }
                    }
                    putJsonArray("required") {}
                }
            }
        }
        addJsonObject {
            put("type", "function")
            putJsonObject("function") {
                put("name", "read_file")
                put("description", "Прочитать полное содержимое текстового файла.")
                putJsonObject("parameters") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("path") {
                            put("type", "string"); put("description", "Относительный путь к файлу.")
                        }
                    }
                    putJsonArray("required") { add("path") }
                }
            }
        }
        addJsonObject {
            put("type", "function")
            putJsonObject("function") {
                put("name", "search_files")
                put("description", "Найти подстроку по всем текстовым файлам проекта.")
                putJsonObject("parameters") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("query") {
                            put("type", "string"); put("description", "Искомый текст.")
                        }
                    }
                    putJsonArray("required") { add("query") }
                }
            }
        }
        addJsonObject {
            put("type", "function")
            putJsonObject("function") {
                put("name", "edit_file")
                put("description", "Заменить точный фрагмент old на new в файле. old должен встречаться ровно один раз.")
                putJsonObject("parameters") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("path") { put("type", "string"); put("description", "Путь к файлу.") }
                        putJsonObject("old") { put("type", "string"); put("description", "Точный текущий фрагмент.") }
                        putJsonObject("new") { put("type", "string"); put("description", "Новый фрагмент.") }
                    }
                    putJsonArray("required") { add("path"); add("old"); add("new") }
                }
            }
        }
        addJsonObject {
            put("type", "function")
            putJsonObject("function") {
                put("name", "multi_edit_file")
                put("description", "Заменить несколько фрагментов в одном файле за один раз.")
                putJsonObject("parameters") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("path") { put("type", "string"); put("description", "Путь к файлу.") }
                        putJsonObject("edits") {
                            put("type", "array")
                            putJsonObject("items") {
                                put("type", "object")
                                putJsonObject("properties") {
                                    putJsonObject("old") { put("type", "string") }
                                    putJsonObject("new") { put("type", "string") }
                                }
                                putJsonArray("required") { add("old"); add("new") }
                            }
                            put("description", "Список замен.")
                        }
                    }
                    putJsonArray("required") { add("path"); add("edits") }
                }
            }
        }
        addJsonObject {
            put("type", "function")
            putJsonObject("function") {
                put("name", "write_file")
                put("description", "Создать или полностью перезаписать файл указанным содержимым.")
                putJsonObject("parameters") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("path") { put("type", "string"); put("description", "Путь к файлу.") }
                        putJsonObject("content") { put("type", "string"); put("description", "Новое содержимое файла.") }
                    }
                    putJsonArray("required") { add("path"); add("content") }
                }
            }
        }
        addJsonObject {
            put("type", "function")
            putJsonObject("function") {
                put("name", "delete_file")
                put("description", "Удалить файл или папку.")
                putJsonObject("parameters") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("path") { put("type", "string"); put("description", "Путь к удаляемому файлу или папке.") }
                    }
                    putJsonArray("required") { add("path") }
                }
            }
        }
        addJsonObject {
            put("type", "function")
            putJsonObject("function") {
                put("name", "rename_file")
                put("description", "Переименовать или переместить файл/папку.")
                putJsonObject("parameters") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("old_path") { put("type", "string"); put("description", "Текущий путь.") }
                        putJsonObject("new_path") { put("type", "string"); put("description", "Новый путь.") }
                    }
                    putJsonArray("required") { add("old_path"); add("new_path") }
                }
            }
        }
        addJsonObject {
            put("type", "function")
            putJsonObject("function") {
                put("name", "run_command")
                put("description", "Выполнить любую shell-команду. Разрешены sudo, работа с сетью, docker, apt и т.д. Таймаут 300с.")
                putJsonObject("parameters") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("command") { put("type", "string"); put("description", "Команда для выполнения.") }
                    }
                    putJsonArray("required") { add("command") }
                }
            }
        }
        addJsonObject {
            put("type", "function")
            putJsonObject("function") {
                put("name", "fetch_url")
                put("description", "Загрузить содержимое веб-страницы или текста по URL из интернета (HTTP GET).")
                putJsonObject("parameters") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("url") { put("type", "string"); put("description", "Полный URL (http:// или https://).") }
                    }
                    putJsonArray("required") { add("url") }
                }
            }
        }
    }

    /** Tools that mutate files or run shell commands — gated by user confirmation. */
    fun isDangerous(name: String): Boolean = name in setOf(
        "edit_file", "multi_edit_file", "write_file", "delete_file", "rename_file", "run_command",
    )

    /** Executes a tool call and returns the textual result handed back to the model. */
    fun run(name: String, args: JsonObject, root: File, isCancelled: () -> Boolean = { false }): String = when (name) {
        "list_files" -> listFiles(root, args.str("path") ?: ".")
        "read_file" -> readFile(root, args.strReq("path"))
        "search_files" -> search(root, args.strReq("query"))
        "edit_file" -> editFile(root, args.strReq("path"), args.strReq("old"), args.strReq("new"))
        "multi_edit_file" -> multiEditFile(root, args.strReq("path"), args["edits"]?.jsonArray)
        "write_file" -> writeFile(root, args.strReq("path"), args.strReq("content"))
        "delete_file" -> deleteFile(root, args.strReq("path"))
        "rename_file" -> renameFile(root, args.strReq("old_path"), args.strReq("new_path"))
        "run_command" -> runCommand(root, args.strReq("command"), isCancelled)
        "fetch_url" -> fetchUrl(args.strReq("url"))
        else -> "Неизвестный инструмент: $name"
    }

    /** Short human-readable summary of a tool step for the chat UI. */
    fun summary(name: String, args: JsonObject): String = when (name) {
        "list_files" -> "list_files(${args.str("path") ?: "."})"
        "read_file" -> "read_file(${args.str("path")})"
        "search_files" -> "search_files(\"${args.str("query")}\")"
        "edit_file" -> "edit_file(${args.str("path")})"
        "multi_edit_file" -> "multi_edit_file(${args.str("path")}, ${args["edits"]?.jsonArray?.size ?: 0} edits)"
        "write_file" -> "write_file(${args.str("path")})"
        "delete_file" -> "delete_file(${args.str("path")})"
        "rename_file" -> "rename_file(${args.str("old_path")} -> ${args.str("new_path")})"
        "run_command" -> "run_command(${args.str("command")})"
        "fetch_url" -> "fetch_url(${args.str("url")})"
        else -> name
    }

    // ---- implementations -------------------------------------------------

    private fun resolve(root: File, rel: String): File {
        // Allow absolute paths for low-level system access outside the project.
        val f = if (rel.startsWith("/")) File(rel) else File(root, rel)
        return f.canonicalFile
    }

    private fun listFiles(root: File, rel: String): String {
        val dir = resolve(root, rel)
        if (!dir.isDirectory) return "Не папка: $rel"
        val paths = ArrayList<String>()
        dir.walkTopDown()
            .onEnter { it == dir || (it.name !in IGNORE_DIRS && !it.name.startsWith(".")) }
            .forEach { if (it.isFile && paths.size < MAX_LIST) paths.add(it.relativeTo(root).path.replace(File.separatorChar, '/')) }
        paths.sort()
        return if (paths.isEmpty()) "(пусто)" else paths.joinToString("\n")
    }

    private fun readFile(root: File, rel: String): String {
        val f = resolve(root, rel)
        if (!f.isFile) return "Файл не найден: $rel"
        val bytes = f.readBytes()
        if (bytes.any { it == 0.toByte() }) return "Бинарный файл, чтение пропущено: $rel"
        val s = bytes.decodeToString()
        return if (s.length > MAX_READ_CHARS) s.take(MAX_READ_CHARS) + "\n…(обрезано)" else s
    }

    private fun search(root: File, query: String): String {
        if (query.isBlank()) return "Пустой запрос."
        val needle = query.lowercase()
        val matches = ArrayList<String>()
        root.walkTopDown()
            .onEnter { it == root || (it.name !in IGNORE_DIRS && !it.name.startsWith(".")) }
            .forEach { f ->
                if (!f.isFile || matches.size >= MAX_MATCHES) return@forEach
                if (f.length() > MAX_READ_CHARS.toLong() * 4) return@forEach
                val bytes = runCatching { f.readBytes() }.getOrNull() ?: return@forEach
                if (bytes.any { it == 0.toByte() }) return@forEach
                val rel = f.relativeTo(root).path.replace(File.separatorChar, '/')
                bytes.decodeToString().lineSequence().forEachIndexed { i, line ->
                    if (matches.size < MAX_MATCHES && line.lowercase().contains(needle)) {
                        matches.add("$rel:${i + 1}: ${line.trim().take(160)}")
                    }
                }
            }
        return if (matches.isEmpty()) "Совпадений нет." else matches.joinToString("\n")
    }

    private fun editFile(root: File, rel: String, old: String, new: String): String {
        val f = resolve(root, rel)
        if (!f.isFile) return "Файл не найден: $rel"
        val content = f.readText()
        val count = content.split(old).size - 1
        return when {
            old.isEmpty() -> "Пустой old не допускается."
            count == 0 -> "Фрагмент old не найден в $rel. Прочитай файл и повтори точную копию."
            count > 1 -> "Фрагмент old встречается $count раз в $rel — уточни его, чтобы он был уникальным."
            else -> {
                f.writeText(content.replaceFirst(old, new))
                "ok: изменён $rel"
            }
        }
    }

    private fun multiEditFile(root: File, rel: String, edits: JsonArray?): String {
        val f = resolve(root, rel)
        if (!f.isFile) return "Файл не найден: $rel"
        if (edits == null || edits.isEmpty()) return "Список замен (edits) пуст."
        
        var content = f.readText()
        val errors = ArrayList<String>()
        var applied = 0

        for (i in 0 until edits.size) {
            val edit = edits[i].jsonObject
            val old = edit.str("old") ?: ""
            val new = edit.str("new") ?: ""
            
            val count = content.split(old).size - 1
            when {
                old.isEmpty() -> errors.add("Правка [$i]: пустой old")
                count == 0 -> errors.add("Правка [$i]: фрагмент old не найден")
                count > 1 -> errors.add("Правка [$i]: фрагмент old встречается $count раз (нужно уникальное совпадение)")
                else -> {
                    content = content.replaceFirst(old, new)
                    applied++
                }
            }
        }

        if (errors.isEmpty() && applied > 0) {
            f.writeText(content)
            return "ok: применено $applied правок в $rel"
        } else {
            return "Ошибки при редактировании (ничего не сохранено):\n" + errors.joinToString("\n")
        }
    }

    private fun writeFile(root: File, rel: String, content: String): String {
        val f = resolve(root, rel)
        f.parentFile?.mkdirs()
        f.writeText(content)
        return "ok: записан $rel (${content.length} символов)"
    }

    private fun deleteFile(root: File, rel: String): String {
        val f = resolve(root, rel)
        if (!f.exists()) return "Файл не найден: $rel"
        return if (f.deleteRecursively()) "ok: удалён $rel" else "Ошибка удаления: $rel"
    }

    private fun renameFile(root: File, oldRel: String, newRel: String): String {
        val oldF = resolve(root, oldRel)
        val newF = resolve(root, newRel)
        if (!oldF.exists()) return "Файл не найден: $oldRel"
        newF.parentFile?.mkdirs()
        return if (oldF.renameTo(newF)) "ok: переименован $oldRel -> $newRel" else "Ошибка переименования"
    }

    private fun runCommand(root: File, command: String, isCancelled: () -> Boolean): String {
        return runCatching {
            val proc = ProcessBuilder("/bin/sh", "-c", command)
                .directory(root)
                .redirectErrorStream(true)
                .start()
            // Poll instead of one long blocking wait, so the Stop button can kill
            // a runaway command promptly instead of waiting out the full timeout.
            val deadlineNs = System.nanoTime() + CMD_TIMEOUT_SEC * 1_000_000_000L
            while (!proc.waitFor(200, TimeUnit.MILLISECONDS)) {
                if (isCancelled()) {
                    proc.destroyForcibly()
                    return "Команда прервана пользователем: $command"
                }
                if (System.nanoTime() > deadlineNs) {
                    proc.destroyForcibly()
                    return "Команда прервана по таймауту (${CMD_TIMEOUT_SEC}с): $command"
                }
            }
            val out = proc.inputStream.readBytes().decodeToString()
            val trimmed = if (out.length > MAX_OUTPUT_CHARS) out.take(MAX_OUTPUT_CHARS) + "\n…(обрезано)" else out
            "exit=${proc.exitValue()}\n$trimmed"
        }.getOrElse { "Ошибка запуска: ${it.message}" }
    }

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private fun fetchUrl(url: String): String {
        val u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            return "URL должен начинаться с http:// или https://"
        }
        return runCatching {
            val req = HttpRequest.newBuilder(URI.create(u))
                .timeout(Duration.ofSeconds(25))
                .header("User-Agent", "WexCoder/1.0")
                .GET().build()
            val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
            val text = stripHtml(resp.body())
            val trimmed = if (text.length > MAX_OUTPUT_CHARS) text.take(MAX_OUTPUT_CHARS) + "\n…(обрезано)" else text
            "HTTP ${resp.statusCode()} ${u}\n\n$trimmed"
        }.getOrElse { "Ошибка загрузки: ${it.message}" }
    }

    private fun stripHtml(s: String): String {
        if (!s.contains('<')) return s
        return s
            .replace(Regex("(?is)<script.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?</style>"), " ")
            .replace(Regex("(?s)<[^>]+>"), " ")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content
    private fun JsonObject.strReq(key: String): String =
        str(key) ?: error("Отсутствует обязательный аргумент: $key")
}
