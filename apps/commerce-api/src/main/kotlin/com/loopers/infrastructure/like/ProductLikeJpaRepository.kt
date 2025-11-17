package com.loopers.infrastructure.like

import com.loopers.domain.like.ProductLike
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ProductLikeJpaRepository : JpaRepository<ProductLike, Long> {
    fun findAllByProductIdIn(productIds: List<Long>): List<ProductLike>
    fun findAllByProductId(productId: Long): List<ProductLike>

    fun findAllByUserId(userId: Long, pageable: Pageable): Page<ProductLike>

    @Query(
        """
        SELECT CASE WHEN COUNT(pl) > 0 THEN true ELSE false END 
        FROM ProductLike pl WHERE pl.productId = :productId AND pl.userId = :userId
    """,
    )
    fun existsByProductIdAndUserId(productId: Long, userId: Long): Boolean

    @Query("SELECT pl FROM ProductLike pl WHERE pl.productId = :productId AND pl.userId = :userId")
    fun findBy(productId: Long, userId: Long): ProductLike?

    @Modifying
    @Query("DELETE FROM ProductLike pl WHERE pl.productId = :productId AND pl.userId = :userId")
    fun deleteBy(productId: Long, userId: Long)
}
