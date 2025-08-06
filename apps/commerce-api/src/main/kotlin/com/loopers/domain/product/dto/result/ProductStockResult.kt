package com.loopers.domain.product.dto.result

import com.loopers.domain.product.dto.command.ProductStockCommand
import com.loopers.domain.product.entity.ProductStock

class ProductStockResult {
    data class DecreaseStock(
        val productStock: ProductStock,
        val quantity: Int,
    )

    data class DecreaseStocks(
        val decreaseStocks: List<DecreaseStock>,
    ) {
        fun toCommand(): ProductStockCommand.DecreaseStocks {
            return ProductStockCommand.DecreaseStocks(
                decreaseStocks.map {
                    ProductStockCommand.DecreaseStock(
                        it.productStock,
                        it.quantity,
                    )
                },
            )
        }
    }
}
