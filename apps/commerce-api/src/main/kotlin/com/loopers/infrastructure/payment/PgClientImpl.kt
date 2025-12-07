package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgPaymentCreateResult
import com.loopers.domain.payment.PgTransaction
import com.loopers.domain.payment.PgTransactionStatus
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import com.loopers.domain.payment.PgPaymentRequest as DomainPgPaymentRequest

@Component
class PgClientImpl(
    private val pgPaymentFeignClient: PgPaymentFeignClient,
    private val pgQueryFeignClient: PgQueryFeignClient,
    private val exceptionClassifier: PgExceptionClassifier,
    @Value("\${pg.callback-base-url}")
    private val callbackBaseUrl: String,
) : PgClient {

    @CircuitBreaker(name = "pg-payment", fallbackMethod = "requestPaymentFallback")
    @Retry(name = "pg-payment")
    override fun requestPayment(request: DomainPgPaymentRequest): PgPaymentCreateResult {
        val infraRequest = request.toInfraRequest(callbackBaseUrl)

        return try {
            val response = pgPaymentFeignClient.requestPayment(infraRequest)
            val data = extractData(response)
            PgPaymentCreateResult.Accepted(data.transactionKey)
        } catch (e: Exception) {
            throw exceptionClassifier.classify(e)
        }
    }

    @Suppress("unused")
    private fun requestPaymentFallback(
        request: DomainPgPaymentRequest,
        e: Exception,
    ): PgPaymentCreateResult = when (e) {
        is PgResponseUncertainException -> PgPaymentCreateResult.Uncertain
        else -> PgPaymentCreateResult.NotReached
    }

    @CircuitBreaker(name = "pg-query")
    @Retry(name = "pg-query")
    override fun findTransaction(transactionKey: String): PgTransaction {
        return try {
            val result = pgQueryFeignClient.getPayment(transactionKey)
                .let { extractData(it) }
                .let { toDomainTransaction(it) }
            result
        } catch (e: Exception) {
            throw exceptionClassifier.classify(e)
        }
    }

    @CircuitBreaker(name = "pg-query")
    @Retry(name = "pg-query")
    override fun findTransactionsByPaymentId(paymentId: Long): List<PgTransaction> {
        return try {
            // 도메인의 paymentId를 외부 PG API의 orderId 파라미터로 전달
            val response = pgQueryFeignClient.getPaymentsByOrderId(paymentId.toString().padStart(6, '0'))
            val data = extractData(response)
            val result = data.transactions.map { summary ->
                PgTransaction(
                    transactionKey = summary.transactionKey,
                    paymentId = data.orderId.toLong(),
                    status = PgTransactionStatus.valueOf(summary.status),
                    failureReason = summary.reason,
                )
            }
            result
        } catch (e: Exception) {
            throw exceptionClassifier.classify(e)
        }
    }

    private fun <T> extractData(response: PgResponse<T>): T =
        response.data ?: throw PgRequestNotReachedException("PG 응답 데이터 없음")

    private fun DomainPgPaymentRequest.toInfraRequest(callbackUrl: String) = PgPaymentRequest(
        // 도메인의 paymentId를 외부 PG API의 orderId 필드로 변환
        orderId = paymentId.toString().padStart(6, '0'),
        cardType = cardInfo.cardType.name,
        cardNo = cardInfo.cardNo,
        amount = amount.amount.toLong(),
        callbackUrl = callbackUrl,
    )

    private fun toDomainTransaction(response: PgPaymentDetailResponse) = PgTransaction(
        transactionKey = response.transactionKey,
        // 외부 PG API의 orderId 응답을 도메인의 paymentId로 변환
        paymentId = response.orderId.toLong(),
        status = PgTransactionStatus.valueOf(response.status),
        failureReason = response.reason,
    )
}
