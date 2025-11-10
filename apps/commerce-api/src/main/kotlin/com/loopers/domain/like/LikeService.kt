package com.loopers.domain.like

import org.springframework.stereotype.Service

@Service
class LikeService(
    private val likeRepository: LikeRepository,
) {
    fun addLike(userId: Long, productId: Long) {
        // NOTE: 멱등성을 위해 이미 존재하면 저장하지 않음
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return
        }

        val like = Like(userId = userId, productId = productId)
        likeRepository.save(like)
    }

    fun removeLike(userId: Long, productId: Long) {
        likeRepository.deleteByUserIdAndProductId(userId, productId)
    }
}
