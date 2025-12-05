package com.loopers.infrastructure.pg

/**
 * PG 호출 관련 예외
 */
sealed class PgException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    /**
     * 요청이 PG에 도달하지 않음이 확실한 경우
     * 재시도 가능, 즉시 롤백 가능
     */
    class RequestNotReached(
        message: String,
        cause: Throwable? = null,
    ) : PgException(message, cause)

    /**
     * 요청이 PG에 도달했는지 불확실한 경우 (Read Timeout 등)
     * 재시도 불가, IN_PROGRESS 유지 필요
     */
    class ResponseUncertain(
        message: String,
        cause: Throwable? = null,
    ) : PgException(message, cause)

    /**
     * 서킷 브레이커가 열린 상태
     * 즉시 실패, 즉시 롤백 가능
     */
    class CircuitOpen(
        message: String,
    ) : PgException(message)

    /**
     * PG 비즈니스 에러 (잘못된 카드번호, 한도 초과 등)
     * 재시도 불가, 사용자에게 에러 메시지 전달
     */
    class BusinessError(
        val errorCode: String,
        message: String,
    ) : PgException(message)
}
