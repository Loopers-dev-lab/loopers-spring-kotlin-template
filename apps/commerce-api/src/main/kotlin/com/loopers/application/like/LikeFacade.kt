package com.loopers.application.like

import com.loopers.domain.like.LikeEventPublisher
import com.loopers.domain.like.LikeService
import com.loopers.domain.like.LikedEvent
import com.loopers.domain.like.UnlikedEvent
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class LikeFacade(private val likeService: LikeService, private val likeEventPublisher: LikeEventPublisher) {

    companion object {
        private val log = LoggerFactory.getLogger(LikeFacade::class.java)
    }

    @Transactional
    fun like(userId: Long, productId: Long) {
        log.info("좋아요 요청 시작 - userId: {}, productId: {}", userId, productId)
        val result = likeService.like(userId, productId)

        if (result.changed) {
            val eventId = UUID.randomUUID().toString()
            likeEventPublisher.publish(LikedEvent(productId, userId, eventId))
        }
        log.info("좋아요 요청 완료 - userId: {}, productId: {}", userId, productId)
    }

    @Transactional
    fun unlike(userId: Long, productId: Long) {
        log.info("좋아요 취소 시작 - userId: {}, productId: {}", userId, productId)
        val result = likeService.unLike(userId, productId)

        if (result.changed) {
            val eventId = UUID.randomUUID().toString()
            likeEventPublisher.publish(UnlikedEvent(productId, userId, eventId))
        }
        log.info("좋아요 취소 완료 - userId: {}, productId: {}", userId, productId)
    }
}
