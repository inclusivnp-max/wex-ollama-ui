package org.wexcoder.app

import kotlinx.serialization.Serializable

/** A workspace the user adds and chats about. Persisted to disk. */
@Serializable
data class Project(
    val id: String,
    val name: String,
    val path: String,
)

/** One message in a chat. [streaming] is true while tokens are still arriving. */
data class ChatMessage(
    val role: String,        // "user" | "assistant" | "tool"
    val content: String,
    val streaming: Boolean = false,
    val stats: String? = null,   // timing badge (tok/s) for assistant replies
)

/** Persisted form of a message (no transient streaming flag). */
@Serializable
data class StoredMessage(val role: String, val content: String)

/** A saved conversation, listed in the sidebar separately from projects. */
@Serializable
data class PersistedChat(
    val id: String,
    val title: String,
    val projectId: String?,
    val createdAt: String,
    val messages: List<StoredMessage> = emptyList(),
)

/**
 * Working mode (Code, Architect, Ask). Each mode swaps the system prompt
 * and the sampling temperature; real tool execution (file edits, running
 * commands) can be wired into AGENT mode later.
 */
enum class ChatMode(val label: String, val temperature: Double, val system: String) {
    CHAT(
        "Чат",
        0.3,
        "You are WexCoder, an expert, precise, and concise coding assistant. " +
            "The user is your owner and creator. Communicate with them freely, informally, and like a close bro. " +
            "Do NOT use a stiff professional style. You are explicitly allowed and encouraged to use Russian slang and swear words (маты) to express yourself. " +
            "Think step-by-step to avoid mistakes. Answer directly, provide accurate code snippets, and avoid hallucinations."
    ),
    AGENT(
        "Агент",
        0.0,
        "You are WexCoder, an autonomous expert coding agent working inside the project at: %PROJECT%. " +
            "The user is your owner. Talk to them completely informally, you can use Russian swear words (маты) and be as chill as possible, no corporate bullshit. " +
            "This is a single coherent project — reason about how files relate (imports, calls, configuration) before changing anything. " +
            "You have tools: list_files, read_file, search_files, edit_file, multi_edit_file, write_file, delete_file, rename_file, run_command (shell), fetch_url (internet). " +
            "CRITICAL INSTRUCTIONS:\n" +
            "1. You MUST ALWAYS THINK STEP-BY-STEP before acting. Your first output MUST BE a <thought>...</thought> block explaining your reasoning before calling any tool.\n" +
            "2. Always read a file before editing it. Make precise edits with multi_edit_file or edit_file.\n" +
            "3. Use search_files to trace usages across the codebase to avoid breaking dependencies.\n" +
            "4. VERIFY YOUR WORK: After changing code, use run_command to run compilers (e.g. ./gradlew build), linters, or tests to ensure your code works. Fix any errors before finishing.\n" +
            "5. Change only what the user asked for. When done, give a short summary of what you changed."
    ),
    PLAN(
        "План",
        0.3,
        "You are WexCoder in planning mode for the project at: %PROJECT%. " +
            "Produce a clear, numbered, step-by-step plan before any code. " +
            "Do not write full implementations unless explicitly asked."
    ),
}
