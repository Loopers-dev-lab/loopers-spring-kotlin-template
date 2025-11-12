package com.loopers.infrastructure.like

import com.loopers.domain.like.Like
import com.loopers.domain.like.LikeRepository
import org.springframework.stereotype.Component

@Component
class LikeRepositoryImpl(
    private val likeJpaRepository: LikeJpaRepository,
) : LikeRepository {
    override fun save(like: Like): Like {
        return likeJpaRepository.save(like)
    }

    override fun findByUserIdAndProductId(userId: Long, productId: Long): Like? {
        return likeJpaRepository.findByUserIdAndProductId(userId, productId)
    }

    override fun findByUserId(userId: Long): List<Like> {
        return likeJpaRepository.findByUserId(userId)
    }

    override fun existsByUserIdAndProductId(userId: Long, productId: Long): Boolean {
        return likeJpaRepository.existsByUserIdAndProductId(userId, productId)
    }

    override fun delete(like: Like) {
        likeJpaRepository.delete(like)
    }

    override fun countByProductId(productId: Long): Long {
        return likeJpaRepository.countByProductId(productId)
    }
}
