package com.loopers.infrastructure.event

import com.loopers.domain.like.event.ProductLikedEvent
import com.loopers.domain.like.event.ProductUnlikedEvent
import com.loopers.domain.order.event.OrderCreatedEvent
import com.loopers.domain.product.event.ProductBrowsedEvent
import com.loopers.domain.product.event.ProductViewedEvent
import com.loopers.support.event.ActionType
import com.loopers.support.event.EntityType
import com.loopers.support.event.UserActionEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class UserActionTrackingEventHandler(
    private val eventPublisher: ApplicationEventPublisher,
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleProductLiked(event: ProductLikedEvent) {
        eventPublisher.publishEvent(
            UserActionEvent(
                userId = event.memberId.toString(),
                actionType = ActionType.LIKE,
                targetEntityType = EntityType.PRODUCT,
                targetEntityId = event.productId,
                metadata = mapOf("likeId" to event.likeId.toString()),
                occurredAt = event.likedAt
            )
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleProductUnliked(event: ProductUnlikedEvent) {
        eventPublisher.publishEvent(
            UserActionEvent(
                userId = event.memberId.toString(),
                actionType = ActionType.UNLIKE,
                targetEntityType = EntityType.PRODUCT,
                targetEntityId = event.productId,
                metadata = emptyMap(),
                occurredAt = event.unlikedAt
            )
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleProductBrowsed(event: ProductBrowsedEvent) {
        // 비로그인 사용자는 추적하지 않음
        if (event.memberId == null) return

        eventPublisher.publishEvent(
            UserActionEvent(
                userId = event.memberId,
                actionType = ActionType.BROWSE,
                targetEntityType = EntityType.PRODUCT,
                targetEntityId = 0L, // 목록 조회는 특정 상품 없음
                metadata = mapOf(
                    "brandId" to (event.brandId?.toString() ?: "all"),
                    "sortType" to event.sortType.name,
                    "page" to event.page.toString()
                ),
                occurredAt = event.browsedAt
            )
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleProductViewed(event: ProductViewedEvent) {
        // 비로그인 사용자는 추적하지 않음
        if (event.memberId == null) return

        eventPublisher.publishEvent(
            UserActionEvent(
                userId = event.memberId,
                actionType = ActionType.VIEW,
                targetEntityType = EntityType.PRODUCT,
                targetEntityId = event.productId,
                metadata = emptyMap(),
                occurredAt = event.viewedAt
            )
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderCreated(event: OrderCreatedEvent) {
        eventPublisher.publishEvent(
            UserActionEvent(
                userId = event.memberId,
                actionType = ActionType.ORDER,
                targetEntityType = EntityType.ORDER,
                targetEntityId = event.orderId,
                metadata = mapOf(
                    "orderAmount" to event.orderAmount.toString(),
                    "couponId" to (event.couponId?.toString() ?: "")
                ),
                occurredAt = event.createdAt
            )
        )
    }

}
