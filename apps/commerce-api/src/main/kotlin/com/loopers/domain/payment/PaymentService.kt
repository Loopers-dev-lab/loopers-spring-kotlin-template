package com.loopers.domain.payment

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
class PaymentService(
    private val paymentRepository: PaymentRepository,
) {

    @Transactional
    fun createPayment(orderId: Long, userId: Long, amount: BigDecimal): Payment {
        val payment = Payment.of(
            orderId = orderId,
            userId = userId,
            amount = amount,
        )
        payment.complete()
        return paymentRepository.save(payment)
    }
}
