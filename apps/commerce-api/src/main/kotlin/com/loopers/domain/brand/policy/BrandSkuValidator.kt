package com.loopers.domain.brand.policy

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

object BrandSkuValidator {

    fun validateSkuCode(code: String) {
        if (!Regex(BrandSkuPolicy.SkuCode.PATTERN).matches(code)) {
            throw CoreException(ErrorType.BAD_REQUEST, BrandSkuPolicy.SkuCode.MESSAGE)
        }
    }
}
