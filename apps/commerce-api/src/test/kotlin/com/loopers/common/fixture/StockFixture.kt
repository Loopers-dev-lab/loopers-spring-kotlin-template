package com.loopers.common.fixture

import com.loopers.domain.stock.Stock

object StockFixture {

    fun create(
        productId: Long = 100L,
        quantity: Int = 100,
    ): Stock {
        return Stock.of(
            productId = productId,
            quantity = quantity,
        )
    }
}
