package com.loopers.application.user

import com.loopers.domain.user.User
import java.time.LocalDate
import java.time.ZonedDateTime

data class UserInfo(
    val id: Long,
    val name: String,
    val email: String,
    val gender: String,
    val birthDate: LocalDate,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun from(user: User): UserInfo = UserInfo(
                id = user.id,
                name = user.name,
                email = user.email,
                gender = user.gender.name,
                birthDate = user.birthDate,
                createdAt = user.createdAt,
            )
    }
}
