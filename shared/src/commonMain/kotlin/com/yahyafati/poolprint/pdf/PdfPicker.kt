package com.yahyafati.poolprint.pdf

import androidx.compose.runtime.Composable

/** Returns a function that opens the platform's PDF chooser; [onPicked] gets the chosen document. */
@Composable
expect fun rememberPdfPicker(onPicked: (PdfDocumentSource) -> Unit): () -> Unit
