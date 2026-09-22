package com.costproject.app.data.remote

import com.costproject.app.data.dto.ApiResponse
import com.costproject.app.data.dto.AuthResponseDto
import com.costproject.app.data.dto.LoginRequest
import com.costproject.app.data.dto.RefreshRequest
import com.costproject.app.data.dto.RegisterRequest
import com.costproject.app.data.dto.TokenPairDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * Authentication endpoints. Takes the public client - see createPublicHttpClient
 * for why these must not share the authenticated one.
 *
 * Paths have no leading slash so they resolve beneath the base URL's /api/v1/.
 */
class AuthApiService(private val client: HttpClient) {

    suspend fun register(request: RegisterRequest): AuthResponseDto =
        client.post("auth/register") { setBody(request) }.body<ApiResponse<AuthResponseDto>>().data

    suspend fun login(request: LoginRequest): AuthResponseDto =
        client.post("auth/login") { setBody(request) }.body<ApiResponse<AuthResponseDto>>().data

    suspend fun refresh(refreshToken: String): TokenPairDto =
        client.post("auth/refresh") { setBody(RefreshRequest(refreshToken)) }.body<ApiResponse<TokenPairDto>>().data
}
