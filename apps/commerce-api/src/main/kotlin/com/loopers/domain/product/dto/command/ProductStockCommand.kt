package com.loopers.domain.product.dto.command

import com.loopers.domain.product.entity.ProductStock

class ProductStockCommand {
    data class GetDecreaseStock(
        val decreaseStocks: List<DecreaseStock>,
    ) {
        data class DecreaseStock(
            val productOptionId: Long,
            val quantity: Int,
        )
    }

    data class DecreaseStock(
        val productStock: ProductStock,
        val quantity: Int,
    )

    data class DecreaseStocks(
        val decreaseStocks: List<DecreaseStock>,
    )
}
