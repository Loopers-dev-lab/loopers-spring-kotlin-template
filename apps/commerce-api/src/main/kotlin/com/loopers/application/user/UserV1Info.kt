package com.loopers.application.user

import com.loopers.domain.user.Gender
import com.loopers.domain.user.User
import java.time.LocalDate

class UserV1Info {
    data class SignUp(
        val id: Long,
        val username: String,
        val gender: Gender,
        val birth: LocalDate,
        val email: String,
    ) {
        companion object {
            fun from(user: User): SignUp = SignUp(
                id = user.id,
                birth = user.birth,
                email = user.email,
                gender = user.gender,
                username = user.username,
            )
        }
    }

    data class GetById(
        val id: Long,
        val username: String,
        val gender: Gender,
        val birth: LocalDate,
        val email: String,
    ) {
        companion object {
            fun from(user: User): GetById = GetById(
                id = user.id,
                birth = user.birth,
                email = user.email,
                gender = user.gender,
                username = user.username,
            )
        }
    }
}
