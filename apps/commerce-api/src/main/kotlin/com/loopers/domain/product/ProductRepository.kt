package com.loopers.domain.product

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ProductRepository {
    fun findBy(productId: Long): Product?
    fun findAll(brandId: Long?, sort: ProductSort, pageable: Pageable): Page<Product>
}
