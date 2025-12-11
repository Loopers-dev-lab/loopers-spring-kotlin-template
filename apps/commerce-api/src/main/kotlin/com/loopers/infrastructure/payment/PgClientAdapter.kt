package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgPaymentCreateResult
import com.loopers.domain.payment.PgTransaction
import com.loopers.domain.payment.PgTransactionStatus
import com.loopers.support.values.Money
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import com.loopers.domain.payment.PgPaymentRequest as DomainPgPaymentRequest

@Component
class PgClientAdapter(
    private val pgGateway: PgGateway,
    @Value("\${pg.callback-base-url}")
    private val callbackBaseUrl: String,
) : PgClient {

    override fun requestPayment(request: DomainPgPaymentRequest): PgPaymentCreateResult {
        if (request.amount == Money.ZERO_KRW) {
            return PgPaymentCreateResult.NotRequired
        }

        return try {
            val infraRequest = request.toInfraRequest()
            val response = pgGateway.requestPayment(infraRequest)
            PgPaymentCreateResult.Accepted(response.transactionKey)
        } catch (e: PgInfraException) {
            e.toPaymentResult()
        } catch (e: CallNotPermittedException) {
            PgPaymentCreateResult.NotReached
        }
    }

    override fun findTransaction(transactionKey: String): PgTransaction {
        return pgGateway.findTransaction(transactionKey).toDomain()
    }

    override fun findTransactionsByPaymentId(paymentId: Long): List<PgTransaction> {
        val orderId = paymentId.toOrderId()
        val response = pgGateway.findTransactionsByOrderId(orderId)
        return response.transactions.map { it.toDomain(response.orderId) }
    }

    private fun DomainPgPaymentRequest.toInfraRequest() = PgPaymentRequest(
        orderId = paymentId.toOrderId(),
        cardType = cardInfo.cardType.name,
        cardNo = cardInfo.cardNo,
        amount = amount.amount.toLong(),
        callbackUrl = callbackBaseUrl,
    )

    private fun Long.toOrderId(): String = toString().padStart(6, '0')

    private fun PgPaymentDetailResponse.toDomain() = PgTransaction(
        transactionKey = transactionKey,
        paymentId = orderId.toLong(),
        status = PgTransactionStatus.valueOf(status),
        failureReason = reason,
    )

    private fun PgTransactionSummary.toDomain(orderId: String) = PgTransaction(
        transactionKey = transactionKey,
        paymentId = orderId.toLong(),
        status = PgTransactionStatus.valueOf(status),
        failureReason = reason,
    )

    private fun PgInfraException.toPaymentResult(): PgPaymentCreateResult = when (this) {
        is PgResponseUncertainException -> PgPaymentCreateResult.Uncertain
        is PgRequestNotReachedException -> PgPaymentCreateResult.NotReached
    }
}
