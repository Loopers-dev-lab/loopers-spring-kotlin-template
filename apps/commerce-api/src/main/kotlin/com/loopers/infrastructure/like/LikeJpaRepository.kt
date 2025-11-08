package com.loopers.infrastructure.like

import com.loopers.domain.like.Like
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface LikeJpaRepository : JpaRepository<Like, Long> {
    fun existsByUserIdAndProductId(userId: Long, productId: Long): Boolean
    fun countByProductId(productId: Long): Long
    fun deleteByUserIdAndProductId(userId: Long, productId: Long)
    fun findByUserId(userId: Long, pageable: Pageable): Page<Like>
}
