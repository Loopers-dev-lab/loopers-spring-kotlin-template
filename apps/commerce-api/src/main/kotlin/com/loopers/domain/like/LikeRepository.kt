package com.loopers.domain.like

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface LikeRepository {
    fun existsByUserIdAndProductId(userId: Long, productId: Long): Boolean
    fun countByProductId(productId: Long): Long
    fun countByProductIdIn(productIds: List<Long>): Map<Long, Long>
    fun save(like: Like): Like
    fun saveAll(likes: List<Like>): List<Like>
    fun deleteByUserIdAndProductId(userId: Long, productId: Long)
    fun findByUserId(userId: Long, pageable: Pageable): Page<Like>
    fun findValidLikesByUserId(userId: Long, pageable: Pageable): Page<Like>
}
