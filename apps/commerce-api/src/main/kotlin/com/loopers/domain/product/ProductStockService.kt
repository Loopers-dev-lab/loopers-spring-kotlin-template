package com.loopers.domain.product

import com.loopers.domain.product.dto.command.ProductStockCommand
import com.loopers.domain.product.entity.ProductStock
import org.springframework.stereotype.Component

@Component
class ProductStockService(
    private val productStockRepository: ProductStockRepository,
) {
    fun findAll(productOptionIds: List<Long>): List<ProductStock> {
        return productStockRepository.findAll(productOptionIds)
    }

    fun decreaseStock(command: ProductStockCommand.DecreaseStocks) {
        val decreaseMap = command.decreaseStocks.associateBy { it.productOptionId }

        val productStocks = findAll(decreaseMap.keys.toList())
        productStocks.forEach { productStock ->
            val quantity = decreaseMap[productStock.productOptionId]?.quantity
                ?: throw IllegalArgumentException("감소 수량이 존재하지 않습니다. optionId=${productStock.productOptionId}")

            productStock.deduct(quantity)
        }
    }
}
