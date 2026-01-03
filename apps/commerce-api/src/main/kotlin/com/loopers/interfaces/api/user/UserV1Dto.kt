package com.loopers.interfaces.api.user

import com.loopers.application.user.UserInfo
import com.loopers.application.user.UserRegisterRequest
import com.loopers.domain.user.Gender
import java.time.LocalDate
import java.time.LocalDateTime

class UserV1Dto {
    data class RegisterRequest(val name: String, val email: String, val gender: Gender, val birthDate: LocalDate) {
        fun toCommand(): UserRegisterRequest = UserRegisterRequest(
            name = name,
            email = email,
            gender = gender,
            birthDate = birthDate,
        )
    }

    data class UserResponse(
        val id: Long,
        val name: String,
        val email: String,
        val gender: String,
        val birthDate: LocalDate,
        val createdAt: LocalDateTime,
    ) {
        companion object {
            fun from(info: UserInfo): UserResponse = UserResponse(
                id = info.id,
                name = info.name,
                email = info.email,
                gender = info.gender,
                birthDate = info.birthDate,
                createdAt = info.createdAt,
            )
        }
    }
}
