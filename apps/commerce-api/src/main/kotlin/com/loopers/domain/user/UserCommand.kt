package com.loopers.domain.user

class UserCommand {
    data class SignUp(
        val username: String,
        val birth: String,
        val email: String,
        val gender: Gender,
    )
}
