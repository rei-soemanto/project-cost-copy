package com.costproject.app.data.remote

import io.ktor.client.engine.HttpClientEngine

/** OkHttp on Android, Darwin (NSURLSession) on iOS. */
expect fun createHttpEngine(): HttpClientEngine

/**
 * Base URL of the API, ending in a slash so relative request paths resolve
 * beneath it ("projects" -> ".../api/v1/projects").
 *
 * Android debug builds default to 10.0.2.2, the emulator's alias for the host
 * machine; override with `-PapiBaseUrl=...` for a physical device.
 */
expect val defaultApiBaseUrl: String
