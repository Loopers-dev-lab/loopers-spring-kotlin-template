package com.loopers.interfaces.handler.like

import com.loopers.domain.like.LikeEvent
import com.loopers.domain.like.LikedEvent
import com.loopers.domain.like.UnlikedEvent
import com.loopers.domain.outbox.OutBoxService
import com.loopers.domain.product.signal.ProductTotalSignalService
import com.loopers.event.EventType
import com.loopers.event.LikeEventPayload
import org.apache.kafka.common.KafkaException
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class LikeEventHandler(
    private val productTotalSignalService: ProductTotalSignalService,
    private val outboxService: OutBoxService,
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(LikeEventHandler::class.java)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleLikedBefore(event: LikeEvent) {
        val payload = LikeEventPayload(
            productId = event.productId,
            userId = event.userId,
            type = LikeEventPayload.LikeType.LIKED,
        )
        outboxService.enqueue(event.eventId, EventType.Topic.LIKE_EVENT, payload)
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleLiked(event: LikedEvent) {
        productTotalSignalService.incrementLikeCount(event.productId)

        val payload = LikeEventPayload(
            productId = event.productId,
            userId = event.userId,
            type = LikeEventPayload.LikeType.LIKED,
        )

        try {
            kafkaTemplate.send(EventType.Topic.LIKE_EVENT, payload)
        } catch (e: KafkaException) {
            logger.error("Error sending like event", e)
            outboxService.markAsFailed(event.eventId)
        }
        outboxService.markAsPublished(event.eventId)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleUnLikedBefore(event: UnlikedEvent) {
        val payload = LikeEventPayload(
            productId = event.productId,
            userId = event.userId,
            type = LikeEventPayload.LikeType.UNLIKED,
        )
        outboxService.enqueue(event.eventId, EventType.Topic.LIKE_EVENT, payload)
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleUnliked(event: UnlikedEvent) {
        productTotalSignalService.decrementLikeCount(event.productId)

        val payload = LikeEventPayload(
            productId = event.productId,
            userId = event.userId,
            type = LikeEventPayload.LikeType.UNLIKED,
        )

        try {
            kafkaTemplate.send(EventType.Topic.LIKE_EVENT, payload)
        } catch (e: KafkaException) {
            logger.error("Error sending like event", e)
            outboxService.markAsFailed(event.eventId)
        }
        outboxService.markAsPublished(event.eventId)
    }
}
