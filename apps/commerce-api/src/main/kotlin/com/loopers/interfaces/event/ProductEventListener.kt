package com.loopers.interfaces.event

import com.loopers.domain.like.ProductLikeEvent
import com.loopers.domain.outbox.AggregateType
import com.loopers.domain.outbox.OutboxEvent
import com.loopers.domain.outbox.OutboxEventPublisher
import com.loopers.domain.outbox.OutboxService
import com.loopers.domain.product.ProductEvent
import com.loopers.domain.user.UserService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 상품 메트릭 이벤트를 Outbox에 저장하는 리스너
 *
 * 트랜잭션 커밋 전(BEFORE_COMMIT)에 Outbox에 저장하여
 * 비즈니스 트랜잭션과 메시지 발행의 원자성 보장
 *
 * 저장되는 메트릭 이벤트:
 * - 좋아요 수 (ProductLiked, ProductUnliked)
 * - 조회 수 (ProductViewed)
 *
 * 주문 관련 이벤트:
 * - OrderFacade에서 포인트 결제 완료 시 직접 Outbox에 저장
 * - PaymentEventListener에서 카드 결제 성공/실패 시 직접 Outbox에 저장
 */
@Component
class ProductEventListener(
    private val outboxService: OutboxService,
    private val userService: UserService,
    private val outboxEventPublisher: OutboxEventPublisher,
) {
    private val log = LoggerFactory.getLogger(ProductEventListener::class.java)

    /**
     * 좋아요 추가 이벤트 -> Outbox 저장
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleProductLiked(event: ProductLikeEvent.ProductLiked) {
        log.debug("Outbox 저장: ProductLiked - productId={}, userId={}", event.productId, event.userId)

        val user = userService.getMyInfo(event.userId)

        val metricEvent = OutboxEvent.LikeCountChanged(
            productId = event.productId,
            userId = user.id,
            action = OutboxEvent.LikeCountChanged.LikeAction.LIKED,
        )

        outboxService.save(
            aggregateType = AggregateType.PRODUCT,
            aggregateId = event.productId.toString(),
            eventType = OutboxEvent.LikeCountChanged.EVENT_TYPE,
            payload = metricEvent,
        )
    }

    /**
     * 좋아요 취소 이벤트 -> Outbox 저장
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleProductUnliked(event: ProductLikeEvent.ProductUnliked) {
        log.debug("Outbox 저장: ProductUnliked - productId={}, userId={}", event.productId, event.userId)

        val user = userService.getMyInfo(event.userId)

        val metricEvent = OutboxEvent.LikeCountChanged(
            productId = event.productId,
            userId = user.id,
            action = OutboxEvent.LikeCountChanged.LikeAction.UNLIKED,
        )

        outboxService.save(
            aggregateType = AggregateType.PRODUCT,
            aggregateId = event.productId.toString(),
            eventType = OutboxEvent.LikeCountChanged.EVENT_TYPE,
            payload = metricEvent,
        )
    }

    /**
     * 상품 조회 이벤트 -> Outbox 저장
     *
     * 조회는 트랜잭션 없이 발생할 수 있으므로 @EventListener + @Transactional 사용
     */
    @EventListener
    @Transactional
    fun handleProductViewed(event: ProductEvent.ProductViewed) {
        log.debug("Outbox 저장: ProductViewed - productId={}, userId={}", event.productId, event.userId)

        val metricEvent = OutboxEvent.ViewCountIncreased(
            productId = event.productId,
            userId = event.userId,
        )

        outboxService.save(
            aggregateType = AggregateType.PRODUCT,
            aggregateId = event.productId.toString(),
            eventType = OutboxEvent.ViewCountIncreased.EVENT_TYPE,
            payload = metricEvent,
        )
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handle(event: ProductEvent.OutOfStock) {
        log.debug("Outbox 저장: OutOfStock - productId={}", event.productId)

        val metricEvent = OutboxEvent.SoldOut(
            productId = event.productId,
        )

        outboxService.save(
            aggregateType = AggregateType.PRODUCT,
            aggregateId = event.productId.toString(),
            eventType = OutboxEvent.SoldOut.EVENT_TYPE,
            payload = metricEvent,
        )
    }
}
