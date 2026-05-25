package com.ku.lostandfound.network

data class UserMeResponse(
    val success: Boolean,
    val message: String,
    val data: UserMeData?,
    val errors: Map<String, String>? = null,
)

data class UserMeData(
    val email: String,
    val message: String,
)
