package com.loopers.domain.stock

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class StockService(
    private val stockRepository: StockRepository,
) {

    @Transactional
    fun deductStock(productId: Long, quantity: Int) {
        val stock = stockRepository.findByProductIdWithLock(productId)
            ?: throw CoreException(ErrorType.OUT_OF_STOCK, "재고 정보가 없습니다. [productId: $productId]")
        stock.deduct(quantity)
    }
}
