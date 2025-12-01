package com.loopers.infrastructure.like

import com.loopers.domain.like.ProductLikeCount
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ProductLikeCountJpaRepository : JpaRepository<ProductLikeCount, Long> {
    fun findAllByProductIdIn(productIds: List<Long>): List<ProductLikeCount>
    fun findByProductId(productId: Long): ProductLikeCount

    @Modifying
    @Query(
        """
        UPDATE ProductLikeCount plc 
        SET plc.likeCount = plc.likeCount + 1,
            plc.updatedAt = CURRENT_TIMESTAMP
        WHERE plc.productId = :productId
        """,
    )
    fun increase(productId: Long): Int

    @Modifying
    @Query(
        """
        UPDATE ProductLikeCount plc 
        SET plc.likeCount = CASE 
            WHEN plc.likeCount > 0 THEN plc.likeCount - 1 
            ELSE 0 
        END,
        plc.updatedAt = CURRENT_TIMESTAMP
        WHERE plc.productId = :productId
        """,
    )
    fun decrease(productId: Long?): Int
}
