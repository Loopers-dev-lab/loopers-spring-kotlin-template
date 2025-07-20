package com.loopers.domain.user.validation

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

object UserValidator {

    fun validateUserName(userName: String) {
        if (!Regex(UserPolicy.UserName.PATTERN).matches(userName)) {
            throw CoreException(ErrorType.BAD_REQUEST, UserPolicy.UserName.MESSAGE)
        }
    }

    fun validateBirthDate(birthDate: String) {
        if (!Regex(UserPolicy.BirthDate.PATTERN).matches(birthDate)) {
            throw CoreException(ErrorType.BAD_REQUEST, UserPolicy.BirthDate.MESSAGE)
        }
    }

    fun validateEmail(email: String) {
        if (!Regex(UserPolicy.Email.PATTERN).matches(email)) {
            throw CoreException(ErrorType.BAD_REQUEST, UserPolicy.Email.MESSAGE)
        }
    }
}
