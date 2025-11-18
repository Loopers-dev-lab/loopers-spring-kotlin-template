package com.loopers.domain.product

import org.springframework.data.domain.Slice

interface ProductRepository {
    fun findAllBy(query: PageQuery): Slice<Product>
    fun findAllByIds(ids: List<Long>): List<Product>
    fun findAllByIdsWithLock(ids: List<Long>): List<Product>
    fun findById(id: Long): Product?
    fun save(product: Product): Product
    fun saveAll(products: List<Product>): List<Product>
}
