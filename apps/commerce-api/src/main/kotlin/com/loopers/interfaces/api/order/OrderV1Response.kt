package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderInfo
import com.loopers.domain.order.PaymentStatus

class OrderV1Response {
    data class PlaceOrder(
        val orderId: Long,
        val paymentId: Long,
        val paymentStatus: PaymentStatus,
    ) {
        companion object {
            fun from(info: OrderInfo.PlaceOrder): PlaceOrder {
                return PlaceOrder(
                    orderId = info.orderId,
                    paymentId = info.paymentId,
                    paymentStatus = info.paymentStatus,
                )
            }
        }
    }
}
