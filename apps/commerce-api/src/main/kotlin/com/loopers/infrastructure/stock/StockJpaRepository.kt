package com.loopers.infrastructure.stock

import com.loopers.domain.stock.Stock
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface StockJpaRepository : JpaRepository<Stock, Long> {
    fun findByProductId(productId: Long): Stock?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Stock s WHERE s.productId = :productId")
    fun findByProductIdWithLock(productId: Long): Stock?
}
