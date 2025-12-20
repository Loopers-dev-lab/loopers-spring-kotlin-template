package com.loopers.domain.outbox

import java.time.ZonedDateTime

/**
 * 상품 메트릭 이벤트
 *
 * 외부 데이터 플랫폼(Kafka)으로 전송되는 상품 관련 메트릭 이벤트
 * Outbox 패턴을 통해 안정적으로 발행됨
 *
 * 토픽 분리 전략:
 * - product-like-events: 좋아요 (저빈도, 중요도 높음)
 * - product-view-events: 조회수 (고빈도, 유실 허용 가능)
 * - order-completed-events: 주문 완료 (저빈도, 중요도 매우 높음)
 * - order-canceled-events: 주문 취소 (저빈도, 중요도 매우 높음)
 */
object OutboxEvent {

    /**
     * 좋아요 수 변경 이벤트
     * 토픽: product-like-events
     */
    data class LikeCountChanged(
        val productId: Long,
        val userId: Long,
        val action: LikeAction,
        val timestamp: ZonedDateTime = ZonedDateTime.now(),
    ) {
        enum class LikeAction {
            LIKED,
            UNLIKED,
        }

        companion object {
            const val EVENT_TYPE = "LIKE_COUNT_CHANGED"
            const val TOPIC = "product-like-events"
        }
    }

    /**
     * 조회 수 증가 이벤트
     * 토픽: product-view-events
     */
    data class ViewCountIncreased(
        val productId: Long,
        val userId: Long?,
        val timestamp: ZonedDateTime = ZonedDateTime.now(),
    ) {
        companion object {
            const val EVENT_TYPE = "VIEW_COUNT_INCREASED"
            const val TOPIC = "product-view-events"
        }
    }

    /**
     * 주문 완료 이벤트
     * 토픽: order-completed-events
     */
    data class SoldOut(
        val productId: Long,
        val timestamp: ZonedDateTime = ZonedDateTime.now(),
    ) {
        companion object {
            const val EVENT_TYPE = "PRODUCT_SOLD_OUT"
            const val TOPIC = "product-sold-out-events"
        }
    }

    /**
     * 주문 완료 이벤트
     * 토픽: order-completed-events
     */
    data class OrderCompleted(
        val orderId: Long,
        val userId: Long,
        val totalAmount: Long,
        val items: List<OrderItem>,
        val timestamp: ZonedDateTime = ZonedDateTime.now(),
    ) {
        data class OrderItem(
            val productId: Long,
            val quantity: Int,
            val price: Long,
        )

        companion object {
            const val EVENT_TYPE = "ORDER_COMPLETED"
            const val TOPIC = "order-completed-events"
        }
    }

    /**
     * 주문 취소 이벤트
     * 토픽: order-canceled-events
     */
    data class OrderCanceled(
        val orderId: Long,
        val userId: Long,
        val reason: String?,
        val items: List<OrderItem>,
        val timestamp: ZonedDateTime = ZonedDateTime.now(),
    ) {
        data class OrderItem(
            val productId: Long,
            val quantity: Int,
            val price: Long,
        )

        companion object {
            const val EVENT_TYPE = "ORDER_CANCELED"
            const val TOPIC = "order-canceled-events"
        }
    }
}
