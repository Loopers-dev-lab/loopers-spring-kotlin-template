package com.loopers.support.error

import org.springframework.http.HttpStatus

/**
 * Error types for batch module.
 * Based on api-design-weekly-monthly-ranking.md#2.2 Error Cases
 */
enum class BatchErrorType(val status: HttpStatus, val code: String, val message: String) {
    INTERNAL_ERROR(
        HttpStatus.INTERNAL_SERVER_ERROR,
        HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase,
        "An error occurred.",
    ),
    BAD_REQUEST(
        HttpStatus.BAD_REQUEST,
        HttpStatus.BAD_REQUEST.reasonPhrase,
        "Invalid request.",
    ),
    INVALID_PERIOD(
        HttpStatus.BAD_REQUEST,
        "INVALID_PERIOD",
        "Invalid period type",
    ),
    INVALID_DATE_FORMAT(
        HttpStatus.BAD_REQUEST,
        "INVALID_DATE_FORMAT",
        "Invalid date format",
    ),
    JOB_ALREADY_RUNNING(
        HttpStatus.CONFLICT,
        "JOB_ALREADY_RUNNING",
        "The batch is already running",
    ),
}
