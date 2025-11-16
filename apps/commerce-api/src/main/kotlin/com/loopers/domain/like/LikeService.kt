package com.loopers.domain.like

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LikeService(
    private val likeRepository: LikeRepository,
) {
    /**
     * 좋아요를 등록한다
     * UniqueConstraint를 활용하여 멱등성 보장
     * 동시성 처리는 호출하는 쪽(Facade)에서 DataIntegrityViolationException을 catch하여 처리
     */
    @Transactional
    fun addLike(userId: Long, productId: Long) {
        // 이미 존재하는지 확인
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return
        }

        // 저장 시도
        val like = Like(userId = userId, productId = productId)
        likeRepository.save(like)
    }

    @Transactional
    fun removeLike(userId: Long, productId: Long) {
        likeRepository.deleteByUserIdAndProductId(userId, productId)
    }
}
