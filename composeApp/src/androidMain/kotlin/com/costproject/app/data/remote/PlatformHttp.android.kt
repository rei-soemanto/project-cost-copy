package com.costproject.app.data.remote

import com.costproject.app.BuildConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

actual fun createHttpEngine(): HttpClientEngine = OkHttp.create()

/** Set at build time; see apiBaseUrl in composeApp/build.gradle.kts. */
actual val defaultApiBaseUrl: String = BuildConfig.API_BASE_URL
