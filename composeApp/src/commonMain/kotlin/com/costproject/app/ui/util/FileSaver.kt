package com.costproject.app.ui.util

import androidx.compose.runtime.Composable

const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

enum class SaveResult { Saved, Cancelled, Failed }

/** Lets the user save a file wherever they choose. Obtain one with [rememberFileSaver]. */
fun interface FileSaver {
    /** Opens the platform's save UI, suggesting [fileName]. */
    fun launch(fileName: String)
}

/**
 * The platform's "save a file" flow: the system document picker on Android, the
 * share sheet (with "Save to Files") on iOS.
 *
 * [content] is read when the user has picked a destination, not when [FileSaver.launch]
 * is called. The bytes therefore stay in the caller's ViewModel, which survives a
 * rotation while the picker is open; a copy held here would not.
 */
@Composable
expect fun rememberFileSaver(
    mimeType: String,
    content: () -> ByteArray?,
    onResult: (SaveResult) -> Unit
): FileSaver
