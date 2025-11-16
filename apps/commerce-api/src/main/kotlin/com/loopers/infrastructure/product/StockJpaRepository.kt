package com.loopers.infrastructure.product

import com.loopers.domain.product.Stock
import org.springframework.data.jpa.repository.JpaRepository

interface StockJpaRepository : JpaRepository<Stock, Long> {
    fun findAllByProductIdIn(productIds: List<Long>): List<Stock>
}
