package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface ProductJpaRepository : JpaRepository<Product, Long> {
    @Query("SELECT p FROM Product p WHERE p.brandId = :brandId AND p.deletedAt IS NULL")
    fun findByBrandId(brandId: Long, pageable: Pageable): Page<Product>

    @Query("SELECT COUNT(p) FROM Product p WHERE p.brandId = :brandId AND p.deletedAt IS NULL")
    fun countByBrandId(brandId: Long): Long

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.deletedAt IS NULL")
    fun findByIdWithLock(id: Long): Product?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id IN :ids AND p.deletedAt IS NULL")
    fun findAllByIdInWithLock(ids: List<Long>): List<Product>
}
