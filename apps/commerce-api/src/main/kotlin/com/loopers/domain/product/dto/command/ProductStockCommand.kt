package com.loopers.domain.product.dto.command

class ProductStockCommand {
    data class DecreaseStocks(
        val decreaseStocks: List<DecreaseStock>,
    ) {
        data class DecreaseStock(
            val productOptionId: Long,
            val quantity: Int,
        )
    }
}
