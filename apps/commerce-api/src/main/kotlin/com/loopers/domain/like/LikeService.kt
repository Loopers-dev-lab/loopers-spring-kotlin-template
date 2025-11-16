package com.loopers.domain.like

import org.springframework.context.annotation.Lazy
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
open class LikeService(
    private val likeRepository: LikeRepository,
    @Lazy private val self: LikeService,
) {
    fun addLike(userId: Long, productId: Long) {
        // NOTE: 낙관적 락 방식 - UniqueConstraint를 활용하여 동시성 문제 해결
        // 재시도 로직을 트랜잭션 밖에서 수행하여 rollback 방지
        var retryCount = 0
        val maxRetries = 3

        while (retryCount < maxRetries) {
            try {
                // self-injection을 통해 프록시를 거쳐 트랜잭션 적용
                self.addLikeInternal(userId, productId)
                return
            } catch (e: DataIntegrityViolationException) {
                // UniqueConstraint 위반 - 다른 트랜잭션이 먼저 삽입함 (멱등성 보장)
                retryCount++
                if (retryCount >= maxRetries) {
                    throw e
                }
                Thread.sleep((100L * (1 shl retryCount)).coerceAtMost(1000L))
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    open fun addLikeInternal(userId: Long, productId: Long) {
        // 이미 존재하는지 확인
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return
        }

        // 저장 시도
        val like = Like(userId = userId, productId = productId)
        likeRepository.save(like)
    }

    fun removeLike(userId: Long, productId: Long) {
        likeRepository.deleteByUserIdAndProductId(userId, productId)
    }
}
