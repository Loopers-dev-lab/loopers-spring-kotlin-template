package com.loopers.infrastructure.product

import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class StockRdbRepository(
    private val stockJpaRepository: StockJpaRepository,
) : StockRepository {

    @Transactional(readOnly = true)
    override fun findByProductId(productId: Long): Stock? {
        return stockJpaRepository.findByProductId(productId)
    }

    @Transactional
    override fun findByProductIdWithLock(productId: Long): Stock? {
        return stockJpaRepository.findByProductIdWithLock(productId)
    }

    @Transactional(readOnly = true)
    override fun findAllByProductIds(productIds: List<Long>): List<Stock> {
        if (productIds.isEmpty()) return emptyList()
        return stockJpaRepository.findAllByProductIdIn(productIds)
    }

    @Transactional
    override fun findAllByProductIdsWithLock(productIds: List<Long>): List<Stock> {
        if (productIds.isEmpty()) return emptyList()
        return stockJpaRepository.findAllByProductIdsWithLock(productIds)
    }

    @Transactional
    override fun save(stock: Stock): Stock {
        return stockJpaRepository.save(stock)
    }

    @Transactional
    override fun saveAll(stocks: List<Stock>): List<Stock> {
        if (stocks.isEmpty()) return emptyList()
        return stockJpaRepository.saveAll(stocks)
    }
}
