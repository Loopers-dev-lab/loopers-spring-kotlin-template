package com.loopers.application.order

import com.loopers.domain.order.CreateOrderCommand
import com.loopers.domain.order.OrderItemCommand
import com.loopers.domain.order.OrderService
import org.springframework.stereotype.Component

@Component
class OrderFacade(
    private val orderService: OrderService,
) {
    /**
     * 주문 생성
     */
    fun createOrder(request: CreateOrderRequest): OrderInfo {
        val order = orderService.createOrder(
            CreateOrderCommand(
                memberId = request.memberId,
                items = request.items.map {
                    OrderItemCommand(it.productId, it.quantity)
                },
                couponId = request.couponId
            )
        )

        return OrderInfo.from(order)
    }

}
