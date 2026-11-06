package org.wexcoder.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.wexcoder.app.ui.AppUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.awt.Dimension
import kotlin.system.exitProcess

fun main() {
    if (relaunchedForTilingWm()) return
    runApp()
}

/**
 * Java/AWT under XWayland on **tiling Wayland compositors** (niri, sway, …) never
 * delivers window-resize events unless `_JAVA_AWT_WM_NONREPARENTING=1` is set
 * *before* the JVM starts — without it the Compose UI stays laid out at its
 * initial size and is simply clipped when the compositor resizes the window
 * (i.e. "doesn't adapt"). The variable can't be set from inside the running JVM,
 * so on Linux we re-exec ourselves once with it present.
 *
 * @return true if we relaunched (the caller must return immediately).
 */
private fun relaunchedForTilingWm(): Boolean {
    val isLinux = System.getProperty("os.name").orEmpty().lowercase().contains("linux")
    if (!isLinux) return false
    if (System.getenv("_JAVA_AWT_WM_NONREPARENTING") != null) return false
    if (System.getenv("WEXCODER_RELAUNCHED") != null) return false

    return runCatching {
        val javaBin = System.getProperty("java.home") + "/bin/java"
        val cmd = listOf(javaBin, "-cp", System.getProperty("java.class.path"), "org.wexcoder.app.MainKt")
        val process = ProcessBuilder(cmd).inheritIO().apply {
            environment()["_JAVA_AWT_WM_NONREPARENTING"] = "1"
            environment()["WEXCODER_RELAUNCHED"] = "1"
        }.start()
        exitProcess(process.waitFor())
    }.getOrDefault(false)   // if re-exec fails, fall through and run normally
}

private fun runApp() = application {
    val scope = rememberCoroutineScope()
    val state = remember { AppState(scope) }

    // Restore the last window size (niri may override it, but floating windows keep it).
    val windowState = rememberWindowState(
        width = state.initialWindowWidth.dp,
        height = state.initialWindowHeight.dp,
    )

    // Persist size after the compositor / user stops resizing. collectLatest +
    // delay debounces the burst so we don't hit the disk on every resize tick.
    LaunchedEffect(windowState) {
        snapshotFlow { windowState.size }.collectLatest { size ->
            delay(400)
            state.saveWindowSize(size.width.value, size.height.value)
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "WexCoder",
    ) {
        // Low minimum so niri can pack the window into a narrow column without
        // breaking the layout; the UI degrades gracefully below this anyway.
        window.minimumSize = Dimension(320, 240)

        AppUi(state)
    }
}
