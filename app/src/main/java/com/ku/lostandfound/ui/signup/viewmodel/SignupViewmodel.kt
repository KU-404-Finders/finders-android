package com.ku.lostandfound.ui.signup.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class SignupViewmodel {
    var name by mutableStateOf("")
    var password by mutableStateOf("")
    var emailPrefix by mutableStateOf("")
    var verificationCode by mutableStateOf("")

    val email: String
        get() = if (emailPrefix.isBlank()) "" else "${emailPrefix.trim()}@konkuk.ac.kr"

    val isNameValid: Boolean
        get() = name.matches(Regex("^[가-힣]{2,5}$"))

    val isPasswordValid: Boolean
        get() = password.length >= 6 && password.any { it.isLetter() } && password.any { it.isDigit() }

    val isEmailValid: Boolean
        get() = emailPrefix.isNotBlank()

    val isVerificationCodeValid: Boolean
        get() = verificationCode.length == 6 && verificationCode.all { it.isDigit() }

    fun reset() {
        name = ""
        password = ""
        emailPrefix = ""
        verificationCode = ""
    }
}
