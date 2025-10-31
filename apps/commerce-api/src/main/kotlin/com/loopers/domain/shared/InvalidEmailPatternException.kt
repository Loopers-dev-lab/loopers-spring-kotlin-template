package com.loopers.domain.shared

import com.loopers.support.error.ErrorType

class InvalidEmailPatternException(
    val errorType: ErrorType,
    val customMessage: String? = null,
) : RuntimeException(customMessage ?: errorType.message)
