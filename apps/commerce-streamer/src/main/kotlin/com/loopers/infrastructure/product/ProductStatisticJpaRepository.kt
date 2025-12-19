package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductStatistic
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProductStatisticJpaRepository : JpaRepository<ProductStatistic, Long> {

    fun findByProductId(productId: Long): ProductStatistic?

    fun findAllByProductIdIn(productIds: List<Long>): List<ProductStatistic>
}
