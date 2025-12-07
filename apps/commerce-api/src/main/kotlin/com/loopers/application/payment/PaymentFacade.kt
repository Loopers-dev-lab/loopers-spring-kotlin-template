package com.loopers.application.payment

import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.PaymentService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentFacade(private val paymentService: PaymentService, private val orderService: OrderService) {

    companion object {
        private val logger = LoggerFactory.getLogger(PaymentFacade::class.java)
    }

    @Transactional
    fun handleCallback(transactionKey: String, status: String) {
        logger.info("Payment callback received: transactionKey=$transactionKey, status=$status")

        val payment = paymentService.updatePaymentStatus(transactionKey, status)

        orderService.updateOrderByStatus(payment.refOrderKey, status)

        logger.info("Payment callback processed: transactionKey=$transactionKey, status=$status")
    }
}
