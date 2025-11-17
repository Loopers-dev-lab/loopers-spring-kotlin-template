package com.loopers.infrastructure.product

import com.loopers.domain.product.Stock
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints

interface StockJpaRepository : JpaRepository<Stock, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT s FROM Stock s WHERE s.productId IN :productIds ORDER BY s.productId ASC")
    fun findAllByProductIdIn(productIds: List<Long>): List<Stock>
}
