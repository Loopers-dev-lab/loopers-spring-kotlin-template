package com.loopers.interfaces.api.user

import com.loopers.application.user.UserInfo
import java.time.LocalDate

class UserInfoDto {
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
