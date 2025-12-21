package com.loopers.domain.payment

import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.event.PaymentCompletedEvent
import com.loopers.domain.payment.event.PaymentFailedEvent
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class PaymentCallbackService(
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val eventPublisher: ApplicationEventPublisher
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

        if (callback.isSuccess()) {
            handleSuccess(payment, callback)
        } else {
            handleFailure(payment, callback)
        }
    }

    private fun handleSuccess(payment: Payment, callback: PaymentCallbackDto) {
        payment.markAsSuccess()

        // order는 성공 케이스에서만 필요
        val order = orderRepository.findByIdOrThrow(payment.orderId)

        // 이벤트 발행 (주문 완료 처리는 이벤트 핸들러에서)
        eventPublisher.publishEvent(
            PaymentCompletedEvent(
                aggregateId = payment.orderId,
                paymentId = requireNotNull(payment.id) { "Payment ID는 null일 수 없습니다" },
                orderId = payment.orderId,
                memberId = order.memberId,
                amount = payment.amount.amount,
                completedAt = Instant.now()
            )
        )
        logger.info("결제 완료 이벤트 발행: orderId=${payment.orderId}, paymentId=${payment.id}")
    }

    private fun handleFailure(payment: Payment, callback: PaymentCallbackDto) {
        payment.markAsFailed(callback.reason ?: "결제 실패")

        // 이벤트 발행
        eventPublisher.publishEvent(
            PaymentFailedEvent(
                aggregateId = payment.orderId,
                paymentId = requireNotNull(payment.id) { "Payment ID는 null일 수 없습니다" },
                orderId = payment.orderId,
                reason = callback.reason ?: "결제 실패",
                failedAt = Instant.now()
            )
        )
        logger.warn("결제 실패 이벤트 발행: orderId=${payment.orderId}, reason=${callback.reason}")
    }
}
