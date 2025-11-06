package com.loopers.interfaces.api.user

import com.loopers.application.user.UserV1Criteria
import com.loopers.domain.user.Gender
import io.swagger.v3.oas.annotations.media.Schema

class UserV1Request {

    @Schema(description = "회원가입 요청")
    data class SignUp(
        @field:Schema(
            description = "계정 ID (영문 및 숫자 10자 이내)",
        )
        val username: String,

        @field:Schema(
            description = "생일 (yyyy-MM-dd)",
        )
        val birth: String,

        @field:Schema(
            description = "이메일 (xx@yy.zz)",
        )
        val email: String,

        @field:Schema(
            description = "성별",
        )
        val gender: Gender,
    ) {
        fun to(): UserV1Criteria.SignUp = UserV1Criteria.SignUp(username, birth, email, gender)
    }
}
