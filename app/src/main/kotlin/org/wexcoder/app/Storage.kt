package org.wexcoder.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * App settings persisted alongside projects. Window size is stored in
 * density-independent units (dp) so it restores correctly across DPI changes —
 * handy when niri hands the window very different geometries between monitors.
 */
@Serializable
data class Settings(
    val windowWidth: Float = 1100f,
    val windowHeight: Float = 780f,
    val lastModel: String? = null,
    val ollamaHost: String? = null,
    val contextSize: Int? = null,
    val repeatPenalty: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    /** When false, the agent asks before every file-mutating / shell tool call. */
    val autoApproveTools: Boolean = false,
)

/** Persists projects and settings to ~/.config/wexcoder/. */
object Storage {
    private val dir = File(System.getProperty("user.home"), ".config/wexcoder")
    private val projectsFile = File(dir, "projects.json")
    private val settingsFile = File(dir, "settings.json")
    private val chatsFile = File(dir, "chats.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun loadProjects(): List<Project> = runCatching {
        if (projectsFile.exists()) json.decodeFromString<List<Project>>(projectsFile.readText())
        else emptyList()
    }.getOrDefault(emptyList())

    fun saveProjects(projects: List<Project>) {
        runCatching {
            dir.mkdirs()
            projectsFile.writeText(json.encodeToString(projects))
        }
    }

    fun loadSettings(): Settings = runCatching {
        if (settingsFile.exists()) json.decodeFromString<Settings>(settingsFile.readText())
        else Settings()
    }.getOrDefault(Settings())

    fun saveSettings(settings: Settings) {
        runCatching {
            dir.mkdirs()
            settingsFile.writeText(json.encodeToString(settings))
        }
    }

    fun loadChats(): List<PersistedChat> = runCatching {
        if (chatsFile.exists()) json.decodeFromString<List<PersistedChat>>(chatsFile.readText())
        else emptyList()
    }.getOrDefault(emptyList())

    fun saveChats(chats: List<PersistedChat>) {
        runCatching {
            dir.mkdirs()
            chatsFile.writeText(json.encodeToString(chats))
        }
    }
}
