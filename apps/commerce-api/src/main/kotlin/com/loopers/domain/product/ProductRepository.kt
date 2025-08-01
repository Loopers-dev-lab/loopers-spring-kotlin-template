package com.loopers.domain.product

import com.loopers.domain.product.dto.criteria.ProductCriteria
import com.loopers.domain.product.entity.Product
import org.springframework.data.domain.Page

interface ProductRepository {
    fun find(id: Long): Product?

    fun findAll(ids: List<Long>): List<Product>

    fun findAll(criteria: ProductCriteria.FindAll): Page<Product>

    fun save(product: Product): Product
}
