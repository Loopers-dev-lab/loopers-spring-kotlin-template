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
    val items: List<OrderItemInfo>,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun from(order: Order, couponId: Long?): OrderCreatedEvent {
            return OrderCreatedEvent(
                orderId = requireNotNull(order.id) { "Order id must not be null when creating OrderCreatedEvent" },
                userId = order.userId,
                amount = order.totalAmount.amount.toLong(),
                couponId = couponId,
                items = order.items.map { OrderItemInfo.from(it) },
                createdAt = order.createdAt,
            )
        }
    }

    /**
     * 주문 상품 정보
     */
    data class OrderItemInfo(
        val productId: Long,
        val productName: String,
        val quantity: Int,
        val priceAtOrder: Long,
    ) {
        companion object {
            fun from(orderItem: com.loopers.domain.order.OrderItem): OrderItemInfo {
                return OrderItemInfo(
                    productId = orderItem.productId,
                    productName = orderItem.productName,
                    quantity = orderItem.quantity,
                    priceAtOrder = orderItem.priceAtOrder.amount.toLong(),
                )
            }
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
            val completedAt = try {
                payment.updatedAt
            } catch (e: UninitializedPropertyAccessException) {
                // updatedAt이 초기화되지 않은 경우(테스트 등) 현재 시각 사용
                ZonedDateTime.now()
            }

            return PaymentCompletedEvent(
                paymentId = requireNotNull(payment.id) { "Payment id must not be null when creating PaymentCompletedEvent" },
                orderId = payment.orderId,
                userId = payment.userId,
                amount = payment.amount,
                transactionKey = payment.transactionKey,
                completedAt = completedAt,
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
            val failedAt = try {
                payment.updatedAt
            } catch (e: UninitializedPropertyAccessException) {
                // updatedAt이 초기화되지 않은 경우(테스트 등) 현재 시각 사용
                ZonedDateTime.now()
            }

            return PaymentFailedEvent(
                paymentId = requireNotNull(payment.id) { "Payment id must not be null when creating PaymentFailedEvent" },
                orderId = payment.orderId,
                userId = payment.userId,
                amount = payment.amount,
                reason = payment.failureReason,
                failedAt = failedAt,
            )
        }
    }
}
