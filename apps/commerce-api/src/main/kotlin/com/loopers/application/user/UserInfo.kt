package com.loopers.application.user

import com.loopers.domain.user.Gender
import com.loopers.domain.user.User
import java.time.LocalDate

data class UserInfo(
    val id: Long,
    val userId: String,
    val email: String,
    val birthDate: LocalDate,
    val gender: Gender,
    val point: Long,
) {
    companion object {
        fun from(user: User): UserInfo {
            return UserInfo(
                id = user.id,
                userId = user.userId,
                email = user.email,
                birthDate = user.birthDate,
                gender = user.gender,
                point = user.point,
            )
        }
    }
}
