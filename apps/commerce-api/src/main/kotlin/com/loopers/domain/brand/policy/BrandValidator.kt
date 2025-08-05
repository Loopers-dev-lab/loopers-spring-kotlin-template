package com.loopers.domain.brand.policy

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

object BrandValidator {

    fun validateName(name: String) {
        if (!Regex(BrandPolicy.Name.PATTERN).matches(name)) {
            throw CoreException(ErrorType.BAD_REQUEST, BrandPolicy.Name.MESSAGE)
        }
    }

    fun validateDescription(description: String) {
        if (!Regex(BrandPolicy.Description.PATTERN).matches(description)) {
            throw CoreException(ErrorType.BAD_REQUEST, BrandPolicy.Description.MESSAGE)
        }
    }
}
