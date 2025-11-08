package com.loopers.application.order

import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class OrderFacade(
    private val orderService: OrderService,
    private val orderRepository: OrderRepository
) {
    fun createOrder(userId: Long, request: OrderCreateRequest): OrderCreateInfo {
        val orderItemRequests = request.items.map {
            com.loopers.domain.order.OrderItemRequest(
                productId = it.productId,
                quantity = it.quantity
            )
        }
        val order = orderService.createOrder(userId, orderItemRequests)
        return OrderCreateInfo.from(order)
    }

    fun getOrders(userId: Long, pageable: Pageable): Page<OrderListInfo> {
        val orders = orderRepository.findByUserId(userId, pageable)
        return orders.map { OrderListInfo.from(it) }
    }

    fun getOrderDetail(userId: Long, orderId: Long): OrderDetailInfo {
        val order = orderRepository.findById(orderId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다: $orderId")

        if (!order.isOwnedBy(userId)) {
            throw CoreException(ErrorType.FORBIDDEN, "다른 사용자의 주문입니다")
        }

        return OrderDetailInfo.from(order)
    }
}
