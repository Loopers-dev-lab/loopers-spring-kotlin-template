package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface ProductJpaRepository : JpaRepository<Product, Long> {
    fun findByBrandId(brandId: Long): List<Product>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    fun findByIdWithLock(id: Long): Product?
}
