package com.loopers.domain.product

interface StockRepository {
    fun findByProductId(productId: Long): Stock?
    fun findByProductIdWithLock(productId: Long): Stock?
    fun findAllByProductIds(productIds: List<Long>): List<Stock>
    fun findAllByProductIdsWithLock(productIds: List<Long>): List<Stock>
    fun save(stock: Stock): Stock
    fun saveAll(stocks: List<Stock>): List<Stock>
}
