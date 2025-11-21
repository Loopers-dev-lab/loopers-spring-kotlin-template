package com.loopers.domain.like

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeService(
    private val likeRepository: LikeRepository,
) {

    @Transactional(readOnly = true)
    fun countLikesByProductId(productId: Long): Long {
        return likeRepository.countByProductId(productId)
    }

    @Transactional
    fun addLike(userId: Long, productId: Long) {
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return
        }

        likeRepository.save(Like.of(userId, productId))
    }

    @Transactional
    fun removeLike(userId: Long, productId: Long) {
        val like = likeRepository.findByUserIdAndProductId(userId, productId) ?: return
        likeRepository.delete(like)
    }
}
