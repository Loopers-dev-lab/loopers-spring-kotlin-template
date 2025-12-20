package com.loopers.domain.event

import java.time.ZonedDateTime

/**
 * Outbox 이벤트 정의
 * commerce-api에서 발행한 이벤트를 consume하기 위한 데이터 클래스
 */
object OutboxEvent {

    /**
     * 좋아요 수 변경 이벤트
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
     * 품절 이벤트
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
