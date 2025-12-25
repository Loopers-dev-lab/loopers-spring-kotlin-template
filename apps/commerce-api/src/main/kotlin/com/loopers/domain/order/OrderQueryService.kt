package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class OrderQueryService(private val orderRepository: OrderRepository) {
    fun getOrders(userId: Long, pageable: Pageable): Page<Order> = orderRepository.findByUserId(userId, pageable)

    fun getOrderDetail(userId: Long, orderId: Long): Order {
        val order = orderRepository.findById(orderId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다: $orderId")

        if (!order.isOwnedBy(userId)) {
            throw CoreException(ErrorType.FORBIDDEN, "다른 사용자의 주문입니다")
        }

        return order
    }
}
