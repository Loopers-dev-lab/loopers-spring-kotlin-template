package com.loopers.domain.product

interface ProductRepository {
    fun save(product: Product): Product
    fun findById(id: Long): Product?
}
