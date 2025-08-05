package com.loopers.domain.order.policy

object OrderItemPolicy {
    object Quantity {
        const val MESSAGE = "0원보다 작을 수 없습니다."
        const val MIN = 0
    }
}
