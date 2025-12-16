package com.loopers.interfaces.event.like

import com.loopers.domain.like.LikeCanceledEventV1
import com.loopers.domain.like.LikeCreatedEventV1
import com.loopers.domain.like.LikeDataPlatformClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class LikeDataPlatformEventListener(
    private val likeDataPlatformClient: LikeDataPlatformClient,
) {
    private val logger = LoggerFactory.getLogger(LikeDataPlatformEventListener::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onLikeCreated(event: LikeCreatedEventV1) {
        logger.info("onLikeCreated start - eventType: ${event.eventType}, aggregateId: ${event.aggregateId}")
        try {
            likeDataPlatformClient.sendLikeCreated(event.userId, event.productId)
            logger.info("onLikeCreated success - eventType: ${event.eventType}, aggregateId: ${event.aggregateId}")
        } catch (e: Exception) {
            logger.error("onLikeCreated failed - eventType: ${event.eventType}, aggregateId: ${event.aggregateId}", e)
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onLikeCanceled(event: LikeCanceledEventV1) {
        logger.info("onLikeCanceled start - eventType: ${event.eventType}, aggregateId: ${event.aggregateId}")
        try {
            likeDataPlatformClient.sendLikeCanceled(event.userId, event.productId)
            logger.info("onLikeCanceled success - eventType: ${event.eventType}, aggregateId: ${event.aggregateId}")
        } catch (e: Exception) {
            logger.error("onLikeCanceled failed - eventType: ${event.eventType}, aggregateId: ${event.aggregateId}", e)
        }
    }
}
