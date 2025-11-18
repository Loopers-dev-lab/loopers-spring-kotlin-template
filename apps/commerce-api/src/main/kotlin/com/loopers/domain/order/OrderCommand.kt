package com.loopers.domain.order

import com.loopers.support.values.Money

class OrderCommand {
    data class PlaceOrder(
        val userId: Long,
        val usePoint: Money,
        val items: List<PlaceOrderItem>,
    )

    data class PlaceOrderItem(
        val productId: Long,
        val productName: String,
        val quantity: Int,
        val currentPrice: Money,
    )
}
