package com.loopers.application.user

import com.loopers.domain.user.Gender
import java.time.LocalDate

data class UserRegisterRequest(val name: String, val email: String, val gender: Gender, val birthDate: LocalDate)
