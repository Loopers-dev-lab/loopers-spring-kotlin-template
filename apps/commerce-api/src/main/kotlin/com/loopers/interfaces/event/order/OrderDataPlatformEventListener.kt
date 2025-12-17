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
        logger.info("[PaymentPaidEventV1] start - paymentId: ${event.paymentId}, orderId: ${event.orderId}")
        try {
            orderDataPlatformClient.sendOrderCompleted(event.orderId)
            logger.info("[PaymentPaidEventV1] success - paymentId: ${event.paymentId}, orderId: ${event.orderId}")
        } catch (e: Exception) {
            logger.error("[PaymentPaidEventV1] failed - paymentId: ${event.paymentId}, orderId: ${event.orderId}", e)
        }
    }
}
