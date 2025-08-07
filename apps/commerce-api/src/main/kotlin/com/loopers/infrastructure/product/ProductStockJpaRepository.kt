package com.loopers.infrastructure.product

import com.loopers.domain.product.entity.ProductStock
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface ProductStockJpaRepository : JpaRepository<ProductStock, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ps FROM ProductStock ps WHERE ps.id in :productOptionIds")
    fun findAllWithLock(productOptionIds: List<Long>): List<ProductStock>
}
