package com.loopers.domain.order.vo

import com.loopers.domain.order.policy.OrderItemValidator

@JvmInline
value class OrderItemQuantity(
    val value: Int,
) {
    init {
        OrderItemValidator.validateQuantity(value)
    }
}
