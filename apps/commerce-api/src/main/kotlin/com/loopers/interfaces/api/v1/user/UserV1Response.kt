package com.loopers.interfaces.api.v1.user

import com.loopers.domain.user.Gender
import com.loopers.domain.user.UserResult

object UserV1Response {

    data class SignUp(
        val userId: String,
        val email: String,
        val birthDate: String,
        val gender: Gender,
    ) {
        companion object {
            fun from(userResult: UserResult) = with(userResult) {
                SignUp(
                    userId = userId,
                    email = email,
                    birthDate = birthDate,
                    gender = gender,
                )
            }
        }
    }

    data class MyInfo(
        val userId: String,
        val email: String,
        val birthDate: String,
        val gender: Gender,
    ) {
        companion object {
            fun from(userResult: UserResult) = with(userResult) {
                MyInfo(
                    userId = userId,
                    email = email,
                    birthDate = birthDate,
                    gender = gender,
                )
            }
        }
    }
}
