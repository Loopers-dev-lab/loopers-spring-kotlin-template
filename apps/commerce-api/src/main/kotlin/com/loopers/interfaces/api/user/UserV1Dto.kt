package com.loopers.interfaces.api.user

import com.loopers.application.user.UserInfo
import com.loopers.domain.user.Gender

class UserV1Dto {
    data class RegisterUserRequest(val userId: String, val email: String, val birth: String, val gender: Gender)
    data class UserResponse(val userId: String, val email: String, val birth: String, val gender: Gender) {
        companion object {
            fun from(user: UserInfo): UserResponse {
                return UserResponse(
                    userId = user.userId,
                    email = user.email,
                    birth = user.birth,
                    gender = user.gender,
                )
            }
        }
    }
}
