package com.loopers.application.order

import com.loopers.domain.order.OrderService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class OrderStateService(
    private val orderService: OrderService,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun orderFailure(orderId: Long, reason: String) {
        val order = orderService.get(orderId)
        order.failure(reason)
    }
}
