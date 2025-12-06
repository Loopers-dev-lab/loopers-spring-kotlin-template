package com.loopers.domain.event

import com.loopers.domain.order.Order
import com.loopers.domain.payment.Payment
import java.time.ZonedDateTime

/**
 * 주문 생성 이벤트
 * 주문이 생성되었을 때 발행
 */
data class OrderCreatedEvent(
    val orderId: Long,
    val userId: Long,
    val amount: Long,
    val couponId: Long?,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun from(order: Order, couponId: Long?): OrderCreatedEvent {
            return OrderCreatedEvent(
                orderId = order.id!!,
                userId = order.userId,
                amount = order.totalAmount.amount.toLong(),
                couponId = couponId,
                createdAt = order.createdAt,
            )
        }
    }
}

/**
 * 결제 완료 이벤트
 * 결제가 성공적으로 완료되었을 때 발행
 */
data class PaymentCompletedEvent(
    val paymentId: Long,
    val orderId: Long,
    val userId: Long,
    val amount: Long,
    val transactionKey: String?,
    val completedAt: ZonedDateTime,
) {
    companion object {
        fun from(payment: Payment): PaymentCompletedEvent {
            return PaymentCompletedEvent(
                paymentId = payment.id!!,
                orderId = payment.orderId,
                userId = payment.userId,
                amount = payment.amount,
                transactionKey = payment.transactionKey,
                completedAt = ZonedDateTime.now(),
            )
        }
    }
}

/**
 * 결제 실패 이벤트
 * 결제가 실패했을 때 발행
 */
data class PaymentFailedEvent(
    val paymentId: Long,
    val orderId: Long,
    val userId: Long,
    val amount: Long,
    val reason: String?,
    val failedAt: ZonedDateTime,
) {
    companion object {
        fun from(payment: Payment): PaymentFailedEvent {
            return PaymentFailedEvent(
                paymentId = payment.id!!,
                orderId = payment.orderId,
                userId = payment.userId,
                amount = payment.amount,
                reason = payment.failureReason,
                failedAt = ZonedDateTime.now(),
            )
        }
    }
}
