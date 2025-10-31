package com.loopers.domain.user

data class UserResult(
    val userId: String,
    val email: String,
    val birthDate: String,
    val gender: Gender,
) {
    companion object {
        fun from(user: User) = with(user) {
            UserResult(
                userId = userId.value,
                email = email.value,
                birthDate = birthDate.value,
                gender = gender,
            )
        }
    }
}
