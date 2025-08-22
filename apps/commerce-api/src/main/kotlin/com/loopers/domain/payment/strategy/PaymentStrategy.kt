package com.loopers.domain.payment.strategy

import com.loopers.domain.order.entity.Order
import com.loopers.domain.payment.entity.Payment

interface PaymentStrategy {
    fun supports(): Payment.Method
    fun process(order: Order, payment: Payment)
}
