package com.costproject.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(val fullName: String, val email: String, val password: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class UserDto(val id: String, val email: String, val fullName: String)

/** Returned by register and login. */
@Serializable
data class AuthResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto
)

/** Returned by refresh. */
@Serializable
data class TokenPairDto(val accessToken: String, val refreshToken: String)
