package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgPaymentCreateResult
import com.loopers.domain.payment.PgPaymentRequest
import com.loopers.domain.payment.PgTransaction
import com.loopers.support.values.Money
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Component
@Primary
class PgClientResultDecorator(
    @Qualifier("pgClientImpl")
    private val delegate: PgClient,
) : PgClient {

    override fun requestPayment(request: PgPaymentRequest): PgPaymentCreateResult {
        if (request.amount == Money.ZERO_KRW) {
            return PgPaymentCreateResult.NotRequired
        }

        return try {
            delegate.requestPayment(request)
        } catch (e: PgInfraException) {
            when (e) {
                is PgResponseUncertainException -> PgPaymentCreateResult.Uncertain
                is PgRequestNotReachedException -> PgPaymentCreateResult.NotReached
            }
        } catch (e: CallNotPermittedException) {
            PgPaymentCreateResult.NotReached
        }
    }

    override fun findTransaction(transactionKey: String): PgTransaction {
        return delegate.findTransaction(transactionKey)
    }

    override fun findTransactionsByPaymentId(paymentId: Long): List<PgTransaction> {
        return delegate.findTransactionsByPaymentId(paymentId)
    }
}
