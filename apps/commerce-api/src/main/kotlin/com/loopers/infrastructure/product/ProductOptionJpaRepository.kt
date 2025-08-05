package com.loopers.infrastructure.product

import com.loopers.domain.product.entity.ProductOption
import org.springframework.data.jpa.repository.JpaRepository

interface ProductOptionJpaRepository : JpaRepository<ProductOption, Long> {
    fun findAllByProductId(productId: Long): MutableList<ProductOption>
}
