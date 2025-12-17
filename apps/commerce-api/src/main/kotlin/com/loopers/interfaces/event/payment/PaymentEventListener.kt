package com.loopers.interfaces.event.payment

import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentCreatedEventV1
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class PaymentEventListener(
    private val paymentService: PaymentService,
) {
    private val logger = LoggerFactory.getLogger(PaymentEventListener::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPaymentCreated(event: PaymentCreatedEventV1) {
        logger.info("[PaymentCreatedEventV1] start - paymentId: ${event.paymentId}")
        try {
            paymentService.requestPgPayment(event.paymentId)
            logger.info("[PaymentCreatedEventV1] success - paymentId: ${event.paymentId}")
        } catch (e: Exception) {
            logger.error("[PaymentCreatedEventV1] failed - paymentId: ${event.paymentId}", e)
        }
    }
}
