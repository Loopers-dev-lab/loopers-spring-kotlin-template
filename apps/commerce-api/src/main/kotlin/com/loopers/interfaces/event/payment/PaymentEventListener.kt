package com.loopers.interfaces.event.payment

import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentCreatedEventV1
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class PaymentEventListener(
    private val paymentService: PaymentService,
) {
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPaymentCreated(event: PaymentCreatedEventV1) {
        paymentService.requestPgPayment(event.paymentId)
    }
}
