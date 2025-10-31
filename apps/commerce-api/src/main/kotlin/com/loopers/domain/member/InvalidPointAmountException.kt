package com.loopers.domain.member

import com.loopers.support.error.ErrorType

class InvalidPointAmountException(
    val errorType: ErrorType,
    val customMessage: String? = null,
) : RuntimeException(customMessage ?: errorType.message)
