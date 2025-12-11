package com.loopers.infrastructure.payment

import feign.FeignException
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException

/**
 * PG 통신 예외를 도메인 예외로 분류
 *
 * 핵심 판단 기준:
 * - Uncertain: 요청이 PG에 도달했는지 불확실 → 재시도 불가 (이중 결제 위험)
 * - NotReached: 요청이 PG에 도달하지 않음 확실 → 재시도 가능
 */
@Component
class PgExceptionClassifier {

    fun classify(e: Throwable): PgInfraException {
        if (e is PgInfraException) return e

        val root = findRootCause(e)

        return when {
            root is PgInfraException -> root
            root is FeignException -> classifyFeignException(root)
            root.isReadTimeout() -> PgResponseUncertainException("응답 타임아웃", root)
            root.isConnectionReset() -> PgResponseUncertainException("연결 끊김", root)
            root is ConnectException -> PgRequestNotReachedException("연결 실패", root)
            root is SocketTimeoutException -> PgRequestNotReachedException("연결 타임아웃", root)
            else -> PgRequestNotReachedException("네트워크 오류: ${root.message}", root)
        }
    }

    private fun classifyFeignException(e: FeignException): PgRequestNotReachedException {
        val msg = when (e.status()) {
            in 500..599 -> "PG 서버 오류"
            429 -> "요청 제한 초과"
            else -> "PG 요청 실패"
        }
        return PgRequestNotReachedException("$msg (status=${e.status()})", e)
    }

    private fun findRootCause(e: Throwable): Throwable {
        var cause = e
        while (cause.cause != null && cause.cause !== cause) {
            cause = cause.cause!!
        }
        return cause
    }

    private fun Throwable.isReadTimeout(): Boolean =
        this is SocketTimeoutException && message?.lowercase()?.contains("read timed out") == true

    private fun Throwable.isConnectionReset(): Boolean =
        (this is SocketException || this is IOException) && message?.lowercase()?.contains("connection reset") == true
}
