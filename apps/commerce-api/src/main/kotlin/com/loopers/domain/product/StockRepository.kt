package com.loopers.domain.product

interface StockRepository {
    fun findByProductId(productId: Long): Stock?
    fun findByProductIdWithLock(productId: Long): Stock?
    fun save(stock: Stock): Stock
}
