package com.loopers.support.error

class BatchException(
    val errorType: BatchErrorType,
    val customMessage: String? = null,
) : RuntimeException(customMessage ?: errorType.message)
