package com.loopers.application.order

import com.loopers.domain.product.ProductCommand
import com.loopers.support.values.Money

class OrderCriteria {
    data class PlaceOrder(
        val userId: Long,
        val usePoint: Money,
        val items: List<PlaceOrderItem>,
        val issuedCouponId: Long?,
        val cardType: String? = null,
        val cardNo: String? = null,
    ) {
        /**
         * 카드 결제가 필요한지 여부
         */
        fun requiresCardPayment(): Boolean = cardType != null && cardNo != null

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
