package com.loopers.application.order

import com.loopers.domain.order.PaymentStatus

class OrderInfo {
    data class PlaceOrder(
        val orderId: Long,
        val paymentId: Long,
        val paymentStatus: PaymentStatus,
    )
}
