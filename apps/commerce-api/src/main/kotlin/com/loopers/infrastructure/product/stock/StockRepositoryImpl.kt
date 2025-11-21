package com.loopers.infrastructure.product.stock

import com.loopers.domain.product.stock.StockModel
import com.loopers.domain.product.stock.StockRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class StockRepositoryImpl(private val stockJpaRepository: StockJpaRepository) : StockRepository {
    override fun getStockByProductIdWithPessimisticLock(productId: Long): StockModel =
        stockJpaRepository.getStockByProductIdWithPessimisticLock(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "재고가 존재하지 않습니다.")

    override fun save(stockModel: StockModel): StockModel = stockJpaRepository.save(stockModel)

    override fun findByRefProductId(refProductId: Long): StockModel? = stockJpaRepository.findByRefProductId(refProductId)
}
