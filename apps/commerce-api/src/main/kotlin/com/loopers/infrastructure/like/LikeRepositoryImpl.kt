package com.loopers.infrastructure.like

import com.loopers.domain.like.Like
import com.loopers.domain.like.LikeRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class LikeRepositoryImpl(
    private val likeJpaRepository: LikeJpaRepository
) : LikeRepository {
    override fun existsByUserIdAndProductId(userId: Long, productId: Long): Boolean {
        return likeJpaRepository.existsByUserIdAndProductId(userId, productId)
    }

    override fun countByProductId(productId: Long): Long {
        return likeJpaRepository.countByProductId(productId)
    }

    override fun save(like: Like): Like {
        return likeJpaRepository.save(like)
    }

    override fun deleteByUserIdAndProductId(userId: Long, productId: Long) {
        likeJpaRepository.deleteByUserIdAndProductId(userId, productId)
    }

    override fun findByUserId(userId: Long, pageable: Pageable): Page<Like> {
        return likeJpaRepository.findByUserId(userId, pageable)
    }
}
