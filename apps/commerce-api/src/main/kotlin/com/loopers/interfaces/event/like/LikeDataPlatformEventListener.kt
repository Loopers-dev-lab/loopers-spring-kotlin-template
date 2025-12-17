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
        logger.info("[LikeCreatedEventV1] start - userId: ${event.userId}, productId: ${event.productId}")
        try {
            likeDataPlatformClient.sendLikeCreated(event.userId, event.productId)
            logger.info("[LikeCreatedEventV1] success - userId: ${event.userId}, productId: ${event.productId}")
        } catch (e: Exception) {
            logger.error("[LikeCreatedEventV1] failed - userId: ${event.userId}, productId: ${event.productId}", e)
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onLikeCanceled(event: LikeCanceledEventV1) {
        logger.info("[LikeCanceledEventV1] start - userId: ${event.userId}, productId: ${event.productId}")
        try {
            likeDataPlatformClient.sendLikeCanceled(event.userId, event.productId)
            logger.info("[LikeCanceledEventV1] success - userId: ${event.userId}, productId: ${event.productId}")
        } catch (e: Exception) {
            logger.error("[LikeCanceledEventV1] failed - userId: ${event.userId}, productId: ${event.productId}", e)
        }
    }
}
