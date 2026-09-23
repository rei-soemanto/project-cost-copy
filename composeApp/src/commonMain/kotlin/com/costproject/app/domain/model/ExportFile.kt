package com.costproject.app.domain.model

/**
 * A downloaded backup, ready to hand to the platform's save picker.
 *
 * A plain class rather than a data class: data-class equality on a ByteArray
 * compares references, which would be misleading.
 */
class ExportFile(val fileName: String, val bytes: ByteArray)
