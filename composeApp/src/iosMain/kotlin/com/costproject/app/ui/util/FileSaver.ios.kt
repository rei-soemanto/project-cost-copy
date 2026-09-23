package com.costproject.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

/**
 * Writes the file to the app's temporary folder and opens the iOS share sheet,
 * which offers "Save to Files" as well as AirDrop, Mail and so on.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun rememberFileSaver(
    mimeType: String,
    content: () -> ByteArray?,
    onResult: (SaveResult) -> Unit
): FileSaver {
    val currentContent by rememberUpdatedState(content)
    val currentOnResult by rememberUpdatedState(onResult)

    return remember {
        FileSaver { fileName ->
            val bytes = currentContent()
            val root = UIApplication.sharedApplication.keyWindow?.rootViewController
            if (bytes == null || root == null) {
                currentOnResult(SaveResult.Failed)
                return@FileSaver
            }

            val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + fileName)
            val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
            if (!data.writeToURL(url, atomically = true)) {
                currentOnResult(SaveResult.Failed)
                return@FileSaver
            }

            val sheet = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
            // Required on iPad, where the share sheet is a popover and crashes without an anchor.
            sheet.popoverPresentationController?.sourceView = root.view
            sheet.completionWithItemsHandler = { _, completed, _, error ->
                currentOnResult(
                    when {
                        error != null -> SaveResult.Failed
                        completed -> SaveResult.Saved
                        else -> SaveResult.Cancelled
                    }
                )
            }
            root.presentViewController(sheet, animated = true, completion = null)
        }
    }
}
