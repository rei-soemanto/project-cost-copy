package com.costproject.app.data.remote

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

actual fun createHttpEngine(): HttpClientEngine = Darwin.create()

/** The iOS simulator shares the host network, so localhost reaches a dev server on the Mac. */
actual val defaultApiBaseUrl: String = "http://localhost:3000/api/v1/"
