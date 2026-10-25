package com.yahyafati.poolprint.ui

import androidx.compose.runtime.Composable

/** A function that offers text to other apps (the Android share sheet), or null where there is none (desktop). */
@Composable
expect fun rememberTextSharer(): ((String) -> Unit)?

/** Puts plain text on the system clipboard (ClipEntry has no common constructor). */
expect suspend fun androidx.compose.ui.platform.Clipboard.setText(text: String)
