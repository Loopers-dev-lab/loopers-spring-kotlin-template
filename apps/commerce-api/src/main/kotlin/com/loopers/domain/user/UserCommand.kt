package com.loopers.domain.user

object UserCommand {

    data class SignUp(
        val userId: String,
        val email: String,
        val birthDate: String,
        val gender: Gender,
    )
}
