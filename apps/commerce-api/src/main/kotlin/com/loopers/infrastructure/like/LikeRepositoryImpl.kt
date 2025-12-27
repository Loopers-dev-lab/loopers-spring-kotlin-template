package com.loopers.infrastructure.like

import com.loopers.domain.like.Like
import com.loopers.domain.like.LikeRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
class LikeRepositoryImpl(private val likeJpaRepository: LikeJpaRepository) : LikeRepository {
    override fun existsByUserIdAndProductId(
        userId: Long,
        productId: Long,
    ): Boolean = likeJpaRepository.existsByUserIdAndProductId(userId, productId)

    override fun countByProductId(
        productId: Long,
    ): Long = likeJpaRepository.countByProductId(productId)

    override fun countByProductIdIn(productIds: List<Long>): Map<Long, Long> =
        likeJpaRepository.countByProductIdInGrouped(productIds)
            .associate { it.getProductId() to it.getLikeCount() }

    override fun save(like: Like): Like = likeJpaRepository.save(like)
    override fun saveAll(likes: List<Like>): List<Like> = likeJpaRepository.saveAll(likes)

    override fun deleteByUserIdAndProductId(userId: Long, productId: Long): Long = likeJpaRepository.deleteByUserIdAndProductId(
        userId,
        productId,
    )

    override fun findByUserId(
        userId: Long,
        pageable: Pageable,
    ): Page<Like> = likeJpaRepository.findByUserId(userId, pageable)

    override fun findValidLikesByUserId(
        userId: Long,
        pageable: Pageable,
    ): Page<Like> = likeJpaRepository.findValidLikesByUserId(userId, pageable)
}
