package com.loopers.domain.member

import com.loopers.support.error.ErrorType

class DuplicateMemberIdException(
    val errorType: ErrorType,
    val customMessage: String? = null,
) : RuntimeException(customMessage ?: errorType.message)
