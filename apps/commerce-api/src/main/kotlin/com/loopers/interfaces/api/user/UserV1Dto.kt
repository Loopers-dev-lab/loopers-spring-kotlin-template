package com.loopers.interfaces.api.user

import com.loopers.application.user.UserInfo
import com.loopers.domain.user.Gender
import java.time.LocalDate

class UserV1Dto {
    data class RegisterUserRequest(
        val userId: String,
        val email: String,
        val birthDate: String,
        val gender: Gender,
    )

    data class UserResponse(
        val id: Long,
        val userId: String,
        val email: String,
        val birthDate: LocalDate,
        val gender: Gender,
        val point: Long,
    ) {
        companion object {
            fun from(info: UserInfo): UserResponse {
                return UserResponse(
                    id = info.id,
                    userId = info.userId,
                    email = info.email,
                    birthDate = info.birthDate,
                    gender = info.gender,
                    point = info.point,
                )
            }
        }
    }

    data class PointResponse(
        val point: Long,
    )

    data class ChargePointRequest(
        val amount: Long,
    )
}
