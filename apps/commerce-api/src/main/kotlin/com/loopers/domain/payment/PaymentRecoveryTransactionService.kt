package com.loopers.domain.payment

import com.loopers.domain.order.OrderService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentRecoveryTransactionService(
    private val orderService: OrderService,
    private val paymentRepository: PaymentRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional(propagation= Propagation.REQUIRES_NEW)
    fun handlePaymentSuccess(orderId: Long, payment: Payment) {
        orderService.completeOrderWithPayment(orderId)

        payment.markAsSuccess()
        paymentRepository.save(payment)

        logger.info("결제 복구 완료: orderId=$orderId")
    }

    @Transactional(propagation= Propagation.REQUIRES_NEW)
    fun handlePaymentFailure(orderId: Long, payment: Payment, reason: String?) {
        payment.markAsFailed(reason ?: "PG에서 결제 실패")
        paymentRepository.save(payment)

        orderService.failOrder(orderId)

        logger.info("결제 실패 처리 완료: orderId=$orderId, reason={$reason ?: 'PG에서 결제 실패'}")
    }


}
