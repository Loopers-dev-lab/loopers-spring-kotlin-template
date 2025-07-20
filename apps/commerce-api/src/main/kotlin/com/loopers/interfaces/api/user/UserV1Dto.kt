package com.loopers.interfaces.api.user

import com.loopers.application.user.UserInfo
import com.loopers.domain.user.User.Gender
import jakarta.validation.constraints.Pattern

class UserV1Dto {
    data class UserResponse(
        val id: Long,
        val userName: String,
        val gender: Gender,
        val birthDate: String,
        val email: String,
    ) {
        companion object {
            fun from(user: UserInfo?): UserResponse? {
                return user
                    ?.let { UserResponse(it.id, it.userName, it.gender, it.birthDate, it.email) }
            }
        }
    }

    data class UserSignUpRequest(
        @field:Pattern(regexp = "^[a-zA-Z0-9]{1,10}$", message = "영문 및 숫자 10자 이내")
        val userName: String,

        val gender: Gender,

        @field:Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "yyyy-MM-dd")
        val birthDate: String,

        @field:Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\$", message = "xx@yy.zz")
        val email: String,
    ) {
        fun toUserSignUp(): UserInfo.SignUp {
            return UserInfo.SignUp.of(userName, gender, birthDate, email)
        }
    }
}
