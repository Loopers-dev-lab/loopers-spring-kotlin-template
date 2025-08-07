package com.loopers.domain.product

import com.loopers.domain.product.entity.ProductStock

interface ProductStockRepository {
    fun find(productStockId: Long): ProductStock?

    fun findAll(productOptionIds: List<Long>): List<ProductStock>

    fun findAllWithLock(productOptionIds: List<Long>): List<ProductStock>

    fun save(productStock: ProductStock): ProductStock
}
