package com.loopers.domain.stock

interface StockRepository {
    fun save(stock: Stock): Stock
}
