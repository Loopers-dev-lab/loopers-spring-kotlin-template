package com.loopers.application.payment

import com.loopers.domain.payment.PaymentService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentStateService(
    private val paymentService: PaymentService,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun paymentFailure(paymentId: Long) {
        val payment = paymentService.get(paymentId)
        payment.failure()
    }
}
