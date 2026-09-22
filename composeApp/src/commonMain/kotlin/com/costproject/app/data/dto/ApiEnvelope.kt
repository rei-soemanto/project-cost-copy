package com.costproject.app.data.dto

import kotlinx.serialization.Serializable

/** Success envelope: every 2xx body is `{ "data": ... }`. See server/API.md. */
@Serializable
data class ApiResponse<T>(val data: T)

/** Error envelope: every non-2xx body is `{ "error": { ... } }`. */
@Serializable
data class ErrorResponse(val error: ErrorBody)

@Serializable
data class ErrorBody(
    val code: String,
    val message: String,
    val details: List<FieldErrorDto>? = null
)

@Serializable
data class FieldErrorDto(val path: String, val message: String)
