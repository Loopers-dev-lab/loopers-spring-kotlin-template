package com.loopers.domain.product

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductService(
    private val productRepository: ProductRepository,
) {
    @Transactional(readOnly = true)
    fun getProduct(productId: Long): Product {
        return productRepository.findByIdOrThrow(productId)
    }

    @Transactional(readOnly = true)
    fun getProducts(
        brandId: Long?,
        sort: ProductSortType,
        pageable: Pageable,
    ): Page<Product> {
        return productRepository.findAll(brandId, sort, pageable)
    }

    @Transactional(readOnly = true)
    fun getProductsByIds(productIds: List<Long>): List<Product> {
        return productRepository.findAllByIdIn(productIds)
    }
}
