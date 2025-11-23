package com.loopers.domain.product

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ProductRepository {
    fun findById(id: Long): Product?
    fun findAllById(ids: List<Long>): List<Product>
    fun findByIdInAndDeletedAtIsNull(ids: List<Long>): List<Product>
    fun findAll(brandId: Long?, sort: String, pageable: Pageable): Page<Product>
    fun save(product: Product): Product
    fun saveAll(products: List<Product>): List<Product>
    fun existsById(id: Long): Boolean

    /**
     * 비관적 락(PESSIMISTIC_WRITE)으로 상품을 조회합니다.
     * Redis 장애 시 fallback으로 사용됩니다.
     */
    fun findByIdWithLock(id: Long): Product?
}
