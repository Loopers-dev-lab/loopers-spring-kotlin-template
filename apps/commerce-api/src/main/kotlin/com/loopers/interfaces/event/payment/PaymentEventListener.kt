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
        logger.info("onPaymentCreated start - eventType: ${event.eventType}, aggregateId: ${event.aggregateId}")
        try {
            paymentService.requestPgPayment(event.paymentId)
            logger.info("onPaymentCreated success - eventType: ${event.eventType}, aggregateId: ${event.aggregateId}")
        } catch (e: Exception) {
            logger.error("onPaymentCreated failed - eventType: ${event.eventType}, aggregateId: ${event.aggregateId}", e)
        }
    }
}
