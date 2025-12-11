package com.loopers.infrastructure.payment

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.springframework.stereotype.Component

@Component
class PgGateway(
    private val pgPaymentFeignClient: PgPaymentFeignClient,
    private val pgQueryFeignClient: PgQueryFeignClient,
    private val exceptionClassifier: PgExceptionClassifier,
) {

    @CircuitBreaker(name = "pg-payment")
    @Retry(name = "pg-payment")
    fun requestPayment(request: PgPaymentRequest): PgPaymentResponse {
        return try {
            extractData(pgPaymentFeignClient.requestPayment(request))
        } catch (e: Exception) {
            throw exceptionClassifier.classify(e)
        }
    }

    @CircuitBreaker(name = "pg-query")
    @Retry(name = "pg-query")
    fun findTransaction(transactionKey: String): PgPaymentDetailResponse {
        return try {
            extractData(pgQueryFeignClient.getPayment(transactionKey))
        } catch (e: Exception) {
            throw exceptionClassifier.classify(e)
        }
    }

    @CircuitBreaker(name = "pg-query")
    @Retry(name = "pg-query")
    fun findTransactionsByOrderId(orderId: String): PgPaymentListResponse {
        return try {
            extractData(pgQueryFeignClient.getPaymentsByOrderId(orderId))
        } catch (e: Exception) {
            throw exceptionClassifier.classify(e)
        }
    }

    private fun <T> extractData(response: PgResponse<T>): T =
        response.data ?: throw PgRequestNotReachedException("PG 응답 데이터 없음")
}
