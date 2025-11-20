package com.loopers.domain.product.stock

interface StockRepository {
    fun getStockByProductIdWithPessimisticLock(productId: Long): StockModel

    fun save(stockModel: StockModel): StockModel

    fun findByRefProductId(refProductId: Long): StockModel?
}
