package com.loopers.interfaces.handler.like

import com.loopers.domain.like.LikedEvent
import com.loopers.domain.like.UnlikedEvent
import com.loopers.domain.outbox.OutBoxEventType
import com.loopers.domain.outbox.OutBoxService
import com.loopers.domain.product.signal.ProductTotalSignalService
import com.loopers.event.CatalogEventPayload
import com.loopers.event.CatalogType
import com.loopers.event.EventType
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class LikeEventHandler(
        private val productTotalSignalService: ProductTotalSignalService,
        private val outboxService: OutBoxService,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(LikeEventHandler::class.java)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleLikedBefore(event: LikedEvent) {
        val payload =
                CatalogEventPayload(
                        eventId = event.eventId,
                        productId = event.productId,
                        userId = event.userId,
                        type = CatalogType.LIKED,
                )
        outboxService.enqueue(
                event.eventId,
                EventType.Topic.CATALOG_EVENT,
                payload,
                OutBoxEventType.CATALOG
        )
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleLiked(event: LikedEvent) {
        productTotalSignalService.incrementLikeCount(event.productId)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleUnLikedBefore(event: UnlikedEvent) {
        val payload =
                CatalogEventPayload(
                        eventId = event.eventId,
                        productId = event.productId,
                        userId = event.userId,
                        type = CatalogType.UNLIKED,
                )
        outboxService.enqueue(
                event.eventId,
                EventType.Topic.CATALOG_EVENT,
                payload,
                OutBoxEventType.CATALOG
        )
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleUnliked(event: UnlikedEvent) {
        productTotalSignalService.decrementLikeCount(event.productId)
    }
}
