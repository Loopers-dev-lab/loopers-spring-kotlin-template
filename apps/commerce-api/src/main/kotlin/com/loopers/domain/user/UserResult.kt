package com.loopers.domain.user

class UserResult {

    data class UserInfoResult(
        val username: String,
        val email: String,
        val birthDate: String,
        val gender: User.Gender,
    ) {
        companion object {
            fun from(user: User): UserInfoResult {
                return UserInfoResult(
                    username = user.username,
                    email = user.email,
                    birthDate = user.birthDate,
                    gender = user.gender,
                )
            }
        }
    }
}
