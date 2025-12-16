package com.loopers.interfaces.event.order

import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.PaymentFailedEventV1
import com.loopers.domain.payment.PaymentPaidEventV1
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class OrderEventListener(
    private val orderService: OrderService,
) {
    private val logger = LoggerFactory.getLogger(OrderEventListener::class.java)

    @EventListener
    fun onPaymentPaid(event: PaymentPaidEventV1) {
        orderService.completePayment(event.orderId)
    }

    @EventListener()
    fun onPaymentFailed(event: PaymentFailedEventV1) {
        orderService.cancelOrder(event.orderId)
    }
}
