package com.costproject.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun todayLabel(): String {
    val format = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
    return format.format(Date())
}
