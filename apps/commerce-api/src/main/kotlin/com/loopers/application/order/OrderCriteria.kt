package com.loopers.application.order

import com.loopers.domain.product.ProductCommand
import com.loopers.support.values.Money

class OrderCriteria {
    data class PlaceOrder(
        val userId: Long,
        val usePoint: Money,
        val items: List<PlaceOrderItem>,
        val issuedCouponId: Long?,
    ) {
        fun to(): ProductCommand.DecreaseStocks {
            return ProductCommand.DecreaseStocks(
                units = items.map {
                    ProductCommand.DecreaseStockUnit(
                        productId = it.productId,
                        amount = it.quantity,
                    )
                },
            )
        }
    }

    data class PlaceOrderItem(
        val productId: Long,
        val quantity: Int,
    )
}
