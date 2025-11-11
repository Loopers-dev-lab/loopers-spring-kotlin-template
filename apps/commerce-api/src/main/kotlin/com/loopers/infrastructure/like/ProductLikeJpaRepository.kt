package com.loopers.infrastructure.like

import com.loopers.domain.like.ProductLike
import org.springframework.data.jpa.repository.JpaRepository

interface ProductLikeJpaRepository : JpaRepository<ProductLike, Long> {
    fun findAllByProductIdIn(productId: List<Long>): List<ProductLike>
    fun findAllByProductId(productId: Long): List<ProductLike>
}
