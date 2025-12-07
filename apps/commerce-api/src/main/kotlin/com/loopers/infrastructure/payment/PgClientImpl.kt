package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgPaymentCreateResult
import com.loopers.domain.payment.PgPaymentRequest
import com.loopers.domain.payment.PgTransaction
import com.loopers.domain.payment.PgTransactionStatus
import com.loopers.support.values.Money
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import com.loopers.domain.payment.CardType as DomainCardType

@Component
class PgClientImpl(
    private val pgFeignClient: PgFeignClient,
    private val exceptionClassifier: PgExceptionClassifier,
    private val meterRegistry: MeterRegistry,
    @Value("\${pg.callback-base-url}")
    private val callbackBaseUrl: String,
) : PgClient {

    @CircuitBreaker(name = "pg", fallbackMethod = "requestPaymentFallback")
    @Retry(name = "pg")
    override fun requestPayment(request: PgPaymentRequest): PgPaymentCreateResult {
        val sample = Timer.start(meterRegistry)
        val infraRequest = request.toInfraRequest(callbackBaseUrl)

        return try {
            val response = pgFeignClient.requestPayment(infraRequest)
            val data = extractData(response)
            recordSuccess(sample, "requestPayment")
            PgPaymentCreateResult.Accepted(data.transactionKey)
        } catch (e: Exception) {
            recordFailure(sample, "requestPayment", e)
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
        val sample = Timer.start(meterRegistry)

        return try {
            val result = pgFeignClient.getPayment(transactionKey)
                .let { extractData(it) }
                .let { toDomainTransaction(it) }
            recordSuccess(sample, "findTransaction")
            result
        } catch (e: Exception) {
            recordFailure(sample, "findTransaction", e)
            throw exceptionClassifier.classify(e)
        }
    }

    @CircuitBreaker(name = "pg")
    @Retry(name = "pg")
    override fun findTransactionsByOrderId(orderId: Long): List<PgTransaction> {
        val sample = Timer.start(meterRegistry)

        return try {
            val response = pgFeignClient.getPaymentsByOrderId(orderId.toString().padStart(6, '0'))
            val result = extractData(response).transactions.map { summary ->
                pgFeignClient.getPayment(summary.transactionKey)
                    .let { extractData(it) }
                    .let { toDomainTransaction(it) }
            }
            recordSuccess(sample, "findTransactionsByOrderId")
            result
        } catch (e: Exception) {
            recordFailure(sample, "findTransactionsByOrderId", e)
            throw exceptionClassifier.classify(e)
        }
    }

    private fun <T> extractData(response: PgResponse<T>): T =
        response.data ?: throw PgRequestNotReachedException("PG 응답 데이터 없음")

    private fun PgPaymentRequest.toInfraRequest(callbackUrl: String) = PgPaymentRequest(
        orderId = orderId.toString().padStart(6, '0'),
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

    private fun recordSuccess(sample: Timer.Sample, method: String) {
        sample.stop(meterRegistry.timer(METRIC_LATENCY, TAG_RESULT, "success", TAG_METHOD, method))
        meterRegistry.counter(METRIC_TOTAL, TAG_RESULT, "success", TAG_METHOD, method).increment()
    }

    private fun recordFailure(sample: Timer.Sample, method: String, e: Exception) {
        sample.stop(meterRegistry.timer(METRIC_LATENCY, TAG_RESULT, "failure", TAG_METHOD, method))
        meterRegistry.counter(
            METRIC_TOTAL,
            TAG_RESULT,
            "failure",
            TAG_METHOD,
            method,
            TAG_ERROR,
            e.javaClass.simpleName,
        ).increment()
    }

    companion object {
        private const val METRIC_LATENCY = "pg_request_latency_seconds"
        private const val METRIC_TOTAL = "pg_request_total"
        private const val TAG_RESULT = "result"
        private const val TAG_METHOD = "method"
        private const val TAG_ERROR = "error"
    }
}
