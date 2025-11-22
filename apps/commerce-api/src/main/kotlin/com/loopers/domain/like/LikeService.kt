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
     * 이미 존재하는 경우 별도 처리 없이 반환 (멱등성)
     * 동시성 경합 상황에서 UniqueConstraint 위반이 발생할 수 있으므로 호출하는 쪽에서 DataIntegrityViolationException 처리 필요
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
