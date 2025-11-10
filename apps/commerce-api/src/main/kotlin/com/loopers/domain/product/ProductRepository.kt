package com.loopers.domain.product

interface ProductRepository {
    fun findById(productId: Long): ProductModel?

    fun save(product: ProductModel): ProductModel
}
