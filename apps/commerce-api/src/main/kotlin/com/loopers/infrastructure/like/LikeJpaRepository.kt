package com.loopers.infrastructure.like

import com.loopers.domain.like.Like
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ProductLikeCount {
    fun getProductId(): Long
    fun getLikeCount(): Long
}

interface LikeJpaRepository : JpaRepository<Like, Long> {
    fun existsByUserIdAndProductId(userId: Long, productId: Long): Boolean
    fun countByProductId(productId: Long): Long
    fun deleteByUserIdAndProductId(userId: Long, productId: Long)
    fun findByUserId(userId: Long, pageable: Pageable): Page<Like>

    @Query(
        """
        SELECT l.productId AS productId, COUNT(l) AS likeCount
        FROM Like l
        WHERE l.productId IN :productIds
        GROUP BY l.productId
        """,
    )
    fun countByProductIdInGrouped(productIds: List<Long>): List<ProductLikeCount>
}
