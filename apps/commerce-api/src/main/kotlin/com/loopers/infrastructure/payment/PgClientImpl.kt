package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgPaymentCreateResult
import com.loopers.domain.payment.PgPaymentRequest
import com.loopers.domain.payment.PgServiceUnavailableException
import com.loopers.domain.payment.PgTransaction
import com.loopers.domain.payment.PgTransactionStatus
import com.loopers.support.values.Money
import feign.FeignException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryRegistry
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import com.loopers.domain.payment.CardType as DomainCardType

@Component
class PgClientImpl(
    private val pgFeignClient: PgFeignClient,
    circuitBreakerRegistry: CircuitBreakerRegistry,
    retryRegistry: RetryRegistry,
) : PgClient {

    private val circuitBreaker: CircuitBreaker = circuitBreakerRegistry.circuitBreaker("pg")
    private val retry: Retry = retryRegistry.retry("pg")

    override fun requestPayment(request: PgPaymentRequest): PgPaymentCreateResult {
        return try {
            val infraRequest = PgPaymentRequest(
                orderId = request.orderId.toString(),
                cardType = request.cardInfo.cardType.name,
                cardNo = request.cardInfo.cardNo,
                amount = request.amount.amount.toLong(),
                callbackUrl = request.callbackUrl,
            )

            val response = executeWithResilience {
                pgFeignClient.requestPayment(infraRequest)
            }

            val data = extractData(response)

            PgPaymentCreateResult.Accepted(data.transactionKey)
        } catch (e: PgResponseUncertainException) {
            PgPaymentCreateResult.Uncertain
        } catch (e: PgRequestNotReachedException) {
            throw PgServiceUnavailableException(e.message ?: "PG 서비스 불가", e)
        }
    }

    override fun findTransaction(transactionKey: String): PgTransaction {
        return try {
            val response = executeWithResilience {
                pgFeignClient.getPayment(transactionKey)
            }
            toDomainTransaction(extractData(response))
        } catch (e: PgInfraException) {
            throw PgServiceUnavailableException(e.message ?: "PG 서비스 불가", e)
        }
    }

    override fun findTransactionsByOrderId(orderId: Long): List<PgTransaction> {
        return try {
            val response = executeWithResilience {
                pgFeignClient.getPaymentsByOrderId(orderId.toString())
            }
            val data = extractData(response)

            data.transactions.map { summary ->
                val detail = executeWithResilience {
                    pgFeignClient.getPayment(summary.transactionKey)
                }
                toDomainTransaction(extractData(detail))
            }
        } catch (e: PgInfraException) {
            throw PgServiceUnavailableException(e.message ?: "PG 서비스 불가", e)
        }
    }

    /**
     * Resilience 적용 + 예외 분류
     *
     * 실행 순서: CircuitBreaker(Retry(실제 호출))
     * - Retry가 안쪽: 일시적 오류를 재시도로 흡수
     * - CircuitBreaker가 바깥쪽: 재시도 후 최종 결과만 서킷에 기록
     */
    private fun <T> executeWithResilience(block: () -> T): T {
        val retrySupplier = Retry.decorateSupplier(retry) {
            try {
                block()
            } catch (e: Exception) {
                throw classifyException(e)
            }
        }

        return try {
            circuitBreaker.executeSupplier(retrySupplier)
        } catch (e: CallNotPermittedException) {
            throw PgRequestNotReachedException("서킷 브레이커 오픈. ${e.message}", e)
        } catch (e: PgInfraException) {
            throw e
        }
    }

    /**
     * 예외를 PgInfraException으로 분류
     *
     * 핵심 분류 기준:
     * - 요청 도달 불확실 (ResponseUncertain): Read Timeout, Connection Reset
     * - 요청 미도달 확실 (RequestNotReached): Connection Timeout, Connection Refused, HTTP 에러
     */
    private fun classifyException(e: Exception): PgInfraException {
        val cause = findRootCause(e)
        val message = cause.message?.lowercase() ?: ""

        return when {
            cause is FeignException -> {
                val status = cause.status()
                val detail = "status=$status, message=${cause.message}"
                when (status) {
                    in 500..599, 429 -> PgRequestNotReachedException("PG 서버 오류. $detail", cause)
                    else -> PgRequestNotReachedException("PG 요청 오류. $detail", cause)
                }
            }

            cause is SocketTimeoutException && message.contains("read timed out") -> {
                PgResponseUncertainException("응답 타임아웃. ${cause.message}", cause)
            }

            (cause is SocketException || cause is IOException) && message.contains("connection reset") -> {
                PgResponseUncertainException("연결 끊김. ${cause.message}", cause)
            }

            cause is SocketTimeoutException -> {
                PgRequestNotReachedException("연결 타임아웃. ${cause.message}", cause)
            }

            cause is ConnectException -> {
                PgRequestNotReachedException("연결 실패. ${cause.message}", cause)
            }

            else -> {
                PgRequestNotReachedException("네트워크 오류. ${cause.message}", cause)
            }
        }
    }

    private fun findRootCause(e: Throwable): Throwable {
        var cause: Throwable = e
        while (cause.cause != null && cause.cause !== cause) {
            cause = cause.cause!!
        }
        return cause
    }

    private fun <T> extractData(response: PgResponse<T>): T {
        return response.data
            ?: throw PgRequestNotReachedException("PG 응답 데이터 없음")
    }

    private fun toDomainTransaction(response: PgPaymentDetailResponse): PgTransaction {
        return PgTransaction(
            transactionKey = response.transactionKey,
            orderId = response.orderId.toLong(),
            cardType = DomainCardType.valueOf(response.cardType),
            cardNo = response.cardNo,
            amount = Money.krw(response.amount),
            status = PgTransactionStatus.valueOf(response.status),
            failureReason = response.reason,
        )
    }
}
