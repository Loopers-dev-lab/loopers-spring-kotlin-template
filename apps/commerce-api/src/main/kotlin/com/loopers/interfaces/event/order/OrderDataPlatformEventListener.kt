package com.loopers.interfaces.event.order

import com.loopers.domain.order.OrderDataPlatformClient
import com.loopers.domain.payment.PaymentPaidEventV1
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class OrderDataPlatformEventListener(
    private val orderDataPlatformClient: OrderDataPlatformClient,
) {
    private val logger = LoggerFactory.getLogger(OrderDataPlatformEventListener::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPaymentPaid(event: PaymentPaidEventV1) {
        logger.info("onPaymentPaid start - eventType: ${event.eventType}, aggregateId: ${event.aggregateId}")
        try {
            orderDataPlatformClient.sendOrderCompleted(event.orderId)
            logger.info("onPaymentPaid success - eventType: ${event.eventType}, aggregateId: ${event.aggregateId}")
        } catch (e: Exception) {
            logger.error("onPaymentPaid failed - eventType: ${event.eventType}, aggregateId: ${event.aggregateId}", e)
        }
    }
}
