package com.loopers.domain.like

interface LikeRepository {
    fun save(like: Like): Like
    fun findByUserId(userId: Long): List<Like>
    fun existsByUserIdAndProductId(userId: Long, productId: Long): Boolean
    fun delete(like: Like)
    fun countByProductId(productId: Long): Long
}
