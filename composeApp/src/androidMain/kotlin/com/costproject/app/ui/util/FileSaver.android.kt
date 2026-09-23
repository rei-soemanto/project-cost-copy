package com.costproject.app.ui.util

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Uses the Storage Access Framework's "create document" picker. The user chooses
 * the folder - Downloads, Google Drive, and so on - so the app needs no storage
 * permission on any Android version.
 */
@Composable
actual fun rememberFileSaver(
    mimeType: String,
    content: () -> ByteArray?,
    onResult: (SaveResult) -> Unit
): FileSaver {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentContent by rememberUpdatedState(content)
    val currentOnResult by rememberUpdatedState(onResult)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { uri ->
        if (uri == null) {
            currentOnResult(SaveResult.Cancelled)
            return@rememberLauncherForActivityResult
        }
        val bytes = currentContent()
        if (bytes == null) {
            currentOnResult(SaveResult.Failed)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) } != null
                } catch (_: IOException) {
                    false
                } catch (_: SecurityException) {
                    false
                }
            }
            currentOnResult(if (saved) SaveResult.Saved else SaveResult.Failed)
        }
    }

    return remember(launcher) {
        FileSaver { fileName ->
            try {
                launcher.launch(fileName)
            } catch (_: ActivityNotFoundException) {
                // No document provider on the device - rare, but possible on stripped-down builds.
                currentOnResult(SaveResult.Failed)
            }
        }
    }
}
