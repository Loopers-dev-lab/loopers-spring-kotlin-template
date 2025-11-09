package com.loopers.application.order

import com.loopers.domain.order.OrderQueryService
import com.loopers.domain.order.OrderService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class OrderFacade(
    private val orderService: OrderService,
    private val orderQueryService: OrderQueryService,
) {
    fun createOrder(userId: Long, request: OrderCreateRequest): OrderCreateInfo {
        val orderItemRequests = request.items.map {
            com.loopers.domain.order.OrderItemRequest(
                productId = it.productId,
                quantity = it.quantity,
            )
        }
        val order = orderService.createOrder(userId, orderItemRequests)
        return OrderCreateInfo.from(order)
    }

    fun getOrders(userId: Long, pageable: Pageable): Page<OrderListInfo> {
        val orders = orderQueryService.getOrders(userId, pageable)
        return orders.map { OrderListInfo.from(it) }
    }

    fun getOrderDetail(userId: Long, orderId: Long): OrderDetailInfo {
        val order = orderQueryService.getOrderDetail(userId, orderId)
        return OrderDetailInfo.from(order)
    }
}
