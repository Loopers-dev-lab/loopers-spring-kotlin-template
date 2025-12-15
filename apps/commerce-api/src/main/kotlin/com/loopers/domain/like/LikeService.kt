package com.loopers.domain.like

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeService(private val likeRepository: LikeRepository) {

    @Transactional
    fun like(userId: Long, productId: Long): LikeResult {
        val like = likeRepository.findByUserIdAndProductId(userId, productId)
        if (like != null) return LikeResult(changed = false)

        likeRepository.save(LikeModel.create(userId, productId))
        return LikeResult(changed = true)
    }

    @Transactional
    fun unLike(userId: Long, productId: Long): LikeResult {
        val like = likeRepository.findByUserIdAndProductId(userId, productId)
            ?: return LikeResult(changed = false)

        likeRepository.deleteByUserIdAndProductId(userId, productId)
        return LikeResult(changed = true)
    }
}
