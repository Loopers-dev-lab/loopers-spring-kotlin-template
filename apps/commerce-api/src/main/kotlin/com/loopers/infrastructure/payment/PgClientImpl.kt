package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgPaymentCreateResult
import com.loopers.domain.payment.PgPaymentRequest
import com.loopers.domain.payment.PgTransaction
import com.loopers.domain.payment.PgTransactionStatus
import com.loopers.support.values.Money
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.springframework.stereotype.Component
import com.loopers.domain.payment.CardType as DomainCardType

@Component
class PgClientImpl(
    private val pgFeignClient: PgFeignClient,
    private val exceptionClassifier: PgExceptionClassifier,
) : PgClient {

    @CircuitBreaker(name = "pg", fallbackMethod = "requestPaymentFallback")
    @Retry(name = "pg")
    override fun requestPayment(request: PgPaymentRequest): PgPaymentCreateResult {
        val infraRequest = request.toInfraRequest()

        return try {
            val response = pgFeignClient.requestPayment(infraRequest)
            val data = extractData(response)
            PgPaymentCreateResult.Accepted(data.transactionKey)
        } catch (e: Exception) {
            throw exceptionClassifier.classify(e)
        }
    }

    @Suppress("unused")
    private fun requestPaymentFallback(
        request: PgPaymentRequest,
        e: Exception,
    ): PgPaymentCreateResult = when (e) {
        is PgResponseUncertainException -> PgPaymentCreateResult.Uncertain
        else -> PgPaymentCreateResult.NotReached
    }

    @CircuitBreaker(name = "pg")
    @Retry(name = "pg")
    override fun findTransaction(transactionKey: String): PgTransaction {
        return try {
            pgFeignClient.getPayment(transactionKey)
                .let { extractData(it) }
                .let { toDomainTransaction(it) }
        } catch (e: Exception) {
            throw exceptionClassifier.classify(e)
        }
    }

    @CircuitBreaker(name = "pg")
    @Retry(name = "pg")
    override fun findTransactionsByOrderId(orderId: Long): List<PgTransaction> {
        return try {
            val response = pgFeignClient.getPaymentsByOrderId(orderId.toString())
            extractData(response).transactions.map { summary ->
                pgFeignClient.getPayment(summary.transactionKey)
                    .let { extractData(it) }
                    .let { toDomainTransaction(it) }
            }
        } catch (e: Exception) {
            throw exceptionClassifier.classify(e)
        }
    }

    private fun <T> extractData(response: PgResponse<T>): T =
        response.data ?: throw PgRequestNotReachedException("PG 응답 데이터 없음")

    private fun PgPaymentRequest.toInfraRequest() = com.loopers.infrastructure.payment.PgPaymentRequest(
        orderId = orderId.toString(),
        cardType = cardInfo.cardType.name,
        cardNo = cardInfo.cardNo,
        amount = amount.amount.toLong(),
        callbackUrl = callbackUrl,
    )

    private fun toDomainTransaction(response: PgPaymentDetailResponse) = PgTransaction(
        transactionKey = response.transactionKey,
        orderId = response.orderId.toLong(),
        cardType = DomainCardType.valueOf(response.cardType),
        cardNo = response.cardNo,
        amount = Money.krw(response.amount),
        status = PgTransactionStatus.valueOf(response.status),
        failureReason = response.reason,
    )
}
