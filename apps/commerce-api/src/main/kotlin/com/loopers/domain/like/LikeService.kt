package com.loopers.domain.like

import org.springframework.stereotype.Component

@Component
class LikeService(private val likeRepository: LikeRepository) {

    fun like(userId: Long, productId: Long): Boolean {
        val like = likeRepository.findByUserIdAndProductId(userId, productId)

        if (like != null) {
            likeRepository.save(LikeModel.create(userId, productId))
            return true
        }
        return false
    }

    fun unLike(userId: Long, productId: Long): Boolean {
        val like = likeRepository.findByUserIdAndProductId(userId, productId)

        if (like != null) {
            likeRepository.deleteByUserIdAndProductId(userId, productId)
            return true
        }
        return false
    }
}
