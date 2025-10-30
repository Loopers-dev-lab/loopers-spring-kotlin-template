package com.loopers.application.user

data class UserRegisterCommand(val loginId: String, val email: String, val birthDate: String, val gender: String)
