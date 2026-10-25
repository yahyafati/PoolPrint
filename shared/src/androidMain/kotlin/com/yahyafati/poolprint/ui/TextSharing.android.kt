package com.yahyafati.poolprint.ui

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberTextSharer(): ((String) -> Unit)? {
    val context = LocalContext.current
    return remember(context) {
        { text: String ->
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

actual suspend fun androidx.compose.ui.platform.Clipboard.setText(text: String) {
    setClipEntry(androidx.compose.ui.platform.ClipEntry(android.content.ClipData.newPlainText("Pool Print", text)))
}
