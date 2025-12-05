package com.loopers.domain.payment

/**
 * PG 서비스 사용 불가 예외
 *
 * PG에 결제 요청을 할 수 없는 상황을 나타냅니다.
 * 요청이 PG에 도달하지 않았음이 확실하므로 즉시 롤백 가능합니다.
 *
 * 발생 조건:
 * - 연결 타임아웃/실패
 * - 서킷 브레이커 오픈 상태
 * - PG 서버 오류 (5xx)
 */
class PgServiceUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
