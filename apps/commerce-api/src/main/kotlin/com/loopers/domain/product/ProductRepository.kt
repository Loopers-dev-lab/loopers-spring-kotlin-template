package com.loopers.domain.product

import com.loopers.application.product.ProductInfo
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ProductRepository {
    fun findById(productId: Long): ProductModel?

    fun save(product: ProductModel): ProductModel

    fun findAllProductInfos(pageable: Pageable): Page<ProductInfo>

    fun getProductBy(productId: Long): ProductModel
}
