package com.loopers.application.user

import com.loopers.domain.user.Gender
import com.loopers.domain.user.UserCommand

class UserV1Criteria {
    data class SignUp(
        val username: String,
        val birth: String,
        val email: String,
        val gender: Gender,
    ) {
        fun to(): UserCommand.SignUp = UserCommand.SignUp(username, birth, email, gender)
    }
}
