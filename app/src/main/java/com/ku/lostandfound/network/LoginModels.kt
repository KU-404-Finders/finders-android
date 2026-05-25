package com.ku.lostandfound.network

data class LoginRequest(
    val email: String,
    val password: String,
)

data class TokenData(
    val accessToken: String,
    val refreshToken: String,
)

data class LoginResponse(
    val success: Boolean,
    val message: String,
    val data: TokenData?,
    val errors: Map<String, String>? = null,
)

data class RefreshTokenRequest(
    val refreshToken: String,
)

data class RefreshTokenResponse(
    val success: Boolean,
    val message: String,
    val data: TokenData?,
    val errors: Map<String, String>? = null,
)

data class ApiMessageResponse(
    val success: Boolean,
    val message: String,
    val data: Nothing? = null,
    val errors: Map<String, String>? = null,
)

data class AuthErrorResponse(
    val success: Boolean,
    val message: String,
    val data: Nothing? = null,
    val errors: Map<String, String>? = null,
)
