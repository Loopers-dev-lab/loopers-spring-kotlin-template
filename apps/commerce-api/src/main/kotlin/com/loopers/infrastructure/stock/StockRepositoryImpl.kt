package com.loopers.infrastructure.stock

import com.loopers.domain.stock.Stock
import com.loopers.domain.stock.StockRepository
import org.springframework.stereotype.Component

@Component
class StockRepositoryImpl(
    private val stockJpaRepository: StockJpaRepository,
) : StockRepository {
    override fun save(stock: Stock): Stock {
        return stockJpaRepository.save(stock)
    }

    override fun findByProductIdWithLock(productId: Long): Stock? {
        return stockJpaRepository.findByProductIdWithLock(productId)
    }
}
