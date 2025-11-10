package com.loopers.domain.product

interface ProductRepository {
    fun save(product: Product): Product
    fun findById(id: Long): Product?
    fun findByIdWithLock(id: Long): Product?
    fun findAll(): List<Product>
    fun findByBrandId(brandId: Long): List<Product>
    fun existsById(id: Long): Boolean
}
