package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderInfo

class OrderV1Response {
    data class PlaceOrder(
        val orderId: Long,
    ) {
        companion object {
            fun from(info: OrderInfo.PlaceOrder): PlaceOrder {
                return PlaceOrder(
                    orderId = info.orderId,
                )
            }
        }
    }
}
