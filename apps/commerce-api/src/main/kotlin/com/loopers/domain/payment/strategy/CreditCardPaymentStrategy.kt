package com.loopers.domain.payment.strategy

import com.loopers.domain.order.entity.Order
import com.loopers.domain.payment.entity.Payment
import com.loopers.domain.payment.type.CardType
import com.loopers.infrastructure.pg.PgDto
import com.loopers.infrastructure.pg.PgGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CreditCardPaymentStrategy(
    private val pgGateway: PgGateway,
) : PaymentStrategy {
    override fun supports() = Payment.Method.CREDIT_CARD

    @Transactional
    override fun process(order: Order, payment: Payment) {
        val req = PgDto.PaymentRequest(
            orderId = "LOOPERS-" + order.id,
            cardType = CardType.of(payment.cardType),
            cardNo = payment.cardNumber,
            amount = payment.paymentPrice.value.toLong(),
            callbackUrl = "http://localhost:8080/api/v1/payments/webhook",
        )

        when (val result = pgGateway.payment(order.userId, req)) {
            is PgGateway.Result.Ok -> {
                payment.updateTransactionKey(result.transactionKey)
            }

            is PgGateway.Result.BadRequest -> {
                payment.failure(result.message)
                order.failure(result.message)
            }

            is PgGateway.Result.Retryable -> {
                payment.failure(result.message)
                order.failure(result.message)
            }
        }
    }
}
