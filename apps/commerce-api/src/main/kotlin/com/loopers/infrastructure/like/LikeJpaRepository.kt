package com.loopers.infrastructure.like

import com.loopers.domain.like.Like
import org.springframework.data.jpa.repository.JpaRepository

interface LikeJpaRepository : JpaRepository<Like, Long> {
    fun findByUserIdAndProductId(userId: Long, productId: Long): Like?
    fun findByUserId(userId: Long): List<Like>
    fun existsByUserIdAndProductId(userId: Long, productId: Long): Boolean
    fun countByProductId(productId: Long): Long
}
