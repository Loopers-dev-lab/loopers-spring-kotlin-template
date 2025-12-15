package com.loopers.application.like

import com.loopers.domain.like.LikeEventPublisher
import com.loopers.domain.like.LikeService
import com.loopers.domain.like.LikedEvent
import com.loopers.domain.like.UnlikedEvent
import jakarta.transaction.Transactional
import org.springframework.stereotype.Component

@Component
class LikeFacade(
        private val likeService: LikeService,
        private val likeEventPublisher: LikeEventPublisher
) {

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(LikeFacade::class.java)
    }

    @Transactional
    fun like(userId: Long, productId: Long) {
        log.info("좋아요 요청 시작 - userId: {}, productId: {}", userId, productId)
        val result = likeService.like(userId, productId)

        if (result.changed) {
            likeEventPublisher.publish(LikedEvent(productId, userId))
        }
        log.info("좋아요 요청 완료 - userId: {}, productId: {}", userId, productId)
    }

    @Transactional
    fun unlike(userId: Long, productId: Long) {
        log.info("좋아요 취소 시작 - userId: {}, productId: {}", userId, productId)
        val result = likeService.unLike(userId, productId)

        if (result.changed) {
            likeEventPublisher.publish(UnlikedEvent(productId, userId))
        }
        log.info("좋아요 취소 완료 - userId: {}, productId: {}", userId, productId)
    }
}
