package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ProductJpaRepository : JpaRepository<Product, Long> {
    fun findByBrandId(brandId: Long, pageable: Pageable): Page<Product>
}
