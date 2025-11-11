package com.loopers.domain.product

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ProductRepository {
    fun findBy(id: Long): Product?
    fun findAllBy(ids: List<Long>): List<Product>
    fun findAll(brandId: Long?, sort: ProductSort, pageable: Pageable): Page<Product>
    fun findStockAllBy(productIds: List<Long>): List<Stock>
}
