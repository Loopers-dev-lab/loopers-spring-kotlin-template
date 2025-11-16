package com.loopers.domain.like

import jakarta.transaction.Transactional
import org.springframework.stereotype.Component

@Component
class LikeService(private val likeRepository: LikeRepository) {

    @Transactional
    fun like(userId: Long, productId: Long): Boolean {
        val like = likeRepository.findByUserIdAndProductId(userId, productId)

        if (like == null) {
            likeRepository.save(LikeModel.create(userId, productId))
            return true
        }
        return false
    }

    @Transactional
    fun unLike(userId: Long, productId: Long): Boolean {
        val like = likeRepository.findByUserIdAndProductId(userId, productId)

        if (like != null) {
            likeRepository.deleteByUserIdAndProductId(userId, productId)
            return true
        }
        return false
    }
}
