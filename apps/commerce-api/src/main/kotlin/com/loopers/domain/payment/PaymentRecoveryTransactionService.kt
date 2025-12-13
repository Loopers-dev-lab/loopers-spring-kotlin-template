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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun handlePaymentSuccess(orderId: Long, paymentId: Long) {
        val payment = paymentRepository.findByIdOrThrow(paymentId)

        // 상태 확인 후 조건부 처리 (멱등성 보장)
        if (payment.status != PaymentStatus.PENDING) {
            logger.info("결제가 이미 처리됨: paymentId=$paymentId, status=${payment.status}")
            return
        }

        // 결제 상태 먼저 업데이트 (일관성)
        payment.markAsSuccess()
        paymentRepository.save(payment)

        // 주문 완료 처리
        orderService.completeOrderWithPayment(orderId)

        logger.info("결제 복구 완료: orderId=$orderId")
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun handlePaymentFailure(orderId: Long, paymentId: Long, reason: String?) {
        val payment = paymentRepository.findByIdOrThrow(paymentId)

        // 상태 확인 후 조건부 처리 (멱등성 보장)
        if (payment.status != PaymentStatus.PENDING) {
            logger.info("결제가 이미 처리됨: paymentId=$paymentId, status=${payment.status}")
            return
        }

        // 주문 실패 먼저 처리 (일관성 - handlePaymentSuccess와 동일한 순서)
        orderService.failOrder(orderId)

        // 결제 상태 업데이트
        payment.markAsFailed(reason ?: "PG에서 결제 실패")
        paymentRepository.save(payment)

        logger.info("결제 실패 처리 완료: orderId={}, reason={}", orderId, reason ?: "PG에서 결제 실패")
    }
}
