package com.loopers.domain.order.vo

import com.loopers.domain.order.policy.OrderValidator
import java.math.BigDecimal

@JvmInline
value class OrderFinalPrice(
    val value: BigDecimal,
) {
    init {
        OrderValidator.validateFinalPrice(value)
    }
}
