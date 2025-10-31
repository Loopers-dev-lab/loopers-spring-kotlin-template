package com.loopers.interfaces.api.user

import com.loopers.domain.user.User
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

class UserRequest {
    data class UserCreateRequestDto(
        @field:Schema(description = "아이디", example = "user123")
        @field:NotBlank(message = "아이디는 필수값입니다.")
        val username: String,

        @field:Schema(description = "이메일", example = "user@example.com")
        @field:NotBlank(message = "이메일은 필수값입니다.")
        @field:Email(message = "이메일 형식이 올바르지 않습니다.")
        val email: String,

        @field:Schema(description = "생년월일 (yyyy-MM-dd 형식)", example = "1997-03-25")
        @field:NotBlank(message = "생년월일은 필수값입니다.")
        @field:Pattern(
            regexp = "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$",
            message = "생년월일은 yyyy-MM-dd 형식이어야 합니다.",
        )
        val birthDate: String,

        @field:Schema(description = "성별", example = "MALE")
        @field:NotNull(message = "성별은 필수값입니다.")
        val gender: User.Gender,
    )
}
