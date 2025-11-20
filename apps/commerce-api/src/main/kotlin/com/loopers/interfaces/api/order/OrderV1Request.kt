package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderCriteria
import com.loopers.support.values.Money

class OrderV1Request {
    data class PlaceOrder(
        val items: List<PlaceOrderItem>,
        val usePoint: Int? = null,
        val issuedCouponId: Long? = null,
    ) {
        fun toCriteria(userId: Long): OrderCriteria.PlaceOrder {
            return OrderCriteria.PlaceOrder(
                userId = userId,
                items = items.map { it.toCriteria() },
                usePoint = usePoint?.let { Money.krw(it) } ?: Money.ZERO_KRW,
                issuedCouponId = issuedCouponId,
            )
        }
    }

    data class PlaceOrderItem(
        val productId: Long,
        val quantity: Int,
    ) {
        fun toCriteria(): OrderCriteria.PlaceOrderItem {
            return OrderCriteria.PlaceOrderItem(
                productId = productId,
                quantity = quantity,
            )
        }
    }
}
