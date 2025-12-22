package com.loopers.domain.product

import com.loopers.application.product.ProductInfo
import com.loopers.application.ranking.ProductWithBrand
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ProductRepository {
    fun findById(productId: Long): ProductModel?

    fun save(product: ProductModel): ProductModel

    fun findAllProductInfos(pageable: Pageable, brandId: Long?): Page<ProductInfo>

    fun getProductBy(productId: Long): ProductModel

    fun findByIdsIn(productIds: List<Long>): List<ProductModel>

    fun findByIdsInWithBrand(productIds: List<Long>): List<ProductWithBrand>
}
