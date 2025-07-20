package com.loopers.application.user

import com.loopers.domain.user.User

data class UserInfo(
    val id: Long,
    val userName: String,
    val gender: User.Gender,
    val birthDate: String,
    val email: String,
) {
    companion object {
        fun from(user: User): UserInfo {
            return UserInfo(user.id, user.userName.value, user.gender, user.birthDate.value, user.email.value)
        }
    }

    data class SignUp(
        val userName: String,
        val gender: User.Gender,
        val birthDate: String,
        val email: String,
    ) {

        companion object {
            fun of(userName: String, gender: User.Gender, birthDate: String, email: String): SignUp {
                return SignUp(userName, gender, birthDate, email)
            }
        }

        fun toEntity(): User {
            return User.create(userName, gender, birthDate, email)
        }
    }
}
