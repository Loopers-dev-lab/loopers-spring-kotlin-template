package com.loopers.interfaces.api.user

import com.loopers.application.user.UserV1Info
import com.loopers.domain.user.Gender
import java.time.LocalDate

class UserV1Response {
    data class SignUp(
        val id: Long,
        val username: String,
        val birth: LocalDate,
        val email: String,
        val gender: Gender,
    ) {
        companion object {
            fun from(info: UserV1Info.SignUp): SignUp {
                return SignUp(info.id, info.username, info.birth, info.email, info.gender)
            }
        }
    }

    data class GetUserById(
        val id: Long,
        val username: String,
        val birth: LocalDate,
        val email: String,
        val gender: Gender,
    ) {
        companion object {
            fun from(info: UserV1Info.GetById): GetUserById {
                return GetUserById(info.id, info.username, info.birth, info.email, info.gender)
            }
        }
    }
}
