package com.yahyafati.poolprint

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yahyafati.poolprint.app.createAndroidAppDependencies
import com.yahyafati.poolprint.pdf.PdfDocumentSource
import com.yahyafati.poolprint.pdf.UriPdfSource

class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf<PdfDocumentSource?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val deps = createAndroidAppDependencies(this)
        incoming = pdfFrom(intent)
        setContent {
            App(deps, appVersion = BuildConfig.VERSION_NAME, incomingDocument = incoming, onIncomingHandled = { incoming = null })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pdfFrom(intent)?.let { incoming = it }
    }

    /** A PDF shared to Pool Print. Only `content://` URIs are accepted, never `file://` paths into our own storage. */
    private fun pdfFrom(intent: Intent): PdfDocumentSource? {
        if (intent.action != Intent.ACTION_SEND || intent.type != "application/pdf") return null
        val uri = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
        if (uri == null || uri.scheme != "content") return null
        return UriPdfSource(contentResolver, uri)
    }
}
