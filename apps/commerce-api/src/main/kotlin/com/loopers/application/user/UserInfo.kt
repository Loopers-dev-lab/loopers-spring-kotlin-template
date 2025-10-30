package com.loopers.application.user

import com.loopers.domain.user.UserModel
import java.time.LocalDate

data class UserInfo(val loginId: String, val email: String, val birthDate: LocalDate, val gender: String) {
    companion object {
        fun from(model: UserModel): UserInfo = UserInfo(
            loginId = model.loginId.value,
            email = model.email.value,
            birthDate = model.birthDate.value,
            gender = model.gender.name,
        )
    }
}
