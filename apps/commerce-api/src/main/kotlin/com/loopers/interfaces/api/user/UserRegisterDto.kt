package com.loopers.interfaces.api.user

import com.loopers.application.user.UserInfo
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate

class UserRegisterDto {
    data class Request(
        @field:NotBlank(message = "로그인 아이디는 필수입니다")
        val loginId: String,
        @field:NotBlank(message = "이메일은 필수입니다")
        val email: String,
        @field:NotBlank(message = "생년월일은 필수입니다")
        val birthDate: String,
        @field:NotBlank(message = "성별은 필수입니다")
        val gender: String,
    )

    data class Response(val loginId: String, val email: String, val birthDate: LocalDate, val gender: String) {
        companion object {
            fun from(info: UserInfo): Response = Response(
                loginId = info.loginId,
                email = info.email,
                birthDate = info.birthDate,
                gender = info.gender,
            )
        }
    }
}
