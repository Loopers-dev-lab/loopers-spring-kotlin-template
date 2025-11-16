package com.loopers.domain.like

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class LikeService(
    private val likeRepository: LikeRepository,
) {
    /**
     * 좋아요를 등록한다 (낙관적 락 방식)
     * UniqueConstraint를 활용하여 동시성 문제 해결
     * 제약 조건 위반 시 최대 3회까지 재시도 (100ms, 200ms, 400ms 백오프)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Retryable(
        retryFor = [DataIntegrityViolationException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 100, multiplier = 2.0),
    )
    fun addLike(userId: Long, productId: Long) {
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
