package com.costproject.app.data.dto

import kotlinx.serialization.Serializable

/** GET /me. isAdmin decides whether the app offers the all-users export. */
@Serializable
data class ProfileDto(
    val id: String,
    val email: String,
    val fullName: String,
    val isAdmin: Boolean
)
