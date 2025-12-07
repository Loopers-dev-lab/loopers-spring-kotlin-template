package com.loopers.domain.payment.job

import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.dto.PaymentDto
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PaymentRetryJob(private val paymentService: PaymentService, private val orderService: OrderService) {

    companion object {
        private val logger = LoggerFactory.getLogger(PaymentRetryJob::class.java)
    }

    @Scheduled(fixedDelay = 600000)
    fun retryFailedPayments() {
        val pendingOrders = orderService.findByStatus(OrderStatus.PENDING)
        pendingOrders.forEach { order ->
            val failedPayment = paymentService.findFailedPaymentByOrderKey(order.orderKey)
                ?: return@forEach

            val request = PaymentDto.Request.from(
                orderKey = order.orderKey,
                cardType = failedPayment.cardType,
                cardNo = failedPayment.cardNo,
                amount = failedPayment.amount.amount,
            )

            when (val result = paymentService.pay(request)) {
                is PaymentDto.Result.Success -> {
                    orderService.requestPayment(order)
                    logger.info("Payment retry succeeded for order: ${order.orderKey}")
                }

                is PaymentDto.Result.Failed -> {
                    logger.warn("Payment retry failed for order: ${order.orderKey}, reason: ${result.errorMessage}")
                }
            }
        }
    }
}
