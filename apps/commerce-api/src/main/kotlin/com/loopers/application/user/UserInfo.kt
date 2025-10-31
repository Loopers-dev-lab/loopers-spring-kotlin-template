package com.loopers.application.user

import com.loopers.domain.user.Gender
import com.loopers.domain.user.User

data class UserInfo(
    val userId: String,
    val email: String,
    val birth: String,
    val gender: Gender,
) {
    companion object {
        fun from(user: User): UserInfo {
            return UserInfo(
                userId = user.userId,
                email = user.email,
                birth = user.birth,
                gender = user.gender,
            )
        }
    }
}
