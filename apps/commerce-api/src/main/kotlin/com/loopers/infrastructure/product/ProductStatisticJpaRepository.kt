package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductStatistic
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ProductStatisticJpaRepository : JpaRepository<ProductStatistic, Long> {
    fun findByProductId(productId: Long): ProductStatistic?
    fun findAllByProductIdIn(productIds: List<Long>): List<ProductStatistic>

    @Modifying
    @Query("UPDATE ProductStatistic p SET p.likeCount = p.likeCount + 1 WHERE p.productId = :productId")
    fun incrementLikeCount(@Param("productId") productId: Long)

    @Modifying
    @Query("UPDATE ProductStatistic p SET p.likeCount = p.likeCount - 1 WHERE p.productId = :productId")
    fun decrementLikeCount(@Param("productId") productId: Long)
}
