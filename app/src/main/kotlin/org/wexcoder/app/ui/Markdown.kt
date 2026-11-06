package org.wexcoder.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import org.jetbrains.skia.Data
import org.jetbrains.skia.svg.SVGDOM
import org.wexcoder.app.saveTextFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.Alignment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A small Markdown renderer — enough for chat replies: **bold**, *italic*,
 * `inline code`, ```fenced code blocks```, # headings and - bullet lists.
 * Intentionally dependency-free (no network needed).
 */
@Composable
fun MarkdownText(text: String, color: Color, modifier: Modifier = Modifier) {
    val blocks = remember(text) { splitBlocks(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Code -> CodeBlock(block.code, block.lang)
                is MdBlock.Svg -> SvgBlock(block.svg)
                is MdBlock.Thought -> ThoughtBlock(block.text)
                is MdBlock.Text -> Text(
                    text = renderInlineMarkdown(block.text),
                    color = color,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun ThoughtBlock(text: String) {
    var expanded by remember { mutableStateOf(true) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Мысли агента",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(expanded) {
                Text(
                    text = text.trim(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String, lang: String) {
    val clipboardManager = LocalClipboardManager.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (lang.isNotBlank()) {
                    Text(
                        lang,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(code)) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Копировать код",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val highlighted = remember(code) { highlightCode(code) }
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Text(
                    highlighted,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private sealed interface MdBlock {
    data class Text(val text: String) : MdBlock
    data class Code(val code: String, val lang: String) : MdBlock
    data class Svg(val svg: String) : MdBlock
    data class Thought(val text: String) : MdBlock
}

/**
 * Renders an inline SVG as a real image via Skia's SVGDOM (already bundled with
 * Compose Desktop — no extra dependency). Toolbar toggles image/source and saves
 * to a .svg file. Falls back to the source if the SVG can't be parsed.
 */
@Composable
private fun SvgBlock(svg: String) {
    val clipboard = LocalClipboardManager.current
    var showCode by remember { mutableStateOf(false) }
    val dom = remember(svg) {
        runCatching { SVGDOM(Data.makeFromBytes(svg.toByteArray(Charsets.UTF_8))) }.getOrNull()
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "svg",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    IconButton(onClick = { showCode = !showCode }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            if (showCode) Icons.Filled.Image else Icons.Filled.Code,
                            contentDescription = if (showCode) "Показать картинку" else "Показать код",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = { clipboard.setText(AnnotatedString(svg)) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = "Копировать SVG",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = { saveTextFile("image.svg", svg) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Filled.Save,
                            contentDescription = "Сохранить в файл",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                dom == null -> {
                    Text(
                        "⚠ Не удалось разобрать SVG — показываю исходник.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(6.dp))
                    SvgSource(svg)
                }
                showCode -> SvgSource(svg)
                else -> Box(
                    Modifier.fillMaxWidth().height(240.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        // setContainerSize resolves viewBox / percentage sizing to the
                        // available area so the drawing scales to fit the bubble.
                        dom.setContainerSize(size.width, size.height)
                        drawIntoCanvas { canvas -> dom.render(canvas.nativeCanvas) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SvgSource(svg: String) {
    val highlighted = remember(svg) { highlightCode(svg) }
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Text(
            highlighted,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ---- syntax highlighting (language-agnostic) ----------------------------

private val COL_KEYWORD = Color(0xFFC792EA)   // purple
private val COL_STRING = Color(0xFFC3E88D)    // green
private val COL_COMMENT = Color(0xFF7F848E)   // muted gray
private val COL_NUMBER = Color(0xFFF78C6C)    // orange

private val KEYWORDS = setOf(
    "abstract", "and", "as", "assert", "async", "await", "begin", "bool", "boolean",
    "break", "by", "case", "catch", "char", "class", "companion", "const", "constructor",
    "continue", "data", "def", "default", "del", "do", "double", "elif", "else", "end",
    "enum", "except", "extends", "false", "final", "finally", "float", "fn", "for", "from",
    "fun", "function", "global", "if", "impl", "implements", "import", "in", "init", "instanceof",
    "int", "interface", "internal", "is", "lambda", "lateinit", "let", "long", "match", "mut",
    "new", "nil", "none", "not", "null", "object", "open", "operator", "or", "override",
    "package", "pass", "print", "println", "private", "protected", "public", "raise", "return",
    "self", "sealed", "static", "str", "string", "struct", "super", "suspend", "switch", "then",
    "this", "throw", "throws", "trait", "true", "try", "type", "typeof", "union", "unsafe", "use",
    "val", "var", "void", "when", "where", "while", "with", "yield",
    "True", "False", "None",
)

private fun highlightCode(code: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    val n = code.length
    fun eol(start: Int): Int { var e = start; while (e < n && code[e] != '\n') e++; return e }

    while (i < n) {
        val c = code[i]
        when {
            c == '/' && i + 1 < n && code[i + 1] == '/' -> {
                val e = eol(i); withStyle(SpanStyle(color = COL_COMMENT)) { append(code.substring(i, e)) }; i = e
            }
            c == '#' -> {
                val e = eol(i); withStyle(SpanStyle(color = COL_COMMENT)) { append(code.substring(i, e)) }; i = e
            }
            c == '/' && i + 1 < n && code[i + 1] == '*' -> {
                var e = code.indexOf("*/", i + 2); e = if (e < 0) n else e + 2
                withStyle(SpanStyle(color = COL_COMMENT)) { append(code.substring(i, e)) }; i = e
            }
            c == '"' || c == '\'' || c == '`' -> {
                var e = i + 1
                while (e < n) { if (code[e] == '\\') e += 2 else if (code[e] == c) { e++; break } else e++ }
                if (e > n) e = n
                withStyle(SpanStyle(color = COL_STRING)) { append(code.substring(i, e)) }; i = e
            }
            c.isDigit() -> {
                var e = i; while (e < n && (code[e].isLetterOrDigit() || code[e] == '.' || code[e] == '_')) e++
                withStyle(SpanStyle(color = COL_NUMBER)) { append(code.substring(i, e)) }; i = e
            }
            c.isLetter() || c == '_' -> {
                var e = i; while (e < n && (code[e].isLetterOrDigit() || code[e] == '_')) e++
                val word = code.substring(i, e)
                if (word in KEYWORDS) withStyle(SpanStyle(color = COL_KEYWORD)) { append(word) } else append(word)
                i = e
            }
            else -> { append(c); i++ }
        }
    }
}

/** Splits the source into thought blocks and markdown blocks. */
private fun splitBlocks(src: String): List<MdBlock> {
    // Reasoning models (DeepSeek-R1 etc.) emit <think>…</think>; our agent prompt
    // asks for <thought>…</thought>. Fold both into one collapsible block.
    val out = ArrayList<MdBlock>()
    var remaining = src.replace("<think>", "<thought>").replace("</think>", "</thought>")
    while (remaining.isNotEmpty()) {
        val start = remaining.indexOf("<thought>")
        if (start == -1) {
            out.addAll(splitMarkdownBlocks(remaining))
            break
        }
        if (start > 0) {
            out.addAll(splitMarkdownBlocks(remaining.substring(0, start)))
        }
        val end = remaining.indexOf("</thought>", start)
        if (end == -1) {
            out.add(MdBlock.Thought(remaining.substring(start + 9)))
            break
        } else {
            out.add(MdBlock.Thought(remaining.substring(start + 9, end)))
            remaining = remaining.substring(end + 10)
        }
    }
    return out
}

/** Splits the source into plain-text blocks and fenced code blocks. */
private fun splitMarkdownBlocks(src: String): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    val lines = src.split("\n")
    val buf = StringBuilder()
    var i = 0

    fun flushText() {
        val t = buf.toString().trim('\n')
        if (t.isNotBlank()) out.add(MdBlock.Text(t))
        buf.setLength(0)
    }

    while (i < lines.size) {
        val trimmed = lines[i].trimStart()
        if (trimmed.startsWith("```")) {
            flushText()
            val lang = trimmed.removePrefix("```").trim()
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                code.append(lines[i]).append('\n'); i++
            }
            if (i < lines.size) i++ // skip closing fence
            val body = code.toString().trimEnd('\n')
            // A fenced block that actually contains an <svg> is drawn, not printed.
            if (body.contains("<svg") && body.contains("</svg>")) out.add(MdBlock.Svg(body))
            else out.add(MdBlock.Code(body, lang))
        } else if (trimmed.startsWith("<svg")) {
            // Raw, un-fenced <svg>…</svg> straight in the text.
            flushText()
            val code = StringBuilder()
            while (i < lines.size) {
                code.append(lines[i]).append('\n')
                val closed = lines[i].contains("</svg>")
                i++
                if (closed) break
            }
            out.add(MdBlock.Svg(code.toString().trimEnd('\n')))
        } else {
            buf.append(lines[i]).append('\n'); i++
        }
    }
    flushText()
    return out
}

private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
private val BULLET = Regex("^(\\s*)[-*+]\\s+(.*)$")

/** Builds an AnnotatedString for a non-code block, line by line. */
private fun renderInlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val lines = text.split("\n")
    lines.forEachIndexed { idx, line ->
        if (idx > 0) append("\n")

        val heading = HEADING.find(line)
        if (heading != null) {
            val level = heading.groupValues[1].length
            val size = when (level) { 1 -> 22f; 2 -> 19f; 3 -> 17f; else -> 15f }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = size.sp)) {
                appendInline(heading.groupValues[2])
            }
            return@forEachIndexed
        }

        val bullet = BULLET.find(line)
        if (bullet != null) {
            append(bullet.groupValues[1])
            append("•  ")
            appendInline(bullet.groupValues[2])
            return@forEachIndexed
        }

        appendInline(line)
    }
}

/** Inline spans: `code`, **bold**, *italic*. Unmatched markers are kept literally. */
private fun AnnotatedString.Builder.appendInline(s: String) {
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c == '`' -> {
                val end = s.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0x33808080),
                        )
                    ) { append(s.substring(i + 1, end)) }
                    i = end + 1
                } else { append(c); i++ }
            }
            c == '*' && i + 1 < s.length && s[i + 1] == '*' -> {
                val end = s.indexOf("**", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(s.substring(i + 2, end))
                    }
                    i = end + 2
                } else { append(c); i++ }
            }
            c == '*' -> {
                val end = s.indexOf('*', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(s.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(c); i++ }
            }
            else -> { append(c); i++ }
        }
    }
}
