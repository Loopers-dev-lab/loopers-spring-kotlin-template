package com.loopers.infrastructure.product

import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockRepository
import org.springframework.stereotype.Component

@Component
class StockRepositoryImpl(
    private val stockJpaRepository: StockJpaRepository,
) : StockRepository {
    override fun findByProductId(
        productId: Long,
    ): Stock? = stockJpaRepository.findByProductId(productId)

    override fun findByProductIdWithLock(
        productId: Long,
    ): Stock? = stockJpaRepository.findByProductIdWithLock(productId)

    override fun save(stock: Stock): Stock = stockJpaRepository.save(stock)
}
