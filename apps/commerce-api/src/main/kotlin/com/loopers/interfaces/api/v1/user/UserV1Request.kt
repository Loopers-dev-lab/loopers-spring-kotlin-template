package com.loopers.interfaces.api.v1.user

import com.loopers.domain.user.Gender
import com.loopers.domain.user.UserCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

object UserV1Request {

    data class SignUp(
        @field:NotBlank
        val userId: String,

        @field:NotBlank
        val email: String,

        @field:NotBlank
        val birthDate: String,

        @field:NotNull
        val gender: Gender,
    ) {
        fun toCommand() = UserCommand.SignUp(
            userId = userId,
            email = email,
            birthDate = birthDate,
            gender = gender,
        )
    }
}
