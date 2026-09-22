package com.costproject.app.util

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale

actual fun todayLabel(): String {
    val formatter = NSDateFormatter().apply {
        dateFormat = "dd MMM yyyy"
        locale = NSLocale(localeIdentifier = "id_ID")
    }
    return formatter.stringFromDate(NSDate())
}
