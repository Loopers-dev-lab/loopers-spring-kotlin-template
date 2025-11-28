package com.loopers.domain.product

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ProductRepository {
    fun save(product: Product): Product
    fun findById(id: Long): Product?
    fun findByIdOrThrow(id: Long): Product
    fun findByIdWithLock(id: Long): Product?
    fun findByIdWithLockOrThrow(id: Long): Product
    fun findAll(
        brandId: Long?,
        sort: ProductSortType,
        pageable: Pageable,
    ) : Page<Product>
    fun count(brandId: Long?): Long
    fun findAllByIdIn(ids: List<Long>): List<Product>
    fun findAllByIdInWithLock(ids: List<Long>): List<Product>
}

enum class ProductSortType {
    LATEST, // 최신순
    PRICE_ASC, // 가격 낮은순
    LIKES_DESC, // 좋아요 많은순
}
