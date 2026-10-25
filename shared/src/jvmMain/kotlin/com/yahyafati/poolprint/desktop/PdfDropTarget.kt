package com.yahyafati.poolprint.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.unit.dp
import com.yahyafati.poolprint.ui.PoolTheme
import java.awt.datatransfer.DataFlavor
import java.io.File
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import poolprint.shared.generated.resources.*

/**
 * Wraps [content] so a PDF can be dropped anywhere on it. While a file hovers a hint covers the window;
 * a drop without a PDF in it (when [onFiles] returns false) shows a short "only PDFs" message instead.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun PdfDropTarget(onFiles: (List<File>) -> Boolean, content: @Composable () -> Unit) {
    val currentOnFiles by rememberUpdatedState(onFiles)
    var hovering by remember { mutableStateOf(false) }
    var rejected by remember { mutableStateOf(0) }
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { hovering = true }
            override fun onExited(event: DragAndDropEvent) { hovering = false }
            override fun onEnded(event: DragAndDropEvent) { hovering = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                hovering = false
                val accepted = currentOnFiles(filesIn(event.awtTransferable))
                if (!accepted) rejected++
                return accepted
            }
        }
    }
    Box(
        Modifier.fillMaxSize().dragAndDropTarget(
            shouldStartDragAndDrop = { it.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) },
            target = target,
        ),
    ) {
        content()
        if (hovering) DropHint(stringResource(Res.string.drop_hint))
        if (rejected > 0) {
            DropHint(stringResource(Res.string.drop_rejected))
            LaunchedEffect(rejected) {
                delay(3_000)
                rejected = 0
            }
        }
    }
}

@Composable
private fun DropHint(text: String) {
    Box(
        Modifier.fillMaxSize().background(PoolTheme.colors.bg.copy(alpha = 0.94f)).padding(16.dp)
            .border(BorderStroke(2.dp, PoolTheme.colors.rule)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = PoolTheme.type.title, color = PoolTheme.colors.on)
    }
}
