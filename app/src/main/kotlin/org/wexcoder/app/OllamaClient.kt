package org.wexcoder.app

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Minimal client for the local Ollama HTTP API.
 *
 * Base URL is taken from the `OLLAMA_HOST` env var (same one the `ollama` CLI
 * uses), falling back to http://localhost:11434. All calls are blocking — invoke
 * them off the UI thread (Dispatchers.IO).
 */
object OllamaClient {

    /** Resolved once at startup; honours `OLLAMA_HOST` like the ollama CLI. */
    var base: String = run {
        val raw = (System.getenv("OLLAMA_HOST") ?: "").trim()
        when {
            raw.isEmpty() -> "http://localhost:11434"
            raw.startsWith("http://") || raw.startsWith("https://") -> raw.trimEnd('/')
            else -> "http://${raw.trimEnd('/')}"   // e.g. "127.0.0.1:11434"
        }
    }

    // Default kept well within qwen2.5-coder's native 32K window; a huge value
    // makes Ollama stretch positional encoding past training and the model gets
    // confused even on short prompts. Raise it in Settings only if the model
    // actually supports it and there's VRAM to spare.
    var contextSize: Int = 16384

    // Sampling knobs shared by every request. repeat_penalty is the important one:
    // without it small / abliterated models fall into verbatim repetition loops
    // (the same paragraph over and over). top_p / top_k trim the long tail so the
    // model stays on-topic. These match the values Qwen recommends for coding.
    var repeatPenalty: Double = 1.1
    var topP: Double = 0.9
    var topK: Int = 40

    // Short connect timeout so an offline server is detected quickly; the
    // generation request itself has no read timeout (large models are slow).
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** The `options` block sent with every /api/chat request. */
    private fun samplingOptions(temperature: Double): JsonObject = buildJsonObject {
        put("temperature", temperature)
        put("num_ctx", contextSize)
        put("repeat_penalty", repeatPenalty)
        put("top_p", topP)
        put("top_k", topK)
    }

    /** True if the Ollama server answers on /api/version within the timeout. */
    fun isUp(): Boolean = runCatching {
        val req = HttpRequest.newBuilder(URI.create("$base/api/version"))
            .timeout(Duration.ofSeconds(3)).GET().build()
        client.send(req, HttpResponse.BodyHandlers.ofString()).statusCode() == 200
    }.getOrDefault(false)

    /** Names of locally available models (GET /api/tags). Empty on any error. */
    fun listModels(): List<String> = runCatching {
        val req = HttpRequest.newBuilder(URI.create("$base/api/tags"))
            .timeout(Duration.ofSeconds(5)).GET().build()
        val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
        val models = json.parseToJsonElement(resp.body()).jsonObject["models"]?.jsonArray
            ?: return emptyList()
        models.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
    }.getOrDefault(emptyList())

    /**
     * Formats the per-response timing Ollama returns in its final chunk into a
     * short badge ("⚡ 38 tok/s · 2.1 с · 512 ток."), or null if absent.
     */
    private fun formatStats(obj: JsonObject): String? {
        val evalCount = obj["eval_count"]?.jsonPrimitive?.content?.toLongOrNull() ?: return null
        val evalDurNs = obj["eval_duration"]?.jsonPrimitive?.content?.toLongOrNull() ?: return null
        if (evalDurNs <= 0L) return null
        val toksPerSec = evalCount.toDouble() / (evalDurNs / 1_000_000_000.0)
        val totalNs = obj["total_duration"]?.jsonPrimitive?.content?.toLongOrNull() ?: evalDurNs
        val totalSec = totalNs / 1_000_000_000.0
        return String.format(
            java.util.Locale.US, "⚡ %d tok/s · %.1f с · %d ток.",
            Math.round(toksPerSec), totalSec, evalCount,
        )
    }

    /**
     * Streams a chat completion (POST /api/chat, stream=true). [onToken] is called
     * for each text chunk as it arrives. Reading stops promptly when [isCancelled]
     * returns true (the connection is closed, which tells Ollama to stop too).
     * [onStats] receives the timing badge once the final chunk arrives.
     * Throws on transport / HTTP / API errors.
     */
    fun chatStream(
        model: String,
        system: String,
        history: List<ChatMessage>,
        temperature: Double,
        isCancelled: () -> Boolean,
        onStats: (String?) -> Unit = {},
        onToken: (String) -> Unit,
    ) {
        val messages = buildJsonArray {
            if (system.isNotBlank()) add(buildJsonObject {
                put("role", "system"); put("content", system)
            })
            history.forEach {
                add(buildJsonObject { put("role", it.role); put("content", it.content) })
            }
        }
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("messages", messages)
            put("options", samplingOptions(temperature))
        }.toString()

        val req = HttpRequest.newBuilder(URI.create("$base/api/chat"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream())
        if (resp.statusCode() != 200) {
            val detail = runCatching { resp.body().readBytes().decodeToString() }.getOrDefault("")
            error("Ollama вернул HTTP ${resp.statusCode()}${if (detail.isNotBlank()) ": ${detail.take(300)}" else ""}")
        }

        var stats: String? = null
        resp.body().bufferedReader().use { reader ->
            while (true) {
                if (isCancelled()) break
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue

                obj["error"]?.jsonPrimitive?.content?.let { error("Ollama: $it") }

                val chunk = obj["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content
                if (!chunk.isNullOrEmpty()) onToken(chunk)

                formatStats(obj)?.let { stats = it }
                if (obj["done"]?.jsonPrimitive?.content == "true") break
            }
        }
        onStats(stats)
    }

    /**
     * Streams an agent chat completion (POST /api/chat, stream=true). 
     * Yields content tokens via [onToken] and returns the final assistant message 
     * object (which contains the full content and any tool_calls).
     */
    fun chatStreamAgent(
        model: String,
        messages: JsonArray,
        tools: JsonArray?,
        temperature: Double,
        isCancelled: () -> Boolean,
        onToken: (String) -> Unit,
    ): JsonObject {
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("messages", messages)
            if (tools != null) put("tools", tools)
            put("options", samplingOptions(temperature))
        }.toString()

        val req = HttpRequest.newBuilder(URI.create("$base/api/chat"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream())
        if (resp.statusCode() != 200) {
            val detail = runCatching { resp.body().readBytes().decodeToString() }.getOrDefault("")
            error("Ollama вернул HTTP ${resp.statusCode()}: $detail")
        }

        val reader = java.io.BufferedReader(java.io.InputStreamReader(resp.body(), Charsets.UTF_8))
        val fullContent = java.lang.StringBuilder()
        var toolCalls: kotlinx.serialization.json.JsonArray? = null
        var stats: String? = null

        reader.useLines { lines ->
            for (line in lines) {
                if (isCancelled()) break
                if (line.isBlank()) continue
                val root = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
                formatStats(root)?.let { stats = it }
                val msg = root["message"]?.jsonObject ?: continue

                val content = msg["content"]?.jsonPrimitive?.content ?: ""
                if (content.isNotEmpty()) {
                    fullContent.append(content)
                    onToken(content)
                }

                val tc = msg["tool_calls"]?.jsonArray
                if (tc != null && tc.isNotEmpty()) {
                    toolCalls = tc
                }
            }
        }

        return buildJsonObject {
            put("role", "assistant")
            put("content", fullContent.toString())
            if (toolCalls != null) {
                put("tool_calls", toolCalls!!)
            }
            stats?.let { put("_stats", it) }
        }
    }
}
