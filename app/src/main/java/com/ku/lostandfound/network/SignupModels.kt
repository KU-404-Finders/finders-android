package com.ku.lostandfound.network

import com.google.gson.annotations.SerializedName

data class SignupRequest(
    val email: String,
    val authCode: String,
    val password: String,
    val name: String,
)

data class SignupResponse(
    val success: Boolean,
    val message: String,
    val data: Nothing? = null,
    val errors: Map<String, String>? = null,
)

data class SignupErrorResponse(
    val success: Boolean,
    val message: String,
    val data: Nothing? = null,
    @SerializedName("errors")
    val errors: Map<String, String>? = null,
)
