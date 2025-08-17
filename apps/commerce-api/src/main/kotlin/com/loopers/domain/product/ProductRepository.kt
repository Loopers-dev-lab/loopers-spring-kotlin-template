package com.loopers.domain.product

import com.loopers.domain.product.dto.criteria.ProductCriteria
import com.loopers.domain.product.entity.Product

interface ProductRepository {
    fun find(id: Long): Product?

    fun findAll(ids: List<Long>): List<Product>

    fun findAll(criteria: ProductCriteria.FindAll): List<Product>

    fun count(criteria: ProductCriteria.FindAll): Long

    fun save(product: Product): Product

    fun delete(id: Long)
}
