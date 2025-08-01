package com.loopers.domain.product

import com.loopers.domain.product.entity.ProductStock

interface ProductStockRepository {
    fun findAll(productOptionIds: List<Long>): List<ProductStock>

    fun save(productStock: ProductStock): ProductStock
}
