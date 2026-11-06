package org.wexcoder.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.util.UUID

/** Default model — auto-switches to whatever is actually downloaded if absent. */
private const val DEFAULT_MODEL = "huihui_ai/qwen2.5-coder-abliterate:7b"

/** UI updates are coalesced to at most one per this many ms while streaming. */
private const val STREAM_FLUSH_MS = 50L

/** Hard cap on tool-call iterations in AGENT mode, so it can't loop forever. */
private const val MAX_AGENT_STEPS = 16

/** Holds all UI state and drives the Ollama calls. */
class AppState(private val scope: CoroutineScope) {

    private var settings = Storage.loadSettings()

    val projects: SnapshotStateList<Project> =
        mutableStateListOf<Project>().apply { addAll(Storage.loadProjects()) }

    var selectedProjectId by mutableStateOf(projects.firstOrNull()?.id)
    var mode by mutableStateOf(ChatMode.CHAT)
    var model by mutableStateOf(settings.lastModel ?: DEFAULT_MODEL)
    val availableModels: SnapshotStateList<String> = mutableStateListOf()
    var busy by mutableStateOf(false)
    var input by mutableStateOf("")

    /** When on, the whole project is digested into the system prompt. */
    var includeProjectContext by mutableStateOf(false)

    /** Connection health, surfaced in the UI. Updated by [refreshModels]. */
    var online by mutableStateOf(false)
    var ollamaHost by mutableStateOf(settings.ollamaHost ?: OllamaClient.base)
        private set
    var contextSize by mutableStateOf(settings.contextSize ?: 16384)
        private set
    var repeatPenalty by mutableStateOf(settings.repeatPenalty ?: 1.1)
        private set
    var topP by mutableStateOf(settings.topP ?: 0.9)
        private set
    var topK by mutableStateOf(settings.topK ?: 40)
        private set

    /** When on, dangerous agent tool calls run without asking. Off by default. */
    var autoApproveTools by mutableStateOf(settings.autoApproveTools)
    var showSettingsDialog by mutableStateOf(false)

    /** Set while the agent loop is blocked waiting for the user to approve a call. */
    var pendingApproval by mutableStateOf<PendingApproval?>(null)
        private set

    /** One pending "allow this tool call?" prompt; [result] is completed by the UI. */
    class PendingApproval(val title: String, val detail: String) {
        val result = kotlinx.coroutines.CompletableDeferred<Boolean>()
    }

    /** Called by the confirmation dialog to unblock the waiting agent loop. */
    fun resolveApproval(approved: Boolean) {
        pendingApproval?.result?.complete(approved)
        pendingApproval = null
    }

    // Cancellation flag for the in-flight generation (read on the IO thread).
    @Volatile private var cancelRequested = false

    // --- Chats (saved conversations), independent from projects -------------

    /** Sidebar list item for a saved chat; [title] is observable. */
    class ChatMeta(val id: String, title: String, val projectId: String?, val createdAt: String) {
        var title by mutableStateOf(title)
    }

    val chats: SnapshotStateList<ChatMeta> = mutableStateListOf()
    private val chatMessages = mutableStateMapOf<String, SnapshotStateList<ChatMessage>>()
    var selectedChatId by mutableStateOf<String?>(null)
        private set

    val selectedProject: Project? get() = projects.firstOrNull { it.id == selectedProjectId }

    val currentMessages: SnapshotStateList<ChatMessage>?
        get() = selectedChatId?.let { chatMessages[it] }

    val currentChatTitle: String?
        get() = selectedChatId?.let { id -> chats.firstOrNull { it.id == id }?.title }

    init {
        settings.ollamaHost?.let { OllamaClient.base = it }
        settings.contextSize?.let { OllamaClient.contextSize = it }
        settings.repeatPenalty?.let { OllamaClient.repeatPenalty = it }
        settings.topP?.let { OllamaClient.topP = it }
        settings.topK?.let { OllamaClient.topK = it }
        loadChats()
        refreshModels()
    }

    fun saveSettingsConfig(
        host: String,
        ctx: Int,
        repeatPenalty: Double,
        topP: Double,
        topK: Int,
        autoApproveTools: Boolean,
    ) {
        OllamaClient.base = host
        OllamaClient.contextSize = ctx
        OllamaClient.repeatPenalty = repeatPenalty
        OllamaClient.topP = topP
        OllamaClient.topK = topK
        ollamaHost = host
        contextSize = ctx
        this.repeatPenalty = repeatPenalty
        this.topP = topP
        this.topK = topK
        this.autoApproveTools = autoApproveTools
        settings = settings.copy(
            ollamaHost = host, contextSize = ctx,
            repeatPenalty = repeatPenalty, topP = topP, topK = topK,
            autoApproveTools = autoApproveTools,
        )
        Storage.saveSettings(settings)
        refreshModels()
    }

    private fun loadChats() {
        Storage.loadChats().forEach { pc ->
            chats.add(ChatMeta(pc.id, pc.title, pc.projectId, pc.createdAt))
            chatMessages[pc.id] = mutableStateListOf<ChatMessage>().apply {
                addAll(pc.messages.map { ChatMessage(it.role, it.content) })
            }
        }
        selectedChatId = chats.firstOrNull()?.id
        if (selectedChatId == null) newChat()
    }

    fun newChat() {
        val id = UUID.randomUUID().toString()
        chats.add(0, ChatMeta(id, "Новый чат", selectedProjectId, java.time.Instant.now().toString()))
        chatMessages[id] = mutableStateListOf()
        selectedChatId = id
        persistChats()
    }

    fun selectChat(id: String) {
        selectedChatId = id
        chats.firstOrNull { it.id == id }?.projectId?.let { selectedProjectId = it }
    }

    fun deleteChat(id: String) {
        chats.removeAll { it.id == id }
        chatMessages.remove(id)
        if (selectedChatId == id) selectedChatId = chats.firstOrNull()?.id
        if (chats.isEmpty()) newChat() else persistChats()
    }

    private fun currentChat(): ChatMeta? = selectedChatId?.let { id -> chats.firstOrNull { it.id == id } }

    private fun persistChats() {
        Storage.saveChats(
            chats.map { meta ->
                PersistedChat(
                    id = meta.id,
                    title = meta.title,
                    projectId = meta.projectId,
                    createdAt = meta.createdAt,
                    messages = (chatMessages[meta.id] ?: emptyList())
                        .filter { it.content.isNotBlank() }
                        .map { StoredMessage(it.role, it.content) },
                )
            }
        )
    }

    fun refreshModels() {
        scope.launch(Dispatchers.IO) {
            val up = OllamaClient.isUp()
            val list = if (up) OllamaClient.listModels() else emptyList()
            withContext(Dispatchers.Main) {
                online = up
                availableModels.clear()
                availableModels.addAll(list)
                if (model !in list && list.isNotEmpty()) {
                    model = list.firstOrNull { it.contains("coder") } ?: list.first()
                    persistSettings()
                }
            }
        }
    }

    fun chooseModel(name: String) {
        model = name
        persistSettings()
    }

    /** Called by the window when its size changes, so it restores next launch. */
    fun saveWindowSize(width: Float, height: Float) {
        if (width < 200f || height < 150f) return  // ignore minimised / bogus values
        if (kotlin.math.abs(width - settings.windowWidth) < 1f &&
            kotlin.math.abs(height - settings.windowHeight) < 1f
        ) return  // unchanged — skip redundant disk writes during resize
        settings = settings.copy(windowWidth = width, windowHeight = height)
        Storage.saveSettings(settings)
    }

    val initialWindowWidth: Float get() = settings.windowWidth
    val initialWindowHeight: Float get() = settings.windowHeight

    private fun persistSettings() {
        settings = settings.copy(lastModel = model)
        Storage.saveSettings(settings)
    }

    fun addProject(path: String) {
        val name = File(path).name.ifBlank { path }
        val project = Project(id = UUID.randomUUID().toString(), name = name, path = path)
        projects.add(project)
        Storage.saveProjects(projects.toList())
        selectedProjectId = project.id
    }

    fun removeProject(id: String) {
        projects.removeAll { it.id == id }
        Storage.saveProjects(projects.toList())
        if (selectedProjectId == id) selectedProjectId = projects.firstOrNull()?.id
    }

    /** Requests the current generation to stop as soon as the next chunk arrives. */
    fun stop() {
        if (busy) cancelRequested = true
        pendingApproval?.let { it.result.complete(false); pendingApproval = null }
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || busy) return

        val chatId = selectedChatId ?: run { newChat(); selectedChatId ?: return }
        val msgs = chatMessages.getOrPut(chatId) { mutableStateListOf() }

        // Name the chat after its first user message.
        currentChat()?.let { meta ->
            if (msgs.none { it.role == "user" }) {
                meta.title = text.lineSequence().firstOrNull().orEmpty().trim().take(40).ifBlank { "Чат" }
                persistChats()
            }
        }

        msgs.add(ChatMessage("user", text))
        input = ""
        busy = true
        cancelRequested = false

        val temperature = mode.temperature
        val chosenModel = model
        val projectPath = selectedProject?.path

        if (mode == ChatMode.AGENT && projectPath != null) {
            launchAgent(msgs, projectPath, chosenModel, temperature)
        } else {
            launchChat(msgs, projectPath, chosenModel, temperature)
        }
    }

    /** Plain streaming chat (CHAT / PLAN modes, or AGENT without a project). */
    private fun launchChat(
        msgs: SnapshotStateList<ChatMessage>,
        projectPath: String?,
        chosenModel: String,
        temperature: Double,
    ) {
        val baseSystem = mode.system.replace("%PROJECT%", projectPath ?: "")
        val attachContext = includeProjectContext && projectPath != null
        // Only user/assistant turns go to the model (skip tool-step bubbles).
        val historyForApi = msgs.filter {
            (it.role == "user" || it.role == "assistant") && it.content.isNotBlank()
        }.toList()

        val assistantIndex = msgs.size
        msgs.add(ChatMessage("assistant", "", streaming = true))

        scope.launch(Dispatchers.IO) {
            val system = if (attachContext) {
                // Keep the digest within the model's context window (roughly ~2 chars
                // per token of budget, leaving headroom for history + the reply).
                val budget = (contextSize * 2).coerceIn(8_000, 120_000)
                val ctx = ProjectContext.build(projectPath, charBudget = budget)
                if (ctx.isBlank()) baseSystem
                else baseSystem + "\n\n# Контекст проекта\n" +
                    "Ниже приведены файлы проекта — считай их источником истины. " +
                    "Когда предлагаешь изменения, указывай путь файла и показывай конкретный код.\n\n" + ctx
            } else baseSystem

            val sb = StringBuilder()
            var lastEmit = 0L
            var statBadge: String? = null
            try {
                OllamaClient.chatStream(
                    model = chosenModel,
                    system = system,
                    history = historyForApi,
                    temperature = temperature,
                    isCancelled = { cancelRequested },
                    onStats = { statBadge = it },
                ) { token ->
                    sb.append(token)
                    val now = System.currentTimeMillis()
                    if (now - lastEmit >= STREAM_FLUSH_MS) {
                        lastEmit = now
                        val snapshot = sb.toString()
                        scope.launch(Dispatchers.Main) {
                            if (assistantIndex < msgs.size) {
                                msgs[assistantIndex] = msgs[assistantIndex].copy(content = snapshot)
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    if (assistantIndex < msgs.size) {
                        val suffix = if (cancelRequested) "\n\n⏹ остановлено" else ""
                        msgs[assistantIndex] = msgs[assistantIndex]
                            .copy(content = sb.toString() + suffix, streaming = false, stats = statBadge)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    online = OllamaClient.isUp()
                    val partial = sb.toString()
                    val note = "⚠ Ошибка: ${e.message}\n\n" +
                        "Проверь, что сервис ollama запущен (${OllamaClient.base}) и модель скачана (`ollama list`)."
                    msgs[assistantIndex] = ChatMessage(
                        role = "assistant",
                        content = if (partial.isBlank()) note else "$partial\n\n$note",
                        streaming = false,
                    )
                }
            } finally {
                withContext(Dispatchers.Main) {
                    busy = false
                    cancelRequested = false
                    pendingApproval?.let { it.result.complete(false); pendingApproval = null }
                    persistChats()
                }
            }
        }
    }

    /** Autonomous tool-calling loop (AGENT mode): the model reads/edits files itself. */
    private fun launchAgent(
        msgs: SnapshotStateList<ChatMessage>,
        projectPath: String,
        chosenModel: String,
        temperature: Double,
    ) {
        val baseSystem = mode.system.replace("%PROJECT%", projectPath)
        val history = msgs.filter {
            (it.role == "user" || it.role == "assistant") && it.content.isNotBlank()
        }.map { it.role to it.content }

        scope.launch(Dispatchers.IO) {
            try {
                val root = File(projectPath)
                val system = baseSystem + "\n\n# Структура проекта\n" + ProjectContext.fileTree(projectPath)

                val convo = ArrayList<JsonObject>()
                convo.add(textMessage("system", system))
                history.forEach { convo.add(textMessage(it.first, it.second)) }

                val tools = AgentTools.spec()
                var steps = 0
                var finalText: String? = null

                while (steps < MAX_AGENT_STEPS && !cancelRequested) {
                    steps++
                    
                    val assistantIndex = msgs.size
                    withContext(Dispatchers.Main) { msgs.add(ChatMessage("assistant", "", streaming = true)) }

                    val sb = java.lang.StringBuilder()
                    var lastEmit = 0L

                    val assistant = OllamaClient.chatStreamAgent(
                        model = chosenModel,
                        messages = JsonArray(convo),
                        tools = tools,
                        temperature = temperature,
                        isCancelled = { cancelRequested }
                    ) { token ->
                        sb.append(token)
                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= STREAM_FLUSH_MS) {
                            lastEmit = now
                            val snapshot = sb.toString()
                            scope.launch(Dispatchers.Main) {
                                if (assistantIndex < msgs.size) {
                                    msgs[assistantIndex] = msgs[assistantIndex].copy(content = snapshot)
                                }
                            }
                        }
                    }
                    convo.add(assistant)

                    val statBadge = assistant["_stats"]?.jsonPrimitive?.content
                    withContext(Dispatchers.Main) {
                        if (assistantIndex < msgs.size) {
                            if (sb.isBlank()) {
                                // If the model made a tool call without any thoughts, drop the empty bubble
                                msgs.removeAt(assistantIndex)
                            } else {
                                msgs[assistantIndex] = msgs[assistantIndex].copy(content = sb.toString(), streaming = false, stats = statBadge)
                            }
                        }
                    }

                    val toolCalls = assistant["tool_calls"]?.jsonArray
                    if (toolCalls.isNullOrEmpty()) {
                        finalText = assistant["content"]?.jsonPrimitive?.content.orEmpty()
                        break
                    }

                    for (call in toolCalls) {
                        if (cancelRequested) break
                        val fn = call.jsonObject["function"]?.jsonObject ?: continue
                        val name = fn["name"]?.jsonPrimitive?.content ?: continue
                        val args = parseArgs(fn["arguments"])
                        val summary = AgentTools.summary(name, args)

                        // Human-in-the-loop: block on approval before any file/shell mutation.
                        if (!autoApproveTools && AgentTools.isDangerous(name)) {
                            val approval = PendingApproval("Разрешить операцию агента?", summary)
                            withContext(Dispatchers.Main) { pendingApproval = approval }
                            val approved = approval.result.await()
                            if (cancelRequested) break
                            if (!approved) {
                                withContext(Dispatchers.Main) { msgs.add(ChatMessage("tool", "🚫 Отклонено: $summary")) }
                                convo.add(toolResultMessage(name,
                                    "Пользователь отклонил эту операцию. Не повторяй её — предложи другой подход."))
                                continue
                            }
                        }

                        val result = runCatching { AgentTools.run(name, args, root) { cancelRequested } }
                            .getOrElse { "Ошибка инструмента: ${it.message}" }

                        val preview = "🔧 $summary\n${result.trim()}"
                            .let { if (it.length > 600) it.take(600) + "\n…" else it }
                        withContext(Dispatchers.Main) { msgs.add(ChatMessage("tool", preview)) }
                        convo.add(toolResultMessage(name, result))
                    }
                }

                val answer = when {
                    cancelRequested -> (finalText.orEmpty()).ifBlank { "—" } + "\n\n⏹ остановлено"
                    finalText != null -> finalText.ifBlank { "(агент завершил работу без текстового ответа)" }
                    else -> "(достигнут лимит шагов агента: $MAX_AGENT_STEPS)"
                }
                
                // If the final text is exactly what was just streamed, we don't need a duplicate bubble.
                withContext(Dispatchers.Main) { 
                    val lastMsg = msgs.lastOrNull()
                    if (lastMsg?.role == "assistant" && lastMsg.content.trim() == finalText?.trim()) {
                        // Already printed in the last stream block
                    } else if (finalText?.isNotBlank() == true || cancelRequested) {
                        msgs.add(ChatMessage("assistant", answer)) 
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    online = OllamaClient.isUp()
                    msgs.add(
                        ChatMessage(
                            "assistant",
                            "⚠ Ошибка агента: ${e.message}\n\n" +
                                "Проверь ollama (${OllamaClient.base}) и что выбранная модель поддерживает инструменты (tools).",
                        )
                    )
                }
            } finally {
                withContext(Dispatchers.Main) {
                    busy = false
                    cancelRequested = false
                    pendingApproval?.let { it.result.complete(false); pendingApproval = null }
                    persistChats()
                }
            }
        }
    }

    private fun textMessage(role: String, content: String): JsonObject = buildJsonObject {
        put("role", role); put("content", content)
    }

    private fun toolResultMessage(name: String, content: String): JsonObject = buildJsonObject {
        put("role", "tool"); put("tool_name", name); put("content", content)
    }

    /** Tool-call arguments may arrive as a JSON object or a JSON-encoded string. */
    private fun parseArgs(el: JsonElement?): JsonObject = when (el) {
        is JsonObject -> el
        is JsonPrimitive -> runCatching { json.parseToJsonElement(el.content).jsonObject }
            .getOrDefault(buildJsonObject {})
        else -> buildJsonObject {}
    }
}
