package com.loopers.interfaces.api

import com.loopers.support.error.BatchErrorType
import com.loopers.support.error.BatchException
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class BatchControllerAdvice {
    private val log = LoggerFactory.getLogger(BatchControllerAdvice::class.java)

    @ExceptionHandler
    fun handle(e: BatchException): ResponseEntity<ApiResponse<*>> {
        log.warn("BatchException : {}", e.customMessage ?: e.message, e)
        return failureResponse(errorType = e.errorType, errorMessage = e.customMessage)
    }

    @ExceptionHandler
    fun handle(e: Throwable): ResponseEntity<ApiResponse<*>> {
        log.error("Exception : {}", e.message, e)
        val errorType = BatchErrorType.INTERNAL_ERROR
        return failureResponse(errorType = errorType)
    }

    private fun failureResponse(
        errorType: BatchErrorType,
        errorMessage: String? = null,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity(
            ApiResponse.fail(errorCode = errorType.code, errorMessage = errorMessage ?: errorType.message),
            errorType.status,
        )
}
