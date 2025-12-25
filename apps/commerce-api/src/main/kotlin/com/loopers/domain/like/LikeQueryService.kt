package com.loopers.domain.like

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class LikeQueryService(private val likeRepository: LikeRepository) {
    fun getLikesByUserId(userId: Long, pageable: Pageable): Page<Like> = likeRepository.findByUserId(userId, pageable)

    fun getValidLikesByUserId(userId: Long, pageable: Pageable): Page<Like> = likeRepository.findValidLikesByUserId(
        userId,
        pageable,
    )

    fun countByProductId(productId: Long): Long = likeRepository.countByProductId(productId)

    fun countByProductIdIn(productIds: List<Long>): Map<Long, Long> = likeRepository.countByProductIdIn(productIds)
}
