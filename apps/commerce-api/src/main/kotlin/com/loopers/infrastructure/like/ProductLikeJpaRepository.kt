package com.loopers.infrastructure.like

import com.loopers.domain.like.ProductLike
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ProductLikeJpaRepository : JpaRepository<ProductLike, Long> {
    fun deleteByUserIdAndProductId(userId: Long, productId: Long): Long

    fun countByUserIdAndProductId(userId: Long, productId: Long): Long

    @Modifying
    @Query(
        """
    INSERT IGNORE INTO product_likes
        (product_id, user_id, created_at, updated_at, deleted_at)
    VALUES
        (:productId, :userId, NOW(), NOW(), NULL)
    """,
        nativeQuery = true,
    )
    fun trySave(
        @Param("productId") productId: Long,
        @Param("userId") userId: Long,
    ): Int
}
