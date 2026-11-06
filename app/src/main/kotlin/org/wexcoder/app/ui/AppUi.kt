package org.wexcoder.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.wexcoder.app.AppState
import org.wexcoder.app.ChatMessage
import org.wexcoder.app.ChatMode

/** Below this width the sidebar becomes a toggled overlay drawer. */
private val COMPACT_BREAKPOINT = 700.dp

@Composable
fun AppUi(state: AppState) {
    var sidebarOpen by remember { mutableStateOf(false) }

    WexTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val compact = maxWidth < COMPACT_BREAKPOINT

                // Content density scales with the window so narrow niri columns
                // stay usable and ultra-wide windows don't get lost in padding.
                val hPad = when {
                    maxWidth < 480.dp -> 12.dp
                    maxWidth < 820.dp -> 20.dp
                    else -> 28.dp
                }
                val bubbleMax = if (maxWidth < 560.dp) maxWidth - hPad * 2
                else minOf(680.dp, maxWidth * 0.72f)

                // Leaving compact mode auto-closes the drawer.
                LaunchedEffect(compact) { if (!compact) sidebarOpen = false }

                if (!compact) {
                    Row(Modifier.fillMaxSize()) {
                        Sidebar(state, Modifier.width(272.dp).fillMaxHeight())
                        VerticalDivider()
                        ChatPanel(
                            state = state,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            hPad = hPad,
                            bubbleMax = bubbleMax,
                        )
                    }
                } else {
                    // Captured here, in BoxWithConstraints scope, before the inner
                    // AnimatedVisibility shadows the `maxWidth` receiver.
                    val drawerWidth = minOf(300.dp, maxWidth * 0.86f)
                    Box(Modifier.fillMaxSize()) {
                        ChatPanel(
                            state = state,
                            modifier = Modifier.fillMaxSize(),
                            hPad = hPad,
                            bubbleMax = bubbleMax,
                            showMenuButton = true,
                            onMenuClick = { sidebarOpen = !sidebarOpen },
                        )
                        AnimatedVisibility(sidebarOpen, enter = fadeIn(), exit = fadeOut()) {
                            Box(Modifier.fillMaxSize()) {
                                // Scrim — tap outside to close, no ripple.
                                Box(
                                    Modifier.fillMaxSize()
                                        .background(Color(0x99000000))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) { sidebarOpen = false }
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shadowElevation = 8.dp,
                                    modifier = Modifier.width(drawerWidth).fillMaxHeight(),
                                ) {
                                    Sidebar(
                                        state,
                                        Modifier.fillMaxSize(),
                                        onNavigate = { sidebarOpen = false },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            SettingsDialog(state)
            ApprovalDialog(state)
        }
    }
}

/** Blocks the agent loop on a dangerous tool call until the user allows or rejects it. */
@Composable
private fun ApprovalDialog(state: AppState) {
    val pending = state.pendingApproval ?: return
    AlertDialog(
        onDismissRequest = { state.resolveApproval(false) },
        title = { Text(pending.title) },
        text = {
            Column {
                Text("Агент хочет выполнить операцию:")
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        pending.detail,
                        Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { state.resolveApproval(true) }) { Text("Разрешить") }
        },
        dismissButton = {
            TextButton(onClick = { state.resolveApproval(false) }) { Text("Отклонить") }
        },
    )
}

@Composable
private fun VerticalDivider() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
}

@Composable
fun ChatPanel(
    state: AppState,
    modifier: Modifier,
    hPad: Dp,
    bubbleMax: Dp,
    showMenuButton: Boolean = false,
    onMenuClick: () -> Unit = {},
) {
    Column(modifier) {
        TopBar(state, showMenuButton, onMenuClick)
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))

        val msgs = state.currentMessages
        if (msgs == null) {
            EmptyState(Modifier.weight(1f))
            InputBar(state, hPad)
            return@Column
        }

        val listState = rememberLazyListState()
        // True only when the real end of the conversation is visible. While the
        // user scrolls up to read, this is false and we stop following — so the
        // stream no longer yanks them back down.
        val atBottom by remember {
            derivedStateOf {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
                last.index >= info.totalItemsCount - 1 &&
                    last.offset + last.size <= info.viewportEndOffset + 8
            }
        }
        LaunchedEffect(msgs.size, msgs.lastOrNull()?.content) {
            if (msgs.isEmpty() || !atBottom) return@LaunchedEffect
            listState.scrollToItem(msgs.size - 1)
            // Pin to the very bottom of a long, still-growing last message.
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            if (last != null) {
                val overflow = last.offset + last.size -
                    (info.viewportEndOffset - info.viewportStartOffset)
                if (overflow > 0) listState.scrollBy(overflow.toFloat())
            }
        }

        if (msgs.isEmpty()) {
            EmptyState(Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = hPad),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                items(msgs) { m -> MessageBubble(m, bubbleMax) }
            }
        }

        InputBar(state, hPad)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TopBar(state: AppState, showMenuButton: Boolean, onMenuClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showMenuButton) {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Filled.Menu, contentDescription = "Меню")
            }
            Spacer(Modifier.width(4.dp))
        }

        StatusDot(state.online)
        Spacer(Modifier.width(8.dp))

        Column(Modifier.weight(1f)) {
            Text(
                state.currentChatTitle ?: "WexCoder",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            state.selectedProject?.let { p ->
                Text(
                    "📁 ${p.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Chips wrap to a new line when the window gets narrow.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ChatMode.entries.forEach { m ->
                FilterChip(
                    selected = state.mode == m,
                    onClick = { state.mode = m },
                    label = { Text(m.label) },
                )
            }
            // Toggles whole-project context (file tree + contents) into the prompt.
            FilterChip(
                selected = state.includeProjectContext,
                onClick = { state.includeProjectContext = !state.includeProjectContext },
                enabled = state.selectedProject != null,
                label = { Text("📁 Проект") },
            )
            IconButton(
                onClick = { state.showSettingsDialog = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(androidx.compose.material.icons.Icons.Filled.Settings, contentDescription = "Настройки", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun StatusDot(online: Boolean) {
    Box(
        Modifier.size(9.dp).background(
            color = if (online) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
            shape = CircleShape,
        )
    )
}

@Composable
private fun MessageBubble(m: ChatMessage, maxWidth: Dp) {
    if (m.role == "tool") {
        ToolStep(m.content, maxWidth)
        return
    }
    val isUser = m.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            Surface(
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(14.dp),
                tonalElevation = if (isUser) 0.dp else 2.dp,
                modifier = Modifier.widthIn(max = maxWidth),
            ) {
                SelectionContainer {
                    when {
                        m.content.isEmpty() && m.streaming ->
                            Text("Обдумываю шаги / Вызываю инструмент...",
                                Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        isUser ->
                            Text(m.content, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge)
                        else ->
                            MarkdownText(
                                text = m.content,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(14.dp),
                            )
                    }
                }
            }
            if (!isUser && m.stats != null) {
                Text(
                    m.stats,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolStep(content: String, maxWidth: Dp) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.widthIn(max = maxWidth),
        ) {
            SelectionContainer {
                Text(
                    content,
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
    }
}

@Composable
private fun InputBar(state: AppState, hPad: Dp) {
    val agentNeedsProject = state.mode == ChatMode.AGENT && state.selectedProject == null
    Row(
        Modifier.fillMaxWidth().padding(horizontal = hPad, vertical = 14.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = state.input,
            onValueChange = { state.input = it },
            modifier = Modifier.weight(1f).onPreviewKeyEvent { e ->
                val enter = e.key == Key.Enter || e.key == Key.NumPadEnter
                if (enter && !e.isShiftPressed) {
                    // Enter sends; Shift+Enter falls through to insert a newline.
                    if (e.type == KeyEventType.KeyDown && !state.busy && state.input.isNotBlank()) {
                        state.send()
                    }
                    true
                } else false
            },
            enabled = !state.busy,
            maxLines = 6,
            placeholder = {
                Text(
                    when {
                        state.busy -> "Модель думает…"
                        agentNeedsProject -> "Агенту нужен проект — выбери слева (или работай в «Чат»)"
                        else -> "Напиши сообщение — режим «${state.mode.label}»"
                    }
                )
            },
        )
        Spacer(Modifier.width(10.dp))
        if (state.busy) {
            // While generating, the action button stops the stream.
            FilledIconButton(
                onClick = { state.stop() },
                modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(Icons.Filled.Stop, contentDescription = "Остановить")
            }
        } else {
            FilledIconButton(
                onClick = { state.send() },
                enabled = state.input.isNotBlank(),
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "WexCoder",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Напиши сообщение, чтобы начать. Для режима «Агент» выбери проект слева.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
