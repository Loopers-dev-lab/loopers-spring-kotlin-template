package com.loopers.domain.order

sealed interface OrderEvent {
    val orderId: Long
    val userId: Long
    val couponId: Long?
}

data class OrderSuccessEvent(override val orderId: Long, override val userId: Long, override val couponId: Long?) : OrderEvent {
    companion object {
        fun from(order: OrderModel, couponId: Long?) =
            OrderSuccessEvent(order.id, order.refUserId, couponId)
    }
}
