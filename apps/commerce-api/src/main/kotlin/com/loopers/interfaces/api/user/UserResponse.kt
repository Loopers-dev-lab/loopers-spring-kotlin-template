package com.loopers.interfaces.api.user

import com.loopers.domain.user.User
import com.loopers.domain.user.UserResult
import io.swagger.v3.oas.annotations.media.Schema

class UserResponse {
    data class UserResponseDto(
        @get:Schema(description = "아이디", example = "user123")
        val username: String,

        @get:Schema(description = "이메일", example = "user@example.com")
        val email: String,

        @get:Schema(description = "생년월일 (yyyy-MM-dd 형식)", example = "1997-03-25")
        val birthDate: String,

        @get:Schema(description = "성별", example = "MALE")
        val gender: User.Gender,
    ) {
        companion object {
            fun from(result: UserResult.UserInfoResult): UserResponseDto {
                return UserResponseDto(
                    username = result.username,
                    email = result.email,
                    birthDate = result.birthDate,
                    gender = result.gender,
                )
            }
        }
    }
}
