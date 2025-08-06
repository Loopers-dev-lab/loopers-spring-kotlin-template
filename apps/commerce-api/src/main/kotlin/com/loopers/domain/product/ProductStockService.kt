package com.loopers.domain.product

import com.loopers.domain.product.dto.command.ProductStockCommand
import com.loopers.domain.product.dto.command.ProductStockCommand.DecreaseStock
import com.loopers.domain.product.dto.command.ProductStockCommand.DecreaseStocks
import com.loopers.domain.product.dto.result.ProductStockResult
import org.springframework.stereotype.Component

@Component
class ProductStockService(
    private val productStockRepository: ProductStockRepository,
) {
    fun getDecreaseStock(command: ProductStockCommand.GetDecreaseStock): ProductStockResult.DecreaseStocks {
        val decreaseMap = command.decreaseStocks.associateBy { it.productOptionId }
        val stocks = productStockRepository.findAll(decreaseMap.keys.toList())

        return ProductStockResult.DecreaseStocks(
            stocks.map { stock ->
                val quantity = decreaseMap[stock.productOptionId]?.quantity ?: 0
                ProductStockResult.DecreaseStock(stock, quantity)
            },
        )
    }

    fun decreaseStock(command: DecreaseStock): Int {
        return command.productStock.deduct(command.quantity)
    }

    fun decreaseStocks(command: DecreaseStocks) {
        command.decreaseStocks.forEach { decreaseStock(it) }
    }
}
