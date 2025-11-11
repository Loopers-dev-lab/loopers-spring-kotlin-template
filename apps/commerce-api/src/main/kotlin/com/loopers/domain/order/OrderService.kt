package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class OrderService(
    private val orderRepository: OrderRepository,
) {
    fun getOrders(userId: Long, pageable: Pageable): Page<Order> {
        return orderRepository.findAllBy(userId, pageable)
    }

    fun getOrder(id: Long, userId: Long): Order {
        val order = orderRepository.findById(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다: $userId")

        order.validateOwner(userId)

        return order
    }

    fun getOrderDetail(orderId: Long): List<OrderDetail> {
        return orderRepository.findAllOrderDetailBy(orderId)
    }
}
