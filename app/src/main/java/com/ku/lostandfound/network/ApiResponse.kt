package com.ku.lostandfound.network

data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null,
    val errors: Map<String, String>? = null,
)

data class SpringSecurityErrorResponse(
    val timestamp: String? = null,
    val status: Int? = null,
    val error: String? = null,
    val path: String? = null,
)
