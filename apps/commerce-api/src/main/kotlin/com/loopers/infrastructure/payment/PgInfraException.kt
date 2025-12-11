package com.loopers.infrastructure.payment

/**
 * PG 인프라 예외 베이스
 */
sealed class PgInfraException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * 요청이 PG에 도달하지 않음이 확실한 경우
 *
 * 재시도 가능, 즉시 롤백 가능
 */
class PgRequestNotReachedException(
    message: String,
    cause: Throwable? = null,
) : PgInfraException(message, cause)

/**
 * 요청이 PG에 도달했는지 불확실한 경우
 *
 * 재시도 불가 (이중 결제 위험), IN_PROGRESS 유지
 */
class PgResponseUncertainException(
    message: String,
    cause: Throwable? = null,
) : PgInfraException(message, cause)
