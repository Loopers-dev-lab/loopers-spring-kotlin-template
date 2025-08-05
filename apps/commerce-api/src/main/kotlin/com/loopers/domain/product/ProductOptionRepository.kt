package com.loopers.domain.product

import com.loopers.domain.product.entity.ProductOption

interface ProductOptionRepository {
    fun find(productOptionId: Long): ProductOption?

    fun findAll(productOptionIds: List<Long>): List<ProductOption>

    fun findAll(productIds: Long): List<ProductOption>

    fun save(productOption: ProductOption): ProductOption
}
