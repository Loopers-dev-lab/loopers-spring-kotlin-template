package com.loopers.infrastructure.stock

import com.loopers.domain.stock.Stock
import org.springframework.data.jpa.repository.JpaRepository

interface StockJpaRepository : JpaRepository<Stock, Long>
