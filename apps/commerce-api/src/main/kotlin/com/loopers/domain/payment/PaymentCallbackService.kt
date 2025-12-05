package com.loopers.domain.payment

import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentCallbackService(
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val orderService: OrderService
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    @Transactional
    fun handlePaymentCallback(callback: PaymentCallbackDto) {
        logger.info("결제 콜백 수신: transactionKey=${callback.transactionKey}, status=${callback.status}")

        val payment = paymentRepository.findByTransactionKey(callback.transactionKey)
            ?: throw CoreException(ErrorType.PAYMENT_NOT_FOUND, "결제 정보를 찾을 수 없습니다.")

        if (payment.status != PaymentStatus.PENDING) {
            logger.warn("이미 처리된 결제: paymentId=${payment.id}, status=${payment.status}")
            return
        }

        val order = orderRepository.findByIdOrThrow(payment.orderId)

        if (callback.isSuccess()) {
            orderService.completeOrderWithPayment(payment.orderId)
            payment.markAsSuccess()
            logger.info("결제 콜백 처리 완료: orderId=${payment.orderId}, paymentId=${payment.id}")
        } else {
            payment.markAsFailed(callback.reason ?: "결제 실패")
            order.fail()
            logger.warn("결제 콜백 실패: orderId=${order.id}, reason=${callback.reason}")
        }
    }
}
