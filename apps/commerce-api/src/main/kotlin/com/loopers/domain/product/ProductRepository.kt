package com.loopers.domain.product

import com.loopers.domain.common.PageCommand
import com.loopers.domain.common.PageResult

interface ProductRepository {
    fun save(product: Product): Product
    fun findById(id: Long): Product?
    fun getProducts(pageCommand: PageCommand): PageResult<ProductResult.ProductInfo>
}
